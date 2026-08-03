package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingUiArchitectureTest {
    private static final Path CLIENT = Path.of("src/main/java/com/makar/tacticaltablet/tablet/client");

    @Test
    void allPhaseScreensUseTheSharedTacticalFoundationAndExistingPackets() throws IOException {
        String voting = source("VotingScreen.java");
        String teams = source("TeamSelectScreen.java");
        String maps = source("MapVotingScreen.java");

        for (String source : List.of(voting, teams, maps)) {
            assertTrue(source.contains("extends TacticalPhaseScreen"));
            assertTrue(source.contains("Component.translatable("));
            assertFalse(source.contains("extends Button"));
            assertFalse(source.contains("gui.components.Button"));
        }
        assertTrue(voting.contains("new VoteModePacket(mode)"));
        assertTrue(teams.contains("new JoinTeamPacket(team)"));
        assertTrue(maps.contains("new VoteMapPacket(mapName)"));
        assertTrue(maps.contains("new SetCompetitivePacket(pendingCompetitive)"));
        assertTrue(maps.contains("new SetClanWarPacket(pendingClanWar)"));
    }

    @Test
    void mapGridScrollsAndClipsArbitraryCandidateCounts() throws IOException {
        String maps = source("MapVotingScreen.java");

        assertTrue(maps.contains("VotingGridLayout.columnsFor"));
        assertTrue(maps.contains("VotingGridLayout.clampScrollRow"));
        assertTrue(maps.contains("ScissorScope.open("));
        assertTrue(maps.contains("button.visible = visibleRow >= 0 && visibleRow < visibleRows"));
        assertTrue(maps.contains("reveal(next)"));
        assertFalse(maps.contains("builderLines"));
    }

    @Test
    void pendingFeedbackDoesNotClaimSuccessBeforeServerState() throws IOException {
        String voting = source("VotingScreen.java");
        String teams = source("TeamSelectScreen.java");
        String maps = source("MapVotingScreen.java");

        assertTrue(voting.contains("TabletClientState.getSelectedVote() == pendingMode"));
        assertTrue(teams.contains("TabletClientState.getSelectedTeam() == pendingTeam.ordinal()"));
        assertTrue(maps.contains("pendingMap.equals(MapVoteClientState.getSelectedMap())"));
        for (String source : List.of(voting, teams, maps)) {
            assertTrue(source.contains("PENDING_TIMEOUT_TICKS"));
            assertTrue(source.contains("screen.tacticaltablet.common.server_timeout"));
        }
    }

    @Test
    void escapeClosesVotingScreensWithoutMapVoteReopeningOnTheNextTick() throws IOException {
        String foundation = source("TacticalPhaseScreen.java");
        String events = source("ClientEvents.java");

        assertTrue(foundation.contains("public final boolean shouldCloseOnEsc()"));
        assertTrue(foundation.contains("return true;"));
        assertTrue(events.contains("mapVoting && (current instanceof VotingScreen || current instanceof TeamSelectScreen)"));
        assertFalse(events.contains("mapVoting && !(current instanceof MapVotingScreen)"));
    }

    @Test
    void mapPreviewsUseOptionalFixedAspectResourcesResolvedOutsideRendering() throws IOException {
        String maps = source("MapVotingScreen.java");
        String cache = source("ClientResourcePresenceCache.java");

        assertTrue(maps.contains("MapPreviewAssets.textureFor(mapName)"));
        assertTrue(maps.contains("ClientResourcePresenceCache.exists(candidate)"));
        assertTrue(cache.contains("getResource(key).isPresent()"));
        assertTrue(cache.contains("RegisterClientReloadListenersEvent"));
        assertTrue(maps.contains("GuiTextureRenderer.blitRegionWithAlpha"));
        String rendering = maps.substring(maps.indexOf("protected void renderContent"));
        assertFalse(rendering.contains("getResource("));
    }

    private static String source(String name) throws IOException {
        return Files.readString(CLIENT.resolve(name)).replace("\r\n", "\n");
    }
}
