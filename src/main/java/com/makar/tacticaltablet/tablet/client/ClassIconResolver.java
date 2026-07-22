package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.ClassDefinition;
import com.makar.tacticaltablet.tablet.ClassDefinitions;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Predicate;

public final class ClassIconResolver {
    private ClassIconResolver() {
    }

    public static ResourceLocation resolve(ClassDefinition definition, Predicate<ResourceLocation> resourceExists) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(resourceExists, "resourceExists");
        return resourceExists.test(definition.icon()) ? definition.icon() : ClassDefinitions.FALLBACK_ICON;
    }
}
