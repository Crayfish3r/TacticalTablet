package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractTrackerScreenTest {

    private static final double EPSILON = 1.0E-6;

    @Test
    void mapsMinecraftYawToMapCardinalDirections() {
        assertDirection(180.0F, 0.0D, -1.0D);
        assertDirection(-90.0F, 1.0D, 0.0D);
        assertDirection(0.0F, 0.0D, 1.0D);
        assertDirection(90.0F, -1.0D, 0.0D);
    }

    @Test
    void interpolatesAcrossYawWrapUsingShortestTurn() {
        assertEquals(180.0F, ContractTrackerScreen.interpolateYaw(179.0F, -179.0F, 0.5F), EPSILON);
        assertEquals(-180.0F, ContractTrackerScreen.interpolateYaw(-179.0F, 179.0F, 0.5F), EPSILON);
    }

    private static void assertDirection(float yaw, double expectedX, double expectedY) {
        assertEquals(expectedX, ContractTrackerScreen.directionX(yaw), EPSILON);
        assertEquals(expectedY, ContractTrackerScreen.directionY(yaw), EPSILON);
    }
}
