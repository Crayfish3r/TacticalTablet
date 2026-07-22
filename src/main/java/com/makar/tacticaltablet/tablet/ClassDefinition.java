package com.makar.tacticaltablet.tablet;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ClassDefinition(
        ResourceLocation id,
        Component name,
        String classKey,
        ClassCategory category,
        int actionId,
        int price,
        int fixedTier,
        ResourceLocation icon,
        int displayOrder
) {
    public ClassDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(classKey, "classKey");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(icon, "icon");
        if (classKey.isBlank()) throw new IllegalArgumentException("classKey must not be blank");
        if (actionId < 0) throw new IllegalArgumentException("actionId must not be negative");
        if (price < 0) throw new IllegalArgumentException("price must not be negative");
    }
}
