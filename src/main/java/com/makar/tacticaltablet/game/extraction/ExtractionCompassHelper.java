package com.makar.tacticaltablet.game.extraction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class ExtractionCompassHelper {
    private static final String TAG_EVENT_ITEM = "tactical_event_item";
    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_EVENT_ID = "event_id";
    private static final String TAG_TARGET_X = "extraction_target_x";
    private static final String TAG_TARGET_Y = "extraction_target_y";
    private static final String TAG_TARGET_Z = "extraction_target_z";
    private static final String TAG_TARGET_DIMENSION = "extraction_target_dimension";
    private static final String EVENT_TYPE = "extraction_point";
    private static final int EXTRACTION_COMPASS_MODEL_DATA = 93001;

    private ExtractionCompassHelper() {
    }

    public static void giveOrUpdate(ServerPlayer player, ExtractionPointData data, ResourceKey<Level> dimension) {
        if (player == null || data == null || data.center == null || data.eventId == null) return;

        ItemStack existing = findExtractionCompass(player.getInventory());
        if (!existing.isEmpty()) {
            configureCompass(existing, data.eventId, data.center, dimension);
            removeDuplicates(player, existing);
            return;
        }

        ItemStack compass = new ItemStack(Items.RECOVERY_COMPASS);
        configureCompass(compass, data.eventId, data.center, dimension);
        if (!player.getInventory().add(compass)) {
            player.drop(compass, false);
        }
    }

    public static void removeAllExtractionCompasses(ServerPlayer player) {
        if (player == null) return;
        Inventory inventory = player.getInventory();
        removeFromList(inventory.items);
        removeFromList(inventory.offhand);
    }

    public static boolean isExtractionCompass(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.RECOVERY_COMPASS)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.getBoolean(TAG_EVENT_ITEM)
                && EVENT_TYPE.equals(tag.getString(TAG_EVENT_TYPE));
    }

    private static ItemStack findExtractionCompass(Inventory inventory) {
        for (ItemStack stack : inventory.items) {
            if (isExtractionCompass(stack)) return stack;
        }
        for (ItemStack stack : inventory.offhand) {
            if (isExtractionCompass(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void removeDuplicates(ServerPlayer player, ItemStack kept) {
        boolean foundKept = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isExtractionCompass(stack)) continue;
            if (!foundKept && stack == kept) {
                foundKept = true;
                continue;
            }
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
    }

    private static void removeFromList(net.minecraft.core.NonNullList<ItemStack> items) {
        for (int index = 0; index < items.size(); index++) {
            if (isExtractionCompass(items.get(index))) {
                items.set(index, ItemStack.EMPTY);
            }
        }
    }

    private static void configureCompass(
            ItemStack stack,
            UUID eventId,
            BlockPos target,
            ResourceKey<Level> dimension
    ) {
        stack.setHoverName(Component.literal("бизнес-темка"));

        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_EVENT_ITEM, true);
        tag.putString(TAG_EVENT_TYPE, EVENT_TYPE);
        tag.putString(TAG_EVENT_ID, eventId.toString());
        tag.putInt(TAG_TARGET_X, target.getX());
        tag.putInt(TAG_TARGET_Y, target.getY());
        tag.putInt(TAG_TARGET_Z, target.getZ());
        tag.putString(TAG_TARGET_DIMENSION, dimension.location().toString());
        tag.putInt("CustomModelData", EXTRACTION_COMPASS_MODEL_DATA);
        tag.putBoolean("LodestoneTracked", false);
        tag.putString("LodestoneDimension", dimension.location().toString());

        CompoundTag pos = new CompoundTag();
        pos.putInt("X", target.getX());
        pos.putInt("Y", target.getY());
        pos.putInt("Z", target.getZ());
        tag.put("LodestonePos", pos);

        CompoundTag display = tag.getCompound("display");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§7Указывает на активную бизнес-точку"))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§7Исчезнет после завершения события"))));
        display.put("Lore", lore);
        tag.put("display", display);
    }
}
