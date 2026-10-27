package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.tablet.classes.TabletClassSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record TabletClassesStatePacket(TabletClassSnapshot snapshot) {
    public TabletClassesStatePacket(FriendlyByteBuf b) { this(TabletClassSnapshot.read(b)); }
    public void encode(FriendlyByteBuf b) { snapshot.write(b); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.makar.tacticaltablet.tablet.client.prototype.TabletClassesClient.accept(snapshot)));
        context.setPacketHandled(true);
    }
}
