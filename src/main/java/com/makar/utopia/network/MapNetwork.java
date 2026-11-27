package com.makar.utopia.network;

import com.makar.utopia.Utopia;
import com.makar.utopia.boundary.CityZone;
import com.makar.utopia.boundary.Polygon;
import com.makar.utopia.client.TabletMapState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

public final class MapNetwork {
    public static final int MAX_DIMENSION_LENGTH = 128;
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Utopia.MOD_ID, "map"), () -> VERSION, VERSION::equals, VERSION::equals);

    public record Geometry(ResourceLocation dimension, Polygon perimeter, Polygon hill, Polygon port) { }
    public record Status(CityZone zone, int portRemainingMillis) { }

    private record Snapshot(Geometry geometry, Status status) { }

    private MapNetwork() { }

    public static void register() {
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapNetwork::encodeSnapshot)
                .decoder(MapNetwork::decodeSnapshot)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> TabletMapState.setGeometry(packet.geometry, packet.status)))
                .add();
        CHANNEL.messageBuilder(Status.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapNetwork::writeStatus)
                .decoder(MapNetwork::readStatus)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> TabletMapState.setStatus(packet)))
                .add();
    }

    public static void sendGeometry(ServerPlayer player, Geometry geometry, Status status) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(geometry, status));
    }

    public static void sendStatus(ServerPlayer player, Status status) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), status);
    }

    private static void encodeSnapshot(Snapshot packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.geometry.dimension.toString(), MAX_DIMENSION_LENGTH);
        writePolygon(buffer, packet.geometry.perimeter);
        writePolygon(buffer, packet.geometry.hill);
        buffer.writeBoolean(packet.geometry.port != null);
        if (packet.geometry.port != null) writePolygon(buffer, packet.geometry.port);
        writeStatus(packet.status, buffer);
    }

    private static Snapshot decodeSnapshot(FriendlyByteBuf buffer) {
        ResourceLocation dimension = new ResourceLocation(buffer.readUtf(MAX_DIMENSION_LENGTH));
        Polygon perimeter = readPolygon(buffer);
        Polygon hill = readPolygon(buffer);
        Polygon port = buffer.readBoolean() ? readPolygon(buffer) : null;
        return new Snapshot(new Geometry(dimension, perimeter, hill, port), readStatus(buffer));
    }

    private static void writeStatus(Status status, FriendlyByteBuf buffer) {
        buffer.writeEnum(status.zone);
        buffer.writeVarInt(status.portRemainingMillis);
    }

    private static Status readStatus(FriendlyByteBuf buffer) {
        CityZone zone = buffer.readEnum(CityZone.class);
        int remaining = buffer.readVarInt();
        if (remaining < 0 || remaining > CityZone.PORT_PROTECTION_MILLIS) {
            throw new IllegalArgumentException("Invalid port timer: " + remaining);
        }
        return new Status(zone, remaining);
    }

    private static void writePolygon(FriendlyByteBuf buffer, Polygon polygon) {
        buffer.writeVarInt(polygon.vertices().size());
        for (Polygon.Point point : polygon.vertices()) {
            buffer.writeDouble(point.x());
            buffer.writeDouble(point.z());
        }
    }

    private static Polygon readPolygon(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 3 || count > Polygon.MAX_VERTICES) throw new IllegalArgumentException("Invalid vertex count: " + count);
        List<Polygon.Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) points.add(new Polygon.Point(buffer.readDouble(), buffer.readDouble()));
        return new Polygon(points);
    }
}
