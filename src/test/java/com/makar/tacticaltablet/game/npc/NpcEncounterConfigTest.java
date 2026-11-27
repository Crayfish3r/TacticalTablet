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

    @Test void adminCanEnableAValidProfileWithoutRewritingItsSettings() throws Exception {
        var ordinary = directory.resolve("tacticaltablet_npcs.json");
        String disabled = "{\"enabled\":false}";
        Files.writeString(ordinary, disabled);
        assertTrue(NpcEncounterConfig.load(ordinary, message -> fail(message), new NpcEncounterConfig(), true).enabled);
        assertFalse(NpcEncounterConfig.load(ordinary, message -> fail(message)).enabled);
        assertEquals(disabled, Files.readString(ordinary));
        var arena = directory.resolve("tacticaltablet_arena_npcs.json");
        Files.writeString(arena, disabled);
        assertTrue(NpcEncounterConfig.loadArena(arena, message -> fail(message), path -> fail(path.toString()), true).enabled);
        assertEquals(disabled, Files.readString(arena));
        Files.writeString(ordinary, "{\"enabled\":false,\"maxActiveNpcs\":0}");
        assertFalse(NpcEncounterConfig.load(ordinary, message -> {}, new NpcEncounterConfig(), true).enabled);
    }

    @Test void arenaMigratesOnlyTheExactLegacyProfileAndKeepsOneBackup() throws Exception {
        String legacy = """
                {
                  "maxActiveNpcs": 24, "enabled": true, "maxGroupSize": 4, "minGroupSize": 2,
                  "minSpawnIntervalSeconds": 20, "maxSpawnIntervalSeconds": 35,
                  "minDistanceFromPlayers": 40, "maxDistanceFromPlayers": 100, "minDistanceBetweenGroups": 32,
                  "maxSpawnAttempts": 8, "stopSpawningAtZonePhase": 5, "dimension": "minecraft:overworld",
                  "allowClanWar": false, "allowSpecialModes": false,
                  "presets": [
                    {
                      "id": "arena_assault", "weight": 8, "minSize": 2, "maxSize": 4,
                      "weapons": ["tacz:ak47", "tacz:ak47", "tacz:ak47", "tacz:ak47"],
                      "behavior": "ATTACK", "ammoPolicy": "FINITE", "reserve": 120, "radius": 24
                    },
                    {
                      "id": "arena_patrol", "weight": 2, "minSize": 2, "maxSize": 3,
                      "weapons": ["tacz:ak47", "tacz:ak47", "tacz:ak47"],
                      "behavior": "PATROL", "ammoPolicy": "FINITE", "reserve": 120, "radius": 24
                    }
                  ]
                }
                """;
        var path = directory.resolve("tacticaltablet_arena_npcs.json");
        String customized = legacy.replace("\"enabled\": true", "\"enabled\": true, \"customField\": true");
        Files.writeString(path, customized);
        var backups = new ArrayList<Path>();
        var custom = NpcEncounterConfig.loadArena(path, message -> fail(message), backups::add);
        assertEquals(List.of("HARD", "HARD"), custom.presets.stream().map(NpcEncounterConfig.Preset::difficulty).toList());
        assertEquals(customized, Files.readString(path));
        assertTrue(backups.isEmpty());

        Files.writeString(path, legacy);
        byte[] original = Files.readAllBytes(path);
        var updated = NpcEncounterConfig.loadArena(path, message -> fail(message), backups::add);
        assertEquals(List.of("arena_assault", "arena_hard_assault", "arena_patrol", "arena_rpg_pair"),
                updated.presets.stream().map(NpcEncounterConfig.Preset::id).toList());
        assertEquals(1, backups.size());
        assertArrayEquals(original, Files.readAllBytes(backups.get(0)));

        byte[] committed = Files.readAllBytes(path);
        NpcEncounterConfig.loadArena(path, message -> fail(message), backups::add);
        assertArrayEquals(committed, Files.readAllBytes(path));
        assertEquals(1, backups.size());
    }

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
