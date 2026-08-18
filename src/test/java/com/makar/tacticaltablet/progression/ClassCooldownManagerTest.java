package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassCooldownManagerTest {
    @Test
    void crossbowmanUsesItsStableActionIdAndSevenMinuteCooldown() {
        int classId = ClassCooldownManager.classIdForClass("crossbowman");

        assertEquals(24, classId);
        assertEquals(7L * 60L * 1000L, ClassCooldownManager.getCooldownTime(classId));
    }

    @Test
    void smartStormtrooperUsesItsStableActionIdAndTenMinuteCooldown() {
        int classId = ClassCooldownManager.classIdForClass("smartstormtrooper");

        assertEquals(25, classId);
        assertEquals(10L * 60L * 1000L, ClassCooldownManager.getCooldownTime(classId));
    }
}
