package com.makar.tacticaltablet.core;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TacticalTabletMod.MODID);

    public static final RegistryObject<SimpleParticleType> AIRDROP_SMOKE = PARTICLES.register(
            "airdrop_smoke",
            () -> new SimpleParticleType(true)
    );

    private ModParticles() {
    }
}
