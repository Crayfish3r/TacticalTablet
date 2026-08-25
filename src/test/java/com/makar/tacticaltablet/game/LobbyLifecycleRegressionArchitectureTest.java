package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyLifecycleRegressionArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void welcomeIsOwnedOnlyByPlayerLogin() throws IOException {
        String lobby = source("game/lobby/LobbyManager.java");
        String events = source("game/ServerEvents.java");

        assertFalse(method(lobby, "private static void moveToLobby", "public static boolean isMatchParticipantCandidate")
                .contains("showWelcomeOnJoin"));
        assertEquals(1, occurrences(events, "LobbyManager.showWelcomeOnJoin(player)"));
    }

    @Test
    void ordinaryRespawnUsesExplicitSurvivalNormalizingPath() throws IOException {
        String transition = source("game/respawn/DeathTransitionManager.java");
        String events = source("game/ServerEvents.java");

        assertTrue(transition.contains("LobbyManager.moveRespawningPlayerToLobby(player)"));
        assertTrue(events.contains("LobbyManager.moveRespawningPlayerToLobby(player)"));
    }

    @Test
    void physicalLobbyBlocksCommonStructureMutationPaths() throws IOException {
        String events = source("game/ServerEvents.java");

        assertTrue(events.contains("onLobbyBlockBreak(BlockEvent.BreakEvent event)"));
        assertTrue(events.contains("onLobbyPlayerBlockPlace(BlockEvent.EntityPlaceEvent event)"));
        assertTrue(events.contains("onLobbyBlockToolModification(BlockEvent.BlockToolModificationEvent event)"));
        assertTrue(events.contains("onLobbyFarmlandTrample(BlockEvent.FarmlandTrampleEvent event)"));
        assertTrue(events.contains("onLobbyFluidBlockChange(BlockEvent.FluidPlaceBlockEvent event)"));
        assertTrue(events.contains("onLobbyPiston(PistonEvent.Pre event)"));
        assertTrue(events.contains("onLobbyExplosion(ExplosionEvent.Start event)"));
        assertTrue(events.contains("onLobbyMobGriefing(EntityMobGriefingEvent event)"));
        assertTrue(events.contains("serverLevel.dimension().equals(GameStateManager.LOBBY_DIMENSION)"));
        assertTrue(events.contains("onLobbyEntityAttack(AttackEntityEvent event)"));
        assertTrue(events.contains("onLobbyProjectileImpact(ProjectileImpactEvent event)"));
        assertTrue(events.contains("onLobbyPaintingJoin(EntityJoinLevelEvent event)"));
        assertTrue(events.contains("painting.canUpdate(false)"));
        assertTrue(events.contains("painting.setInvulnerable(true)"));
        assertTrue(events.contains("painting.getHeight() == 32"));
        assertTrue(events.contains("painting.getPos().getY() - 1.0D"));
        assertTrue(events.contains("LOBBY_PAINTING_POSITION_NORMALIZED"));
        assertTrue(events.contains("onLobbyDecorationInteract(PlayerInteractEvent.EntityInteract event)"));
        assertTrue(events.contains("entity instanceof HangingEntity || entity instanceof ArmorStand"));
        assertTrue(events.contains("event.getEntity().setHealth(Math.max(1.0F"));
    }

    @Test
    void bootstrapUsesTemplateDimensionsAndIncludesSavedEntities() throws IOException {
        String bootstrap = source("game/lobby/LobbyBootstrapManager.java");

        assertTrue(bootstrap.contains("setKnownShape(true)"));
        assertTrue(bootstrap.contains("template.getSize()"));
        assertTrue(bootstrap.contains("new StructurePlaceSettings().setIgnoreEntities(false)"));
        assertFalse(bootstrap.contains("private static final int STRUCTURE_SIZE"));
        assertTrue(bootstrap.contains("Block.UPDATE_CLIENTS"));
    }

    @Test
    void fragileLobbyRepairIsAutomaticAndNeverOverwritesExistingBlocks() throws IOException {
        String bootstrap = source("game/lobby/LobbyBootstrapManager.java");
        String command = source("command/LobbyCommand.java");
        String mod = source("core/TacticalTabletMod.java");
        String events = source("game/ServerEvents.java");

        assertTrue(bootstrap.contains("repairMissingFragileBlocks"));
        assertTrue(bootstrap.contains("filterBlocks(LOBBY_SPAWN_ORIGIN, settings, Blocks.MOSS_CARPET)"));
        assertTrue(bootstrap.contains("if (!lobby.getBlockState(block.pos()).isAir()) continue"));
        assertTrue(bootstrap.contains("lobby.setBlock(block.pos(), block.state(), Block.UPDATE_CLIENTS)"));
        assertTrue(command.contains("Commands.literal(\"ttlobby\")"));
        assertTrue(command.contains("Commands.literal(\"repair\")"));
        assertTrue(command.contains("source.hasPermission(2)"));
        assertTrue(mod.contains("LobbyCommand.register(event.getDispatcher())"));
        assertTrue(events.contains("LobbyBootstrapManager.repairMissingFragileBlocks(event.getServer())"));
    }


    @Test
    void lateSpectatorMarkerIsReleasedOnlyAfterTheMatch() throws IOException {
        String admission = source("game/MatchAdmissionManager.java");
        String lobby = source("game/lobby/LobbyManager.java");

        assertTrue(admission.contains("putString(DATA_LATE_SPECTATOR_MATCH, matchKey)"));
        assertTrue(admission.contains("GameStateManager.isRunning(player.server)"));
        assertTrue(lobby.contains("MatchAdmissionManager.releaseLateSpectatorAfterMatch(player)"));
        assertTrue(lobby.contains("ordinaryRespawn || releasedLateSpectator"));
    }
    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static int occurrences(String source, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath)).replace("\r\n", "\n");
    }
}
