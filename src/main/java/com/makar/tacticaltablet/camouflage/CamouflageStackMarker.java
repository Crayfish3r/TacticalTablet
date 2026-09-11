package com.makar.tacticaltablet.camouflage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class CamouflageStackMarker {
    private static final String NAMESPACE = "TacticalTablet";
    private static final String AUTO_CAMOUFLAGE = "AutoCamouflage";
    private static final String OWNER = "Owner";
    private static final String PRESET = "Preset";
    private static final String PIECE = "Piece";

    private CamouflageStackMarker() { }

    public static void markAutoCamouflage(ItemStack stack, UUID owner, String preset, String piece) {
        if (stack == null || stack.isEmpty() || owner == null || preset == null || preset.isBlank()
                || piece == null || piece.isBlank()) return;
        markAutoCamouflage(stack.getOrCreateTag(), owner, preset, piece);
    }

    static void markAutoCamouflage(CompoundTag root, UUID owner, String preset, String piece) {
        if (root == null || owner == null || preset == null || preset.isBlank()
                || piece == null || piece.isBlank()) return;
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(AUTO_CAMOUFLAGE, true);
        marker.putString(OWNER, owner.toString());
        marker.putString(PRESET, preset);
        marker.putString(PIECE, piece);
        root.put(NAMESPACE, marker);
    }

    public static boolean isAutoCamouflage(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return false;
        return isAutoCamouflage(stack.getTag());
    }

    static boolean isAutoCamouflage(CompoundTag root) {
        if (root == null || !root.contains(NAMESPACE, Tag.TAG_COMPOUND)) return false;
        CompoundTag marker = root.getCompound(NAMESPACE);
        if (!marker.contains(AUTO_CAMOUFLAGE, Tag.TAG_BYTE)
                || !marker.getBoolean(AUTO_CAMOUFLAGE)
                || !marker.contains(OWNER, Tag.TAG_STRING)
                || !marker.contains(PRESET, Tag.TAG_STRING)
                || !marker.contains(PIECE, Tag.TAG_STRING)
                || marker.getString(PRESET).isBlank()
                || marker.getString(PIECE).isBlank()) return false;
        try {
            UUID.fromString(marker.getString(OWNER));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean isAutoCamouflageOwnedBy(ItemStack stack, UUID owner) {
        if (!isAutoCamouflage(stack) || owner == null) return false;
        return owner.toString().equals(stack.getTag().getCompound(NAMESPACE).getString(OWNER));
    }

    public static Optional<String> getPreset(ItemStack stack) {
        return isAutoCamouflage(stack)
                ? Optional.of(stack.getTag().getCompound(NAMESPACE).getString(PRESET))
                : Optional.empty();
    }

    public static Optional<String> getPiece(ItemStack stack) {
        return isAutoCamouflage(stack)
                ? Optional.of(stack.getTag().getCompound(NAMESPACE).getString(PIECE))
                : Optional.empty();
    }
}
