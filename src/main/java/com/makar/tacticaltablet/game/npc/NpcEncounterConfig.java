package com.makar.tacticaltablet.game.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** World-local JSON, like extraction settings. Invalid input fails closed without rewriting it. */
public final class NpcEncounterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CONFIG_BYTES = 256 * 1024;
    // Complete output of arenaDefaults() before 9676a9d. Keep independent of current defaults.
    private static final JsonElement LEGACY_ARENA = JsonParser.parseString("""
            {
              "enabled": true,
              "maxActiveNpcs": 24,
              "minGroupSize": 2,
              "maxGroupSize": 4,
              "minSpawnIntervalSeconds": 20,
              "maxSpawnIntervalSeconds": 35,
              "minDistanceFromPlayers": 40.0,
              "maxDistanceFromPlayers": 100.0,
              "minDistanceBetweenGroups": 32.0,
              "maxSpawnAttempts": 8,
              "stopSpawningAtZonePhase": 5,
              "dimension": "minecraft:overworld",
              "allowClanWar": false,
              "allowSpecialModes": false,
              "presets": [
                {
                  "id": "arena_assault", "weight": 8, "minSize": 2, "maxSize": 4,
                  "weapons": ["tacz:ak47", "tacz:ak47", "tacz:ak47", "tacz:ak47"],
                  "behavior": "ATTACK", "ammoPolicy": "FINITE", "reserve": 120, "radius": 24.0
                },
                {
                  "id": "arena_patrol", "weight": 2, "minSize": 2, "maxSize": 3,
                  "weapons": ["tacz:ak47", "tacz:ak47", "tacz:ak47"],
                  "behavior": "PATROL", "ammoPolicy": "FINITE", "reserve": 120, "radius": 24.0
                }
              ]
            }
            """);
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
                         String behavior, String ammoPolicy, int reserve, double radius, String difficulty) {
        public Preset(String id, int weight, int minSize, int maxSize, List<String> weapons,
                      String behavior, String ammoPolicy, int reserve, double radius) {
            this(id, weight, minSize, maxSize, weapons, behavior, ammoPolicy, reserve, radius, "HARD");
        }
        public Preset {
            weapons = weapons == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(weapons));
            difficulty = difficulty == null ? "HARD" : difficulty.strip().toUpperCase(Locale.ROOT);
        }
    }

    public static NpcEncounterConfig load(Path path, Consumer<String> diagnostic) {
        return load(path, diagnostic, new NpcEncounterConfig());
    }

    public static NpcEncounterConfig arenaDefaults() {
        var c = new NpcEncounterConfig();
        c.enabled = true; c.maxActiveNpcs = 24; c.minGroupSize = 2; c.maxGroupSize = 4;
        c.minSpawnIntervalSeconds = 20; c.maxSpawnIntervalSeconds = 35;
        c.presets = List.of(
                new Preset("arena_assault", 8, 2, 4, List.of("tacz:ak47", "tacz:ak47", "tacz:ak47", "tacz:ak47"),
                        "ATTACK", "FINITE", 120, 24, "DEFAULT"),
                new Preset("arena_hard_assault", 2, 2, 4, List.of("tacz:ak47", "tacz:ak47", "tacz:ak47", "tacz:ak47"),
                        "ATTACK", "FINITE", 120, 24, "HARD"),
                new Preset("arena_patrol", 2, 2, 3, List.of("tacz:ak47", "tacz:ak47", "tacz:ak47"),
                        "PATROL", "FINITE", 120, 24, "DEFAULT"),
                new Preset("arena_rpg_pair", 1, 2, 2, List.of("tacz:rpg7", "tacz:rpg7"),
                        "ATTACK", "FINITE", 3, 24, "DEFAULT"));
        return c;
    }

    public static NpcEncounterConfig loadArena(Path path, Consumer<String> diagnostic,
                                               Consumer<Path> migrated) throws IOException {
        return loadArena(path, diagnostic, migrated, false);
    }

    static NpcEncounterConfig loadArena(Path path, Consumer<String> diagnostic,
                                       Consumer<Path> migrated, boolean forceEnabled) throws IOException {
        try {
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                var defaults = validateArena(arenaDefaults(), diagnostic);
                writeArena(path, defaults, null);
                return defaults;
            }

            byte[] original = readBounded(path);
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(original)).toString();
            JsonElement source;
            try (var reader = new JsonReader(new StringReader(json))) {
                source = GSON.getAdapter(JsonElement.class).read(reader);
                if (!source.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                    throw new IOException("Expected a single JSON object");
                }
            }
            if (LEGACY_ARENA.equals(source)) {
                try (var reader = new JsonReader(new StringReader(json))) {
                    if (matchesLegacy(reader, LEGACY_ARENA)) {
                        var defaults = validateArena(arenaDefaults(), diagnostic);
                        Path backup = path.resolveSibling(path.getFileName() + ".pre-difficulty-" + UUID.randomUUID() + ".bak");
                        requireSaved(new AtomicFileStore().writeBytes(backup, original), "Cannot save migration backup");
                        if (!Arrays.equals(original, readBounded(backup))) throw new IOException("Invalid migration backup: " + backup);
                        writeArena(path, defaults, original);
                        migrated.accept(backup);
                        return defaults;
                    }
                }
            }

            var parser = new GsonBuilder().registerTypeAdapter(NpcEncounterConfig.class,
                    (com.google.gson.InstanceCreator<NpcEncounterConfig>) type -> arenaDefaults()).create();
            NpcEncounterConfig config = parser.fromJson(source, NpcEncounterConfig.class);
            if (forceEnabled && config != null) config.enabled = true;
            return validateArena(config, diagnostic);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Arena NPC profile: " + exception.getMessage(), exception);
        }
    }

    /** A JSON tree can collapse duplicate keys or round numbers when comparing primitives. */
    private static boolean matchesLegacy(JsonReader reader, JsonElement expected) throws IOException {
        if (expected.isJsonObject()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) return false;
            var fields = expected.getAsJsonObject();
            var remaining = new HashSet<>(fields.keySet());
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!remaining.remove(name) || !matchesLegacy(reader, fields.get(name))) return false;
            }
            reader.endObject();
            return remaining.isEmpty();
        }
        if (expected.isJsonArray()) {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return false;
            reader.beginArray();
            for (JsonElement member : expected.getAsJsonArray()) {
                if (!reader.hasNext() || !matchesLegacy(reader, member)) return false;
            }
            if (reader.hasNext()) return false;
            reader.endArray();
            return true;
        }
        var value = expected.getAsJsonPrimitive();
        if (value.isNumber()) {
            return reader.peek() == JsonToken.NUMBER
                    && value.getAsBigDecimal().compareTo(new BigDecimal(reader.nextString())) == 0;
        }
        if (value.isBoolean()) return reader.peek() == JsonToken.BOOLEAN && reader.nextBoolean() == value.getAsBoolean();
        return reader.peek() == JsonToken.STRING && reader.nextString().equals(value.getAsString());
    }

    private static NpcEncounterConfig validateArena(NpcEncounterConfig config, Consumer<String> diagnostic) throws IOException {
        var validated = validate(config, diagnostic);
        if (!validated.enabled) throw new IOException("NPC profile is disabled or invalid");
        if (!"minecraft:overworld".equals(validated.dimension)) {
            throw new IOException("Arena NPC dimension must be minecraft:overworld, found " + validated.dimension);
        }
        return validated;
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
            if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("NPC config exceeds 256 KiB: " + path);
            return bytes;
        }
    }

    private static void writeArena(Path path, NpcEncounterConfig config, byte[] original) throws IOException {
        var store = new AtomicFileStore((temporary, target, options) -> {
            boolean unchanged = original == null ? Files.notExists(target, LinkOption.NOFOLLOW_LINKS)
                    : Arrays.equals(original, readBounded(target));
            if (!unchanged) throw new IOException("Source profile changed before commit: " + target);
            Files.move(temporary, target, options);
        });
        requireSaved(store.writeUtf8(path, GSON.toJson(config)), "Cannot commit Arena NPC profile");
    }

    private static void requireSaved(FileSaveResult result, String operation) throws IOException {
        if (result.status() == FileSaveResult.Status.SUCCESS) return;
        var cause = result.exception().orElse(null);
        throw new IOException(operation + ": " + result.diagnostic()
                + (cause == null ? "" : " (" + cause.getMessage() + ")"), cause);
    }

    public static NpcEncounterConfig load(Path path, Consumer<String> diagnostic, NpcEncounterConfig defaults) {
        return load(path, diagnostic, defaults, false);
    }

    static NpcEncounterConfig load(Path path, Consumer<String> diagnostic, NpcEncounterConfig defaults,
                                   boolean forceEnabled) {
        try {
            if (!Files.exists(path)) {
                var saved = new AtomicFileStore().writeUtf8(path, GSON.toJson(defaults));
                if (saved.status() != com.makar.tacticaltablet.storage.FileSaveResult.Status.SUCCESS)
                    throw new IOException("Cannot commit default NPC config: " + saved.diagnostic());
                if (forceEnabled) defaults.enabled = true;
                return defaults;
            }
            if (Files.size(path) > MAX_CONFIG_BYTES) throw new IOException("NPC config exceeds 256 KiB");
            var parser = new GsonBuilder().registerTypeAdapter(NpcEncounterConfig.class,
                    (com.google.gson.InstanceCreator<NpcEncounterConfig>) type -> defaults).create();
            NpcEncounterConfig config = parser.fromJson(Files.readString(path), NpcEncounterConfig.class);
            if (forceEnabled && config != null) config.enabled = true;
            return validate(config, diagnostic);
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
            if (p != null && !List.of("HARD", "DEFAULT").contains(p.difficulty())) {
                diagnostic.accept("Ignoring NPC preset " + p.id() + ": invalid difficulty '" + p.difficulty()
                        + "' (expected HARD or DEFAULT)");
                return false;
            }
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
