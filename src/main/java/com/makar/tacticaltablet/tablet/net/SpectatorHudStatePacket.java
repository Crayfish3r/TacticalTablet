package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.SpectatorHudSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** Server-authoritative S2C state for the competitive spectator HUD. */
public final class SpectatorHudStatePacket {
    private final SpectatorHudSnapshot snapshot;

    public SpectatorHudStatePacket(SpectatorHudSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public SpectatorHudStatePacket(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            snapshot = null;
            return;
        }
        snapshot = new SpectatorHudSnapshot(
                buf.readUtf(SpectatorHudSnapshot.MAX_PLAYER_NAME_LENGTH),
                buf.readUtf(SpectatorHudSnapshot.MAX_CLASS_NAME_LENGTH),
                readNonNegative(buf), readNonNegative(buf), readNonNegative(buf), readNonNegative(buf));
    }

    public static SpectatorHudStatePacket clear() {
        return new SpectatorHudStatePacket((SpectatorHudSnapshot) null);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(snapshot != null);
        if (snapshot == null) return;
        buf.writeUtf(snapshot.playerName(), SpectatorHudSnapshot.MAX_PLAYER_NAME_LENGTH);
        buf.writeUtf(snapshot.className(), SpectatorHudSnapshot.MAX_CLASS_NAME_LENGTH);
        buf.writeInt(snapshot.kills());
        buf.writeInt(snapshot.deaths());
        buf.writeInt(snapshot.wins());
        buf.writeInt(snapshot.matchesPlayed());
    }

    public Optional<SpectatorHudSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (snapshot == null) {
                com.makar.tacticaltablet.client.SpectatorHudClientState.clear();
            } else {
                com.makar.tacticaltablet.client.SpectatorHudClientState.update(snapshot);
            }
        }));
        context.setPacketHandled(true);
    }

    private static int readNonNegative(FriendlyByteBuf buf) {
        return Math.max(0, buf.readInt());
    }
}
