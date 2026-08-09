package com.makar.tacticaltablet.game.chaos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosPlayerClassesTest {
    @Test
    void playersShareOfferButSpendClassesIndependently() {
        List<String> offered = List.of("vip", "shop", "base");
        ChaosPlayerClasses first = new ChaosPlayerClasses(offered);
        ChaosPlayerClasses second = new ChaosPlayerClasses(offered);

        assertTrue(first.select("vip"));
        assertTrue(first.consumeSelected());
        assertFalse(first.isAvailable("vip"));
        assertTrue(second.isAvailable("vip"));
        assertTrue(first.isAvailable("shop"));
    }

    @Test
    void rejectsClassesOutsideTripletAndRepeatedSelection() {
        ChaosPlayerClasses state = new ChaosPlayerClasses(List.of("a", "b", "c"));
        assertFalse(state.select("other"));
        assertTrue(state.select("a"));
        assertFalse(state.select("b"));
        assertTrue(state.consumeSelected());
        assertFalse(state.select("a"));
        assertTrue(state.select("b"));
    }
}
