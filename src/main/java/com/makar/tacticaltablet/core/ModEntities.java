package com.makar.tacticaltablet.core;

import com.makar.tacticaltablet.corpse.CorpseEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TacticalTabletMod.MODID);

    public static final RegistryObject<EntityType<CorpseEntity>> PLAYER_CORPSE = ENTITIES.register(
            "player_corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.35F)
                    .clientTrackingRange(64)
                    .updateInterval(20)
                    .build("player_corpse")
    );

    private ModEntities() {
    }
}
