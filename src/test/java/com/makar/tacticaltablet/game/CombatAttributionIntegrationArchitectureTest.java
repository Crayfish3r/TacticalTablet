package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatAttributionIntegrationArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void mdcOriginalSourceIsObservedEvenWhenCanceledAndExactApiExists() throws IOException {
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        String ledger = Files.readString(MAIN.resolve("game/CombatAttributionLedger.java"));

        assertTrue(events.contains("onLivingHurtAttribution(LivingHurtEvent event)"));
        assertTrue(events.contains("receiveCanceled = true"));
        assertTrue(events.contains("observeIncomingAttack"));
        assertTrue(ledger.contains("recordAppliedDamage(ServerPlayer victim, DamageSource source, float effectiveDamage)"));
        assertTrue(ledger.contains("isVictimRecordable(victim.isAlive(), victim.isDeadOrDying())"));
        assertTrue(ledger.contains("clear(victim.getUUID())"));
    }

    @Test
    void deathRespawnAndMatchLifecycleClearAllPreviousLifeAttribution() throws IOException {
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        String gameState = Files.readString(MAIN.resolve("game/GameStateManager.java"));
        String runtime = Files.readString(MAIN.resolve("game/set/SetMatchRuntime.java"));

        assertTrue(events.contains("onPlayerClone(PlayerEvent.Clone event)"));
        assertTrue(events.contains("CombatAttributionLedger.clear(oldPlayer.getUUID())"));
        assertTrue(events.contains("CombatAttributionLedger.clear(newPlayer.getUUID())"));
        assertTrue(events.contains("onServerStopping(ServerStoppingEvent event)"));
        assertTrue(gameState.contains("matchPhase = MatchPhase.POST_GAME;\n        CombatAttributionLedger.reset();"));
        assertTrue(runtime.contains("CombatAttributionLedger.reset()"));
    }

    @Test
    void deathUsesVanillaResolverBeforeFreshFallbackAndAlwaysClearsLedger() throws IOException {
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        int deathMethod = events.indexOf("private static void processPlayerDeath");
        int direct = events.indexOf("ResponsiblePlayerResolver.resolve(source)", deathMethod);
        int fallback = events.indexOf("CombatAttributionLedger.findFresh(victim)", deathMethod);
        int clear = events.indexOf("CombatAttributionLedger.clear(victim.getUUID())");

        assertTrue(deathMethod >= 0);
        assertTrue(direct > deathMethod);
        assertTrue(fallback > direct);
        assertTrue(clear >= 0);
    }

    @Test
    void oneClaimGuardsAllDeathRewardsStatisticsLivesAndCorpseEffects() throws IOException {
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        int claim = events.indexOf("SetMatchRuntime.claimDeath");
        int process = events.indexOf("PlayerDeathFinalization.process", claim);
        int corpse = events.indexOf("CorpseLootManager.createCorpse", process);
        int death = events.indexOf("PlayerProgressManager.addDeath", process);
        int lives = events.indexOf("LivesManager.handleDeath", process);
        int killerConsequences = events.indexOf("processKillerConsequences", process);

        assertTrue(claim >= 0 && process > claim);
        assertTrue(corpse > process && death > process && lives > process && killerConsequences > process);
    }
}
