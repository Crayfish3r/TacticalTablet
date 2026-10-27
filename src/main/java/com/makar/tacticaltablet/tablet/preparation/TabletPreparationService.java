package com.makar.tacticaltablet.tablet.preparation;

import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.*;
import com.makar.tacticaltablet.game.chaos.ChaosSelectionAdmission;
import com.makar.tacticaltablet.game.chaos.ChaosSetManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.*;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import java.util.ArrayList;

/** Server-thread presentation snapshot and admission only. Existing handlers execute gameplay. */
public final class TabletPreparationService {
    private TabletPreparationService() { }

    public static void handle(ServerPlayer player, int action, long requestId, Runnable existingAction) {
        if (player == null || requestId <= 0) return;
        if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.TABLET_SNAPSHOT)) return;
        String denial = "";
        if (!player.isAlive() || !InventoryManager.hasTablet(player)) denial = "Нужен планшет в инвентаре.";
        else if (action == 7) denial = rtpReason(player);
        else if (action != -1) {
            ClassDefinition definition = ClassDefinitions.byActionId(action).orElse(null);
            denial = definition == null ? "Неизвестный класс." : classReason(player, definition);
        }
        boolean rejected = !denial.isEmpty();
        if (!rejected && action != -1) {
            existingAction.run(); // Includes the original rate limit, kit issue and RTP checks.
            if (action != 7 && (!PlayerTabletState.isKitUsed(player)
                    || !ClassDefinitions.byActionId(action).orElseThrow().classKey()
                    .equals(PlayerTabletState.getSelectedClass(player)))) {
                rejected = true;
                denial = "Сервер не выдал набор. Проверьте доступность класса или повторите позже.";
            }
        }
        var rows = new ArrayList<PreparationSnapshot.ClassAvailability>();
        for (ClassDefinition definition : ClassDefinitions.all())
            rows.add(new PreparationSnapshot.ClassAvailability(definition.actionId(), classReason(player, definition)));
        var rtp = RtpTimerManager.debugSnapshot(player.server, player).requestState();
        boolean pending = rtp == RtpTimerManager.RtpRequestState.QUEUED
                || rtp == RtpTimerManager.RtpRequestState.SEARCHING || rtp == RtpTimerManager.RtpRequestState.RETRY_WAIT;
        var snapshot = new PreparationSnapshot(requestId, rejected, denial, PlayerTabletState.getSelectedClass(player),
                PlayerTabletState.isRtpUsed(player), pending, rtpReason(player), rows);
        PacketHandler.sendToPlayer(player, ClassXPManager.createStatePacket(player).withPreparation(snapshot));
        // Same server-thread snapshot, same nonce, no second poll or synthetic vote from the client.
        MapSetManager.sync(player, false, requestId);
        com.makar.tacticaltablet.game.arena.ArenaMatchRuntime.sync(player);
    }

    private static String admission(ServerPlayer player) {
        if (!InventoryManager.hasTablet(player)) return "Нужен планшет в инвентаре.";
        if (!GameStateManager.isRunning(player.server)) return "Матч ещё не начался.";
        if (MapSetManager.isArenaSet() && !com.makar.tacticaltablet.game.arena.ArenaMatchRuntime.combatOpen())
            return "Арена завершена. Ожидаем фиксацию результатов.";
        if (!player.isAlive() || player.isSpectator() || MatchAdmissionManager.isLateSpectator(player)
                || !LivesManager.canContinueMatch(player)) return "Вы не являетесь активным участником матча.";
        return "";
    }

    private static String classReason(ServerPlayer player, ClassDefinition definition) {
        String reason = admission(player);
        if (!reason.isEmpty()) return reason;
        String key = definition.classKey();
        if (MapSetManager.isChaosSet()) {
            if (!ChaosSelectionAdmission.canSelect(GameStateManager.isRunning(player.server),
                    LivesManager.canContinueMatch(player), GameStateManager.isInLobby(player),
                    player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL))
                return "Выбор класса доступен участнику в режиме выживания в лобби.";
            return ChaosSetManager.canUse(player, key) ? "" : "Класс недоступен в текущей игре Хаоса.";
        }
        if (PlayerTabletState.isKitUsed(player)) return "Класс для этой жизни уже получен.";
        if (!GameStateManager.isInLobby(player) && !player.getTags().contains("in_lobby")
                && !player.getTags().contains("war.playing")) return "Сейчас нельзя выбрать класс.";
        if (CompetitiveClassPolicy.isVipBlocked(MapSetManager.isCompetitiveSet(), key)) return "Недоступен в соревновательном режиме.";
        if (PlayerProgressManager.isShopClass(key)) {
            if (MapSetManager.isCompetitiveSet()) return "Магазинные классы недоступны в этом режиме.";
            if (!PlayerProgressManager.isClassPurchased(player, key)) return "Класс не приобретён. Покупки на этой странице недоступны.";
        }
        if (ClanManager.MARINE_CLASS.equals(key) && !ClanManager.isClanClassUnlocked(player, key)) return "Клановый класс не открыт.";
        if (PlayerProgressManager.isExclusiveClass(key) && !PlayerProgressManager.isExclusiveClassGranted(player, key)) return "Этот класс вам не выдан.";
        if (PlayerProgressManager.isBaseProgressionClass(key) && !PlayerProgressManager.isBaseClassUnlocked(player, key)) return "Сначала необходимо открыть класс.";
        long remaining = ClassCooldownManager.getRemaining(player, definition.actionId());
        return remaining > 0 ? "Перезарядка класса: " + (remaining + 999) / 1000 + " с." : "";
    }

    private static String rtpReason(ServerPlayer player) {
        String reason = admission(player);
        if (!reason.isEmpty()) return reason;
        if (PlayerTabletState.isRtpUsed(player)) return "Вы уже вступили в бой.";
        if (MapSetManager.isArenaSet() && !PlayerTabletState.isKitUsed(player)) return "Сначала получите набор для этой жизни.";
        if (MapSetManager.isChaosSet() && (ChaosSetManager.requiresSelection(player) || !PlayerTabletState.isKitUsed(player)))
            return "Сначала выберите класс для текущей игры Хаоса.";
        if (!GameStateManager.isInLobby(player) || !player.getTags().contains("in_lobby"))
            return "Вступление через RTP доступно из лобби.";
        if (!LivesManager.isBoundToCurrentMatch(player)) return "Вы не зарегистрированы в текущем матче.";
        return RtpTimerManager.canRtp(player, false) ? "" : "RTP временно недоступен. Обновите данные или повторите позже.";
    }
}
