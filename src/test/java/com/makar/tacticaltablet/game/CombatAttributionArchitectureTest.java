package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatAttributionArchitectureTest {
    @Test
    void damageAndDeathUseTheSameParticipantBoundaryAndResolver() throws IOException {
        String events = source("ServerEvents.java");
        String damage = section(events, "public static void onLivingDamage", "@SubscribeEvent",
                events.indexOf("public static void onLivingDamage") + 1);
        String death = section(events, "private static void processPlayerDeath",
                "private static void processKillerConsequences", 0);

        assertTrue(damage.contains("ResponsiblePlayerResolver.resolve(event.getSource())"));
        assertTrue(death.contains("ResponsiblePlayerResolver.resolve(source)"));
        assertTrue(damage.contains("ActivePvpParticipant.isEligible(attacker)"));
        assertTrue(damage.contains("ActivePvpParticipant.isEligible(victim)"));
        assertTrue(events.contains("return ActivePvpParticipant.isEligible(player);"));
        assertFalse(damage.contains("LivesManager.isAliveParticipant"));
        assertTrue(damage.contains("actualHealthLostFromFinalDamage"));
        assertFalse(damage.contains("victim.getAbsorptionAmount()"));
        assertTrue(damage.contains("CombatDamageEventClaims.claim(event)"));
    }

    @Test
    void resolverUsesTheRequiredSafeAttributionOrder() throws IOException {
        String resolver = source("ResponsiblePlayerResolver.java");
        int sourcePlayer = resolver.indexOf("sourceEntity instanceof ServerPlayer");
        int directProjectile = resolver.indexOf("directEntity instanceof Projectile");
        int safeOwnerController = resolver.indexOf("resolveOwnerOrController(sourceEntity)");

        assertTrue(sourcePlayer >= 0);
        assertTrue(directProjectile > sourcePlayer);
        assertTrue(safeOwnerController > directProjectile);
        assertTrue(resolver.contains("ownable.getOwner() instanceof ServerPlayer"));
        assertTrue(resolver.contains("getControllingPassenger() instanceof ServerPlayer"));
        assertFalse(resolver.contains("return (ServerPlayer)"));
    }

    @Test
    void diagnosticsAreAggregatedAndDisabledByDefault() throws IOException {
        String diagnostics = source("CombatAttributionDiagnostics.java");

        assertTrue(diagnostics.contains("Boolean.getBoolean(ENABLE_PROPERTY)"));
        assertTrue(diagnostics.contains("[combat-attribution-summary]"));
        assertFalse(diagnostics.contains("LOGGER.info(\"[combat-attribution]"));
    }

    private static String source(String file) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/game/" + file
        ));
    }

    private static String section(
            String source,
            String startMarker,
            String endMarker,
            int fromIndex
    ) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, Math.max(start + 1, fromIndex));
        return source.substring(start, end);
    }
}
