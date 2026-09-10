package com.makar.tacticaltablet.casino;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

public final class CasinoMachineItem extends BlockItem {
    public CasinoMachineItem(Block block, Properties properties) { super(block, properties); }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.makar.tacticaltablet.client.casino.CasinoMachineItemRenderer();
                return renderer;
            }
        });
    }
}
