package com.makar.tacticaltablet.game.lobby;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoNpcTemplateMigrationTest {
    @Test
    void sanitizingTemplateEntityRemovesUuidWithoutMutatingResourceNbt() {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:villager");
        source.putIntArray("UUID", new int[]{1, 2, 3, 4});
        source.putLong("UUIDMost", 5L);
        source.putLong("UUIDLeast", 6L);

        CompoundTag sanitized = CasinoNpcTemplateMigration.sanitizeEntityTag(source);

        assertFalse(sanitized.contains("UUID"));
        assertFalse(sanitized.contains("UUIDMost"));
        assertFalse(sanitized.contains("UUIDLeast"));
        assertEquals("minecraft:villager", sanitized.getString("id"));

        assertTrue(source.contains("UUID"));
        assertArrayEquals(new int[]{1, 2, 3, 4}, source.getIntArray("UUID"));
        assertEquals(5L, source.getLong("UUIDMost"));
        assertEquals(6L, source.getLong("UUIDLeast"));
    }
}