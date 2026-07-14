package com.makar.tacticaltablet.game.respawn;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostRtpProtectionArchitectureTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void rtpCommitGrantsProtectionWithoutChangingResistance() throws IOException {
        String source = read("game/respawn/RtpTimerManager.java");
        int finishRtp = source.indexOf("private static void finishRtp(ServerPlayer player)");
        int grant = source.indexOf("PostRtpProtectionManager.grant", finishRtp);
        int markUsed = source.indexOf("PlayerTabletState.setRtpUsed(player)", finishRtp);

        assertTrue(finishRtp >= 0);
        assertTrue(grant > markUsed);
        assertFalse(source.contains("removeEffect(MobEffects.DAMAGE_RESISTANCE)"));
        assertFalse(source.contains("addEffect(new MobEffectInstance"));
    }

    @Test
    void attackIsCancelledAtHighestPriorityAndLifecycleClearsState() throws IOException {
        String events = read("game/ServerEvents.java");
        String rtp = read("game/respawn/RtpTimerManager.java");

        assertTrue(events.contains("@SubscribeEvent(priority = EventPriority.HIGHEST)"));
        assertTrue(events.contains("LivingAttackEvent event"));
        assertTrue(events.contains("PostRtpProtectionManager.isProtected(player)"));
        assertTrue(events.contains("event.setCanceled(true)"));
        assertTrue(events.contains("PostRtpProtectionManager.clear(player)"));
        assertTrue(events.contains("PostRtpProtectionManager.clear(victim)"));
        assertTrue(rtp.contains("PostRtpProtectionManager.clearAll()"));
    }

    @Test
    void managerHasNoPollingOrWallClockAndProtocolUsesCurrentVersion() throws IOException {
        String manager = read("game/respawn/PostRtpProtectionManager.java");
        String packetHandler = read("tablet/net/PacketHandler.java");

        assertFalse(manager.contains("void tick("));
        assertFalse(manager.contains("System.currentTimeMillis"));
        assertTrue(manager.contains("overworld().getGameTime()"));
        assertTrue(packetHandler.contains("public static final String VERSION = \"32\""));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath));
    }
}
