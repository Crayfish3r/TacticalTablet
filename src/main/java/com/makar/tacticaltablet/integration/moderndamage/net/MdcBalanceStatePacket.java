package com.makar.tacticaltablet.integration.moderndamage.net;

import com.makar.tacticaltablet.integration.moderndamage.ModernDamageBalanceSnapshot;
import com.makar.tacticaltablet.integration.moderndamage.ModernDamageIntegration;
import com.makar.tacticaltablet.integration.moderndamage.client.ModernDamageClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

public final class MdcBalanceStatePacket {
    public enum Result {
        NONE, SUCCESS, NO_PERMISSION, RATE_LIMITED, UNAVAILABLE, STALE_REVISION,
        INVALID_VALUES, SAVE_FAILED, MALFORMED
    }

    private static final int MAX_VALUES = 128;
    private final ModernDamageIntegration.State integrationState;
    private final String detectedVersion;
    private final String details;
    private final boolean canEdit;
    private final long revision;
    private final double[] values;
    private final Result result;

    public MdcBalanceStatePacket(ModernDamageIntegration.State integrationState, String detectedVersion,
                                 String details, boolean canEdit, long revision, double[] values, Result result) {
        this.integrationState = integrationState;
        this.detectedVersion = detectedVersion == null ? "" : detectedVersion;
        this.details = details == null ? "" : details;
        this.canEdit = canEdit;
        this.revision = revision;
        this.values = values == null ? new double[0] : Arrays.copyOf(values, values.length);
        this.result = result == null ? Result.NONE : result;
    }

    public MdcBalanceStatePacket(FriendlyByteBuf buffer) {
        this.integrationState = enumValue(ModernDamageIntegration.State.values(), buffer.readVarInt(),
                ModernDamageIntegration.State.ERROR);
        this.detectedVersion = buffer.readUtf(32);
        this.details = buffer.readUtf(256);
        this.canEdit = buffer.readBoolean();
        this.revision = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Invalid MDC value count " + count);
        this.values = new double[count];
        for (int index = 0; index < count; index++) values[index] = buffer.readDouble();
        this.result = enumValue(Result.values(), buffer.readVarInt(), Result.MALFORMED);
    }

    public static void encode(MdcBalanceStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.integrationState.ordinal());
        buffer.writeUtf(packet.detectedVersion, 32);
        buffer.writeUtf(packet.details, 256);
        buffer.writeBoolean(packet.canEdit);
        buffer.writeLong(packet.revision);
        buffer.writeVarInt(packet.values.length);
        for (double value : packet.values) buffer.writeDouble(value);
        buffer.writeVarInt(packet.result.ordinal());
    }

    public static void handle(MdcBalanceStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ModernDamageClientState.accept(packet)));
        context.setPacketHandled(true);
    }

    public static MdcBalanceStatePacket forPlayer(ServerPlayer player, Result result) {
        ModernDamageIntegration.Status status = ModernDamageIntegration.status();
        ModernDamageBalanceSnapshot snapshot = ModernDamageIntegration.readBalance();
        return new MdcBalanceStatePacket(status.state(), status.detectedVersion(), status.details(),
                status.supported() && player != null && player.hasPermissions(2), snapshot.revision(),
                snapshot.values(), result);
    }

    public ModernDamageIntegration.State integrationState() { return integrationState; }
    public String detectedVersion() { return detectedVersion; }
    public String details() { return details; }
    public boolean canEdit() { return canEdit; }
    public long revision() { return revision; }
    public double[] values() { return Arrays.copyOf(values, values.length); }
    public Result result() { return result; }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
