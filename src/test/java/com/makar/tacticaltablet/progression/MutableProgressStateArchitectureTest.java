package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutableProgressStateArchitectureTest {

    @Test
    void adapterDoesNotExposeMutableCollections() {
        for (Method method : MutableProgressState.class.getDeclaredMethods()) {
            assertFalse(Collection.class.isAssignableFrom(method.getReturnType()), method::toString);
            assertFalse(Map.class.isAssignableFrom(method.getReturnType()), method::toString);
        }
    }

    @Test
    void cachedProfileIsTheAdapterRatherThanASecondAggregate() {
        Class<?> playerProgress = Arrays.stream(PlayerProgressManager.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("PlayerProgress"))
                .findFirst()
                .orElseThrow();

        assertTrue(MutableProgressState.class.isAssignableFrom(playerProgress));
        assertTrue(Modifier.isPrivate(playerProgress.getModifiers()));
        assertTrue(ProgressEntry.class.isRecord());
        assertTrue(ProgressReceipt.class.isRecord());
    }
}
