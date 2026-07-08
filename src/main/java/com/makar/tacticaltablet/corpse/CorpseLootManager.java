package com.makar.tacticaltablet.corpse;

import com.makar.tacticaltablet.core.ModEntities;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CorpseLootManager {

    private static final double LOOT_FRACTION = 0.50D;

    private CorpseLootManager() {
    }

    public static void createCorpse(ServerPlayer victim) {
        if (victim == null || !(victim.level() instanceof ServerLevel level)) return;

        try {
            List<ItemStack> loot = selectLoot(victim);
            if (loot.isEmpty()) {
                return;
            }

            CorpseEntity corpse = ModEntities.PLAYER_CORPSE.get().create(level);
            if (corpse == null) {
                return;
            }

            SkinData skin = skinData(victim);
            corpse.initialize(
                    victim.getUUID(),
                    victim.getGameProfile().getName(),
                    skin.value(),
                    skin.signature(),
                    loot
            );
            corpse.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0F);

            if (!level.addFreshEntity(corpse)) {
                return;
            }

            victim.getInventory().clearContent();
            victim.getInventory().setChanged();
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to create corpse for {}", victim.getGameProfile().getName(), exception);
        }
    }

    private static List<ItemStack> selectLoot(ServerPlayer victim) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < victim.getInventory().getContainerSize(); slot++) {
            ItemStack stack = victim.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }

        if (stacks.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(stacks);
        int lootCount = Math.max(1, (int) Math.ceil(stacks.size() * LOOT_FRACTION));
        lootCount = Math.min(lootCount, CorpseEntity.CONTAINER_SIZE);
        return new ArrayList<>(stacks.subList(0, lootCount));
    }

    private static SkinData skinData(ServerPlayer player) {
        for (Property property : player.getGameProfile().getProperties().get("textures")) {
            return new SkinData(property.getValue(), property.getSignature());
        }
        return new SkinData("", "");
    }

    private record SkinData(String value, String signature) {
    }
}
