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

    private ModBlockEntities() {
    }
}
