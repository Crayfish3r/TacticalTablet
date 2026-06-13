package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TabletClientState {

    private static Map<Integer, Long> cooldowns = new HashMap<>();
    private static boolean kitUsed;
    private static boolean rtpUsed;
    private static boolean gameRunning;

    private static boolean shouldClose = false;
    private static long rtpEndTime = 0L;

    private static Map<String, Integer> classLevels = new HashMap<>();
    private static Map<String, Integer> classXP = new HashMap<>();
    private static Map<String, Integer> classTiers = new HashMap<>();
    private static Map<String, Integer> unlockedBaseClasses = new HashMap<>();
    private static Map<String, Integer> purchasedClasses = new HashMap<>();

    private static int wins;
    private static int kills;
    private static int deaths;
    private static int matchesPlayed;
    private static int coins;
    private static int careerProgressPercent;
    private static int lives;
    private static int alivePlayers;
    private static int remainingLivesTotal;
    private static int tabletAppearanceTier;
    private static MatchPhase matchPhase = MatchPhase.WAITING;
    private static MatchMode matchMode = MatchMode.SOLO;
    private static MatchMode selectedVote;
    private static int voteTimeLeft;
    private static int soloVotes;
    private static int duoVotes;
    private static int trioVotes;
    private static int squadVotes;
    private static int voteOptionsMask = MatchMode.voteMaskFor(0, false);
    private static int teamSelectTimeLeft;
    private static int teamSlotSize = 1;
    private static int selectedTeam = -1;
    private static Map<String, String> teamSlots = new HashMap<>();

    public static void update(Map<Integer, Long> cd, boolean kit, boolean rtp, long rtpTime) {
        cooldowns = cd == null ? new HashMap<>() : new HashMap<>(cd);
        kitUsed = kit;
        rtpUsed = rtp;
        rtpEndTime = Math.max(rtpTime, 0L);
    }

    public static long getCooldown(int id) {
        return cooldowns.getOrDefault(id, 0L);
    }

    public static boolean isKitUsed() {
        return kitUsed;
    }

    public static boolean isRtpUsed() {
        return rtpUsed;
    }

    public static long getRtpTimeLeft() {
        long left = rtpEndTime - System.currentTimeMillis();
        return Math.max(left, 0L);
    }

    public static void requestClose() {
        shouldClose = true;
    }

    public static boolean shouldClose() {
        return shouldClose;
    }

    public static void resetCloseFlag() {
        shouldClose = false;
    }

    public static void updateLevels(Map<String, Integer> levels) {
        classLevels = levels == null ? new HashMap<>() : new HashMap<>(levels);
    }

    public static int getLevel(String clazz) {
        return classLevels.getOrDefault(clazz, 0);
    }

    public static void updateClassTiers(Map<String, Integer> tiers) {
        classTiers = tiers == null ? new HashMap<>() : new HashMap<>(tiers);
    }

    public static int getClassTier(String clazz) {
        return classTiers.getOrDefault(clazz, getLevel(clazz));
    }

    public static void updateXP(Map<String, Integer> xp) {
        classXP = xp == null ? new HashMap<>() : new HashMap<>(xp);
    }

    public static int getXP(String clazz) {
        return classXP.getOrDefault(clazz, 0);
    }

    public static void updateUnlockedBaseClasses(Map<String, Integer> unlocked) {
        unlockedBaseClasses = unlocked == null ? new HashMap<>() : new HashMap<>(unlocked);
    }

    public static boolean isBaseClassUnlocked(String clazz) {
        return unlockedBaseClasses.getOrDefault(clazz, 0) > 0;
    }

    public static void updatePurchasedClasses(Map<String, Integer> purchased) {
        purchasedClasses = purchased == null ? new HashMap<>() : new HashMap<>(purchased);
    }

    public static boolean isClassPurchased(String clazz) {
        return purchasedClasses.getOrDefault(clazz, 0) > 0;
    }

    public static void updateGameRunning(boolean running) {
        gameRunning = running;
    }

    public static boolean isGameRunning() {
        return gameRunning;
    }

    public static void updateProfileStats(
            int newWins,
            int newKills,
            int newDeaths,
            int newMatchesPlayed,
            int newCoins,
            int newCareerProgressPercent
    ) {
        wins = Math.max(0, newWins);
        kills = Math.max(0, newKills);
        deaths = Math.max(0, newDeaths);
        matchesPlayed = Math.max(0, newMatchesPlayed);
        coins = Math.max(0, newCoins);
        careerProgressPercent = Math.max(0, Math.min(100, newCareerProgressPercent));
    }


    public static void updateLives(int newLives) {
        lives = Math.max(0, newLives);
    }

    public static int getLives() {
        return lives;
    }

    public static void updateMatchCounts(int newAlivePlayers, int newRemainingLivesTotal) {
        alivePlayers = Math.max(0, newAlivePlayers);
        remainingLivesTotal = Math.max(0, newRemainingLivesTotal);
    }

    public static int getAlivePlayers() {
        return alivePlayers;
    }

    public static int getRemainingLivesTotal() {
        return remainingLivesTotal;
    }

    public static void updateTabletAppearanceTier(int tier) {
        tabletAppearanceTier = Math.max(0, tier);
    }

    public static int getTabletAppearanceTier() {
        return tabletAppearanceTier;
    }

    public static void updateMatchSetup(
            MatchPhase phase,
            MatchMode mode,
            MatchMode vote,
            int votingSeconds,
            int soloVoteCount,
            int duoVoteCount,
            int trioVoteCount,
            int squadVoteCount,
            int availableVoteMask,
            int teamSelectSeconds,
            int slotSize,
            int selectedTeamId,
            Map<String, String> slots
    ) {
        matchPhase = phase == null ? MatchPhase.WAITING : phase;
        matchMode = mode == null ? MatchMode.SOLO : mode;
        selectedVote = vote;
        voteTimeLeft = Math.max(0, votingSeconds);
        soloVotes = Math.max(0, soloVoteCount);
        duoVotes = Math.max(0, duoVoteCount);
        trioVotes = Math.max(0, trioVoteCount);
        squadVotes = Math.max(0, squadVoteCount);
        voteOptionsMask = availableVoteMask;
        teamSelectTimeLeft = Math.max(0, teamSelectSeconds);
        teamSlotSize = Math.max(1, slotSize);
        selectedTeam = Math.max(-1, selectedTeamId);
        teamSlots = slots == null ? new HashMap<>() : new HashMap<>(slots);
    }

    public static MatchPhase getMatchPhase() {
        return matchPhase;
    }

    public static MatchMode getMatchMode() {
        return matchMode;
    }

    public static MatchMode getSelectedVote() {
        return selectedVote;
    }

    public static int getVoteTimeLeft() {
        return voteTimeLeft;
    }

    public static int getVoteCount(MatchMode mode) {
        if (mode == MatchMode.SOLO) return soloVotes;
        if (mode == MatchMode.DUO) return duoVotes;
        if (mode == MatchMode.TRIO) return trioVotes;
        if (mode == MatchMode.SQUADS) return squadVotes;
        return 0;
    }

    public static boolean isVoteModeAvailable(MatchMode mode) {
        if (mode == null) return false;
        return (voteOptionsMask & (1 << mode.ordinal())) != 0;
    }

    public static int getTeamSelectTimeLeft() {
        return teamSelectTimeLeft;
    }

    public static int getTeamSlotSize() {
        return teamSlotSize;
    }

    public static int getSelectedTeam() {
        return selectedTeam;
    }

    public static String getTeamSlotName(int teamId, int slot) {
        return teamSlots.getOrDefault(teamId + ":" + slot, "");
    }

    public static int getWins() {
        return wins;
    }

    public static int getKills() {
        return kills;
    }

    public static int getDeaths() {
        return deaths;
    }

    public static int getMatchesPlayed() {
        return matchesPlayed;
    }

    public static String getKdaText() {
        if (deaths <= 0) {
            return kills > 0 ? "∞" : "0.00";
        }

        return String.format(Locale.ROOT, "%.2f", kills / (double) deaths);
    }

    public static int getCoins() {
        return coins;
    }

    public static int getCareerProgressPercent() {
        return careerProgressPercent;
    }
}
