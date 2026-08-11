package com.makar.tacticaltablet.game.chaos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosIntegrationArchitectureTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/makar/tacticaltablet").resolve(relative));
    }

    @Test
    void temporaryAccessBypassesOwnershipWithoutMutatingProfile() throws IOException {
        String packet = source("tablet/net/TabletPacket.java");
        int chaos = packet.indexOf("if (MapSetManager.isChaosSet())");
        int purchase = packet.indexOf("PlayerProgressManager.isShopClass(kit) && !PlayerProgressManager.isClassPurchased");
        assertTrue(chaos >= 0 && chaos < purchase);
        assertTrue(packet.contains("ChaosSetManager.canUse(player, kit)"));
        assertTrue(packet.contains("ChaosSetManager.select(player, kit)"));
        assertTrue(packet.contains("KitManager.giveKit(player, kit, ChaosSetManager.tierFor(player, kit))"));
    }

    @Test
    void poolComesFromRegistryAndContainsTierVariantsWithoutFileOrPlayerFiltering() throws IOException {
        String manager = source("game/chaos/ChaosSetManager.java");
        String kits = source("progression/kit/KitManager.java");
        assertTrue(manager.contains("ClassDefinitions.all().forEach"));
        assertTrue(manager.contains("for (ClassTier tier : ClassTier.values())"));
        assertTrue(!manager.contains("hasConfiguredKit"));
        assertTrue(!manager.contains("RandomGeneratorFactory"));
        assertTrue(manager.contains("new Random(seed)"));
        assertTrue(kits.contains("giveKit(ServerPlayer player, String kitName, int requestedTier)"));
    }

    @Test
    void allClassXpAndRtpAreGatedDuringChaos() throws IOException {
        String xp = source("progression/ClassXPManager.java");
        String rtp = source("game/respawn/RtpTimerManager.java");
        String lobby = source("game/lobby/LobbyManager.java");
        String tablet = source("tablet/client/TabletScreen.java");
        assertTrue(xp.contains("if (MapSetManager.isChaosSet()) return 0;"));
        assertTrue(xp.contains("if (MapSetManager.isChaosSet()) return;"));
        assertTrue(rtp.contains("ChaosSetManager.requiresSelection(player)"));
        assertTrue(rtp.contains("PlayerTabletState.isKitUsed(player)"));
        assertTrue(rtp.contains("isChaosDeploymentReady(player)"));
        assertTrue(lobby.contains("new ChaosStatePacket(ChaosSetManager.snapshot(player))"));
        assertTrue(tablet.contains("!ChaosClientState.requiresSelection()"));
    }

    @Test
    void chaosPacketStateCannotOpenClientGuiByItself() throws IOException {
        String state = source("tablet/client/ChaosClientState.java");
        String events = source("tablet/client/ClientEvents.java");

        assertTrue(!state.contains("Minecraft"));
        assertTrue(!state.contains("setScreen"));
        assertTrue(events.contains("ChaosAutoOpenPolicy.shouldOpen"));
        assertTrue(events.contains("GameStateManager.LOBBY_DIMENSION"));
        assertTrue(events.contains("hasTabletInInventory(mc.player)"));
    }

    @Test
    void discordUsesPinkChaosResultOnlyFromCompletionFlow() throws IOException {
        String discord = source("integration/discord/DiscordLeaderboardService.java");
        String game = source("game/GameStateManager.java");
        assertTrue(discord.contains("CHAOS_COLOR = 0xFF4FA3"));
        assertTrue(discord.contains("Результаты матча · ХАОС"));
        assertTrue(discord.contains("Награда за убийство"));
        assertTrue(discord.contains("Классовый XP"));
        assertTrue(game.contains("DiscordLeaderboardService.sendCurrentMatchLeaderboard"));
        assertTrue(!source("game/MapSetManager.java").contains("sendCurrentMatchLeaderboard"));
    }
}
