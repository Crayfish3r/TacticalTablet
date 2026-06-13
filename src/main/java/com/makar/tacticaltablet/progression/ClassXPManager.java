package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.team.VoteManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.tablet.TabletAppearanceManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletStatePacket;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class ClassXPManager {

    private static final String[] STANDARD_CLASSES = new String[]{
            "stormtrooper",
            "sniper",
            "scout",
            "droneoperator",
            "machinegunner",
            "mortarman",
            "rpgtrooper"
    };

    private static final String[] SHOP_CLASSES = new String[]{
            "boomguy",
            "dream",
            "tagilla",
            "blackops",
            "cowboy",
            "solider",
            "rebel"
    };

    private static final String[] ALL_CLASSES = new String[]{
            "stormtrooper",
            "sniper",
            "scout",
            "droneoperator",
            "machinegunner",
            "mortarman",
            "rpgtrooper",
            "boomguy",
            "dream",
            "tagilla",
            "blackops",
            "cowboy",
            "solider",
            "rebel"
    };

    public static String[] getClasses() {
        return ALL_CLASSES.clone();
    }

    public static String[] getStandardClasses() {
        return STANDARD_CLASSES.clone();
    }

    public static String[] getShopClasses() {
        return SHOP_CLASSES.clone();
    }


    public static boolean isStandardClass(String clazz) {
        if (clazz == null || clazz.isBlank()) return false;

        for (String standardClass : STANDARD_CLASSES) {
            if (standardClass.equalsIgnoreCase(clazz.trim())) {
                return true;
            }
        }

        return false;
    }

    public static int getXP(ServerPlayer player, String clazz) {
        return PlayerProgressManager.getXP(player, clazz);
    }

    public static int getLevel(ServerPlayer player, String clazz) {
        return PlayerProgressManager.getLevel(player, clazz);
    }

    public static void addXP(ServerPlayer player, String clazz, int amount) {
        if (player == null || clazz == null || clazz.isBlank() || amount <= 0) return;
        if (PlayerProgressManager.isShopClass(clazz)) return;

        PlayerProgressManager.addXP(player, clazz, amount);
        sync(player);
    }

    public static void addXPToAllClasses(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;

        for (String clazz : STANDARD_CLASSES) {
            PlayerProgressManager.addXP(player, clazz, amount);
        }

        sync(player);
    }

    public static void sync(ServerPlayer player) {
        if (player == null) return;

        InventoryManager.updateTabletModels(player);
        PacketHandler.sendToPlayer(player, createStatePacket(player));
        ContractManager.syncSelection(player);
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static TabletStatePacket createStatePacket(ServerPlayer player) {
        int ticksLeft = RtpTimerManager.getTimeLeft(player);
        long endTime = ticksLeft > 0 ? System.currentTimeMillis() + ticksLeft * 50L : 0L;
        MatchMode selectedVote = VoteManager.getVote(player);
        java.util.Map<MatchMode, Integer> voteCounts = VoteManager.getVoteCounts();
        TeamMatchManager.Snapshot teamSnapshot = TeamMatchManager.snapshot(
                player.server,
                player,
                GameStateManager.getCurrentMode()
        );

        return new TabletStatePacket(
                ClassCooldownManager.getCooldowns(player),
                PlayerTabletState.isKitUsed(player),
                PlayerTabletState.isRtpUsed(player),
                endTime,
                getAllLevels(player),
                getAllXP(player),
                PlayerProgressManager.getClassTiers(player),
                PlayerProgressManager.getUnlockedBaseClasses(player),
                PlayerProgressManager.getPurchasedClasses(player),
                GameStateManager.isRunning(player.server),
                PlayerProgressManager.getWins(player),
                PlayerProgressManager.getKills(player),
                PlayerProgressManager.getDeaths(player),
                PlayerProgressManager.getMatchesPlayed(player),
                PlayerProgressManager.getCoins(player),
                PlayerProgressManager.getCareerProgressPercent(player),
                LivesManager.getLives(player),
                LivesManager.getAlivePlayerCount(player.server),
                LivesManager.getRemainingLivesTotal(player.server),
                TabletAppearanceManager.getAppearanceTier(player),
                GameStateManager.getMatchPhase(),
                GameStateManager.getCurrentMode(),
                selectedVote,
                VoteManager.getSecondsLeft(),
                voteCounts.getOrDefault(MatchMode.SOLO, 0),
                voteCounts.getOrDefault(MatchMode.DUO, 0),
                voteCounts.getOrDefault(MatchMode.TRIO, 0),
                voteCounts.getOrDefault(MatchMode.SQUADS, 0),
                VoteManager.getVoteOptionsMask(player.server),
                TeamMatchManager.getSecondsLeft(),
                teamSnapshot.maxSlots(),
                teamSnapshot.selectedTeam(),
                teamSnapshot.slots()
        );
    }

    public static Map<String, Integer> getAllLevels(ServerPlayer player) {
        return PlayerProgressManager.getAllClassLevels(player);
    }

    public static Map<String, Integer> getAllXP(ServerPlayer player) {
        return PlayerProgressManager.getAllClassXP(player);
    }

    public static void reset(ServerPlayer player) {
        if (player == null) return;

        for (String clazz : STANDARD_CLASSES) {
            PlayerProgressManager.setXP(player, clazz, 0);
        }

        sync(player);
    }

    private static void sendLevelUp(ServerPlayer player, String clazz, int newLevel) {
        player.sendSystemMessage(
                Component.literal("НОВЫЙ УРОВЕНЬ: " + displayClassName(clazz) + " -> " +
                        (newLevel == 1 ? "ЭПИЧЕСКИЙ" : "ЛЕГЕНДАРНЫЙ"))
        );
    }

    private static String displayClassName(String clazz) {
        return switch (clazz == null ? "" : clazz) {
            case "stormtrooper" -> "Штурмовик";
            case "sniper" -> "Снайпер";
            case "scout" -> "Разведчик";
            case "droneoperator" -> "Оператор дрона";
            case "mortarman" -> "Миномётчик";
            case "machinegunner" -> "Пулемётчик";
            case "rpgtrooper" -> "РПГ-боец";
            default -> clazz == null ? "класс" : clazz;
        };
    }
}

