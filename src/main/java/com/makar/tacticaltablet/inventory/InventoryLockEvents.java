package com.makar.tacticaltablet.inventory;

import com.makar.tacticaltablet.anticheat.AntiCheatManager;
import com.makar.tacticaltablet.anticheat.Severity;
import com.makar.tacticaltablet.anticheat.ViolationType;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID)
public class InventoryLockEvents {

    private static final long DROPPED_ITEM_PICKUP_WINDOW_MS = 120_000L;
    private static final Map<UUID, DroppedItemOwner> playerDroppedItems = new HashMap<>();

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        if (isLobbyOrBattle(player) && !canUseDroppedItems(player)) {
            event.setCanceled(true);
            AntiCheatManager.record(
                    player,
                    ViolationType.ILLEGAL_INVENTORY,
                    Severity.LOW,
                    "blocked item toss item=" + itemName(event.getEntity().getItem())
            );
            return;
        }

        if (canUseDroppedItems(player)) {
            rememberDroppedItem(event.getEntity(), player);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean inLobby = GameStateManager.isInLobby(player)
                || player.getTags().contains("in_lobby");

        boolean inBattle = player.getTags().contains("war.playing");

        if (!inLobby && !inBattle) return;

        BlockEntity blockEntity = player.level().getBlockEntity(event.getPos());

        if (tryRecoverSuperbWarfareJumpPlate(player, event)) {
            return;
        }

        if (blockEntity instanceof Container) {
            if (AirdropManager.isAirdropChest(event.getPos())) {
                return;
            }

            if (canUseSuperbWarfareTool(player, event)) {
                return;
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            AntiCheatManager.record(
                    player,
                    ViolationType.ILLEGAL_CONTAINER,
                    Severity.LOW,
                    "blocked container at " + event.getPos().toShortString()
            );

            player.sendSystemMessage(
                    Component.literal("[WAR] Контейнеры отключены во время матча.")
            );
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!isLobbyOrBattle(player)) return;

        ItemEntity item = event.getItem();
        if (!canUseDroppedItems(player) || !isOwnRecentlyDroppedItem(item, player)) {
            event.setCanceled(true);
        }
    }

    private static void rememberDroppedItem(ItemEntity item, ServerPlayer player) {
        if (item == null || player == null) return;

        purgeExpiredDroppedItems();
        playerDroppedItems.put(
                item.getUUID(),
                new DroppedItemOwner(player.getUUID(), System.currentTimeMillis() + DROPPED_ITEM_PICKUP_WINDOW_MS)
        );
    }

    private static boolean isOwnRecentlyDroppedItem(ItemEntity item, ServerPlayer player) {
        if (item == null || player == null) return false;

        purgeExpiredDroppedItems();

        DroppedItemOwner owner = playerDroppedItems.get(item.getUUID());
        if (owner == null) return false;

        return owner.owner().equals(player.getUUID()) && owner.expiresAtMillis() >= System.currentTimeMillis();
    }

    private static void purgeExpiredDroppedItems() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, DroppedItemOwner>> iterator = playerDroppedItems.entrySet().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtMillis() < now) {
                iterator.remove();
            }
        }
    }

    public static void resetTracking() {
        playerDroppedItems.clear();
    }

    private static boolean isLobbyOrBattle(ServerPlayer player) {
        boolean inLobby = GameStateManager.isInLobby(player)
                || player.getTags().contains("in_lobby");

        boolean inBattle = player.getTags().contains("war.playing");

        return inLobby || inBattle;
    }

    private static boolean canUseDroppedItems(ServerPlayer player) {
        return player.getTags().contains("war.playing")
                && PlayerTabletState.isKitUsed(player)
                && !LivesManager.isEliminated(player);
    }

    private static boolean canUseSuperbWarfareTool(
            ServerPlayer player,
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (!canUseDroppedItems(player)) return false;

        ItemStack stack = player.getItemInHand(event.getHand());
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(
                player.level().getBlockState(event.getPos()).getBlock()
        );

        return isSuperbWarfareCrowbar(itemId) && isSuperbWarfareBlock(blockId);
    }

    private static boolean isSuperbWarfareCrowbar(ResourceLocation itemId) {
        return itemId != null
                && "superbwarfare".equals(itemId.getNamespace())
                && itemId.getPath().contains("crowbar");
    }

    private static boolean isSuperbWarfareBlock(ResourceLocation blockId) {
        return blockId != null && "superbwarfare".equals(blockId.getNamespace());
    }

    private static boolean tryRecoverSuperbWarfareJumpPlate(
            ServerPlayer player,
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (!canUseDroppedItems(player)) return false;

        ItemStack held = player.getItemInHand(event.getHand());
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (!isSuperbWarfareCrowbar(itemId)) return false;

        Block block = player.level().getBlockState(event.getPos()).getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (!isSuperbWarfareJumpPlate(blockId)) return false;

        Level level = player.level();
        if (level.isClientSide) return true;

        ItemStack recovered = new ItemStack(block.asItem());
        if (recovered.isEmpty()) return false;

        level.removeBlock(event.getPos(), false);

        if (!player.getInventory().add(recovered)) {
            ItemEntity dropped = new ItemEntity(
                    level,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    recovered
            );
            level.addFreshEntity(dropped);
            rememberDroppedItem(dropped, player);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.sendSystemMessage(Component.literal("[WAR] Прыжковая платформа возвращена."));
        return true;
    }

    private static boolean isSuperbWarfareJumpPlate(ResourceLocation blockId) {
        if (!isSuperbWarfareBlock(blockId)) return false;

        String path = blockId.getPath();
        return path.contains("jump")
                && (path.contains("plate") || path.contains("pad") || path.contains("board"));
    }

    private static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? stack.getItem().toString() : itemId.toString();
    }

    private record DroppedItemOwner(UUID owner, long expiresAtMillis) {
    }
}
