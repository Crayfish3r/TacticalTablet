package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.chaos.ChaosSetManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.team.VoteManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.tablet.TabletAppearanceManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletMatchSetupStatePacket;
import com.makar.tacticaltablet.tablet.net.TabletStatePacket;
import com.makar.tacticaltablet.tablet.net.ChaosStatePacket;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class ClassXPManager {

    public static String[] getClasses() {
        return PlayerProgressManager.getAllClasses();
    }

    public static String[] getStandardClasses() {
        return PlayerProgressManager.getStandardClasses();
    }

    public static String[] getShopClasses() {
        return PlayerProgressManager.getShopClasses();
    }


    public static boolean isStandardClass(String clazz) {
        return PlayerProgressManager.isBaseProgressionClass(clazz);
    }

    public static int getXP(ServerPlayer player, String clazz) {
        return PlayerProgressManager.getXP(player, clazz);
    }

    public static int getLevel(ServerPlayer player, String clazz) {
        return PlayerProgressManager.getLevel(player, clazz);
    }

    public static int addXP(ServerPlayer player, String clazz, int amount) {
        return addXPInternal(player, clazz, amount, true);
    }

    /**
     * Awards XP without sending player state immediately. Intended for server-side mutation
     * batches that perform one authoritative sync after all related mutations complete.
     */
    public static int addXPDeferredSync(ServerPlayer player, String clazz, int amount) {
        return addXPInternal(player, clazz, amount, false);
    }

    private static int addXPInternal(ServerPlayer player, String clazz, int amount, boolean syncAfter) {
        if (player == null || clazz == null || clazz.isBlank() || amount <= 0) return 0;
        if (MapSetManager.isChaosSet()) return 0;
        if (PlayerProgressManager.isShopClass(clazz)) return 0;

        int awarded = PlayerProgressManager.addXP(player, clazz, applyBoost(player, amount));
        if (syncAfter) {
            sync(player);
        }
        return awarded;
    }

    public static void addXPToAllClasses(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;
        if (MapSetManager.isChaosSet()) return;

        for (String clazz : PlayerProgressManager.getStandardClasses()) {
            PlayerProgressManager.addXP(player, clazz, applyBoost(player, amount));
        }

        sync(player);
    }

    public static boolean isXpBoostEnabled(ServerPlayer player) {
        return PlayerProgressManager.isXpBoostEnabled(player);
    }

    public static void setXpBoostEnabled(ServerPlayer player, boolean enabled) {
        PlayerProgressManager.setXpBoostEnabled(player, enabled);
        PlayerProgressManager.savePlayer(player);
    }

    private static int applyBoost(ServerPlayer player, int amount) {
        if (!isXpBoostEnabled(player)) return amount;
        return amount > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : amount * 2;
    }

    public static void sync(ServerPlayer player) {
        if (player == null) return;

        InventoryManager.updateTabletModels(player);
        PacketHandler.sendToPlayer(player, createStatePacket(player));
        PacketHandler.sendToPlayer(player, new ChaosStatePacket(ChaosSetManager.snapshot(player)));
        ClanManager.sync(player);
        ContractManager.syncSelection(player);
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void syncMatchSetup(ServerPlayer player) {
        if (player == null) return;
        PacketHandler.sendToPlayer(player, createMatchSetupState(player).toPacket());
    }

    public static void syncMatchSetupAll(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncMatchSetup(player);
        }
    }

    public static TabletStatePacket createStatePacket(ServerPlayer player) {
        int ticksLeft = RtpTimerManager.getTimeLeft(player);
        long endTime = ticksLeft > 0 ? System.currentTimeMillis() + ticksLeft * 50L : 0L;
        MatchSetupState matchSetup = createMatchSetupState(player);

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
                matchSetup.matchPhase(),
                matchSetup.matchMode(),
                matchSetup.selectedVote(),
                matchSetup.voteTimeLeft(),
                matchSetup.soloVotes(),
                matchSetup.duoVotes(),
                matchSetup.trioVotes(),
                matchSetup.squadVotes(),
                matchSetup.voteOptionsMask(),
                matchSetup.teamSelectTimeLeft(),
                matchSetup.teamSlotSize(),
                matchSetup.selectedTeam(),
                matchSetup.teamSlots(),
                matchSetup.competitiveSet(),
                matchSetup.clanWarSet()
        );
    }

    private static MatchSetupState createMatchSetupState(ServerPlayer player) {
        MatchMode matchMode = GameStateManager.getCurrentMode();
        Map<MatchMode, Integer> voteCounts = VoteManager.getVoteCounts();
        TeamMatchManager.Snapshot teamSnapshot = TeamMatchManager.snapshot(player.server, player, matchMode);
        return new MatchSetupState(
                GameStateManager.getMatchPhase(),
                matchMode,
                VoteManager.getVote(player),
                VoteManager.getSecondsLeft(),
                voteCounts.getOrDefault(MatchMode.SOLO, 0),
                voteCounts.getOrDefault(MatchMode.DUO, 0),
                voteCounts.getOrDefault(MatchMode.TRIO, 0),
                voteCounts.getOrDefault(MatchMode.SQUADS, 0),
                VoteManager.getVoteOptionsMask(player.server),
                TeamMatchManager.getSecondsLeft(),
                teamSnapshot.maxSlots(),
                teamSnapshot.selectedTeam(),
                teamSnapshot.slots(),
                MapSetManager.isCompetitiveSet(),
                MapSetManager.isClanWarSet()
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

        for (String clazz : PlayerProgressManager.getStandardClasses()) {
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

    private record MatchSetupState(
            MatchPhase matchPhase,
            MatchMode matchMode,
            MatchMode selectedVote,
            int voteTimeLeft,
            int soloVotes,
            int duoVotes,
            int trioVotes,
            int squadVotes,
            int voteOptionsMask,
            int teamSelectTimeLeft,
            int teamSlotSize,
            int selectedTeam,
            Map<String, String> teamSlots,
            boolean competitiveSet,
            boolean clanWarSet
    ) {
        private TabletMatchSetupStatePacket toPacket() {
            return new TabletMatchSetupStatePacket(
                    matchPhase,
                    matchMode,
                    selectedVote,
                    voteTimeLeft,
                    soloVotes,
                    duoVotes,
                    trioVotes,
                    squadVotes,
                    voteOptionsMask,
                    teamSelectTimeLeft,
                    teamSlotSize,
                    selectedTeam,
                    teamSlots,
                    competitiveSet,
                    clanWarSet
            );
        }
    }
}
