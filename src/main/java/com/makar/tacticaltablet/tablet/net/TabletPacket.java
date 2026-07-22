package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchAdmissionManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.kit.KitManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.ClassDefinitions;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class TabletPacket {

    private static final int RTP_ACTION_ID = 7;
    private static final int MIN_ACTION_ID = 0;
    private static final int MAX_ACTION_ID = 523;
    private static final int UNLOCK_BASE_ACTION_OFFSET = 100;
    private static final int UPGRADE_ACTION_OFFSET_STEP = 100;

    private static final Map<Integer, String> KITS = ClassDefinitions.actionIdToClassKey();

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
                return;
            }

            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.TABLET)) {
                LobbyManager.sync(player);
                return;
            }

            if (!InventoryManager.hasTablet(player)) {
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

            Optional<UpgradeAction> upgrade = decodeUpgradeAction(actionId);
            if (upgrade.isPresent()) {
                handleTierUpgrade(player, upgrade.get().classActionId(), upgrade.get().targetTier());
                return;
            }

            String kit = KITS.get(actionId);
            if (kit == null) {
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

    public static int upgradeActionId(int classActionId, int targetTier) {
        if (!KITS.containsKey(classActionId) || ClassTier.byId(targetTier).isEmpty()
                || targetTier == ClassTier.BASIC.id()) {
            return -1;
        }
        return (targetTier + 1) * UPGRADE_ACTION_OFFSET_STEP + classActionId;
    }

    public static Optional<UpgradeAction> decodeUpgradeAction(int actionId) {
        if (actionId < 200 || actionId > MAX_ACTION_ID) return Optional.empty();
        int tier = actionId / UPGRADE_ACTION_OFFSET_STEP - 1;
        int classActionId = actionId % UPGRADE_ACTION_OFFSET_STEP;
        if (!KITS.containsKey(classActionId) || ClassTier.byId(tier).isEmpty() || tier == ClassTier.BASIC.id()) {
            return Optional.empty();
        }
        return Optional.of(new UpgradeAction(classActionId, tier));
    }

    public record UpgradeAction(int classActionId, int targetTier) { }

    private static boolean isUnlockBaseAction(int id) {
        return id >= UNLOCK_BASE_ACTION_OFFSET && id < 200 && KITS.containsKey(id - UNLOCK_BASE_ACTION_OFFSET);
    }

    private void handleShopPurchase(ServerPlayer player, String kit) {
        if (MapSetManager.isCompetitiveSet()) {
            player.sendSystemMessage(Component.literal("[WAR] Магазинные классы недоступны в соревновательном режиме."));
            LobbyManager.sync(player);
            return;
        }
        PlayerProgressManager.applyTabletClassPurchase(player, kit, result -> {
            switch (result) {
                case PURCHASED -> {
                    player.sendSystemMessage(Component.literal("[WAR] Куплен класс " + getDisplayName(kit)
                            + " за " + PlayerProgressManager.getShopPrice(kit) + " монет."));
                }
                case ALREADY_OWNED -> player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit) + " уже куплен."));
                case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Не хватает монет для " + getDisplayName(kit)
                        + ". Нужно " + PlayerProgressManager.getShopPrice(kit) + " монет."));
                case NOT_PURCHASABLE -> player.sendSystemMessage(Component.literal("[WAR] Этот класс нельзя купить."));
            }
        });
    }

    private void handleBaseUnlock(ServerPlayer player, int classActionId) {
        String kit = KITS.get(classActionId);
        if (kit == null || !PlayerProgressManager.isUnlockableBaseClass(kit)) {
            LobbyManager.sync(player);
            return;
        }

        PlayerProgressManager.applyTabletBaseUnlock(player, kit, result -> {
            switch (result) {
                case SUCCESS -> {
                    player.sendSystemMessage(Component.literal("[WAR] Открыт класс " + getDisplayName(kit)
                            + " за " + PlayerProgressManager.BASE_UNLOCK_COST + " монет."));
                }
                case ALREADY_UNLOCKED -> player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit) + " уже открыт."));
                case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Не хватает монет для открытия " + getDisplayName(kit)
                        + ". Нужно " + PlayerProgressManager.BASE_UNLOCK_COST + " монет."));
                default -> player.sendSystemMessage(Component.literal("[WAR] Этот класс нельзя открыть через планшет."));
            }
        });
    }

    private void handleTierUpgrade(ServerPlayer player, int classActionId, int targetTier) {
        String kit = KITS.get(classActionId);
        if (kit == null || !PlayerProgressManager.isBaseProgressionClass(kit)) {
            LobbyManager.sync(player);
            return;
        }

        String tierName = PlayerProgressManager.getTierDisplayName(targetTier);
        PlayerProgressManager.applyTabletTierUpgrade(player, kit, targetTier, result -> {
            switch (result) {
                case SUCCESS -> {
                    player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit)
                            + " улучшен до " + tierName + " за " + PlayerProgressManager.getUpgradeCost(targetTier) + " монет."));
                }
                case LOCKED -> player.sendSystemMessage(Component.literal("[WAR] Сначала открой класс " + getDisplayName(kit) + "."));
                case NOT_ENOUGH_XP -> player.sendSystemMessage(Component.literal("[WAR] Недостаточно опыта для улучшения " + getDisplayName(kit) + "."));
                case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.literal("[WAR] Не хватает монет для улучшения " + getDisplayName(kit)
                        + ". Нужно " + PlayerProgressManager.getUpgradeCost(targetTier) + " монет."));
                case MAX_TIER -> player.sendSystemMessage(Component.literal("[WAR] Класс " + getDisplayName(kit) + " уже максимального уровня."));
                case WRONG_TIER -> player.sendSystemMessage(Component.literal("[WAR] Это улучшение сейчас недоступно."));
                default -> player.sendSystemMessage(Component.literal("[WAR] Этот класс нельзя улучшить."));
            }
        });
    }

    private void handleRtp(ServerPlayer player) {
        if (MatchAdmissionManager.enforceLateSpectator(player, false)) {
            LobbyManager.sync(player);
            return;
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

    private void handleKit(ServerPlayer player, String kit) {
        if (MatchAdmissionManager.enforceLateSpectator(player, false)) {
            LobbyManager.sync(player);
            return;
        }
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
        return ClassDefinitions.byClassKey(kit)
                .map(definition -> definition.name().getString())
                .orElse(kit);
    }
}
