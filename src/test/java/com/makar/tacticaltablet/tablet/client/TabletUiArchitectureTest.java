package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletUiArchitectureTest {
    private static final Path CLIENT = Path.of("src/main/java/com/makar/tacticaltablet/tablet/client");

    @Test
    void actionPagesUseRegistryAndHaveNoEightItemCopyLimit() throws IOException {
        String screen = source("TabletScreen.java");

        assertTrue(screen.contains("actionsFor(ClassCategory.BASE)"));
        assertTrue(screen.contains("actionsFor(ClassCategory.SHOP)"));
        assertTrue(screen.contains("actionsFor(ClassCategory.EXCLUSIVE)"));
        assertFalse(screen.contains("Arrays.copyOf"));
        int pagesStart = screen.indexOf("private static final TabletPage[] PAGES");
        int pagesEnd = screen.indexOf("};", pagesStart);
        assertFalse(screen.substring(pagesStart, pagesEnd).contains("TELEPORT_RTP"));
    }

    @Test
    void scrollGridIsWidgetFreeAndAlwaysReleasesScissor() throws IOException {
        String grid = source("ScrollableActionGrid.java");

        assertFalse(grid.contains("extends Button"));
        assertFalse(grid.contains("addRenderableWidget"));
        assertTrue(grid.contains("try {"));
        assertTrue(grid.contains("finally {"));
        assertTrue(grid.contains("graphics.disableScissor()"));
    }

    @Test
    void navigationAndCardsHaveDedicatedComponents() throws IOException {
        String screen = source("TabletScreen.java");

        assertTrue(screen.contains("TabletNavigationRail navigationRail"));
        assertTrue(screen.contains("TabletActionCard.render"));
        assertTrue(screen.contains("new TabletRtpButton"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(CLIENT.resolve(name)).replace("\r\n", "\n");
    }
}
