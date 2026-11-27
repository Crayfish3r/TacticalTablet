package com.makar.tacticaltablet.tablet.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ButtonTextureSpec(ResourceLocation location, int width, int height) {
    public ButtonTextureSpec {
        Objects.requireNonNull(location, "location");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Button texture dimensions must be positive");
        }
    }
}
