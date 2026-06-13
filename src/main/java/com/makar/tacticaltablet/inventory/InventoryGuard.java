package com.makar.tacticaltablet.inventory;

import com.makar.tacticaltablet.airdrop.AirdropCompassHelper;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.anticheat.AntiCheatManager;
import com.makar.tacticaltablet.anticheat.Severity;
import com.makar.tacticaltablet.anticheat.ViolationType;
import com.makar.tacticaltablet.core.ModItems;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
        Set<String> tags = player.getTags();

        boolean inLobby = GameStateManager.isInLobby(player) || tags.contains("in_lobby");
        boolean playing = tags.contains("war.playing");
        boolean eliminated = LivesManager.isEliminated(player);
        boolean gameRunning = GameStateManager.isRunning(player.server);
        boolean tabletLobbyStage = GameStateManager.isTabletAvailableInLobby(player.server);
        boolean kitUsed = PlayerTabletState.isKitUsed(player);
        boolean rtpUsed = PlayerTabletState.isRtpUsed(player);

        boolean relevant = inLobby || playing || eliminated;
        if (!relevant) return;

        if (eliminated) {
            if (!isInventoryEmpty(player)) {
                int removed = countItems(player);
                InventoryManager.clearInventory(player);
                recordInventory(player, removed, "eliminated inventory cleanup");
            }
            return;
        }

        if (inLobby && !gameRunning && tabletLobbyStage) {
            keepOnlyTabletCompassAndSync(player);
            return;
        }

        if (inLobby && !gameRunning) {
            if (!isInventoryEmpty(player)) {
                int removed = countItems(player);
                InventoryManager.clearInventory(player);
                recordInventory(player, removed, "waiting lobby inventory cleanup");
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
            int removed = countTablets(player);
            InventoryManager.clearTablets(player);
            recordInventory(player, removed, Severity.HIGH, "tablet after kit used");
        }
    }

    private static void keepOnlyTabletCompassAndSync(ServerPlayer player) {
        InventoryCleanup cleanup = keepOnlyTabletAndAirdropCompass(player);
        boolean changed = cleanup.removed() > 0;

        if (!InventoryManager.hasTablet(player)) {
            InventoryManager.giveTabletIfMissing(player);
        }

        AirdropManager.giveCompassToJoiningPlayer(player);

        if (changed) {
            InventoryManager.syncInventory(player);
        }

        if (changed) {
            boolean duplicateTablets = cleanup.extraTablets() > 0;
            recordInventory(
                    player,
                    cleanup.removed(),
                    duplicateTablets ? Severity.HIGH : severityForRemoved(cleanup.removed()),
                    duplicateTablets
                            ? "removed non-tablet items and duplicate tablets"
                            : "removed non-tablet items"
            );
        }
    }

    private static InventoryCleanup keepOnlyTabletAndAirdropCompass(ServerPlayer player) {
        int removed = 0;
        int extraTablets = 0;
        boolean tabletAlreadyKept = false;
        boolean compassAlreadyKept = false;
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
                extraTablets++;
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

        return new InventoryCleanup(removed, extraTablets);
    }

    private static boolean isInventoryEmpty(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static int countItems(ServerPlayer player) {
        int count = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                count++;
            }
        }

        return count;
    }

    private static int countTablets(ServerPlayer player) {
        int count = 0;

        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() == ModItems.TACTICAL_TABLET.get()) {
                count++;
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ModItems.TACTICAL_TABLET.get()) {
                count++;
            }
        }

        return count;
    }

    private static Severity severityForRemoved(int removed) {
        return removed > 1 ? Severity.MEDIUM : Severity.LOW;
    }

    private static void recordInventory(ServerPlayer player, int removed, String reason) {
        recordInventory(player, removed, severityForRemoved(removed), reason);
    }

    private static void recordInventory(ServerPlayer player, int removed, Severity severity, String reason) {
        if (removed <= 0) return;

        AntiCheatManager.record(
                player,
                ViolationType.ILLEGAL_INVENTORY,
                severity,
                "removed " + removed + " items; reason=" + reason
        );
    }

    private record InventoryCleanup(int removed, int extraTablets) {
    }
}

