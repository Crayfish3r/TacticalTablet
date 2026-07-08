package com.makar.tacticaltablet.airdrop;

import com.makar.tacticaltablet.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AirdropCrateBlockEntity extends RandomizableContainerBlockEntity {
    private static final int SLOT_COUNT = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public AirdropCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIRDROP_CRATE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.tacticaltablet.airdrop_crate");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }
}
