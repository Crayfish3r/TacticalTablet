package com.makar.tacticaltablet.tablet.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** Stable mapping from server-provided map names to optional client-side preview textures. */
final class MapPreviewAssets {
    static final int WIDTH = 96;
    static final int HEIGHT = 54;

    private MapPreviewAssets() {
    }

    static ResourceLocation textureFor(String mapName) {
        Objects.requireNonNull(mapName, "mapName");
        String hash = String.format(Locale.ROOT, "%08x", mapName.hashCode());
        return new ResourceLocation("tacticaltablet", "textures/gui/maps/map_" + hash + ".png");
    }
}
