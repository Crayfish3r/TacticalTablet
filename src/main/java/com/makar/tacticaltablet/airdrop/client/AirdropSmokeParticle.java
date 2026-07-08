package com.makar.tacticaltablet.airdrop.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

public final class AirdropSmokeParticle extends TextureSheetParticle {
    private static final float RED = 0.82F;
    private static final float GREEN = 0.035F;
    private static final float BLUE = 0.025F;

    private final SpriteSet sprites;
    private final float initialSize;
    private final float spinSpeed;

    private AirdropSmokeParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            SpriteSet sprites
    ) {
        super(level, x, y, z, velocityX, velocityY, velocityZ);
        this.sprites = sprites;
        this.hasPhysics = false;
        this.friction = 0.96F;
        this.lifetime = 150 + random.nextInt(51);
        this.initialSize = 0.475F + random.nextFloat() * 0.225F;
        this.quadSize = initialSize;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * ((float) Math.PI * 2.0F);
        this.oRoll = roll;
        this.spinSpeed = (random.nextFloat() - 0.5F) * 0.012F;
        this.setColor(RED, GREEN, BLUE);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;

        if (age++ >= lifetime) {
            remove();
            return;
        }

        move(xd, yd, zd);
        xd *= 0.96D;
        yd *= 0.997D;
        zd *= 0.96D;
        roll += spinSpeed;

        float progress = age / (float) lifetime;
        float fadeIn = Math.min(1.0F, age / 10.0F);
        float fadeOut = Math.min(1.0F, (lifetime - age) / 28.0F);
        alpha = 0.82F * fadeIn * fadeOut;
        quadSize = initialSize * (0.85F + progress * 2.35F);
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double velocityX,
                double velocityY,
                double velocityZ
        ) {
            return new AirdropSmokeParticle(
                    level,
                    x,
                    y,
                    z,
                    velocityX,
                    velocityY,
                    velocityZ,
                    sprites
            );
        }
    }
}
