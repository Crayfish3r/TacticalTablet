package com.makar.tacticaltablet.inventory;

import com.makar.tacticaltablet.core.ModItems;
import com.makar.tacticaltablet.tablet.TabletAppearanceManager;

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class InventoryManager {

    public static void clearInventory(ServerPlayer player) {
        if (player == null) return;

        boolean changed = !player.getInventory().isEmpty()
                || !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                || !player.getItemInHand(InteractionHand.OFF_HAND).isEmpty();

        if (!changed) return;

        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        player.getInventory().setChanged();
        syncInventory(player);
    }

    public static void giveFreshTablet(ServerPlayer player) {
        if (player == null) return;

        boolean changed = removeTablets(player);
        player.getInventory().add(createTablet(player));
        changed = true;

        if (changed) {
            player.getInventory().setChanged();
            syncInventory(player);
        }
    }

    public static void giveTabletIfMissing(ServerPlayer player) {
        if (player == null) return;

        if (hasTablet(player)) {
            updateTabletModels(player);
            return;
        }

        player.getInventory().add(createTablet(player));
        player.getInventory().setChanged();
        syncInventory(player);
    }

    public static void clearTablets(ServerPlayer player) {
        if (player == null) return;

        if (removeTablets(player)) {
            player.getInventory().setChanged();
            syncInventory(player);
        }
    }

    public static boolean hasTablet(ServerPlayer player) {
        if (player == null) return false;

        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() == ModItems.TACTICAL_TABLET.get()) {
                return true;
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ModItems.TACTICAL_TABLET.get()) {
                return true;
            }
        }

        return false;
    }

    public static void updateTabletModels(ServerPlayer player) {
        if (player == null) return;

        boolean changed = false;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() == ModItems.TACTICAL_TABLET.get()) {
                String before = stack.getTag() == null ? "" : stack.getTag().toString();
                TabletAppearanceManager.apply(player, stack);
                String after = stack.getTag() == null ? "" : stack.getTag().toString();
                changed |= !before.equals(after);
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ModItems.TACTICAL_TABLET.get()) {
                String before = stack.getTag() == null ? "" : stack.getTag().toString();
                TabletAppearanceManager.apply(player, stack);
                String after = stack.getTag() == null ? "" : stack.getTag().toString();
                changed |= !before.equals(after);
            }
        }

        if (changed) {
            player.getInventory().setChanged();
            syncInventory(player);
        }
    }

    public static void syncInventory(ServerPlayer player) {
        if (player == null) return;

        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();

        player.connection.send(
                new ClientboundContainerSetContentPacket(
                        player.inventoryMenu.containerId,
                        player.inventoryMenu.incrementStateId(),
                        player.inventoryMenu.getItems(),
                        player.inventoryMenu.getCarried()
                )
        );
    }

    private static ItemStack createTablet(ServerPlayer player) {
        return TabletAppearanceManager.apply(player, new ItemStack(ModItems.TACTICAL_TABLET.get()));
    }

    private static boolean removeTablets(ServerPlayer player) {
        boolean changed = false;

        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() == ModItems.TACTICAL_TABLET.get()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
                changed = true;
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ModItems.TACTICAL_TABLET.get()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }

        return changed;
    }
}

