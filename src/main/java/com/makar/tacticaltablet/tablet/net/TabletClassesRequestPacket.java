package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.TabletProgressOperation.*;
import com.makar.tacticaltablet.tablet.classes.TabletClassService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public final class TabletClassesRequestPacket {
    private final Identity identity;
    public TabletClassesRequestPacket(Identity identity) { this.identity = validate(identity); }
    public TabletClassesRequestPacket(FriendlyByteBuf b) {
        this(new Identity(b.readUUID(), b.readVarLong(), b.readEnum(Kind.class), b.readUtf(32), b.readVarInt()));
    }
    private static Identity validate(Identity id) {
        if (id.requestId() <= 0 || id.classKey().length() > 32
                || id.kind() == Kind.FETCH && (!id.classKey().isEmpty() || id.targetTier() != -1)
                || id.kind() != Kind.FETCH && !id.classKey().matches("[a-z0-9_]{1,32}")
                || id.kind() == Kind.UPGRADE_TIER && (ClassTier.byId(id.targetTier()).isEmpty() || id.targetTier() == 0)
                || id.kind() != Kind.UPGRADE_TIER && id.targetTier() != -1)
            throw new IllegalArgumentException("Invalid tablet operation request");
        return id;
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(identity.session()); b.writeVarLong(identity.requestId()); b.writeEnum(identity.kind());
        b.writeUtf(identity.classKey(), 32); b.writeVarInt(identity.targetTier());
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> TabletClassService.handle(context.getSender(), identity));
        context.setPacketHandled(true);
    }
}
