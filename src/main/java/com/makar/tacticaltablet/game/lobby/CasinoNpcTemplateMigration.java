package com.makar.tacticaltablet.game.lobby;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Filters obsolete casino entities from a template copy, never from a live world. */
public final class CasinoNpcTemplateMigration {
    private static final String LEGACY_CASINO_NPC_NAME = "Однорукий бандит";

    private CasinoNpcTemplateMigration() { }

    public static CompoundTag withoutLegacyCasinoNpcs(CompoundTag source) {
        CompoundTag result = source.copy();
        ListTag entities = result.getList("entities", Tag.TAG_COMPOUND);
        entities.removeIf(raw -> isLegacyCasinoNpc(((CompoundTag) raw).getCompound("nbt")));
        return result;
    }

    private static boolean isLegacyCasinoNpc(CompoundTag entity) {
        if (!"minecraft:villager".equals(entity.getString("id"))
                || !entity.contains("CustomName", Tag.TAG_STRING)) return false;
        Component name = Component.Serializer.fromJson(entity.getString("CustomName"));
        return name != null && LEGACY_CASINO_NPC_NAME.equals(name.getString());
    }
}
