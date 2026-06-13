package com.makar.tacticaltablet.map;

import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class WorldCleanupManager {

    private static final double MAX_CLEANUP_RADIUS = 2048.0D;

    public static void clearDroppedItems(MinecraftServer server) {
        if (server == null) return;

        clearDroppedItemsInLevel(server.getLevel(Level.OVERWORLD));
        clearDroppedItemsInLevel(GameStateManager.getLobbyLevel(server));
    }

    private static void clearDroppedItemsInLevel(ServerLevel level) {
        if (level == null) return;

        WorldBorder border = level.getWorldBorder();
        double radius = Math.min(MAX_CLEANUP_RADIUS, Math.max(16.0D, border.getSize() / 2.0D));
        AABB area = new AABB(
                border.getCenterX() - radius,
                level.getMinBuildHeight(),
                border.getCenterZ() - radius,
                border.getCenterX() + radius,
                level.getMaxBuildHeight(),
                border.getCenterZ() + radius
        );

        List<ItemEntity> items = level.getEntities(EntityType.ITEM, area, Entity::isAlive);
        for (ItemEntity item : items) {
            item.discard();
        }
    }
}
