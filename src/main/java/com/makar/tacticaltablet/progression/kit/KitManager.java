package com.makar.tacticaltablet.progression.kit;

import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassXPManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KitManager {

    private static final String KIT_DIRECTORY = "kits";
    private static final String ALT_KIT_DIRECTORY = "kits_alt";
    private static final String ALT_SUFFIX = "_alt";
    private static final int MAX_KIT_NBT_LENGTH = 2048;

    private static final Set<String> LEVEL_KITS = Set.of(
            "stormtrooper",
            "sniper",
            "mortarman",
            "scout",
            "droneoperator",
            "machinegunner",
            "rpgtrooper",
            "boomguy",
            "dream"
    );

    private static final Map<String, CachedKit> CACHE = new ConcurrentHashMap<>();

    public static boolean giveKit(ServerPlayer player, String kitName) {
        if (player == null || kitName == null || kitName.isBlank()) return false;

        String finalKitName = getLeveledKitName(player, kitName);
        String loadedKitName = finalKitName;
        String loadedDirectory = KIT_DIRECTORY;
        List<ItemStack> items = List.of();

        if (KitRotationManager.isAltKitsActive()) {
            String altKitName = finalKitName + ALT_SUFFIX;
            if (kitFileExists(ALT_KIT_DIRECTORY, altKitName)) {
                items = loadKit(ALT_KIT_DIRECTORY, altKitName, true);
                if (!items.isEmpty()) {
                    loadedKitName = altKitName;
                    loadedDirectory = ALT_KIT_DIRECTORY;
                } else {
                    System.out.println("[TacticalTablet] Alt kit not found or empty: " + altKitName
                            + ", fallback to regular: " + finalKitName);
                }
            }
        }

        if (items.isEmpty()) {
            items = loadKit(finalKitName);
        }

        if (items.isEmpty() && !finalKitName.equals(kitName)) {
            System.out.println("[TacticalTablet] Leveled kit not found or empty: " + finalKitName + ", fallback to: " + kitName);
            finalKitName = kitName;
            loadedKitName = kitName;
            loadedDirectory = KIT_DIRECTORY;
            items = loadKit(kitName);
        }

        if (items.isEmpty()) {
            System.out.println("[TacticalTablet] Kit not found or empty: " + kitName);
            return false;
        }

        System.out.println("[TacticalTablet] Giving kit: player=" + player.getScoreboardName()
                + ", class=" + kitName
                + ", level=" + ClassXPManager.getLevel(player, kitName)
                + ", file=" + loadedDirectory + "/" + loadedKitName + ".json");

        for (ItemStack stack : items) {
            player.getInventory().add(stack.copy());
        }

        player.getInventory().setChanged();
        InventoryManager.syncInventory(player);
        return true;
    }

    private static String getLeveledKitName(ServerPlayer player, String kitName) {
        if (!LEVEL_KITS.contains(kitName)) {
            return kitName;
        }

        int level = ClassXPManager.getLevel(player, kitName);

        if (level >= 2) {
            return kitName + "_legend";
        }

        if (level == 1) {
            return kitName + "_epic";
        }

        return kitName;
    }

    private static List<ItemStack> loadKit(String name) {
        return loadKit(KIT_DIRECTORY, name, true);
    }

    private static List<ItemStack> loadKit(String directory, String name, boolean logMissing) {
        try {
            Path configPath = FMLPaths.GAMEDIR.get()
                    .resolve("config/tacticaltablet/" + directory + "/" + name + ".json");

            File file = configPath.toFile();
            String cacheKey = directory + "/" + name;

            if (!file.exists()) {
                CACHE.remove(cacheKey);
                if (logMissing) {
                    System.out.println("[TacticalTablet] Kit file not found: " + file.getAbsolutePath());
                }
                return List.of();
            }

            long lastModified = file.lastModified();
            long length = file.length();
            CachedKit cached = CACHE.get(cacheKey);

            if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                return cached.items;
            }

            List<ItemStack> parsed = parseKitFile(file);
            CACHE.put(cacheKey, new CachedKit(lastModified, length, parsed));
            return parsed;
        } catch (Exception e) {
            System.out.println("[TacticalTablet] Failed to load kit " + directory + "/" + name
                    + ": " + e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static boolean kitFileExists(String directory, String name) {
        Path configPath = FMLPaths.GAMEDIR.get()
                .resolve("config/tacticaltablet/" + directory + "/" + name + ".json");

        return configPath.toFile().exists();
    }

    private static List<ItemStack> parseKitFile(File file) throws Exception {
        List<ItemStack> list = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray items = json.getAsJsonArray("items");

            if (items == null) {
                return list;
            }

            for (JsonElement element : items) {
                String itemId = "unknown";
                try {
                    String nbtString = element.getAsString();
                    if (nbtString.length() > MAX_KIT_NBT_LENGTH) {
                        System.out.println("[TacticalTablet] Kit item NBT too long in " + file.getName()
                                + "; skipping item.");
                        continue;
                    }

                    itemId = extractItemId(nbtString);
                    CompoundTag tag = TagParser.parseTag(nbtString);
                    ItemStack stack = ItemStack.of(tag);

                    if (!stack.isEmpty()) {
                        list.add(stack);
                    }
                } catch (Exception e) {
                    System.out.println("[TacticalTablet] Failed to parse kit item in " + file.getName()
                            + " item=" + itemId + ": " + e.getClass().getSimpleName());
                }
            }
        }

        return List.copyOf(list);
    }

    private static String extractItemId(String nbtString) {
        if (nbtString == null || nbtString.isBlank()) return "unknown";

        int idIndex = nbtString.indexOf("id:");
        if (idIndex < 0) return "unknown";

        int valueStart = idIndex + 3;
        while (valueStart < nbtString.length() && Character.isWhitespace(nbtString.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= nbtString.length()) return "unknown";

        char quote = nbtString.charAt(valueStart);
        if (quote == '"' || quote == '\'') {
            int valueEnd = nbtString.indexOf(quote, valueStart + 1);
            if (valueEnd > valueStart) {
                return nbtString.substring(valueStart + 1, Math.min(valueEnd, valueStart + 80));
            }
        }

        int valueEnd = valueStart;
        while (valueEnd < nbtString.length()) {
            char current = nbtString.charAt(valueEnd);
            if (current == ',' || current == '}' || Character.isWhitespace(current)) {
                break;
            }
            valueEnd++;
        }

        if (valueEnd <= valueStart) return "unknown";
        return nbtString.substring(valueStart, Math.min(valueEnd, valueStart + 80));
    }

    private record CachedKit(long lastModified, long length, List<ItemStack> items) {
    }
}

