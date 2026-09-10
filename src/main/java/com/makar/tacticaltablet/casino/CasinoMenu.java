package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.core.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Slot-free server container shared by machines and the spectator entry. */
public final class CasinoMenu extends AbstractContainerMenu {
    private final UUID sessionId;
    private final ResourceKey<Level> dimension;
    private final BlockPos machinePos;
    private final int openingBalance;

    public CasinoMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, data.readUUID(), data.readVarInt(), data.readBoolean()
                ? null : ResourceKey.create(Registries.DIMENSION, data.readResourceLocation()), data);
    }
    private CasinoMenu(int id, UUID sessionId, int balance, ResourceKey<Level> dimension, FriendlyByteBuf data) {
        this(id, sessionId, balance, dimension, dimension == null ? null : data.readBlockPos());
    }
    public CasinoMenu(int id, UUID sessionId, int balance, ResourceKey<Level> dimension, BlockPos machinePos) {
        super(ModMenuTypes.CASINO.get(), id);
        this.sessionId = sessionId;
        this.openingBalance = Math.max(0, balance);
        this.dimension = dimension;
        this.machinePos = machinePos == null ? null : machinePos.immutable();
        if ((dimension == null) != (machinePos == null)) throw new IllegalArgumentException("Incomplete machine anchor");
    }
    public UUID sessionId() { return sessionId; }
    public int openingBalance() { return openingBalance; }
    public boolean spectatorSource() { return machinePos == null; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos machinePos() { return machinePos; }
    public boolean matches(UUID id, ResourceKey<Level> world, BlockPos pos) {
        return CasinoMachineAccess.matchesAnchor(sessionId, dimension == null ? null : dimension.location().toString(),
                machinePos, id, world == null ? null : world.location().toString(), pos);
    }
    @Override public boolean stillValid(Player player) {
        return player.level().isClientSide || player instanceof ServerPlayer serverPlayer
                && CasinoSessionManager.isMenuValid(serverPlayer, this);
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) CasinoSessionManager.close(serverPlayer, sessionId);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
