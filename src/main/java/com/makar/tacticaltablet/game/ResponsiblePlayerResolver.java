package com.makar.tacticaltablet.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;

public final class ResponsiblePlayerResolver {
    private ResponsiblePlayerResolver() {
    }

    public static ServerPlayer resolve(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof ServerPlayer player) {
            return player;
        }

        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }

        ServerPlayer controlledSource = resolveOwnerOrController(sourceEntity);
        if (controlledSource != null) {
            return controlledSource;
        }
        return resolveOwnerOrController(directEntity);
    }

    private static ServerPlayer resolveOwnerOrController(Entity entity) {
        if (entity instanceof OwnableEntity ownable
                && ownable.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (entity != null && entity.getControllingPassenger() instanceof ServerPlayer controller) {
            return controller;
        }
        return null;
    }
}
