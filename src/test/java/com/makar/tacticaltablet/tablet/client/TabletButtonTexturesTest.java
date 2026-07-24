package com.makar.tacticaltablet.tablet.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletButtonTexturesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String PREFIX = "textures/gui/buttons/";

    @Test
    void registryContainsEveryRequiredTextureWithExactLogicalAndNativeSize() throws IOException {
        Map<String, Size> expected = expectedTextures();
        Map<String, ButtonTextureSpec> actual = TabletButtonTextures.all().stream()
                .collect(Collectors.toMap(
                        spec -> spec.location().getPath(),
                        spec -> spec,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        assertEquals(56, expected.size());
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<String, Size> entry : expected.entrySet()) {
            ButtonTextureSpec spec = actual.get(entry.getKey());
            Size expectedSize = entry.getValue();
            assertEquals(expectedSize.width(), spec.width(), entry.getKey());
            assertEquals(expectedSize.height(), spec.height(), entry.getKey());
            assertEquals("tacticaltablet", spec.location().getNamespace(), entry.getKey());

            Path file = RESOURCES.resolve("assets/tacticaltablet").resolve(entry.getKey());
            assertTrue(Files.isRegularFile(file), file.toString());
            BufferedImage image = ImageIO.read(file.toFile());
            assertNotNull(image, file.toString());
            assertEquals(expectedSize.width(), image.getWidth(), file.toString());
            assertEquals(expectedSize.height(), image.getHeight(), file.toString());
        }
    }

    @Test
    void classCardsHaveExactlyOneNeutralTextureAndNoPerStateOrTierPngs() throws IOException {
        Set<String> names;
        try (var files = Files.list(RESOURCES.resolve("assets/tacticaltablet").resolve(PREFIX))) {
            names = files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }

        assertTrue(names.contains("class_button.png"));
        assertEquals(1, names.stream().filter(name -> name.startsWith("class_button")).count());
        assertFalse(names.contains("class_button_hover.png"));
        assertFalse(names.contains("class_button_disabled.png"));
        for (String tier : Set.of("rare", "epic", "legend", "monster")) {
            assertFalse(names.contains("class_button_" + tier + ".png"));
        }

        Path classButton = RESOURCES.resolve("assets/tacticaltablet").resolve(PREFIX).resolve("class_button.png");
        BufferedImage image = ImageIO.read(classButton.toFile());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                assertEquals((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, "red/green at " + x + "," + y);
                assertEquals((argb >> 8) & 0xFF, argb & 0xFF, "green/blue at " + x + "," + y);
            }
        }
    }

    @Test
    void everyButtonPngContainsRealPartialAlpha() throws IOException {
        for (ButtonTextureSpec spec : TabletButtonTextures.all()) {
            Path file = RESOURCES.resolve("assets/tacticaltablet").resolve(spec.location().getPath());
            BufferedImage image = ImageIO.read(file.toFile());
            assertNotNull(image, file.toString());
            boolean hasPartialAlpha = false;
            for (int y = 0; y < image.getHeight() && !hasPartialAlpha; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    if (alpha > 0 && alpha < 0xFF) {
                        hasPartialAlpha = true;
                        break;
                    }
                }
            }
            assertTrue(hasPartialAlpha, file + " must contain alpha values between 1 and 254");
        }
    }

    @Test
    void stateSelectionUsesDisabledThenActiveThenHoverThenNormal() {
        ButtonTextureSpec normal = spec("normal");
        ButtonTextureSpec hover = spec("hover");
        ButtonTextureSpec active = spec("active");
        ButtonTextureSpec disabled = spec("disabled");
        ButtonTextureSet textures = new ButtonTextureSet(normal, hover, active, disabled);

        assertEquals(disabled, textures.select(false, true, true));
        assertEquals(active, textures.select(true, true, true));
        assertEquals(hover, textures.select(true, false, true));
        assertEquals(normal, textures.select(true, false, false));
    }

    private static ButtonTextureSpec spec(String name) {
        return new ButtonTextureSpec(
                ResourceLocation.fromNamespaceAndPath("tacticaltablet", PREFIX + name + ".png"),
                10,
                10
        );
    }

    private static Map<String, Size> expectedTextures() {
        LinkedHashMap<String, Size> result = new LinkedHashMap<>();
        put(result, new Size(130, 34), "class_button.png");
        for (String page : new String[]{"classes", "shop", "vip", "clans", "profile"}) {
            put(result, new Size(72, 28),
                    "nav_" + page + ".png",
                    "nav_" + page + "_hover.png",
                    "nav_" + page + "_active.png");
        }
        putStates(result, new Size(78, 20), "rtp_button");
        put(result, new Size(264, 22), "clan_row.png", "clan_row_hover.png", "clan_row_active.png");
        putStates(result, new Size(120, 24), "clan_create_button");
        putStates(result, new Size(76, 22), "clan_back_button");
        putStates(result, new Size(120, 24), "clan_action_button");
        putStates(result, new Size(120, 24), "clan_danger_button");
        putStates(result, new Size(58, 18), "clan_small_button");
        putStates(result, new Size(40, 14), "clan_request_accept");
        putStates(result, new Size(44, 14), "clan_request_reject");
        putStates(result, new Size(42, 14), "clan_kick");
        put(result, new Size(16, 16),
                "clan_color.png",
                "clan_color_hover.png",
                "clan_color_selected.png",
                "clan_color_disabled.png");
        putStates(result, new Size(96, 24), "confirm_cancel");
        putStates(result, new Size(96, 24), "confirm_primary");
        return result.entrySet().stream().collect(Collectors.toMap(
                entry -> PREFIX + entry.getKey(),
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private static void putStates(Map<String, Size> result, Size size, String baseName) {
        put(result, size, baseName + ".png", baseName + "_hover.png", baseName + "_disabled.png");
    }

    private static void put(Map<String, Size> result, Size size, String... names) {
        for (String name : names) result.put(name, size);
    }

    private record Size(int width, int height) {
    }
}
