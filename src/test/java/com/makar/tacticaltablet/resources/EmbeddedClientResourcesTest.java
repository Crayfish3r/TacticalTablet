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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedClientResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path MOD_ASSETS = RESOURCES.resolve("assets");
    private static final Path OVERRIDE_PACK =
            RESOURCES.resolve("resourcepacks/tacticaltablet_overrides");
    private static final Path OVERRIDE_ASSETS = OVERRIDE_PACK.resolve("assets");
    private static final Path MANIFEST =
            Path.of("src/test/resources/deluxewarfare-runtime-assets.tsv");
    @Test
    void everyMigratedRuntimeAssetMatchesTheRepositoryManifest() throws Exception {
        List<ManifestEntry> entries = manifest();

        assertEquals(149, entries.size());
        assertEquals(Map.of(
                "curios", 2L,
                "minecraft", 10L,
                "tacticaltablet", 137L
        ), entries.stream().collect(java.util.stream.Collectors.groupingBy(
                entry -> entry.path().substring(0, entry.path().indexOf('/')),
                java.util.stream.Collectors.counting()
        )));

        for (ManifestEntry entry : entries) {
            Path file = asset(entry.path());
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
        Path guiRoot = asset("tacticaltablet/textures/gui");
        List<Path> guiPngs = pngs(guiRoot);
        guiPngs.add(asset("minecraft/textures/gui/container/inventory.png"));
        guiPngs.add(asset("minecraft/textures/gui/recipe_button.png"));
        guiPngs.add(asset("curios/textures/gui/inventory.png"));
        guiPngs.add(asset("curios/textures/gui/inventory_revamp.png"));

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
                asset("tacticaltablet/textures/gui/buttons/class_button.png")
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
            Path file = asset("tacticaltablet/textures/gui/classes/" + icon + ".png");
            BufferedImage image = readImage(file);
            assertEquals(16, image.getWidth(), icon);
            assertEquals(16, image.getHeight(), icon);
        }
        assertFalse(Files.exists(
                asset("tacticaltablet/textures/gui/classes/soldier.png")
        ));
    }

    @Test
    void jsonResourcesAreValidAndLocalReferencesResolve() throws IOException {
        List<Path> jsonFiles;
        try (Stream<Path> modFiles = Files.walk(MOD_ASSETS);
             Stream<Path> overrideFiles = Files.walk(OVERRIDE_ASSETS)) {
            jsonFiles = Stream.concat(modFiles, overrideFiles)
                    .filter(Files::isRegularFile)
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
    void localizationAndRecoveryCompassOverridesRemainFunctional() throws IOException {
        assertFalse(Files.exists(asset("minecraft/font/default.json")));
        assertFalse(Files.exists(asset(
                "deluxewarfare/font/jetbrains_mono_medium.ttf"
        )));

        for (String language : List.of("en_us", "ru_ru")) {
            JsonObject locale = json(
                    asset("minecraft/lang/" + language + ".json")
            ).getAsJsonObject();
            assertEquals("", locale.get("container.crafting").getAsString());
        }

        JsonObject compass = json(
                asset("minecraft/models/item/recovery_compass.json")
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
            if (entry.path().endsWith(".ogg")) {
                assertTrue(entry.size() > 0, entry.path());
            }
        }
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/META-INF/licenses/Curios-LGPL-3.0-or-later.txt"
        )));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/META-INF/licenses/JetBrains-Mono-Apache-2.0.txt"
        )));
        String notices = Files.readString(Path.of(
                "src/main/resources/THIRD_PARTY_NOTICES.txt"
        ));
        assertFalse(notices.contains("JetBrains Mono"));
        assertTrue(notices.contains("Curios GUI reference assets"));
    }

    @Test
    void crossNamespaceOverridesLiveOnlyInTheRequiredTopPriorityPack() throws IOException {
        assertFalse(Files.exists(MOD_ASSETS.resolve("curios")));
        assertFalse(Files.exists(MOD_ASSETS.resolve("minecraft")));
        assertFalse(Files.exists(MOD_ASSETS.resolve("deluxewarfare")));

        JsonObject pack = json(OVERRIDE_PACK.resolve("pack.mcmeta")).getAsJsonObject()
                .getAsJsonObject("pack");
        assertEquals(15, pack.get("pack_format").getAsInt());

        String registration = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/client/EmbeddedClientResourcePack.java"
        ));
        assertTrue(registration.contains("event.getPackType() != PackType.CLIENT_RESOURCES"));
        assertTrue(registration.contains("Pack.Position.TOP"));
        assertTrue(registration.contains("PackSource.BUILT_IN"));
        assertTrue(registration.contains("Component.literal(\"Tactical Tablet Overrides\"),\n"
                + "                true,"));
        assertTrue(registration.contains("Pack.Position.TOP,\n"
                + "                true,"));
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
                        asset(location[0] + "/textures/particle/" + location[1] + ".png")
                ), texture.getAsString());
            }
        }
    }

    private static void verifySoundReferences() throws IOException {
        JsonObject sounds = json(asset("tacticaltablet/sounds.json")).getAsJsonObject();
        for (JsonElement definition : sounds.asMap().values()) {
            for (JsonElement sound : definition.getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonPrimitive()
                        ? sound.getAsString()
                        : sound.getAsJsonObject().get("name").getAsString();
                String[] location = splitLocation(name);
                assertTrue(Files.isRegularFile(
                        asset(location[0] + "/sounds/" + location[1] + ".ogg")
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
                asset(location[0] + "/models/" + location[1] + ".json")
        ), reference);
    }

    private static void verifyTextureReference(String reference) {
        String[] location = splitLocation(reference);
        if ("minecraft".equals(location[0])) {
            return;
        }
        assertTrue(Files.isRegularFile(
                asset(location[0] + "/textures/" + location[1] + ".png")
        ), reference);
    }

    private static Path resolveRawResource(String reference) {
        String[] location = splitLocation(reference);
        return asset(location[0] + "/" + location[1]);
    }

    private static String[] splitLocation(String reference) {
        int separator = reference.indexOf(':');
        return separator < 0
                ? new String[]{"minecraft", reference}
                : new String[]{reference.substring(0, separator), reference.substring(separator + 1)};
    }

    private static List<Path> jsonFilesUnder(String folder) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path assetRoot : List.of(MOD_ASSETS, OVERRIDE_ASSETS)) {
            try (Stream<Path> namespaces = Files.list(assetRoot)) {
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
        Path root = path.startsWith(MOD_ASSETS) ? MOD_ASSETS : OVERRIDE_ASSETS;
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Path asset(String path) {
        int separator = path.indexOf('/');
        String namespace = separator < 0 ? path : path.substring(0, separator);
        return "tacticaltablet".equals(namespace)
                ? MOD_ASSETS.resolve(path)
                : OVERRIDE_ASSETS.resolve(path);
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
                        column(columns, 3).isEmpty() ? null : Integer.valueOf(column(columns, 3)),
                        column(columns, 4).isEmpty() ? null : Integer.valueOf(column(columns, 4)),
                        column(columns, 5).isEmpty() ? null : Boolean.valueOf(column(columns, 5))
                ))
                .toList();
    }

    private static String column(String[] columns, int index) {
        return index < columns.length ? columns[index] : "";
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
