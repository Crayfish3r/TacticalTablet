package com.makar.tacticaltablet.integration.curios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Optional, server-side access to the Curios inventory. */
public final class CuriosInventoryBridge {
    private CuriosInventoryBridge() {
    }

    public static void clear(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded("curios")) {
            return;
        }

        CuriosLoaded.clear(player);
    }

    /** Loaded only after the mod-presence guard, keeping Curios types out of the common bridge API. */
    private static final class CuriosLoaded {
        private CuriosLoaded() {
        }

        private static void clear(ServerPlayer player) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                for (var entry : handler.getCurios().entrySet()) {
                    clearFunctionalSlots(player, entry.getKey(), entry.getValue());
                    clearCosmeticSlots(entry.getValue());
                }
            });
        }

        private static void clearFunctionalSlots(
                ServerPlayer player,
                String identifier,
                top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler stacksHandler
        ) {
            var stacks = stacksHandler.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                var stack = stacks.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                boolean renders = slot < stacksHandler.getRenders().size()
                        && Boolean.TRUE.equals(stacksHandler.getRenders().get(slot));
                var context = new top.theillusivec4.curios.api.SlotContext(
                        identifier, player, slot, false, renders);

                // Curios normally removes these during its next living tick. Remove them now as well
                // so lifecycle code can immediately use the post-unequip maxHealth value.
                player.getAttributes().removeAttributeModifiers(
                        top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(
                                context,
                                top.theillusivec4.curios.api.CuriosApi.getSlotUuid(context),
                                stack
                        )
                );

                stacks.extractItem(slot, Integer.MAX_VALUE, false);
                if (!stacks.getStackInSlot(slot).isEmpty()) {
                    // Lifecycle reset is authoritative even for a Curio that vetoes ordinary unequip.
                    stacks.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }

        private static void clearCosmeticSlots(
                top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler stacksHandler
        ) {
            var cosmetics = stacksHandler.getCosmeticStacks();
            for (int slot = 0; slot < cosmetics.getSlots(); slot++) {
                if (!cosmetics.getStackInSlot(slot).isEmpty()) {
                    cosmetics.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
    }
}
