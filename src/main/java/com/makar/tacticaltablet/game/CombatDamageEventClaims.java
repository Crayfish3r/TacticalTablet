package com.makar.tacticaltablet.game;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class CombatDamageEventClaims {
    private static final Set<Object> CLAIMED_EVENTS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private CombatDamageEventClaims() {
    }

    public static boolean claim(Object event) {
        return event != null && CLAIMED_EVENTS.add(event);
    }

    public static void reset() {
        CLAIMED_EVENTS.clear();
    }
}
