package com.makar.tacticaltablet.airdrop.loot;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

public final class AirdropLootGenerator {

    private AirdropLootGenerator() {
    }

    public static void fillChest(ServerLevel level, BlockPos chestPos) {
        if (level == null || chestPos == null) return;

        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (!(blockEntity instanceof Container container)) {
            TacticalTabletMod.LOGGER.warn("AirDrop loot skipped: block at {} is not a container.", chestPos);
            return;
        }

        container.clearContent();

        AirdropLootLoader.AirdropLootConfig config = AirdropLootLoader.getConfig();
        if (config.sets().isEmpty() || config.totalSetWeight() <= 0) {
            TacticalTabletMod.LOGGER.warn("AirDrop loot skipped: no valid loot sets.");
            return;
        }

        AirdropLootSet set = chooseSet(level, config);
        if (set == null || set.items == null || set.items.isEmpty()) {
            TacticalTabletMod.LOGGER.warn("AirDrop loot skipped: selected loot set is empty.");
            return;
        }

        int placedEntries = 0;

        for (AirdropLootEntry entry : set.items) {
            if (isContainerFull(container)) {
                TacticalTabletMod.LOGGER.warn(
                        "AirDrop loot set '{}' has more entries than free chest slots. Placed {} of {} entries.",
                        set.name,
                        placedEntries,
                        set.items.size()
                );
                break;
            }

            ItemStack stack = createStack(level, entry);
            if (stack.isEmpty()) {
                continue;
            }

            int slot = findPreferredFreeSlot(container, entry.slot);
            if (slot < 0) {
                break;
            }

            container.setItem(slot, stack);
            placedEntries++;
        }

        container.setChanged();
        TacticalTabletMod.LOGGER.info(
                "Filled AirDrop chest with loot set '{}' ({} of {} entries).",
                set.name,
                placedEntries,
                set.items.size()
        );
    }

    public static int reloadLoot() {
        return AirdropLootLoader.reload();
    }

    private static AirdropLootSet chooseSet(ServerLevel level, AirdropLootLoader.AirdropLootConfig config) {
        int roll = level.random.nextInt(config.totalSetWeight()) + 1;
        int cursor = 0;

        for (AirdropLootSet set : config.sets()) {
            cursor += set.weight;
            if (roll <= cursor) {
                return set;
            }
        }

        return config.sets().get(config.sets().size() - 1);
    }

    private static ItemStack createStack(ServerLevel level, AirdropLootEntry entry) {
        ResourceLocation id = new ResourceLocation(entry.item);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, entry.count);

        if (entry.nbt != null && !entry.nbt.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(entry.nbt);
                stack.setTag(tag);
            } catch (Exception exception) {
                TacticalTabletMod.LOGGER.warn("Failed to parse AirDrop loot NBT for {}: {}", entry.item, entry.nbt, exception);
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }

    private static int findPreferredFreeSlot(Container container, Integer preferredSlot) {
        if (preferredSlot != null
                && preferredSlot >= 0
                && preferredSlot < container.getContainerSize()
                && container.getItem(preferredSlot).isEmpty()) {
            return preferredSlot;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static boolean isContainerFull(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
