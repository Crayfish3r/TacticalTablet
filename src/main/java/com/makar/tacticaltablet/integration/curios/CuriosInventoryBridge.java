package com.makar.tacticaltablet.integration.curios;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                List<FunctionalSlot> functionalSlots = new ArrayList<>();
                List<CosmeticSlot> cosmeticSlots = new ArrayList<>();

                // Snapshot occupied slot identities before SlotAttribute removal can resize a handler.
                for (var entry : new ArrayList<>(handler.getCurios().entrySet())) {
                    var stacksHandler = entry.getValue();
                    var stacks = stacksHandler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (!stacks.getStackInSlot(slot).isEmpty()) {
                            boolean renders = slot < stacksHandler.getRenders().size()
                                    && Boolean.TRUE.equals(stacksHandler.getRenders().get(slot));
                            functionalSlots.add(new FunctionalSlot(entry.getKey(), stacksHandler, slot, renders));
                        }
                    }

                    var cosmetics = stacksHandler.getCosmeticStacks();
                    for (int slot = 0; slot < cosmetics.getSlots(); slot++) {
                        if (!cosmetics.getStackInSlot(slot).isEmpty()) {
                            cosmeticSlots.add(new CosmeticSlot(stacksHandler, slot));
                        }
                    }
                }

                for (FunctionalSlot slot : functionalSlots) {
                    clearFunctionalSlot(player, handler, slot);
                }
                for (CosmeticSlot slot : cosmeticSlots) {
                    clearCosmeticSlot(slot);
                }
            });
        }

        private static void clearFunctionalSlot(
                ServerPlayer player,
                top.theillusivec4.curios.api.type.capability.ICuriosItemHandler curiosHandler,
                FunctionalSlot occupiedSlot
        ) {
            var stacks = occupiedSlot.stacksHandler().getStacks();
            int slot = occupiedSlot.slot();
            if (slot >= stacks.getSlots()) {
                // A preceding SlotAttribute update already let Curios process this disappearing slot.
                return;
            }

            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return;
            }

            var context = new top.theillusivec4.curios.api.SlotContext(
                    occupiedSlot.identifier(), player, slot, false, occupiedSlot.renders());

            try {
                removeModifiers(player, curiosHandler, context, stack);
            } catch (RuntimeException exception) {
                TacticalTabletMod.LOGGER.error(
                        "Failed to remove Curios modifiers for {} slot {} from {}",
                        occupiedSlot.identifier(), slot, player.getGameProfile().getName(), exception);
            }

            try {
                // Raw replacement does not invoke this callback. Invoke it once before clearing,
                // matching Curios' forced-loss lifecycle while intentionally bypassing unequip vetoes.
                top.theillusivec4.curios.api.CuriosApi.getCurio(stack)
                        .ifPresent(curio -> curio.onUnequip(context, ItemStack.EMPTY));
            } catch (RuntimeException exception) {
                TacticalTabletMod.LOGGER.error(
                        "Failed to run Curios onUnequip for {} slot {} from {}",
                        occupiedSlot.identifier(), slot, player.getGameProfile().getName(), exception);
            } finally {
                if (slot < stacks.getSlots()) {
                    stacks.setStackInSlot(slot, ItemStack.EMPTY);
                    // Curios compares this cache on its living tick. Normalizing it prevents a
                    // second onUnequip after the authoritative callback above.
                    stacks.setPreviousStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        }

        private static void removeModifiers(
                ServerPlayer player,
                top.theillusivec4.curios.api.type.capability.ICuriosItemHandler curiosHandler,
                top.theillusivec4.curios.api.SlotContext context,
                ItemStack stack
        ) {
            Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create(
                    top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(
                            context,
                            top.theillusivec4.curios.api.CuriosApi.getSlotUuid(context),
                            stack
                    )
            );
            Multimap<String, AttributeModifier> slotModifiers = HashMultimap.create();
            Set<Attribute> slotAttributes = new HashSet<>();

            for (Attribute attribute : modifiers.keySet()) {
                if (attribute instanceof top.theillusivec4.curios.api.SlotAttribute slotAttribute) {
                    slotModifiers.putAll(slotAttribute.getIdentifier(), modifiers.get(attribute));
                    slotAttributes.add(attribute);
                }
            }
            for (Attribute slotAttribute : slotAttributes) {
                modifiers.removeAll(slotAttribute);
            }

            // This is synchronous, so MAX_HEALTH and other ordinary attributes are correct when
            // PlayerLifecycleSanitizer continues immediately after clear().
            player.getAttributes().removeAttributeModifiers(modifiers);
            curiosHandler.removeSlotModifiers(slotModifiers);
        }

        private static void clearCosmeticSlot(CosmeticSlot occupiedSlot) {
            var cosmetics = occupiedSlot.stacksHandler().getCosmeticStacks();
            int slot = occupiedSlot.slot();
            if (slot < cosmetics.getSlots() && !cosmetics.getStackInSlot(slot).isEmpty()) {
                cosmetics.setStackInSlot(slot, ItemStack.EMPTY);
                cosmetics.setPreviousStackInSlot(slot, ItemStack.EMPTY);
            }
        }

        private record FunctionalSlot(
                String identifier,
                top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler stacksHandler,
                int slot,
                boolean renders
        ) {
        }

        private record CosmeticSlot(
                top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler stacksHandler,
                int slot
        ) {
        }
    }
}
