package com.makar.tacticaltablet.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedServerDataResourcesTest {
    private static final Path DATA = Path.of("src/main/resources/data/lobby");

    @Test
    void dimensionAndStructureRetainOriginalIdsAndBytes() throws Exception {
        Path dimension = DATA.resolve("dimension/lobby.json");
        Path dimensionType = DATA.resolve("dimension_type/lobby.json");
        Path structure = DATA.resolve("structures/spawn.nbt");

        assertEquals("f20b614fc6cac9923215b3891e8932f90035a3c2dae479a147b1008c7494ad89",
                sha256NormalizedText(dimension));
        JsonObject dimensionTypeJson = JsonParser.parseString(Files.readString(dimensionType)).getAsJsonObject();
        assertEquals(0.0F, dimensionTypeJson.get("ambient_light").getAsFloat());
        assertEquals("75ed60c6143ef7ebf5fad70d5e942a09177002b4014f4b6a55c38ddcf506c61e",
                sha256(structure));

        JsonObject dimensionJson = JsonParser.parseString(Files.readString(dimension)).getAsJsonObject();
        assertEquals("lobby:lobby", dimensionJson.get("type").getAsString());

        try (InputStream input = Files.newInputStream(structure)) {
            CompoundTag nbt = NbtIo.readCompressed(input);
            assertEquals(3465, nbt.getInt("DataVersion"));
            assertEquals(35, nbt.getList("size", 3).getInt(0));
            assertEquals(20, nbt.getList("size", 3).getInt(1));
            assertEquals(35, nbt.getList("size", 3).getInt(2));
            var blocks = nbt.getList("blocks", 10);
            assertEquals(24_500, blocks.size());
            var palette = nbt.getList("palette", 10);
            int mossCarpetState = -1;
            for (int index = 0; index < palette.size(); index++) {
                if ("minecraft:moss_carpet".equals(palette.getCompound(index).getString("Name"))) {
                    mossCarpetState = index;
                    break;
                }
            }
            assertTrue(mossCarpetState >= 0);
            int mossCarpets = 0;
            for (int index = 0; index < blocks.size(); index++) {
                if (blocks.getCompound(index).getInt("state") == mossCarpetState) mossCarpets++;
            }
            assertEquals(15, mossCarpets);
            int blockEntities = 0;
            for (int index = 0; index < blocks.size(); index++) {
                if (blocks.getCompound(index).contains("nbt", 10)) blockEntities++;
            }
            assertEquals(30, blockEntities);
            assertTrue(nbt.contains("entities", 9));
            assertEquals(11, nbt.getList("entities", 10).size());
            var entities = nbt.getList("entities", 10);
            boolean containsPainting = false;
            for (int index = 0; index < entities.size(); index++) {
                if ("minecraft:painting".equals(entities.getCompound(index).getCompound("nbt").getString("id"))) {
                    containsPainting = true;
                }
            }
            assertTrue(containsPainting);
        }
    }

    @Test
    void noRuntimeFunctionsOrFunctionTagsAreEmbedded() {
        Path data = Path.of("src/main/resources/data");
        assertFalse(Files.exists(data.resolve("war")));
        assertFalse(Files.exists(data.resolve("lobby/functions")));
        assertFalse(Files.exists(data.resolve("minecraft/tags/functions/load.json")));
        assertFalse(Files.exists(data.resolve("minecraft/tags/functions/tick.json")));
        assertTrue(Files.isRegularFile(DATA.resolve("dimension/lobby.json")));
        assertTrue(Files.isRegularFile(DATA.resolve("structures/spawn.nbt")));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256NormalizedText(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String normalized = Files.readString(path).replace("\r\n", "\n");
        digest.update(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }
}
