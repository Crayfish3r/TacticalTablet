package com.makar.tacticaltablet.airdrop;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class AirdropCompassHelper {

    private static final String TAG_AIRDROP_COMPASS = "TacticalTabletAirdropCompass";
    private static final String TAG_AIRDROP_ID = "AirdropId";

    private AirdropCompassHelper() {
    }

    public static void giveOrUpdate(net.minecraft.server.level.ServerPlayer player, AirdropData data) {
        if (player == null || data == null || data.compassTargetPos == null) return;

        ItemStack existing = findAirdropCompass(player.getInventory());
        if (!existing.isEmpty()) {
            configureCompass(existing, data.id, data.compassTargetPos, data.dimension);
            return;
        }

        ItemStack compass = new ItemStack(Items.COMPASS);
        configureCompass(compass, data.id, data.compassTargetPos, data.dimension);

        if (!player.getInventory().add(compass)) {
            player.drop(compass, false);
        }
    }

    public static void removeAllAirdropCompasses(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;

        Inventory inventory = player.getInventory();
        removeFromList(inventory.items);
        removeFromList(inventory.offhand);
    }

    public static boolean isAirdropCompass(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.COMPASS)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_AIRDROP_COMPASS);
    }

    private static ItemStack findAirdropCompass(Inventory inventory) {
        for (ItemStack stack : inventory.items) {
            if (isAirdropCompass(stack)) {
                return stack;
            }
        }

        for (ItemStack stack : inventory.offhand) {
            if (isAirdropCompass(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static void removeFromList(net.minecraft.core.NonNullList<ItemStack> items) {
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            if (isAirdropCompass(stack)) {
                items.set(index, ItemStack.EMPTY);
            }
        }
    }

    private static void configureCompass(
            ItemStack stack,
            UUID airdropId,
            BlockPos target,
            ResourceKey<Level> dimension
    ) {
        stack.setHoverName(Component.literal("§cКомпас сброса"));

        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_AIRDROP_COMPASS, true);
        tag.putString(TAG_AIRDROP_ID, airdropId.toString());
        tag.putBoolean("LodestoneTracked", false);
        tag.putString("LodestoneDimension", dimension.location().toString());

        CompoundTag pos = new CompoundTag();
        pos.putInt("X", target.getX());
        pos.putInt("Y", target.getY());
        pos.putInt("Z", target.getZ());
        tag.put("LodestonePos", pos);

        CompoundTag display = tag.getCompound("display");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§7Указывает в примерную зону сброса"))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§7Точка может быть неточной"))));
        display.put("Lore", lore);
        tag.put("display", display);
    }
}
