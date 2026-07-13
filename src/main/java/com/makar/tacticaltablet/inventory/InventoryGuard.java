package com.makar.tacticaltablet.inventory;

import com.makar.tacticaltablet.airdrop.AirdropCompassHelper;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.core.ModItems;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.extraction.ExtractionCompassHelper;
import com.makar.tacticaltablet.game.extraction.ExtractionPointManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class InventoryGuard {

    private static int tickCounter = 0;

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        tickCounter++;

        if (tickCounter < 40) return;
        tickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            check(player);
        }
    }

    private static void check(ServerPlayer player) {
        if (ModerModeManager.isInModerMode(player)) return;

        Set<String> tags = player.getTags();

        boolean inLobby = GameStateManager.isInLobby(player) || tags.contains("in_lobby");
        boolean playing = tags.contains("war.playing");
        boolean eliminated = LivesManager.isEliminated(player);
        boolean gameRunning = GameStateManager.isRunning(player.server);
        boolean tabletLobbyStage = GameStateManager.isTabletAvailableInLobby(player.server);
        boolean kitUsed = PlayerTabletState.isKitUsed(player);
        boolean rtpUsed = PlayerTabletState.isRtpUsed(player);
        boolean clanWarSpectatorApplicant = isClanWarSpectatorApplicant(player, gameRunning);

        boolean relevant = inLobby || playing || eliminated || clanWarSpectatorApplicant;
        if (!relevant) return;

        if (clanWarSpectatorApplicant) {
            keepOnlyTabletAndSync(player);
            return;
        }

        if (eliminated) {
            if (!isInventoryEmpty(player)) {
                InventoryManager.clearInventory(player);
            }
            return;
        }

        if (inLobby && !gameRunning && tabletLobbyStage) {
            keepOnlyTabletCompassAndSync(player);
            return;
        }

        if (inLobby && !gameRunning) {
            if (!isInventoryEmpty(player)) {
                InventoryManager.clearInventory(player);
            }
            return;
        }

        if (inLobby && gameRunning && !kitUsed) {
            keepOnlyTabletCompassAndSync(player);
            return;
        }

        if (inLobby && gameRunning && !rtpUsed) {
            InventoryManager.giveTabletIfMissing(player);
            return;
        }

        if (playing && rtpUsed && !kitUsed) {
            keepOnlyTabletCompassAndSync(player);
            return;
        }

        if (playing && kitUsed && InventoryManager.hasTablet(player)) {
            InventoryManager.clearTablets(player);
        }
    }

    private static boolean isClanWarSpectatorApplicant(ServerPlayer player, boolean gameRunning) {
        return player != null
                && gameRunning
                && MapSetManager.isClanWarSet()
                && player.isSpectator()
                && !ClanWarManager.hasClan(player);
    }

    private static void keepOnlyTabletAndSync(ServerPlayer player) {
        InventoryCleanup cleanup = keepOnlyTablet(player);
        boolean changed = cleanup.removed() > 0;

        if (!InventoryManager.hasTablet(player)) {
            InventoryManager.giveTabletIfMissing(player);
            changed = true;
        }

        if (changed) {
            InventoryManager.syncInventory(player);
        }
    }

    private static void keepOnlyTabletCompassAndSync(ServerPlayer player) {
        InventoryCleanup cleanup = keepOnlyTabletAndAirdropCompass(player);
        boolean changed = cleanup.removed() > 0;

        if (!InventoryManager.hasTablet(player)) {
            InventoryManager.giveTabletIfMissing(player);
        }

        AirdropManager.giveCompassToJoiningPlayer(player);
        ExtractionPointManager.giveCompassToActiveParticipant(player);

        if (changed) {
            InventoryManager.syncInventory(player);
        }
    }

    private static InventoryCleanup keepOnlyTabletAndAirdropCompass(ServerPlayer player) {
        int removed = 0;
        boolean tabletAlreadyKept = false;
        boolean compassAlreadyKept = false;
        boolean extractionCompassAlreadyKept = false;
        boolean trackerAlreadyKept = false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty()) continue;

            if (stack.getItem() == ModItems.TACTICAL_TABLET.get()) {
                if (!tabletAlreadyKept) {
                    tabletAlreadyKept = true;
                    continue;
                }

                player.getInventory().setItem(i, ItemStack.EMPTY);
                removed++;
                continue;
            }

            if (AirdropCompassHelper.isAirdropCompass(stack)) {
                if (!compassAlreadyKept) {
                    compassAlreadyKept = true;
                    continue;
                }

                player.getInventory().setItem(i, ItemStack.EMPTY);
                removed++;
                continue;
            }

            if (ExtractionCompassHelper.isExtractionCompass(stack) && ExtractionPointManager.isActive()) {
                if (!extractionCompassAlreadyKept) {
                    extractionCompassAlreadyKept = true;
                    continue;
                }

                player.getInventory().setItem(i, ItemStack.EMPTY);
                removed++;
                continue;
            }

            if (stack.getItem() == ModItems.CONTRACT_TRACKER.get()) {
                if (!trackerAlreadyKept) {
                    trackerAlreadyKept = true;
                    continue;
                }

                player.getInventory().setItem(i, ItemStack.EMPTY);
                removed++;
                continue;
            }

            player.getInventory().setItem(i, ItemStack.EMPTY);
            removed++;
        }

        if (removed > 0) {
            player.getInventory().setChanged();
        }

        return new InventoryCleanup(removed);
    }

    private static InventoryCleanup keepOnlyTablet(ServerPlayer player) {
        int removed = 0;
        boolean tabletAlreadyKept = false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty()) continue;

            if (stack.getItem() == ModItems.TACTICAL_TABLET.get()) {
                if (!tabletAlreadyKept) {
                    tabletAlreadyKept = true;
                    continue;
                }
            }

            player.getInventory().setItem(i, ItemStack.EMPTY);
            removed++;
        }

        if (removed > 0) {
            player.getInventory().setChanged();
        }

        return new InventoryCleanup(removed);
    }

    private static boolean isInventoryEmpty(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private record InventoryCleanup(int removed) {
    }
}

