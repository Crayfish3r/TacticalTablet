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
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KitManager {

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
        List<ItemStack> items = loadKit(finalKitName);

        if (items.isEmpty() && !finalKitName.equals(kitName)) {
            System.out.println("[TacticalTablet] Leveled kit not found or empty: " + finalKitName + ", fallback to: " + kitName);
            finalKitName = kitName;
            items = loadKit(kitName);
        }

        if (items.isEmpty()) {
            System.out.println("[TacticalTablet] Kit not found or empty: " + kitName);
            return false;
        }

        System.out.println("[TacticalTablet] Giving kit: player=" + player.getScoreboardName()
                + ", class=" + kitName
                + ", level=" + ClassXPManager.getLevel(player, kitName)
                + ", file=" + finalKitName + ".json");

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
        try {
            Path configPath = FMLPaths.GAMEDIR.get()
                    .resolve("config/tacticaltablet/kits/" + name + ".json");

            File file = configPath.toFile();

            if (!file.exists()) {
                CACHE.remove(name);
                System.out.println("[TacticalTablet] Kit file not found: " + file.getAbsolutePath());
                return List.of();
            }

            long lastModified = file.lastModified();
            long length = file.length();
            CachedKit cached = CACHE.get(name);

            if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                return cached.items;
            }

            List<ItemStack> parsed = parseKitFile(file);
            CACHE.put(name, new CachedKit(lastModified, length, parsed));
            return parsed;
        } catch (Exception e) {
            System.out.println("[TacticalTablet] Failed to load kit: " + name);
            e.printStackTrace();
            return List.of();
        }
    }

    private static List<ItemStack> parseKitFile(File file) throws Exception {
        List<ItemStack> list = new ArrayList<>();

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray items = json.getAsJsonArray("items");

            if (items == null) {
                return list;
            }

            for (JsonElement element : items) {
                try {
                    String nbtString = element.getAsString();
                    CompoundTag tag = TagParser.parseTag(nbtString);
                    ItemStack stack = ItemStack.of(tag);

                    if (!stack.isEmpty()) {
                        list.add(stack);
                    }
                } catch (Exception e) {
                    System.out.println("[TacticalTablet] Failed to parse item: " + element);
                    e.printStackTrace();
                }
            }
        }

        return List.copyOf(list);
    }

    private record CachedKit(long lastModified, long length, List<ItemStack> items) {
    }
}

