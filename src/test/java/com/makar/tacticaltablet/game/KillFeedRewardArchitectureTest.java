package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillFeedRewardArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void oneDeathPublishesOneCompleteFeedEventAfterActualRewards() throws IOException {
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        String process = block(events, "private static void processPlayerDeath", "private static TacticalKillFeed.KillReward");
        String rewards = block(events, "private static TacticalKillFeed.KillReward", "private static void banTeamKiller");

        assertEquals(1, occurrences(process, "TacticalKillFeed.publish("));
        assertTrue(process.indexOf("processKillerConsequences") < process.indexOf("TacticalKillFeed.publish("));
        assertTrue(rewards.contains("PlayerProgressManager.getCoins(killer) - coinsBefore"));
        assertTrue(rewards.contains("int awardedXp = ClassXPManager.addXP"));
        assertTrue(rewards.contains("ChaosSetManager.KILL_COINS"));
        assertTrue(rewards.contains("if (MapSetManager.isChaosSet()) return new TacticalKillFeed.KillReward(awardedCoins, 0)"));
        assertFalse(rewards.contains("XpNotifier.send"));
        assertFalse(rewards.contains("sendSystemMessage"));
    }

    @Test
    void killFeedIsEnabledByDefaultAndRewardsArePersonalized() throws IOException {
        String config = Files.readString(MAIN.resolve("core/TacticalTabletServerConfig.java"));
        String mod = Files.readString(MAIN.resolve("core/TacticalTabletMod.java"));
        String feed = Files.readString(MAIN.resolve("game/TacticalKillFeed.java"));

        assertTrue(config.contains(".define(\"enabled\", true)"));
        assertTrue(config.contains("KILL_FEED_ENABLED.set(true)"));
        assertTrue(config.contains("KILL_FEED_CONFIG_VERSION.set(1)"));
        assertTrue(mod.contains("TacticalTabletServerConfig.migrateKillFeedDefault()"));
        assertTrue(mod.contains("config.save()"));
        assertTrue(feed.contains("viewer.getUUID().equals(killer.getUUID())"));
        assertTrue(feed.contains("rewardViewer ? safeReward.awardedCoins() : 0"));
        assertTrue(feed.contains("rewardViewer ? safeReward.awardedXp() : 0"));
        assertTrue(feed.contains("player.getTags().contains(\"war.playing\")"));
        assertTrue(feed.contains("player.getUUID().equals(victim.getUUID())"));
    }

    @Test
    void matchDeathScreenKeepsKillerDetailsInFeedButPreservesSadTrombone() throws IOException {
        String transition = Files.readString(MAIN.resolve("game/respawn/DeathTransitionManager.java"));
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        int matchBranch = transition.indexOf("victim.getTags().contains(\"war.playing\")");
        int earlyReturn = transition.indexOf("return;", matchBranch);

        assertTrue(matchBranch >= 0 && earlyReturn > matchBranch);
        String branch = transition.substring(matchBranch, earlyReturn);
        assertFalse(branch.contains("killer.getGameProfile().getName()"));
        assertTrue(branch.contains("PlayerProgressManager.isSadTromboneKillsEnabled(killer)"));
        assertTrue(events.contains("DeathTransitionManager.recordDeath(victim, source, killer)"));
    }

    @Test
    void overlayRendersAboveOpenScreensAndNeverUsesChatTitleOrToast() throws IOException {
        String overlay = Files.readString(MAIN.resolve("client/KillFeedOverlay.java"));
        String feed = Files.readString(MAIN.resolve("game/TacticalKillFeed.java"));

        assertTrue(overlay.contains("ScreenEvent.Render.Post"));
        assertTrue(overlay.contains("plainSubstrByWidth"));
        assertFalse(feed.contains("sendSystemMessage"));
        assertFalse(feed.contains("displayClientMessage"));
        assertFalse(feed.contains("showTitle"));
    }

    private static String block(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + 1);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
