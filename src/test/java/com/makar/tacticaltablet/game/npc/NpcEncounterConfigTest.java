package com.makar.tacticaltablet.game.npc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcEncounterConfigTest {
    @TempDir Path directory;

    @Test void defaultsAreOptInAndMatchRequestedLimits() {
        var c = NpcEncounterConfig.validate(new NpcEncounterConfig(), fail -> fail(fail));
        assertFalse(c.enabled); assertFalse(c.allowClanWar); assertFalse(c.allowSpecialModes);
        assertEquals(12, c.maxActiveNpcs); assertEquals(1, c.minGroupSize); assertEquals(4, c.maxGroupSize);
        assertEquals(45, c.minSpawnIntervalSeconds); assertEquals(90, c.maxSpawnIntervalSeconds);
        assertEquals(40, c.minDistanceFromPlayers); assertEquals(100, c.maxDistanceFromPlayers);
        assertEquals(32, c.minDistanceBetweenGroups); assertEquals(8, c.maxSpawnAttempts);
        assertEquals(5, c.stopSpawningAtZonePhase); assertEquals("minecraft:overworld", c.dimension);
        assertEquals(4, c.presets.size());
    }

    @Test void eachInvalidGlobalSettingDisablesSpawning() {
        List<java.util.function.Consumer<NpcEncounterConfig>> mutations = List.of(
                c -> c.maxActiveNpcs = 0, c -> c.maxActiveNpcs = 257, c -> c.minGroupSize = 0,
                c -> c.maxGroupSize = 17, c -> c.minGroupSize = 5, c -> c.minSpawnIntervalSeconds = 0,
                c -> c.maxSpawnIntervalSeconds = 44, c -> c.maxSpawnIntervalSeconds = Integer.MAX_VALUE,
                c -> c.minDistanceFromPlayers = Double.NaN, c -> c.maxDistanceFromPlayers = 20,
                c -> c.minDistanceBetweenGroups = -1, c -> c.maxSpawnAttempts = 65,
                c -> c.stopSpawningAtZonePhase = 0, c -> c.dimension = "lobby:lobby",
                c -> c.dimension = "BAD ID", c -> c.presets = null);
        for (var mutation : mutations) {
            var c = new NpcEncounterConfig(); c.enabled = true; mutation.accept(c);
            var errors = new ArrayList<String>();
            assertFalse(NpcEncounterConfig.validate(c, errors::add).enabled);
            assertFalse(errors.isEmpty());
        }
    }

    @Test void invalidPresetsAreDiagnosedAndIgnoredWithoutInventingGuns() {
        var c = new NpcEncounterConfig(); c.enabled = true;
        var good = c.presets.get(0);
        c.presets = java.util.Arrays.asList(good, good, null,
                new NpcEncounterConfig.Preset("negative", -1, 1, 1, List.of("tacz:ak47"), "ATTACK", "FINITE", 10, 24),
                new NpcEncounterConfig.Preset("unknown", 1, 1, 1, List.of("tacz:ak47"), "MAGIC", "FINITE", 10, 24),
                new NpcEncounterConfig.Preset("badgun", 1, 1, 1, List.of("Not a resource"), "ATTACK", "FINITE", 10, 24),
                new NpcEncounterConfig.Preset("short", 1, 2, 4, List.of("tacz:ak47"), "ATTACK", "FINITE", 10, 24),
                new NpcEncounterConfig.Preset("radius", 1, 1, 1, List.of("tacz:ak47"), "PATROL", "FINITE", 10, 65));
        var errors = new ArrayList<String>();
        assertTrue(NpcEncounterConfig.validate(c, errors::add).enabled);
        assertEquals(List.of(good), c.presets); assertEquals(7, errors.size());
    }

    @Test void emptyAndZeroWeightProfilesDisableEncounters() {
        var c = new NpcEncounterConfig(); c.enabled = true; c.presets = List.of();
        assertFalse(NpcEncounterConfig.validate(c, message -> {}).enabled);
        c.enabled = true;
        c.presets = List.of(new NpcEncounterConfig.Preset("off", 0, 1, 1, List.of("tacz:ak47"), "ATTACK", "FINITE", 10, 24));
        assertFalse(NpcEncounterConfig.validate(c, message -> {}).enabled);
    }

    @Test void brokenJsonIsPreservedAndDefaultCreationIsCommitted() throws Exception {
        var path = directory.resolve("npcs.json"); var errors = new ArrayList<String>();
        assertFalse(NpcEncounterConfig.load(path, errors::add).enabled);
        assertTrue(Files.readString(path).contains("maxActiveNpcs")); assertTrue(errors.isEmpty());
        Files.writeString(path, "{broken json");
        assertFalse(NpcEncounterConfig.load(path, errors::add).enabled);
        assertEquals("{broken json", Files.readString(path)); assertFalse(errors.isEmpty());
    }

    @Test void failedSaveIsReportedAndNeverEnablesNpc() throws Exception {
        var parent = directory.resolve("file"); Files.writeString(parent, "keep");
        var errors = new ArrayList<String>();
        assertFalse(NpcEncounterConfig.load(parent.resolve("npcs.json"), errors::add).enabled);
        assertFalse(errors.isEmpty()); assertEquals("keep", Files.readString(parent));
    }
}
