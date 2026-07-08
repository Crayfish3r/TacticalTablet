package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class DeathScreenPacket {

    private static final int MAX_TEXT_LENGTH = 128;

    private final String title;
    private final String subtitle;
    private final int durationTicks;
    private final boolean playSadTrombone;

    public DeathScreenPacket(String title, String subtitle, int durationTicks) {
        this(title, subtitle, durationTicks, false);
    }

    public DeathScreenPacket(String title, String subtitle, int durationTicks, boolean playSadTrombone) {
        this.title = sanitize(title);
        this.subtitle = sanitize(subtitle);
        this.durationTicks = Math.max(1, durationTicks);
        this.playSadTrombone = playSadTrombone;
    }

    public DeathScreenPacket(FriendlyByteBuf buf) {
        title = buf.readUtf(MAX_TEXT_LENGTH);
        subtitle = buf.readUtf(MAX_TEXT_LENGTH);
        durationTicks = Math.max(1, buf.readVarInt());
        playSadTrombone = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(title, MAX_TEXT_LENGTH);
        buf.writeUtf(subtitle, MAX_TEXT_LENGTH);
        buf.writeVarInt(durationTicks);
        buf.writeBoolean(playSadTrombone);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.makar.tacticaltablet.client.ClientDeathScreenHandler.show(
                        title,
                        subtitle,
                        durationTicks,
                        playSadTrombone
                )
        ));
        context.setPacketHandled(true);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }
}
