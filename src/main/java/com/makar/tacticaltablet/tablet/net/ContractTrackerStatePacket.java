package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ContractTrackerStatePacket {

    private static final int MAX_TARGETS = 16;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_CLASS_LENGTH = 32;

    private final boolean active;
    private final boolean openScreen;
    private final int zoneCenterX;
    private final int zoneCenterZ;
    private final int zoneRadius;
    private final int playerX;
    private final int playerZ;
    private final int signalSecondsLeft;
    private final List<TargetEntry> targets;

    public static ContractTrackerStatePacket empty(boolean openScreen, int zoneCenterX, int zoneCenterZ, int zoneRadius) {
        return new ContractTrackerStatePacket(
                false, openScreen, zoneCenterX, zoneCenterZ, zoneRadius, 0, 0, 0, List.of()
        );
    }

    public ContractTrackerStatePacket(
            boolean active,
            boolean openScreen,
            int zoneCenterX,
            int zoneCenterZ,
            int zoneRadius,
            int playerX,
            int playerZ,
            int signalSecondsLeft,
            List<TargetEntry> targets
    ) {
        this.active = active;
        this.openScreen = openScreen;
        this.zoneCenterX = zoneCenterX;
        this.zoneCenterZ = zoneCenterZ;
        this.zoneRadius = Math.max(1, zoneRadius);
        this.playerX = playerX;
        this.playerZ = playerZ;
        this.signalSecondsLeft = Math.max(0, signalSecondsLeft);
        this.targets = copyTargets(targets);
    }

    public ContractTrackerStatePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.openScreen = buf.readBoolean();
        this.zoneCenterX = buf.readInt();
        this.zoneCenterZ = buf.readInt();
        this.zoneRadius = buf.readInt();
        this.playerX = buf.readInt();
        this.playerZ = buf.readInt();
        this.signalSecondsLeft = buf.readInt();

        int size = PacketCodecs.readBoundedIntSize(buf, MAX_TARGETS, "tracker target count");

        List<TargetEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new TargetEntry(
                    buf.readUtf(MAX_NAME_LENGTH),
                    buf.readUtf(MAX_CLASS_LENGTH),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            ));
        }
        this.targets = List.copyOf(entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeBoolean(openScreen);
        buf.writeInt(zoneCenterX);
        buf.writeInt(zoneCenterZ);
        buf.writeInt(zoneRadius);
        buf.writeInt(playerX);
        buf.writeInt(playerZ);
        buf.writeInt(signalSecondsLeft);
        buf.writeInt(targets.size());
        for (TargetEntry target : targets) {
            buf.writeUtf(target.name(), MAX_NAME_LENGTH);
            buf.writeUtf(target.selectedClass(), MAX_CLASS_LENGTH);
            buf.writeInt(target.kills());
            buf.writeInt(target.wins());
            buf.writeInt(target.careerPercent());
            buf.writeInt(target.difficulty());
            buf.writeInt(target.price());
            buf.writeInt(target.reward());
            buf.writeInt(target.areaX());
            buf.writeInt(target.areaZ());
            buf.writeInt(target.areaRadius());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> invokeClientHandler()));
        ctx.get().setPacketHandled(true);
    }

    private void invokeClientHandler() {
        try {
            Class<?> handler = Class.forName("com.makar.tacticaltablet.tablet.client.ContractClientPacketHandler");
            handler.getMethod("handleTracker", ContractTrackerStatePacket.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to handle contract tracker packet on client", exception);
        }
    }

    public boolean active() {
        return active;
    }

    public boolean openScreen() {
        return openScreen;
    }

    public int zoneCenterX() {
        return zoneCenterX;
    }

    public int zoneCenterZ() {
        return zoneCenterZ;
    }

    public int zoneRadius() {
        return zoneRadius;
    }

    public int playerX() {
        return playerX;
    }

    public int playerZ() {
        return playerZ;
    }

    public int signalSecondsLeft() {
        return signalSecondsLeft;
    }

    public List<TargetEntry> targets() {
        return targets;
    }

    private static List<TargetEntry> copyTargets(List<TargetEntry> input) {
        if (input == null || input.isEmpty()) return List.of();

        List<TargetEntry> result = new ArrayList<>();
        for (TargetEntry entry : input) {
            if (entry == null) continue;
            if (result.size() >= MAX_TARGETS) break;
            result.add(entry);
        }
        return List.copyOf(result);
    }

    public record TargetEntry(
            String name,
            String selectedClass,
            int kills,
            int wins,
            int careerPercent,
            int difficulty,
            int price,
            int reward,
            int areaX,
            int areaZ,
            int areaRadius
    ) {
        public TargetEntry {
            name = name == null ? "" : name;
            selectedClass = selectedClass == null ? "" : selectedClass;
            kills = Math.max(0, kills);
            wins = Math.max(0, wins);
            careerPercent = Math.max(0, Math.min(100, careerPercent));
            difficulty = Math.max(0, difficulty);
            price = Math.max(0, price);
            reward = Math.max(0, reward);
            areaRadius = Math.max(0, areaRadius);
        }
    }
}
