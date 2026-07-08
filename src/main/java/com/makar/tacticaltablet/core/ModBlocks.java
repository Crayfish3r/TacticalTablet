package com.makar.tacticaltablet.core;

import com.makar.tacticaltablet.airdrop.AirdropCrateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TacticalTabletMod.MODID);

    public static final RegistryObject<Block> AIRDROP_CRATE = BLOCKS.register(
            "airdrop_crate",
            () -> new AirdropCrateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    private ModBlocks() {
    }
}
