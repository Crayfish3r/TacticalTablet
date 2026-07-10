package com.makar.tacticaltablet.airdrop.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AirdropLootLoader {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path CONFIG_PATH = FMLPaths.GAMEDIR.get()
            .resolve("config/tacticaltablet/airdrop_loot.json");

    private static final int DEFAULT_MIN_STACKS = 4;
    private static final int DEFAULT_MAX_STACKS = 10;
    private static final int MAX_NBT_LENGTH = 2048;
    private static final int MIN_CHEST_SLOT = 0;
    private static final int MAX_CHEST_SLOT = 26;

    private static AirdropLootConfig cachedConfig = AirdropLootConfig.empty();

    private AirdropLootLoader() {
    }

    public static synchronized AirdropLootConfig getConfig() {
        if (cachedConfig.sets().isEmpty()) {
            reload();
        }

        return cachedConfig;
    }

    public static synchronized int reload() {
        ensureExampleFile();

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            cachedConfig = parseConfig(root);
        } catch (IOException | JsonSyntaxException exception) {
            TacticalTabletMod.LOGGER.error("Failed to load AirDrop loot config at {}", CONFIG_PATH, exception);
            cachedConfig = AirdropLootConfig.empty();
        }

        TacticalTabletMod.LOGGER.info(
                "Loaded {} valid AirDrop loot sets with {} valid entries from {}",
                cachedConfig.sets().size(),
                cachedConfig.entryCount(),
                CONFIG_PATH
        );
        return cachedConfig.entryCount();
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    private static AirdropLootConfig parseConfig(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            return AirdropLootConfig.empty();
        }

        if (root.isJsonArray()) {
            return parseFlatArray(root.getAsJsonArray(), DEFAULT_MIN_STACKS, DEFAULT_MAX_STACKS);
        }

        if (!root.isJsonObject()) {
            TacticalTabletMod.LOGGER.warn("AirDrop loot config root must be an object or an array.");
            return AirdropLootConfig.empty();
        }

        JsonObject object = root.getAsJsonObject();
        int defaultMin = getInt(object, "minItemStacks", DEFAULT_MIN_STACKS);
        int defaultMax = getInt(object, "maxItemStacks", DEFAULT_MAX_STACKS);
        JsonArray sets = object.getAsJsonArray("sets");

        if (sets == null) {
            JsonArray items = object.getAsJsonArray("items");
            return items == null ? AirdropLootConfig.empty() : parseFlatArray(items, defaultMin, defaultMax);
        }

        List<AirdropLootSet> validSets = new ArrayList<>();
        int entryCount = 0;
        int totalSetWeight = 0;

        for (JsonElement element : sets) {
            AirdropLootSet set = GSON.fromJson(element, AirdropLootSet.class);
            AirdropLootSet valid = validateSet(set, defaultMin, defaultMax);
            if (valid == null) {
                continue;
            }

            validSets.add(valid);
            entryCount += valid.items.size();
            totalSetWeight += valid.weight;
        }

        return new AirdropLootConfig(List.copyOf(validSets), entryCount, totalSetWeight);
    }

    private static AirdropLootConfig parseFlatArray(JsonArray array, int minStacks, int maxStacks) {
        List<AirdropLootEntry> validEntries = validateEntries(GSON.fromJson(array, AirdropLootEntry[].class), "default");
        if (validEntries.isEmpty()) {
            return AirdropLootConfig.empty();
        }

        AirdropLootSet defaultSet = new AirdropLootSet();
        defaultSet.name = "default";
        defaultSet.weight = 1;
        defaultSet.minItemStacks = minStacks;
        defaultSet.maxItemStacks = maxStacks;
        defaultSet.items = validEntries;

        return new AirdropLootConfig(List.of(defaultSet), validEntries.size(), defaultSet.weight);
    }

    private static AirdropLootSet validateSet(AirdropLootSet set, int defaultMin, int defaultMax) {
        if (set == null) {
            TacticalTabletMod.LOGGER.warn("Skipped invalid AirDrop loot set: null");
            return null;
        }

        if (set.name == null || set.name.isBlank()) {
            set.name = "unnamed";
        }

        if (set.weight <= 0) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot set '{}' with invalid weight.", set.name);
            return null;
        }

        if (set.minItemStacks <= 0) {
            set.minItemStacks = defaultMin;
        }

        if (set.maxItemStacks <= 0) {
            set.maxItemStacks = defaultMax;
        }

        if (set.maxItemStacks < set.minItemStacks) {
            int fixed = set.minItemStacks;
            set.minItemStacks = set.maxItemStacks;
            set.maxItemStacks = fixed;
        }

        set.items = validateEntries(set.items == null ? null : set.items.toArray(new AirdropLootEntry[0]), set.name);
        if (set.items.isEmpty()) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot set '{}' with no valid items.", set.name);
            return null;
        }

        return set;
    }

    private static List<AirdropLootEntry> validateEntries(AirdropLootEntry[] entries, String setName) {
        List<AirdropLootEntry> parsed = new ArrayList<>();

        if (entries == null) {
            return parsed;
        }

        for (AirdropLootEntry entry : entries) {
            if (!isValid(entry, setName)) {
                continue;
            }

            normalize(entry);
            parsed.add(entry);
        }

        return List.copyOf(parsed);
    }

    private static void ensureExampleFile() {
        if (Files.exists(CONFIG_PATH)) return;

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(exampleConfig(), writer);
            }

            TacticalTabletMod.LOGGER.info("Created example AirDrop loot config at {}", CONFIG_PATH);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to create example AirDrop loot config at {}", CONFIG_PATH, exception);
        }
    }

    private static boolean isValid(AirdropLootEntry entry, String setName) {
        if (entry == null) {
            TacticalTabletMod.LOGGER.warn("Skipped invalid AirDrop loot entry in set '{}': null", setName);
            return false;
        }

        if (entry.item == null || entry.item.isBlank()) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot entry with empty item id in set '{}'.", setName);
            return false;
        }

        ResourceLocation id;
        try {
            id = new ResourceLocation(entry.item);
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot entry with invalid item id in set '{}': {}", setName, entry.item);
            return false;
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot entry with unknown item id in set '{}': {}", setName, entry.item);
            return false;
        }

        if (entry.slot != null && (entry.slot < MIN_CHEST_SLOT || entry.slot > MAX_CHEST_SLOT)) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot entry with invalid slot in set '{}': {}", setName, entry.item);
            return false;
        }

        if (entry.count <= 0 && (entry.min <= 0 || entry.max <= 0 || entry.max < entry.min)) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot entry with invalid count in set '{}': {}", setName, entry.item);
            return false;
        }

        if (entry.nbt != null && entry.nbt.length() > MAX_NBT_LENGTH) {
            TacticalTabletMod.LOGGER.warn("Skipped AirDrop loot entry with oversized NBT in set '{}': {}", setName, entry.item);
            return false;
        }

        return true;
    }

    private static void normalize(AirdropLootEntry entry) {
        entry.item = entry.item.trim();
        if (entry.nbt != null && entry.nbt.isBlank()) {
            entry.nbt = null;
        }
        // TODO: min/max ranges and per-entry weights are parsed for config compatibility,
        // but generation still places explicit entries to preserve current loot balance.
        if (entry.count <= 0) {
            entry.count = entry.min;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.item));
        int maxStackSize = item == null ? 1 : Math.max(1, item.getMaxStackSize());
        if (entry.count > maxStackSize) {
            TacticalTabletMod.LOGGER.warn("Clamped AirDrop loot count for {} to max stack size {}.", entry.item, maxStackSize);
            entry.count = maxStackSize;
        }
        if (entry.weight <= 0) {
            entry.weight = 1;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
    }

    private static ExampleConfig exampleConfig() {
        ExampleSet combat = new ExampleSet();
        combat.name = "combat";
        combat.weight = 5;
        combat.items = Arrays.asList(
                entry("minecraft:golden_apple", null, 0, 2),
                entry("superbwarfare:hand_grenade", null, 1, 3),
                entry("minecraft:diamond", null, 2, 3)
        );

        ExampleSet ammo = new ExampleSet();
        ammo.name = "ammo";
        ammo.weight = 5;
        ammo.items = Arrays.asList(
                entry("tacz:ammo", "{AmmoId:\"tacz:762x39\"}", 0, 48),
                entry("tacz:ammo", "{AmmoId:\"tacz:556x45\"}", 1, 48),
                entry("minecraft:golden_carrot", null, 2, 16)
        );

        ExampleConfig config = new ExampleConfig();
        config.sets = Arrays.asList(combat, ammo);
        return config;
    }

    private static AirdropLootEntry entry(String item, String nbt, int slot, int count) {
        AirdropLootEntry entry = new AirdropLootEntry();
        entry.item = item;
        entry.nbt = nbt;
        entry.slot = slot;
        entry.count = count;
        entry.weight = 1;
        return entry;
    }

    public record AirdropLootConfig(List<AirdropLootSet> sets, int entryCount, int totalSetWeight) {
        static AirdropLootConfig empty() {
            return new AirdropLootConfig(List.of(), 0, 0);
        }
    }

    private static final class ExampleConfig {
        List<ExampleSet> sets;
    }

    private static final class ExampleSet {
        String name;
        int weight;
        List<AirdropLootEntry> items;
    }
}
