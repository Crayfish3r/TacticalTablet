package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.anticheat.AntiCheatManager;
import com.makar.tacticaltablet.anticheat.Severity;
import com.makar.tacticaltablet.anticheat.ViolationType;
import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.progression.kit.KitManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class TabletPacket {

    private static final int RTP_ACTION_ID = 7;
    private static final int MIN_ACTION_ID = 0;
    private static final int MAX_ACTION_ID = 314;
    private static final int UNLOCK_BASE_ACTION_OFFSET = 100;
    private static final int UPGRADE_EPIC_ACTION_OFFSET = 200;
    private static final int UPGRADE_LEGEND_ACTION_OFFSET = 300;

    private static final Map<Integer, String> KITS = Map.ofEntries(
            Map.entry(0, "stormtrooper"),
            Map.entry(1, "sniper"),
            Map.entry(2, "scout"),
            Map.entry(3, "droneoperator"),
            Map.entry(4, "boomguy"),
            Map.entry(5, "mortarman"),
            Map.entry(6, "dream"),
            Map.entry(8, "machinegunner"),
            Map.entry(9, "rpgtrooper"),
            Map.entry(10, "tagilla"),
            Map.entry(11, "blackops"),
            Map.entry(12, "cowboy"),
            Map.entry(13, "solider"),
            Map.entry(14, "rebel"),
            Map.entry(15, "saboteur"),
            Map.entry(16, "killer"),
            Map.entry(17, "miniboss"),
            Map.entry(18, "shahed"),
            Map.entry(19, "krot"),
            Map.entry(20, "marine"),
            Map.entry(21, "medic"),
            Map.entry(22, "microwave"),
            Map.entry(23, "railgunner")
    );

    private final int actionId;

    public TabletPacket(int actionId) {
        this.actionId = actionId;
    }

    public TabletPacket(FriendlyByteBuf buf) {
        this.actionId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(actionId);
    }

    public static void reset(ServerPlayer player) {
        PacketHandler.clearC2SRateLimits(player);
    }

    public static void resetAll() {
        PacketHandler.clearAllC2SRateLimits();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (actionId < MIN_ACTION_ID || actionId > MAX_ACTION_ID) {
                AntiCheatManager.record(
                        player,
                        ViolationType.INVALID_TABLET_PACKET,
                        Severity.HIGH,
                        "invalid actionId=" + actionId
                );
                return;
            }

            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.TABLET)) {
                LobbyManager.sync(player);
                return;
            }

            if (!InventoryManager.hasTablet(player)) {
                AntiCheatManager.record(
                        player,
                        ViolationType.INVALID_TABLET_PACKET,
                        Severity.HIGH,
                        "tablet packet without tablet actionId=" + actionId
                );
                LobbyManager.sync(player);
                return;
            }

            if (actionId == RTP_ACTION_ID) {
                handleRtp(player);
                return;
            }

            if (isUnlockBaseAction(actionId)) {
                handleBaseUnlock(player, actionId - UNLOCK_BASE_ACTION_OFFSET);
                return;
            }

            if (isUpgradeEpicAction(actionId)) {
                handleTierUpgrade(player, actionId - UPGRADE_EPIC_ACTION_OFFSET, PlayerProgressManager.EPIC_TIER);
                return;
            }

            if (isUpgradeLegendAction(actionId)) {
                handleTierUpgrade(player, actionId - UPGRADE_LEGEND_ACTION_OFFSET, PlayerProgressManager.LEGEND_TIER);
                return;
            }

            String kit = KITS.get(actionId);
            if (kit == null) {
                AntiCheatManager.record(
                        player,
                        ViolationType.INVALID_TABLET_PACKET,
                        Severity.HIGH,
                        "unknown actionId=" + actionId
                );
                return;
            }

            if (PlayerProgressManager.isShopClass(kit) && !PlayerProgressManager.isClassPurchased(player, kit)) {
                handleShopPurchase(player, kit);
                return;
            }

            handleKit(player, kit);
        });

        ctx.get().setPacketHandled(true);
    }

    public static int unlockBaseActionId(int classActionId) {
        return UNLOCK_BASE_ACTION_OFFSET + classActionId;
    }

    public static int upgradeEpicActionId(int classActionId) {
        return UPGRADE_EPIC_ACTION_OFFSET + classActionId;
    }

    public static int upgradeLegendActionId(int classActionId) {
        return UPGRADE_LEGEND_ACTION_OFFSET + classActionId;
    }

    private static boolean isUnlockBaseAction(int id) {
        return id >= UNLOCK_BASE_ACTION_OFFSET && id < UPGRADE_EPIC_ACTION_OFFSET;
    }

    private static boolean isUpgradeEpicAction(int id) {
        return id >= UPGRADE_EPIC_ACTION_OFFSET && id < UPGRADE_LEGEND_ACTION_OFFSET;
    }

    private static boolean isUpgradeLegendAction(int id) {
        return id >= UPGRADE_LEGEND_ACTION_OFFSET && id <= MAX_ACTION_ID;
    }

    private void handleShopPurchase(ServerPlayer player, String kit) {
        if (MapSetManager.isCompetitiveSet()) {
            player.sendSystemMessage(Component.literal("[WAR] Магазинные классы недоступны в соревновательном режиме."));
            LobbyManager.sync(player);
            return;
        }
        PlayerProgressManager.PurchaseResult result = PlayerProgressManager.purchaseClass(player, kit);

        switch (result) {
            case PURCHASED -> {
                player.sendSystemMessage(Component.literal("[WAR] Куплен класс " + getDisplayName(kit)
                        + " за " + PlayerProgressManager.getShopPrice(kit) + " монет."));
                PlayerProgressManager.savePlayer(player);
            }
            case ALREADY_OWNED -> player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit) + " уже куплен."));
            case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Не хватает монет для " + getDisplayName(kit)
                    + ". Нужно " + PlayerProgressManager.getShopPrice(kit) + " монет."));
            case NOT_PURCHASABLE -> player.sendSystemMessage(Component.literal("[WAR] Этот класс нельзя купить."));
        }

        LobbyManager.sync(player);
    }

    private void handleBaseUnlock(ServerPlayer player, int classActionId) {
        String kit = KITS.get(classActionId);
        if (kit == null || !PlayerProgressManager.isUnlockableBaseClass(kit)) {
            AntiCheatManager.record(
                    player,
                    ViolationType.INVALID_TABLET_PACKET,
                    Severity.HIGH,
                    "invalid base unlock actionId=" + actionId
            );
            LobbyManager.sync(player);
            return;
        }

        PlayerProgressManager.ProgressionResult result = PlayerProgressManager.unlockBaseClass(player, kit);
        switch (result) {
            case SUCCESS -> {
                player.sendSystemMessage(Component.literal("[WAR] Открыт класс " + getDisplayName(kit)
                        + " за " + PlayerProgressManager.BASE_UNLOCK_COST + " монет."));
                PlayerProgressManager.savePlayer(player);
            }
            case ALREADY_UNLOCKED -> player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit) + " уже открыт."));
            case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Не хватает монет для открытия " + getDisplayName(kit)
                    + ". Нужно " + PlayerProgressManager.BASE_UNLOCK_COST + " монет."));
            default -> player.sendSystemMessage(Component.literal("[WAR] Этот класс нельзя открыть через планшет."));
        }

        LobbyManager.sync(player);
    }

    private void handleTierUpgrade(ServerPlayer player, int classActionId, int targetTier) {
        String kit = KITS.get(classActionId);
        if (kit == null || !PlayerProgressManager.isBaseProgressionClass(kit)) {
            AntiCheatManager.record(
                    player,
                    ViolationType.INVALID_TABLET_PACKET,
                    Severity.HIGH,
                    "invalid class upgrade actionId=" + actionId
            );
            LobbyManager.sync(player);
            return;
        }

        PlayerProgressManager.ProgressionResult result = PlayerProgressManager.upgradeClassTier(player, kit, targetTier);
        String tierName = targetTier >= PlayerProgressManager.LEGEND_TIER ? "LEGEND" : "EPIC";
        switch (result) {
            case SUCCESS -> {
                player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit)
                        + " улучшен до " + tierName + " за " + PlayerProgressManager.getUpgradeCost(targetTier) + " монет."));
                PlayerProgressManager.savePlayer(player);
            }
            case LOCKED -> player.sendSystemMessage(Component.literal("[WAR] Сначала открой класс " + getDisplayName(kit) + "."));
            case NOT_ENOUGH_XP -> player.sendSystemMessage(Component.literal("[WAR] Недостаточно опыта для улучшения " + getDisplayName(kit) + "."));
            case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Не хватает монет для улучшения " + getDisplayName(kit)
                    + ". Нужно " + PlayerProgressManager.getUpgradeCost(targetTier) + " монет."));
            case MAX_TIER -> player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit) + " уже максимального уровня."));
            case WRONG_TIER -> player.sendSystemMessage(Component.literal("[WAR] Это улучшение сейчас недоступно."));
            default -> player.sendSystemMessage(Component.literal("[WAR] Этот класс нельзя улучшить."));
        }

        LobbyManager.sync(player);
    }

    private void handleRtp(ServerPlayer player) {
        String invalidReason = getInvalidRtpReason(player);
        if (!invalidReason.isEmpty()) {
            AntiCheatManager.record(
                    player,
                    ViolationType.INVALID_RTP,
                    getInvalidRtpSeverity(invalidReason),
                    invalidReason
            );
        }

        if (!GameStateManager.isRunning(player.server)) {
            player.sendSystemMessage(Component.literal("[WAR] Планшет недоступен до начала матча."));
            LobbyManager.sync(player);
            return;
        }

        if (MapSetManager.isClanWarSet()
                && !ClanManager.getClanIdForPlayer(player).isBlank()
                && !ClanManager.isClanOwner(player)) {
            player.sendSystemMessage(Component.literal("[WAR] В войне кланов RTP вручную запускает только глава клана."));
            LobbyManager.sync(player);
            return;
        }

        RtpTimerManager.forceRtp(player);
    }

    private String getInvalidRtpReason(ServerPlayer player) {
        if (!GameStateManager.isRunning(player.server)) {
            return "game not running";
        }

        if (!LivesManager.canContinueMatch(player)) {
            return "player eliminated";
        }

        if (!GameStateManager.isInLobby(player)) {
            return "not in lobby dimension";
        }

        if (!player.getTags().contains("in_lobby")) {
            return "missing in_lobby tag";
        }

        if (PlayerTabletState.isRtpUsed(player)) {
            return "rtp already used";
        }

        return "";
    }

    private Severity getInvalidRtpSeverity(String reason) {
        if ("not in lobby dimension".equals(reason)
                || "missing in_lobby tag".equals(reason)
                || "rtp already used".equals(reason)) {
            return Severity.HIGH;
        }

        return Severity.MEDIUM;
    }

    private void handleKit(ServerPlayer player, String kit) {
        if (MapSetManager.isCompetitiveSet() && PlayerProgressManager.isShopClass(kit)) {
            player.sendSystemMessage(Component.literal("[WAR] Магазинные классы недоступны в соревновательном режиме."));
            LobbyManager.sync(player);
            return;
        }
        if (MapSetManager.isClanWarSet()
                && !ClanWarManager.hasClan(player)
                && PlayerProgressManager.isShopClass(kit)) {
            player.sendSystemMessage(Component.literal("[WAR] В войне кланов одиночкам недоступны классы из магазина."));
            LobbyManager.sync(player);
            return;
        }
        if (PlayerProgressManager.isShopClass(kit) && !PlayerProgressManager.isClassPurchased(player, kit)) {
            handleShopPurchase(player, kit);
            return;
        }

        if (ClanManager.MARINE_CLASS.equals(kit)) {
            if (!ClanManager.isClanClassUnlocked(player, kit)) {
                ClanManager.Result result = ClanManager.purchaseClanClass(player, kit);
                switch (result) {
                    case SUCCESS -> player.sendSystemMessage(Component.literal("[WAR] Клан купил класс Морпех за 20KK."));
                    case NOT_OWNER -> player.sendSystemMessage(Component.literal("[WAR] Морпеха может купить только глава клана."));
                    case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Недостаточно KK. Нужно 20KK."));
                    case NOT_FOUND -> player.sendSystemMessage(Component.literal("[WAR] Для покупки Морпеха нужно состоять в клане."));
                    default -> player.sendSystemMessage(Component.literal("[WAR] Морпех сейчас недоступен."));
                }
                LobbyManager.sync(player);
                return;
            }
        }

        if (PlayerProgressManager.isExclusiveClass(kit)
                && !PlayerProgressManager.isExclusiveClassGranted(player, kit)) {
            AntiCheatManager.record(
                    player,
                    ViolationType.INVALID_TABLET_PACKET,
                    Severity.HIGH,
                    "locked exclusive class actionId=" + actionId
            );
            player.sendSystemMessage(Component.literal("[WAR] Этот эксклюзивный класс тебе не выдан."));
            LobbyManager.sync(player);
            return;
        }

        if (PlayerProgressManager.isBaseProgressionClass(kit) && !PlayerProgressManager.isBaseClassUnlocked(player, kit)) {
            player.sendSystemMessage(Component.literal("[WAR] Сначала открой класс " + getDisplayName(kit) + " за "
                    + PlayerProgressManager.BASE_UNLOCK_COST + " монет."));
            LobbyManager.sync(player);
            return;
        }

        if (!GameStateManager.isRunning(player.server)) {
            player.sendSystemMessage(Component.literal("[WAR] Планшет недоступен до начала матча."));
            LobbyManager.sync(player);
            return;
        }

        boolean inLobby = player.getTags().contains("in_lobby") || GameStateManager.isInLobby(player);
        boolean inBattle = player.getTags().contains("war.playing");

        if (!inLobby && !inBattle) {
            player.sendSystemMessage(Component.literal("[WAR] Сейчас нельзя выбрать класс."));
            LobbyManager.sync(player);
            return;
        }

        if (!LivesManager.canContinueMatch(player)) {
            player.sendSystemMessage(Component.literal("[WAR] Ты выбыл из матча."));
            LobbyManager.sync(player);
            return;
        }

        if (PlayerTabletState.isKitUsed(player)) {
            LobbyManager.sync(player);
            return;
        }

        if (ClassCooldownManager.isOnCooldown(player, actionId)) {
            LobbyManager.sync(player);
            return;
        }

        if (!KitManager.giveKit(player, kit)) {
            player.sendSystemMessage(Component.literal("[WAR] Не удалось выдать набор. Выбери другой класс или проверь конфиг наборов."));
            LobbyManager.sync(player);
            return;
        }

        PlayerTabletState.setSelectedClass(player, kit);
        PlayerTabletState.setKitUsed(player);

        if (PlayerTabletState.isRtpUsed(player)) {
            InventoryManager.clearTablets(player);
        }

        LobbyManager.sync(player);
    }

    private String getDisplayName(String kit) {
        if ("boomguy".equals(kit)) return "Подрывник";
        if ("dream".equals(kit)) return "Дрим";
        if ("rpgtrooper".equals(kit)) return "РПГ-боец";
        if ("droneoperator".equals(kit)) return "Оператор дрона";
        if ("machinegunner".equals(kit)) return "Пулемётчик";
        if ("mortarman".equals(kit)) return "Миномётчик";
        if ("stormtrooper".equals(kit)) return "Штурмовик";
        if ("sniper".equals(kit)) return "Снайпер";
        if ("scout".equals(kit)) return "Разведчик";
        if ("tagilla".equals(kit)) return "Тагилла";
        if ("blackops".equals(kit)) return "Спецназ";
        if ("cowboy".equals(kit)) return "Ковбой";
        if ("solider".equals(kit)) return "Солдат";
        if ("rebel".equals(kit)) return "Повстанец";
        if ("saboteur".equals(kit)) return "Диверсант";
        if ("killer".equals(kit)) return "Киллер";
        if ("miniboss".equals(kit)) return "Мини-Босс";
        if ("shahed".equals(kit)) return "Шахед оп.";
        if ("krot".equals(kit)) return "Крот";
        if ("marine".equals(kit)) return "Морпех";
        if ("medic".equals(kit)) return "Медик";
        if ("microwave".equals(kit)) return "Микровэйв";
        if ("railgunner".equals(kit)) return "Рэйл-ганнер";
        return kit;
    }
}
