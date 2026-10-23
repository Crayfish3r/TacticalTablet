package com.makar.tacticaltablet.game.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.storage.AtomicFileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/** World-local JSON, like extraction settings. Invalid input fails closed without rewriting it. */
public final class NpcEncounterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = false;
    public int maxActiveNpcs = 12;
    public int minGroupSize = 1;
    public int maxGroupSize = 4;
    public int minSpawnIntervalSeconds = 45;
    public int maxSpawnIntervalSeconds = 90;
    public double minDistanceFromPlayers = 40;
    public double maxDistanceFromPlayers = 100;
    public double minDistanceBetweenGroups = 32;
    public int maxSpawnAttempts = 8;
    public int stopSpawningAtZonePhase = 5;
    public String dimension = "minecraft:overworld";
    public boolean allowClanWar = false;
    public boolean allowSpecialModes = false;
    public List<Preset> presets = List.of(
            new Preset("lone_survivor", 3, 1, 1, List.of("tacz:ak47"), "ATTACK", "FINITE", 120, 24),
            new Preset("patrol", 2, 2, 2, List.of("tacz:ak47", "tacz:ak47"), "PATROL", "FINITE", 120, 24),
            new Preset("assault", 2, 3, 3, List.of("tacz:ak47", "tacz:ak47", "tacz:ak47"), "ATTACK", "FINITE", 120, 24),
            new Preset("defense", 1, 2, 4, List.of("tacz:ak47", "tacz:ak47", "tacz:ak47", "tacz:ak47"), "DEFEND", "FINITE", 120, 24));

    public record Preset(String id, int weight, int minSize, int maxSize, List<String> weapons,
                         String behavior, String ammoPolicy, int reserve, double radius) {
        public Preset {
            weapons = weapons == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(weapons));
        }
    }

    public static NpcEncounterConfig load(Path path, Consumer<String> diagnostic) {
        try {
            if (!Files.exists(path)) {
                var defaults = new NpcEncounterConfig();
                var saved = new AtomicFileStore().writeUtf8(path, GSON.toJson(defaults));
                if (saved.status() != com.makar.tacticaltablet.storage.FileSaveResult.Status.SUCCESS)
                    throw new IOException("Cannot commit default NPC config: " + saved.diagnostic());
                return defaults;
            }
            if (Files.size(path) > 262144) throw new IOException("NPC config exceeds 256 KiB");
            return validate(GSON.fromJson(Files.readString(path), NpcEncounterConfig.class), diagnostic);
        } catch (IOException | RuntimeException exception) {
            diagnostic.accept("NPC config disabled: " + exception.getMessage());
            return new NpcEncounterConfig();
        }
    }

    static NpcEncounterConfig validate(NpcEncounterConfig c, Consumer<String> diagnostic) {
        if (c == null) { diagnostic.accept("NPC config is null; disabled"); return new NpcEncounterConfig(); }
        if (c.maxActiveNpcs < 1 || c.maxActiveNpcs > 256
                || c.minGroupSize < 1 || c.maxGroupSize > 16 || c.maxGroupSize < c.minGroupSize || c.minGroupSize > c.maxActiveNpcs
                || c.minSpawnIntervalSeconds < 1 || c.maxSpawnIntervalSeconds > 86400
                || c.maxSpawnIntervalSeconds < c.minSpawnIntervalSeconds
                || !finiteRange(c.minDistanceFromPlayers, 1, 4096)
                || !finiteRange(c.maxDistanceFromPlayers, c.minDistanceFromPlayers, 4096)
                || !finiteRange(c.minDistanceBetweenGroups, 0, 4096)
                || c.maxSpawnAttempts < 1 || c.maxSpawnAttempts > 64
                || c.stopSpawningAtZonePhase < 1 || c.stopSpawningAtZonePhase > 100
                || !resourceId(c.dimension) || "lobby:lobby".equals(c.dimension)
                || c.presets == null || c.presets.size() > 64) {
            diagnostic.accept("Invalid NPC limits, intervals, dimension or presets; NPCs disabled (file preserved)");
            c.enabled = false;
            return c;
        }
        var ids = new HashSet<String>();
        c.presets = c.presets.stream().filter(p -> {
            boolean valid = p != null && p.id() != null && p.id().matches("[a-z0-9_.-]{1,64}")
                    && p.weight() >= 0 && p.weight() <= 1000000
                    && p.minSize() >= 1 && p.maxSize() >= p.minSize() && p.maxSize() <= 16
                    && p.weapons().size() >= p.maxSize() && p.weapons().size() <= 16
                    && p.weapons().stream().allMatch(NpcEncounterConfig::resourceId)
                    && List.of("ATTACK", "PATROL", "DEFEND", "STAND").contains(p.behavior() == null ? "" : p.behavior())
                    && List.of("FINITE", "INFINITE").contains(p.ammoPolicy() == null ? "" : p.ammoPolicy())
                    && p.reserve() >= 0 && p.reserve() <= 1000000
                    && finiteRange(p.radius(), 4, "DEFEND".equals(p.behavior()) ? 128 : 64)
                    && ids.add(p.id());
            if (!valid) diagnostic.accept("Ignoring invalid/duplicate NPC preset: " + (p == null ? "null" : p.id()));
            return valid;
        }).toList();
        if (c.presets.stream().noneMatch(p -> p.weight() > 0 && p.maxSize() >= c.minGroupSize
                && p.minSize() <= Math.min(c.maxGroupSize, c.maxActiveNpcs))) {
            diagnostic.accept("No positive-weight NPC presets fitting group size/capacity; NPCs disabled"); c.enabled = false;
        }
        return c;
    }

    private static boolean finiteRange(double v, double min, double max) { return Double.isFinite(v) && v >= min && v <= max; }
    private static boolean resourceId(String id) { return id != null && id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"); }
}
