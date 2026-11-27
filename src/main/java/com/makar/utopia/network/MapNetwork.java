package com.makar.utopia.network;

import com.makar.utopia.Utopia;
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
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Utopia.MOD_ID, "map"), () -> VERSION, VERSION::equals, VERSION::equals);

    public record Geometry(ResourceLocation dimension, Polygon perimeter, Polygon hill) { }

    private record Snapshot(Geometry geometry, boolean insideHill) { }
    private record HillStatus(boolean insideHill) { }

    private MapNetwork() { }

    public static void register() {
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapNetwork::encodeSnapshot)
                .decoder(MapNetwork::decodeSnapshot)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> TabletMapState.setGeometry(packet.geometry, packet.insideHill)))
                .add();
        CHANNEL.messageBuilder(HillStatus.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> buffer.writeBoolean(packet.insideHill))
                .decoder(buffer -> new HillStatus(buffer.readBoolean()))
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> TabletMapState.setHillStatus(packet.insideHill)))
                .add();
    }

    public static void sendGeometry(ServerPlayer player, Geometry geometry, boolean insideHill) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(geometry, insideHill));
    }

    public static void sendHillStatus(ServerPlayer player, boolean insideHill) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HillStatus(insideHill));
    }

    private static void encodeSnapshot(Snapshot packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.geometry.dimension.toString(), MAX_DIMENSION_LENGTH);
        writePolygon(buffer, packet.geometry.perimeter);
        writePolygon(buffer, packet.geometry.hill);
        buffer.writeBoolean(packet.insideHill);
    }

    private static Snapshot decodeSnapshot(FriendlyByteBuf buffer) {
        ResourceLocation dimension = new ResourceLocation(buffer.readUtf(MAX_DIMENSION_LENGTH));
        Geometry geometry = new Geometry(dimension, readPolygon(buffer), readPolygon(buffer));
        return new Snapshot(geometry, buffer.readBoolean());
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
