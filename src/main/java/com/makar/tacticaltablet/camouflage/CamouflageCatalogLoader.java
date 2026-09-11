package com.makar.tacticaltablet.camouflage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class CamouflageCatalogLoader {
    public static final String CONFIG_RELATIVE_PATH = "tacticaltablet/camouflage_presets.json";
    private static final String DEFAULT_RESOURCE = "/defaults/tacticaltablet/camouflage_presets.json";
    private static volatile CamouflageCatalog activeCatalog = CamouflageCatalog.empty();

    private CamouflageCatalogLoader() { }

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_RELATIVE_PATH).toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) {
                String defaultJson = readDefault();
                FileSaveResult result = new AtomicFileStore().writeUtf8(path, defaultJson);
                if (result.status() != FileSaveResult.Status.SUCCESS) {
                    TacticalTabletMod.LOGGER.error("Failed to create camouflage catalog at {}: {}",
                            path, result.diagnostic(), result.exception().orElse(null));
                    activeCatalog = parse(defaultJson, CamouflageCatalogLoader::registeredItem,
                            message -> TacticalTabletMod.LOGGER.warn("{}", message));
                    return;
                }
                TacticalTabletMod.LOGGER.info("Created default camouflage catalog at {}", path);
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            activeCatalog = parse(json, CamouflageCatalogLoader::registeredItem,
                    message -> TacticalTabletMod.LOGGER.warn("{}", message));
            TacticalTabletMod.LOGGER.info("Loaded {} camouflage preset(s) from {}",
                    activeCatalog.presetIds().size(), path);
        } catch (IOException | RuntimeException exception) {
            activeCatalog = CamouflageCatalog.empty();
            TacticalTabletMod.LOGGER.error("Failed to load camouflage catalog at {}", path, exception);
        }
    }

    public static CamouflageCatalog catalog() { return activeCatalog; }

    public static void reset() { activeCatalog = CamouflageCatalog.empty(); }

    static CamouflageCatalog parse(String json, Predicate<ResourceLocation> itemExists, Consumer<String> warning) {
        Map<String, CamouflagePreset> presets = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("schema_version") || root.get("schema_version").getAsInt() != CamouflageCatalog.SCHEMA_VERSION) {
                warning.accept("Unsupported camouflage catalog schema_version");
                return CamouflageCatalog.empty();
            }
            JsonObject presetObject = root.getAsJsonObject("presets");
            if (presetObject == null) {
                warning.accept("Camouflage catalog has no presets object");
                return CamouflageCatalog.empty();
            }
            for (Map.Entry<String, JsonElement> presetEntry : presetObject.entrySet()) {
                String presetId = presetEntry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                try {
                    JsonElement piecesElement = presetEntry.getValue().getAsJsonObject().get("pieces");
                    if (piecesElement == null || !piecesElement.isJsonArray()) {
                        warning.accept("Skipping camouflage preset '" + presetId + "': pieces must be an array");
                        continue;
                    }
                    java.util.List<CamouflagePiece> pieces = new java.util.ArrayList<>();
                    int index = 0;
                    for (JsonElement element : piecesElement.getAsJsonArray()) {
                        parsePiece(presetId, index++, element, itemExists, warning).ifPresent(pieces::add);
                    }
                    if (pieces.isEmpty()) {
                        warning.accept("Skipping camouflage preset '" + presetId + "': no valid pieces");
                        continue;
                    }
                    presets.put(presetId, new CamouflagePreset(presetId, pieces));
                } catch (RuntimeException exception) {
                    warning.accept("Skipping malformed camouflage preset '" + presetId + "': " + exception.getMessage());
                }
            }
        } catch (JsonSyntaxException | IllegalStateException | ClassCastException exception) {
            warning.accept("Malformed camouflage catalog: " + exception.getMessage());
            return CamouflageCatalog.empty();
        }
        return new CamouflageCatalog(presets);
    }

    private static java.util.Optional<CamouflagePiece> parsePiece(
            String presetId, int index, JsonElement element, Predicate<ResourceLocation> itemExists,
            Consumer<String> warning) {
        try {
            JsonObject object = element.getAsJsonObject();
            String slotValue = requiredString(object, "slot");
            String itemValue = requiredString(object, "item");
            String nbtValue = requiredString(object, "nbt");
            String policyValue = requiredString(object, "policy");
            var target = CamouflageTarget.parse(slotValue);
            if (target.isEmpty()) {
                warning.accept(problem(presetId, index, "unknown slot '" + slotValue + "'"));
                return java.util.Optional.empty();
            }
            var policy = CamouflagePolicy.parse(policyValue);
            if (policy.isEmpty()) {
                warning.accept(problem(presetId, index, "unknown policy '" + policyValue + "'"));
                return java.util.Optional.empty();
            }
            ResourceLocation itemId = ResourceLocation.tryParse(itemValue);
            if (itemId == null || !itemExists.test(itemId)) {
                warning.accept(problem(presetId, index, "unknown item '" + itemValue + "'"));
                return java.util.Optional.empty();
            }
            CompoundTag tag = TagParser.parseTag(nbtValue);
            return java.util.Optional.of(new CamouflagePiece(target.get(), itemId, tag, policy.get()));
        } catch (Exception exception) {
            warning.accept(problem(presetId, index, "invalid piece: " + exception.getMessage()));
            return java.util.Optional.empty();
        }
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("missing " + name);
        return value.getAsString();
    }

    private static String problem(String preset, int piece, String detail) {
        return "Camouflage preset '" + preset + "' piece " + piece + ": " + detail;
    }

    private static boolean registeredItem(ResourceLocation id) {
        return ForgeRegistries.ITEMS.containsKey(id);
    }

    private static String readDefault() throws IOException {
        try (InputStream stream = CamouflageCatalogLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) throw new IOException("Missing bundled camouflage catalog " + DEFAULT_RESOURCE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
