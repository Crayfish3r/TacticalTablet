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

    public static final RegistryObject<Block> CASINO_MACHINE = BLOCKS.register("casino_machine",
            () -> new com.makar.tacticaltablet.casino.CasinoMachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(3.5F, 1200.0F).sound(SoundType.METAL)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion()));

    private ModBlocks() {
    }
}
