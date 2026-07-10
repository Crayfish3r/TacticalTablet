package com.makar.tacticaltablet.game.lifecycle;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MatchLifecycleArchitectureTest {
    @Test
    void lifecycleModelDoesNotExposeMinecraftOrForgeTypes() {
        List<Class<?>> classes = List.of(
                MatchState.class,
                MatchStartStep.class,
                MatchTransitionStatus.class,
                MatchFailureStage.class,
                MatchEndReason.class,
                MatchFailure.class,
                LegacyMatchStateMapper.class,
                MatchStartRequest.class,
                MatchContext.class,
                MatchLifecycleSnapshot.class,
                MatchTransitionResult.class,
                MatchTransitionPolicy.class,
                MatchLifecycleService.class
        );

        for (Class<?> clazz : classes) {
            assertNoMinecraftType(clazz);
            for (Field field : clazz.getDeclaredFields()) {
                assertNoMinecraftType(field.getType());
            }
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                for (Class<?> parameterType : constructor.getParameterTypes()) {
                    assertNoMinecraftType(parameterType);
                }
            }
            for (Method method : clazz.getDeclaredMethods()) {
                assertNoMinecraftType(method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertNoMinecraftType(parameterType);
                }
            }
        }
    }

    private static void assertNoMinecraftType(Class<?> type) {
        String name = type.getName();
        assertFalse(name.startsWith("net.minecraft."), name);
        assertFalse(name.startsWith("net.minecraftforge."), name);
        assertFalse(name.startsWith("com.makar.tacticaltablet.game.lifecycle.integration."), name);
    }
}
