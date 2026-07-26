package com.makar.tacticaltablet.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedClientResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets");
    private static final Path MANIFEST =
            Path.of("src/test/resources/deluxewarfare-runtime-assets.tsv");
    private static final Set<String> FONT_FALLBACKS = Set.of(
            "minecraft:include/space",
            "minecraft:include/default",
            "minecraft:include/unifont"
    );

    @Test
    void everyMigratedRuntimeAssetMatchesTheRepositoryManifest() throws Exception {
        List<ManifestEntry> entries = manifest();

        assertEquals(151, entries.size());
        assertEquals(Map.of(
                "curios", 2L,
                "deluxewarfare", 1L,
                "minecraft", 11L,
                "tacticaltablet", 137L
        ), entries.stream().collect(java.util.stream.Collectors.groupingBy(
                entry -> entry.path().substring(0, entry.path().indexOf('/')),
                java.util.stream.Collectors.counting()
        )));

        for (ManifestEntry entry : entries) {
            Path file = ASSETS.resolve(entry.path());
            assertTrue(Files.isRegularFile(file), entry.path());
            assertEquals(entry.size(), Files.size(file), entry.path());
            assertEquals(entry.sha256(), sha256(file), entry.path());

            if (entry.pngWidth() != null) {
                BufferedImage image = readImage(file);
                assertEquals(entry.pngWidth(), image.getWidth(), entry.path());
                assertEquals(entry.pngHeight(), image.getHeight(), entry.path());
                assertEquals(entry.alphaChannel(), image.getColorModel().hasAlpha(), entry.path());
            }
        }
    }

    @Test
    void guiPngsRetainTransparencyAndButtonsContainPartialAlpha() throws IOException {
        Path guiRoot = ASSETS.resolve("tacticaltablet/textures/gui");
        List<Path> guiPngs = pngs(guiRoot);
        guiPngs.add(ASSETS.resolve("minecraft/textures/gui/container/inventory.png"));
        guiPngs.add(ASSETS.resolve("minecraft/textures/gui/recipe_button.png"));
        guiPngs.add(ASSETS.resolve("curios/textures/gui/inventory.png"));
        guiPngs.add(ASSETS.resolve("curios/textures/gui/inventory_revamp.png"));

        for (Path png : guiPngs) {
            BufferedImage image = readImage(png);
            assertTrue(image.getColorModel().hasAlpha(), relative(png));
            assertTrue(hasTransparentPixel(image), relative(png));
        }

        for (Path button : pngs(guiRoot.resolve("buttons"))) {
            assertTrue(hasPartiallyTransparentPixel(readImage(button)), relative(button));
        }
    }

    @Test
    void classButtonRemainsNeutralGrayscaleAndClassIconsAreComplete() throws IOException {
        BufferedImage classButton = readImage(
                ASSETS.resolve("tacticaltablet/textures/gui/buttons/class_button.png")
        );
        for (int y = 0; y < classButton.getHeight(); y++) {
            for (int x = 0; x < classButton.getWidth(); x++) {
                int argb = classButton.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                assertTrue(red == green && green == blue, "colored pixel at " + x + "," + y);
            }
        }

        List<String> icons = List.of(
                "stormtrooper", "sniper", "scout", "droneoperator", "machinegunner",
                "mortarman", "rpgtrooper", "boomguy", "dream", "tagilla", "blackops",
                "cowboy", "solider", "rebel", "saboteur", "killer", "miniboss", "shahed",
                "krot", "marine", "medic", "microwave", "railgunner", "class_fallback"
        );
        for (String icon : icons) {
            Path file = ASSETS.resolve("tacticaltablet/textures/gui/classes/" + icon + ".png");
            BufferedImage image = readImage(file);
            assertEquals(16, image.getWidth(), icon);
            assertEquals(16, image.getHeight(), icon);
        }
        assertFalse(Files.exists(
                ASSETS.resolve("tacticaltablet/textures/gui/classes/soldier.png")
        ));
    }

    @Test
    void jsonResourcesAreValidAndLocalReferencesResolve() throws IOException {
        List<Path> jsonFiles;
        try (Stream<Path> files = Files.walk(ASSETS)) {
            jsonFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();
        }

        for (Path jsonFile : jsonFiles) {
            JsonElement root;
            try (var reader = Files.newBufferedReader(jsonFile)) {
                root = JsonParser.parseReader(reader);
            }
            assertNotNull(root, relative(jsonFile));
            assertTrue(root.isJsonObject() || root.isJsonArray(), relative(jsonFile));
        }

        verifyModelReferences();
        verifyParticleReferences();
        verifySoundReferences();
    }

    @Test
    void fontLocalizationAndRecoveryCompassOverridesRemainFunctional() throws IOException {
        JsonObject font = json(
                ASSETS.resolve("minecraft/font/default.json")
        ).getAsJsonObject();
        JsonArray providers = font.getAsJsonArray("providers");
        Set<String> references = Stream.iterate(0, index -> index + 1)
                .limit(providers.size())
                .map(providers::get)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(provider -> "reference".equals(provider.get("type").getAsString()))
                .map(provider -> provider.get("id").getAsString())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(FONT_FALLBACKS, references);
        assertTrue(providers.toString().contains("deluxewarfare:tactical_mono.ttf"));

        Path fontFile = ASSETS.resolve("deluxewarfare/font/tactical_mono.ttf");
        assertTrue(Files.size(fontFile) > 0);
        for (String language : List.of("en_us", "ru_ru")) {
            JsonObject locale = json(
                    ASSETS.resolve("minecraft/lang/" + language + ".json")
            ).getAsJsonObject();
            assertEquals("", locale.get("container.crafting").getAsString());
        }

        JsonObject compass = json(
                ASSETS.resolve("minecraft/models/item/recovery_compass.json")
        ).getAsJsonObject();
        JsonArray overrides = compass.getAsJsonArray("overrides");
        long extractionOverrides = Stream.iterate(0, index -> index + 1)
                .limit(overrides.size())
                .map(overrides::get)
                .map(JsonElement::getAsJsonObject)
                .map(override -> override.getAsJsonObject("predicate"))
                .filter(predicate -> predicate.has("custom_model_data"))
                .count();
        assertEquals(33, extractionOverrides);
    }

    @Test
    void binaryAssetsAndThirdPartyNoticesArePackagedAsSources() throws IOException {
        for (ManifestEntry entry : manifest()) {
            if (entry.path().endsWith(".ogg") || entry.path().endsWith(".ttf")) {
                assertTrue(entry.size() > 0, entry.path());
            }
        }
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/META-INF/licenses/Curios-LGPL-3.0-or-later.txt"
        )));
        String notices = Files.readString(Path.of(
                "src/main/resources/THIRD_PARTY_NOTICES.txt"
        ));
        assertTrue(notices.contains("DejaVu Sans Mono"));
        assertTrue(notices.contains("Curios GUI reference assets"));
    }

    private static void verifyModelReferences() throws IOException {
        for (Path model : jsonFilesUnder("models")) {
            JsonObject object = json(model).getAsJsonObject();
            if (object.has("model")) {
                String reference = object.get("model").getAsString();
                if (reference.endsWith(".obj")) {
                    assertTrue(Files.isRegularFile(resolveRawResource(reference)), reference);
                }
            }
            if (object.has("parent")) {
                verifyModelReference(object.get("parent").getAsString());
            }
            if (object.has("overrides")) {
                for (JsonElement override : object.getAsJsonArray("overrides")) {
                    verifyModelReference(override.getAsJsonObject().get("model").getAsString());
                }
            }
            if (object.has("textures")) {
                for (JsonElement texture : object.getAsJsonObject("textures").asMap().values()) {
                    String reference = texture.getAsString();
                    if (!reference.startsWith("#")) {
                        verifyTextureReference(reference);
                    }
                }
            }
        }

        for (Path blockstate : jsonFilesUnder("blockstates")) {
            JsonObject object = json(blockstate).getAsJsonObject();
            for (JsonElement variant : object.getAsJsonObject("variants").asMap().values()) {
                if (variant.isJsonArray()) {
                    for (JsonElement choice : variant.getAsJsonArray()) {
                        verifyModelReference(choice.getAsJsonObject().get("model").getAsString());
                    }
                } else {
                    verifyModelReference(variant.getAsJsonObject().get("model").getAsString());
                }
            }
        }
    }

    private static void verifyParticleReferences() throws IOException {
        for (Path particle : jsonFilesUnder("particles")) {
            JsonObject object = json(particle).getAsJsonObject();
            for (JsonElement texture : object.getAsJsonArray("textures")) {
                String[] location = splitLocation(texture.getAsString());
                assertTrue(Files.isRegularFile(
                        ASSETS.resolve(location[0] + "/textures/particle/" + location[1] + ".png")
                ), texture.getAsString());
            }
        }
    }

    private static void verifySoundReferences() throws IOException {
        JsonObject sounds = json(ASSETS.resolve("tacticaltablet/sounds.json")).getAsJsonObject();
        for (JsonElement definition : sounds.asMap().values()) {
            for (JsonElement sound : definition.getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonPrimitive()
                        ? sound.getAsString()
                        : sound.getAsJsonObject().get("name").getAsString();
                String[] location = splitLocation(name);
                assertTrue(Files.isRegularFile(
                        ASSETS.resolve(location[0] + "/sounds/" + location[1] + ".ogg")
                ), name);
            }
        }
    }

    private static void verifyModelReference(String reference) {
        String[] location = splitLocation(reference);
        if ("minecraft".equals(location[0])) {
            return;
        }
        assertTrue(Files.isRegularFile(
                ASSETS.resolve(location[0] + "/models/" + location[1] + ".json")
        ), reference);
    }

    private static void verifyTextureReference(String reference) {
        String[] location = splitLocation(reference);
        if ("minecraft".equals(location[0])) {
            return;
        }
        assertTrue(Files.isRegularFile(
                ASSETS.resolve(location[0] + "/textures/" + location[1] + ".png")
        ), reference);
    }

    private static Path resolveRawResource(String reference) {
        String[] location = splitLocation(reference);
        return ASSETS.resolve(location[0] + "/" + location[1]);
    }

    private static String[] splitLocation(String reference) {
        int separator = reference.indexOf(':');
        return separator < 0
                ? new String[]{"minecraft", reference}
                : new String[]{reference.substring(0, separator), reference.substring(separator + 1)};
    }

    private static List<Path> jsonFilesUnder(String folder) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> namespaces = Files.list(ASSETS)) {
            for (Path namespace : namespaces.toList()) {
                Path root = namespace.resolve(folder);
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(root)) {
                    result.addAll(files.filter(Files::isRegularFile)
                            .filter(file -> file.getFileName().toString().endsWith(".json"))
                            .toList());
                }
            }
        }
        return result;
    }

    private static JsonElement json(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static List<Path> pngs(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return new ArrayList<>(files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".png"))
                    .toList());
        }
    }

    private static BufferedImage readImage(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, relative(file));
            return image;
        }
    }

    private static boolean hasTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasPartiallyTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String relative(Path path) {
        return ASSETS.relativize(path).toString().replace('\\', '/');
    }

    private static List<ManifestEntry> manifest() throws IOException {
        return Files.readAllLines(MANIFEST).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\t", -1))
                .map(columns -> new ManifestEntry(
                        columns[0],
                        Long.parseLong(columns[1]),
                        columns[2],
                        columns[3].isEmpty() ? null : Integer.valueOf(columns[3]),
                        columns[4].isEmpty() ? null : Integer.valueOf(columns[4]),
                        columns[5].isEmpty() ? null : Boolean.valueOf(columns[5])
                ))
                .toList();
    }

    private record ManifestEntry(
            String path,
            long size,
            String sha256,
            Integer pngWidth,
            Integer pngHeight,
            Boolean alphaChannel
    ) {
    }
}
