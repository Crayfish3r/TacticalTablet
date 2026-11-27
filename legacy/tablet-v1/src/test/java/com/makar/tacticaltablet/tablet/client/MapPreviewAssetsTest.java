package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapPreviewAssetsTest {
    @Test
    void usesDocumentedDimensionsAndStableJavaHashPath() {
        assertEquals(96, MapPreviewAssets.WIDTH);
        assertEquals(54, MapPreviewAssets.HEIGHT);
        assertEquals("tacticaltablet:textures/gui/maps/map_00364492.png",
                MapPreviewAssets.textureFor("test").toString());
        assertEquals("tacticaltablet:textures/gui/maps/map_14b815ff.png",
                MapPreviewAssets.textureFor("Раскольск").toString());
        assertEquals("tacticaltablet:textures/gui/maps/map_0b30f167.png",
                MapPreviewAssets.textureFor("Советский город").toString());
    }
}
