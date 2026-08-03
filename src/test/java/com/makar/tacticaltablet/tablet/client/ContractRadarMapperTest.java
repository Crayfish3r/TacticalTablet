package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractRadarMapperTest {

    private static final double EPSILON = 1.0E-6;

    @Test
    void mapsZoneBoundsAndCenterToRadar() {
        assertEquals(10, ContractRadarMapper.coordinate(10, 100, -50, 0, 50));
        assertEquals(60, ContractRadarMapper.coordinate(10, 100, 0, 0, 50));
        assertEquals(109, ContractRadarMapper.coordinate(10, 100, 50, 0, 50));
    }

    @Test
    void clampsWorldCoordinatesOutsideZone() {
        assertEquals(10, ContractRadarMapper.coordinate(10, 100, -500, 0, 50));
        assertEquals(109, ContractRadarMapper.coordinate(10, 100, 500, 0, 50));
    }

    @Test
    void guardsZeroRadiusAndBoundsTargetMarkers() {
        assertEquals(60, ContractRadarMapper.coordinate(10, 100, 0, 0, 0));
        assertEquals(5, ContractRadarMapper.targetRadius(148, 0, 0));
        assertEquals(18, ContractRadarMapper.targetRadius(148, 1_000, 180));
    }

    @Test
    void mapsMinecraftYawToMapCardinalDirections() {
        assertDirection(180.0F, 0.0D, -1.0D);
        assertDirection(-90.0F, 1.0D, 0.0D);
        assertDirection(0.0F, 0.0D, 1.0D);
        assertDirection(90.0F, -1.0D, 0.0D);
    }

    private static void assertDirection(float yaw, double expectedX, double expectedY) {
        assertEquals(expectedX, ContractRadarMapper.directionX(yaw), EPSILON);
        assertEquals(expectedY, ContractRadarMapper.directionY(yaw), EPSILON);
    }
}
