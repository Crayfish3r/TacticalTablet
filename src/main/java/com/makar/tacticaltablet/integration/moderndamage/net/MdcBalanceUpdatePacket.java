package com.makar.tacticaltablet.integration.moderndamage.net;

import com.makar.tacticaltablet.integration.moderndamage.ModernDamageBalanceSchema;
import com.makar.tacticaltablet.integration.moderndamage.ModernDamageIntegration;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MdcBalanceUpdatePacket {
    public record FieldValue(int id, double value) {
    }

    private static final int MAX_FIELDS = 128;
    private final long expectedRevision;
    private final List<FieldValue> fields;

    public MdcBalanceUpdatePacket(long expectedRevision, Map<Integer, Double> fields) {
        this.expectedRevision = expectedRevision;
        this.fields = fields.entrySet().stream().map(entry -> new FieldValue(entry.getKey(), entry.getValue())).toList();
    }

    public MdcBalanceUpdatePacket(FriendlyByteBuf buffer) {
        this.expectedRevision = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_FIELDS) throw new IllegalArgumentException("Invalid MDC field count " + count);
        List<FieldValue> decoded = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            decoded.add(new FieldValue(buffer.readVarInt(), buffer.readDouble()));
        }
        this.fields = List.copyOf(decoded);
    }

    public static void encode(MdcBalanceUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.expectedRevision);
        buffer.writeVarInt(packet.fields.size());
        for (FieldValue field : packet.fields) {
            buffer.writeVarInt(field.id());
            buffer.writeDouble(field.value());
        }
    }

    public static void handle(MdcBalanceUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> handleOnServer(player, packet));
        context.setPacketHandled(true);
    }

    long expectedRevision() {
        return expectedRevision;
    }

    List<FieldValue> fields() {
        return fields;
    }

    private static void handleOnServer(ServerPlayer player, MdcBalanceUpdatePacket packet) {
        if (player == null) return;
        if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.ADMIN_MDC)) {
            reply(player, MdcBalanceStatePacket.Result.RATE_LIMITED);
            return;
        }
        if (!player.hasPermissions(2)) {
            reply(player, MdcBalanceStatePacket.Result.NO_PERMISSION);
            return;
        }
        if (!ModernDamageIntegration.isSupported()) {
            reply(player, MdcBalanceStatePacket.Result.UNAVAILABLE);
            return;
        }

        Map<Integer, Double> submitted = new LinkedHashMap<>();
        for (FieldValue field : packet.fields) {
            if (ModernDamageBalanceSchema.byId(field.id()).isEmpty()
                    || submitted.put(field.id(), field.value()) != null) {
                reply(player, MdcBalanceStatePacket.Result.MALFORMED);
                return;
            }
        }

        ModernDamageIntegration.ApplyResult result = ModernDamageIntegration.applyBalance(
                packet.expectedRevision, submitted, player.getGameProfile().getName());
        MdcBalanceStatePacket.Result wireResult = switch (result.error()) {
            case NONE -> MdcBalanceStatePacket.Result.SUCCESS;
            case UNAVAILABLE -> MdcBalanceStatePacket.Result.UNAVAILABLE;
            case STALE_REVISION -> MdcBalanceStatePacket.Result.STALE_REVISION;
            case VALIDATION_FAILED -> MdcBalanceStatePacket.Result.INVALID_VALUES;
            case SAVE_FAILED -> MdcBalanceStatePacket.Result.SAVE_FAILED;
        };
        if (!result.success()) {
            reply(player, wireResult);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            MdcBalanceStatePacket.Result recipientResult = online == player
                    ? MdcBalanceStatePacket.Result.SUCCESS : MdcBalanceStatePacket.Result.NONE;
            PacketHandler.sendToPlayer(online, MdcBalanceStatePacket.forPlayer(online, recipientResult));
        }
    }

    private static void reply(ServerPlayer player, MdcBalanceStatePacket.Result result) {
        PacketHandler.sendToPlayer(player, MdcBalanceStatePacket.forPlayer(player, result));
    }
}
