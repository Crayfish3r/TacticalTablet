package com.makar.tacticaltablet.airdrop.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class AirdropSmokeStatePacket {
    private final boolean active;
    private final ResourceLocation dimension;
    private final BlockPos pos;

    public AirdropSmokeStatePacket(boolean active, ResourceLocation dimension, BlockPos pos) {
        this.active = active;
        this.dimension = dimension;
        this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
    }

    public AirdropSmokeStatePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.dimension = buf.readResourceLocation();
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::invokeClientHandler));
        context.setPacketHandled(true);
    }

    private void invokeClientHandler() {
        try {
            Class<?> handler = Class.forName("com.makar.tacticaltablet.airdrop.client.AirdropSmokeClientState");
            handler.getMethod("handle", AirdropSmokeStatePacket.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to update AirDrop smoke on client", exception);
        }
    }

    public boolean active() {
        return active;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }
}
