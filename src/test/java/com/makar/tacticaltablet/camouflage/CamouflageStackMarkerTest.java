package com.makar.tacticaltablet.camouflage;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CamouflageStackMarkerTest {
    @Test
    void onlyExplicitlyMarkedStacksAreTemporary() {
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompoundTag ordinaryFracturePointEquivalent = new CompoundTag();
        CompoundTag temporary = new CompoundTag();

        CamouflageStackMarker.markAutoCamouflage(temporary, owner, "spruce", "armor:head");

        assertFalse(CamouflageStackMarker.isAutoCamouflage(ordinaryFracturePointEquivalent));
        assertTrue(CamouflageStackMarker.isAutoCamouflage(temporary));
        CompoundTag marker = temporary.getCompound("TacticalTablet");
        assertEquals(owner.toString(), marker.getString("Owner"));
        assertEquals("spruce", marker.getString("Preset"));
        assertEquals("armor:head", marker.getString("Piece"));
    }

    @Test
    void incompleteLookalikeMarkerIsRejected() {
        CompoundTag stack = new CompoundTag();
        CompoundTag marker = new CompoundTag();
        marker.putBoolean("AutoCamouflage", true);
        stack.put("TacticalTablet", marker);
        assertFalse(CamouflageStackMarker.isAutoCamouflage(stack));
    }
}
