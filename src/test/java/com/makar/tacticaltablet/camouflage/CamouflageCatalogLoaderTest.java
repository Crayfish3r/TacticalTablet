package com.makar.tacticaltablet.camouflage;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CamouflageCatalogLoaderTest {
    @Test
    void canonicalSchemaOneCatalogLoadsAllSixPresetsAndNbt() throws Exception {
        String json = Files.readString(Path.of(
                "src/main/resources/defaults/tacticaltablet/camouflage_presets.json"));
        List<String> warnings = new ArrayList<>();

        CamouflageCatalog catalog = CamouflageCatalogLoader.parse(json, ignored -> true, warnings::add);

        assertEquals(Set.of("acacia", "birch", "snow", "dark_oak", "oak", "spruce"),
                catalog.presetIds());
        assertTrue(warnings.isEmpty());
        for (String id : catalog.presetIds()) assertEquals(6, catalog.find(id).orElseThrow().pieces().size());
        CamouflagePiece uniform = catalog.find("spruce").orElseThrow().pieces().get(1);
        assertEquals(0, uniform.itemTag().getInt("Damage"));
        assertTrue(uniform.itemTag().contains("PlateData"));
        assertTrue(catalog.find("missing").isEmpty());
    }

    @Test
    void malformedItemSlotAndPolicySkipOnlyInvalidPieces() {
        String json = """
                {"schema_version":1,"presets":{"mixed":{"pieces":[
                  {"slot":"minecraft:head","item":"minecraft:stone","nbt":"{Damage:0}","policy":"empty_only"},
                  {"slot":"minecraft:head","item":"bad item id","nbt":"{}","policy":"empty_only"},
                  {"slot":"unknown","item":"minecraft:stone","nbt":"{}","policy":"empty_only"},
                  {"slot":"minecraft:feet","item":"minecraft:stone","nbt":"{}","policy":"overwrite_all"}
                ]}}}
                """;
        List<String> warnings = new ArrayList<>();

        CamouflageCatalog catalog = CamouflageCatalogLoader.parse(
                json, id -> id.equals(ResourceLocation.tryParse("minecraft:stone")), warnings::add);

        assertEquals(1, catalog.find("mixed").orElseThrow().pieces().size());
        assertEquals(3, warnings.size());
    }

    @Test
    void malformedNbtDoesNotDiscardOtherPresets() {
        String json = """
                {"schema_version":1,"presets":{
                  "bad":{"pieces":[{"slot":"minecraft:head","item":"minecraft:stone","nbt":"{broken","policy":"empty_only"}]},
                  "good":{"pieces":[{"slot":"minecraft:feet","item":"minecraft:stone","nbt":"{Damage:0}","policy":"empty_only"}]}
                }}
                """;
        CamouflageCatalog catalog = CamouflageCatalogLoader.parse(json, ignored -> true, ignored -> { });
        assertTrue(catalog.find("bad").isEmpty());
        assertEquals(1, catalog.find("good").orElseThrow().pieces().size());
    }
}
