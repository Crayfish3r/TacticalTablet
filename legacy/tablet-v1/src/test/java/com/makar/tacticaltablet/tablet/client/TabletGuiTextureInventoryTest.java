package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletGuiTextureInventoryTest {
    private static final Path GUI_TEXTURES =
            Path.of("src/main/resources/assets/tacticaltablet/textures/gui");

    private static final Set<String> REQUIRED_TOP_LEVEL_TEXTURES = Set.of(
            "confirm_panel.png",
            "contract_gui.png",
            "heart.png",
            "players_count.png",
            "tablet.png",
            "tablet_epic.png",
            "tablet_legend.png",
            "vote_panel.png"
    );

    @Test
    void topLevelGuiDirectoryContainsOnlyConnectedTextures() throws IOException {
        Set<String> actual;
        try (var files = Files.list(GUI_TEXTURES)) {
            actual = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png"))
                    .collect(Collectors.toSet());
        }

        assertEquals(REQUIRED_TOP_LEVEL_TEXTURES, actual);
    }

    @Test
    void fallbackClassIconExistsForOptionalMissingClassIcons() {
        assertTrue(Files.isRegularFile(GUI_TEXTURES.resolve("classes/class_fallback.png")));
    }

    @Test
    void connectedGuiTexturesKeepTheirNativeDimensions() throws IOException {
        Map<String, Size> expected = Map.of(
                "confirm_panel.png", new Size(240, 132),
                "contract_gui.png", new Size(210, 350),
                "heart.png", new Size(16, 16),
                "players_count.png", new Size(16, 16),
                "tablet.png", new Size(380, 220),
                "tablet_epic.png", new Size(380, 220),
                "tablet_legend.png", new Size(380, 220),
                "vote_panel.png", new Size(220, 118)
        );

        for (Map.Entry<String, Size> entry : expected.entrySet()) {
            Path file = GUI_TEXTURES.resolve(entry.getKey());
            BufferedImage image = ImageIO.read(file.toFile());
            Size size = entry.getValue();
            assertEquals(size.width(), image.getWidth(), file.toString());
            assertEquals(size.height(), image.getHeight(), file.toString());
        }
    }

    private record Size(int width, int height) {
    }
}
