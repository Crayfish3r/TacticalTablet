package com.makar.tacticaltablet.core;

import com.makar.tacticaltablet.airdrop.AirdropCrateBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TacticalTabletMod.MODID);

    public static final RegistryObject<BlockEntityType<AirdropCrateBlockEntity>> AIRDROP_CRATE =
            BLOCK_ENTITIES.register(
                    "airdrop_crate",
                    () -> BlockEntityType.Builder.of(
                            AirdropCrateBlockEntity::new,
                            ModBlocks.AIRDROP_CRATE.get()
                    ).build(null)
            );

    public static final RegistryObject<BlockEntityType<com.makar.tacticaltablet.casino.CasinoMachineBlockEntity>> CASINO_MACHINE =
            BLOCK_ENTITIES.register("casino_machine", () -> BlockEntityType.Builder.of(
                    com.makar.tacticaltablet.casino.CasinoMachineBlockEntity::new, ModBlocks.CASINO_MACHINE.get()).build(null));

    private ModBlockEntities() {
    }
}
