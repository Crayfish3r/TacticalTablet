package com.makar.tacticaltablet.camouflage;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchAdmissionManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.integration.curios.CuriosInventoryBridge;
import com.makar.tacticaltablet.progression.CosmeticCatalog;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Server-thread delivery and marker-only cleanup of temporary camouflage equipment. */
public final class CamouflageLoadoutService {
    private CamouflageLoadoutService() { }

    public static void deploy(ServerPlayer player) {
        if (player == null || !player.server.isSameThread()) return;
        Optional<String> activePreset = ZoneManager.getActiveCamouflagePreset();
        CamouflageCatalog catalog = CamouflageCatalogLoader.catalog();
        boolean participating = GameStateManager.isRunning(player.server)
                && MatchAdmissionManager.isCurrentMatchParticipant(player.getUUID())
                && !player.isSpectator()
                && PlayerTabletState.isKitUsed(player)
                && LivesManager.canContinueMatch(player);
        boolean owns = PlayerProgressManager.ownsCosmetic(player, CosmeticCatalog.GHILLIE_SUIT_ID);
        Optional<CamouflagePreset> preset = activePreset.flatMap(catalog::find);
        if (!CamouflageDeploymentEligibility.canDeliver(
                participating, owns, activePreset.isPresent(), preset.isPresent())) return;

        clearTemporary(player);
        for (CamouflagePiece piece : preset.orElseThrow().pieces()) {
            givePiece(player, preset.get().id(), piece);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void clearTemporary(ServerPlayer player) {
        if (player == null || !player.server.isSameThread()) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (CamouflageStackMarker.isAutoCamouflage(stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        CuriosInventoryBridge.removeMatching(player, CamouflageStackMarker::isAutoCamouflage);
        player.getInventory().setChanged();
    }

    private static void givePiece(ServerPlayer player, String presetId, CamouflagePiece piece) {
        Item item = ForgeRegistries.ITEMS.getValue(piece.itemId());
        if (item == null || item == Items.AIR) {
            TacticalTabletMod.LOGGER.warn("Skipping camouflage item missing from registry: {}", piece.itemId());
            return;
        }
        ItemStack stack = new ItemStack(item);
        stack.setTag(piece.itemTag());
        CamouflageStackMarker.markAutoCamouflage(
                stack, player.getUUID(), presetId, piece.target().markerPiece());

        if (piece.target().kind() == CamouflageTarget.Kind.CURIOS) {
            ItemStack remainder = CuriosInventoryBridge.equipInNamedSlot(
                    player, piece.target().slot(), stack,
                    piece.policy() == CamouflagePolicy.REPLACE_AUTO_ONLY,
                    CamouflageStackMarker::isAutoCamouflage);
            if (!remainder.isEmpty()) addToInventory(player, remainder, piece);
            return;
        }

        EquipmentSlot slot = armorSlot(piece.target().slot());
        ItemStack existing = player.getItemBySlot(slot);
        CamouflagePlacementPolicy.Decision decision = CamouflagePlacementPolicy.decide(
                piece.policy(), !existing.isEmpty(), CamouflageStackMarker.isAutoCamouflage(existing), true);
        if (decision == CamouflagePlacementPolicy.Decision.EQUIP
                || decision == CamouflagePlacementPolicy.Decision.REPLACE) {
            player.setItemSlot(slot, stack);
        } else {
            addToInventory(player, stack, piece);
        }
    }

    private static void addToInventory(ServerPlayer player, ItemStack stack, CamouflagePiece piece) {
        if (!player.getInventory().add(stack)) {
            TacticalTabletMod.LOGGER.debug("Skipped camouflage piece {} for {} because inventory is full",
                    piece.itemId(), player.getGameProfile().getName());
        }
    }

    private static EquipmentSlot armorSlot(String slot) {
        return switch (slot) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> throw new IllegalArgumentException("Unknown armor slot " + slot);
        };
    }
}
