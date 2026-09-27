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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AirdropLootGenerator {

    private static final int MAX_NBT_LENGTH = 2048;

    private AirdropLootGenerator() {
    }

    public static void fillChest(
            ServerLevel level,
            BlockPos chestPos
    ) {
        if (level == null || chestPos == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(chestPos);

        if (!(blockEntity instanceof Container container)) {
            TacticalTabletMod.LOGGER.warn(
                    "AirDrop loot skipped: block at {} is not a container.",
                    chestPos
            );

            return;
        }

        container.clearContent();

        AirdropLootLoader.AirdropLootConfig config =
                AirdropLootLoader.getConfig();

        if (config.sets().isEmpty()
                || config.totalSetWeight() <= 0) {

            TacticalTabletMod.LOGGER.warn(
                    "AirDrop loot skipped: no valid loot sets."
            );

            return;
        }

        AirdropLootSet set = chooseSet(
                level,
                config
        );

        if (set == null
                || set.items == null
                || set.items.isEmpty()) {

            TacticalTabletMod.LOGGER.warn(
                    "AirDrop loot skipped: selected loot set is empty."
            );

            return;
        }

        int fullyPlacedEntries = 0;
        int placedStacks = 0;
        int placedUnits = 0;

        List<AirdropLootEntry> entries = set.items;

        for (int entryIndex = 0;
             entryIndex < entries.size();
             entryIndex++) {

            if (isContainerFull(container)) {
                TacticalTabletMod.LOGGER.warn(
                        "AirDrop chest became full while processing loot set '{}'. "
                                + "Fully placed {} of {} entries.",
                        set.name,
                        fullyPlacedEntries,
                        entries.size()
                );

                break;
            }

            AirdropLootEntry entry = entries.get(entryIndex);

            Set<Integer> futureReservedSlots =
                    collectFutureReservedSlots(
                            entries,
                            entryIndex + 1,
                            container.getContainerSize()
                    );

            ItemStack template = createTemplate(entry);

            if (template.isEmpty()) {
                continue;
            }

            int requestedCount = entry.count;

            if (requestedCount <= 0) {
                TacticalTabletMod.LOGGER.warn(
                        "Skipped AirDrop loot entry {} because requested count was {}.",
                        entry.item,
                        requestedCount
                );

                continue;
            }

            int maxStackSize = Math.max(
                    1,
                    template.getMaxStackSize()
            );

            int remaining = requestedCount;
            int entryPlacedUnits = 0;
            int entryPlacedStacks = 0;

            boolean firstStack = true;

            while (remaining > 0) {
                int slot;

                if (firstStack) {
                    slot = findPreferredFreeSlot(
                            container,
                            entry.slot,
                            futureReservedSlots
                    );
                } else {
                    slot = findOverflowFreeSlot(
                            container,
                            futureReservedSlots
                    );
                }

                if (slot < 0) {
                    break;
                }

                int stackCount = Math.min(
                        remaining,
                        maxStackSize
                );

                ItemStack stack = template.copy();
                stack.setCount(stackCount);

                container.setItem(
                        slot,
                        stack
                );

                remaining -= stackCount;
                entryPlacedUnits += stackCount;
                entryPlacedStacks++;

                placedUnits += stackCount;
                placedStacks++;

                firstStack = false;
            }

            if (remaining == 0) {
                fullyPlacedEntries++;
            } else {
                TacticalTabletMod.LOGGER.warn(
                        "AirDrop loot entry '{}' in set '{}' could not be fully placed. "
                                + "Requested {}, placed {}, missing {}. "
                                + "The container does not have enough free slots.",
                        entry.item,
                        set.name,
                        requestedCount,
                        entryPlacedUnits,
                        remaining
                );
            }

            if (entryPlacedStacks > 1) {
                TacticalTabletMod.LOGGER.debug(
                        "Split AirDrop loot entry '{}' x{} into {} stacks.",
                        entry.item,
                        entryPlacedUnits,
                        entryPlacedStacks
                );
            }
        }

        container.setChanged();

        TacticalTabletMod.LOGGER.info(
                "Filled AirDrop chest with loot set '{}': "
                        + "{} of {} entries fully placed, "
                        + "{} physical stacks, {} total item units.",
                set.name,
                fullyPlacedEntries,
                entries.size(),
                placedStacks,
                placedUnits
        );
    }

    public static int reloadLoot() {
        return AirdropLootLoader.reload();
    }

    private static AirdropLootSet chooseSet(
            ServerLevel level,
            AirdropLootLoader.AirdropLootConfig config
    ) {
        int roll = level.random.nextInt(
                config.totalSetWeight()
        ) + 1;

        int cursor = 0;

        for (AirdropLootSet set : config.sets()) {
            cursor += set.weight;

            if (roll <= cursor) {
                return set;
            }
        }

        return config.sets().get(
                config.sets().size() - 1
        );
    }

    private static ItemStack createTemplate(
            AirdropLootEntry entry
    ) {
        ResourceLocation id;

        try {
            id = new ResourceLocation(entry.item);
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn(
                    "Failed to create AirDrop loot stack because item id '{}' is invalid.",
                    entry.item,
                    exception
            );

            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);

        if (item == null || item == Items.AIR) {
            TacticalTabletMod.LOGGER.warn(
                    "Failed to create AirDrop loot stack because item '{}' is not registered.",
                    entry.item
            );

            return ItemStack.EMPTY;
        }

        ItemStack template = new ItemStack(
                item,
                1
        );

        if (entry.nbt == null || entry.nbt.isBlank()) {
            return template;
        }

        if (entry.nbt.length() > MAX_NBT_LENGTH) {
            TacticalTabletMod.LOGGER.warn(
                    "Skipped AirDrop loot NBT for {}: NBT is too long.",
                    entry.item
            );

            return ItemStack.EMPTY;
        }

        try {
            CompoundTag tag = TagParser.parseTag(
                    entry.nbt
            );

            template.setTag(tag);
        } catch (Exception exception) {
            TacticalTabletMod.LOGGER.warn(
                    "Failed to parse AirDrop loot NBT for {}.",
                    entry.item,
                    exception
            );

            return ItemStack.EMPTY;
        }

        return template;
    }

    private static Set<Integer> collectFutureReservedSlots(
            List<AirdropLootEntry> entries,
            int startIndex,
            int containerSize
    ) {
        Set<Integer> reservedSlots = new HashSet<>();

        for (int index = startIndex;
             index < entries.size();
             index++) {

            Integer slot = entries.get(index).slot;

            if (slot == null) {
                continue;
            }

            if (slot < 0 || slot >= containerSize) {
                continue;
            }

            reservedSlots.add(slot);
        }

        return reservedSlots;
    }

    private static int findPreferredFreeSlot(
            Container container,
            Integer preferredSlot,
            Set<Integer> reservedSlots
    ) {
        if (isFreeSlot(
                container,
                preferredSlot
        )) {
            return preferredSlot;
        }

        int unreservedSlot = findFreeSlot(
                container,
                reservedSlots
        );

        if (unreservedSlot >= 0) {
            return unreservedSlot;
        }

        return findAnyFreeSlot(container);
    }

    private static int findOverflowFreeSlot(
            Container container,
            Set<Integer> reservedSlots
    ) {
        int unreservedSlot = findFreeSlot(
                container,
                reservedSlots
        );

        if (unreservedSlot >= 0) {
            return unreservedSlot;
        }

        return findAnyFreeSlot(container);
    }

    private static int findFreeSlot(
            Container container,
            Set<Integer> excludedSlots
    ) {
        for (int slot = 0;
             slot < container.getContainerSize();
             slot++) {

            if (excludedSlots.contains(slot)) {
                continue;
            }

            if (container.getItem(slot).isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static int findAnyFreeSlot(
            Container container
    ) {
        for (int slot = 0;
             slot < container.getContainerSize();
             slot++) {

            if (container.getItem(slot).isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static boolean isFreeSlot(
            Container container,
            Integer slot
    ) {
        return slot != null
                && slot >= 0
                && slot < container.getContainerSize()
                && container.getItem(slot).isEmpty();
    }

    private static boolean isContainerFull(
            Container container
    ) {
        return findAnyFreeSlot(container) < 0;
    }
}