package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ContractTrackerStatePacket {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_CLASS_LENGTH = 32;

    private final boolean active;
    private final boolean openScreen;
    private final String targetName;
    private final String targetClass;
    private final int targetKills;
    private final int targetWins;
    private final int targetCareerPercent;
    private final int difficulty;
    private final int price;
    private final int reward;
    private final int zoneCenterX;
    private final int zoneCenterZ;
    private final int zoneRadius;
    private final int playerX;
    private final int playerZ;
    private final int targetAreaX;
    private final int targetAreaZ;
    private final int targetAreaRadius;
    private final int signalSecondsLeft;

    public static ContractTrackerStatePacket empty(boolean openScreen, int zoneCenterX, int zoneCenterZ, int zoneRadius) {
        return new ContractTrackerStatePacket(false, openScreen, "", "", 0, 0, 0, 0, 0, 0,
                zoneCenterX, zoneCenterZ, zoneRadius, 0, 0, 0, 0, 0, 0);
    }

    public ContractTrackerStatePacket(
            boolean active,
            boolean openScreen,
            String targetName,
            String targetClass,
            int targetKills,
            int targetWins,
            int targetCareerPercent,
            int difficulty,
            int price,
            int reward,
            int zoneCenterX,
            int zoneCenterZ,
            int zoneRadius,
            int playerX,
            int playerZ,
            int targetAreaX,
            int targetAreaZ,
            int targetAreaRadius,
            int signalSecondsLeft
    ) {
        this.active = active;
        this.openScreen = openScreen;
        this.targetName = targetName == null ? "" : targetName;
        this.targetClass = targetClass == null ? "" : targetClass;
        this.targetKills = Math.max(0, targetKills);
        this.targetWins = Math.max(0, targetWins);
        this.targetCareerPercent = Math.max(0, Math.min(100, targetCareerPercent));
        this.difficulty = Math.max(0, difficulty);
        this.price = Math.max(0, price);
        this.reward = Math.max(0, reward);
        this.zoneCenterX = zoneCenterX;
        this.zoneCenterZ = zoneCenterZ;
        this.zoneRadius = Math.max(1, zoneRadius);
        this.playerX = playerX;
        this.playerZ = playerZ;
        this.targetAreaX = targetAreaX;
        this.targetAreaZ = targetAreaZ;
        this.targetAreaRadius = Math.max(0, targetAreaRadius);
        this.signalSecondsLeft = Math.max(0, signalSecondsLeft);
    }

    public ContractTrackerStatePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.openScreen = buf.readBoolean();
        this.targetName = buf.readUtf(MAX_NAME_LENGTH);
        this.targetClass = buf.readUtf(MAX_CLASS_LENGTH);
        this.targetKills = buf.readInt();
        this.targetWins = buf.readInt();
        this.targetCareerPercent = buf.readInt();
        this.difficulty = buf.readInt();
        this.price = buf.readInt();
        this.reward = buf.readInt();
        this.zoneCenterX = buf.readInt();
        this.zoneCenterZ = buf.readInt();
        this.zoneRadius = buf.readInt();
        this.playerX = buf.readInt();
        this.playerZ = buf.readInt();
        this.targetAreaX = buf.readInt();
        this.targetAreaZ = buf.readInt();
        this.targetAreaRadius = buf.readInt();
        this.signalSecondsLeft = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeBoolean(openScreen);
        buf.writeUtf(targetName, MAX_NAME_LENGTH);
        buf.writeUtf(targetClass, MAX_CLASS_LENGTH);
        buf.writeInt(targetKills);
        buf.writeInt(targetWins);
        buf.writeInt(targetCareerPercent);
        buf.writeInt(difficulty);
        buf.writeInt(price);
        buf.writeInt(reward);
        buf.writeInt(zoneCenterX);
        buf.writeInt(zoneCenterZ);
        buf.writeInt(zoneRadius);
        buf.writeInt(playerX);
        buf.writeInt(playerZ);
        buf.writeInt(targetAreaX);
        buf.writeInt(targetAreaZ);
        buf.writeInt(targetAreaRadius);
        buf.writeInt(signalSecondsLeft);
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

    public String targetName() {
        return targetName;
    }

    public String targetClass() {
        return targetClass;
    }

    public int targetKills() {
        return targetKills;
    }

    public int targetWins() {
        return targetWins;
    }

    public int targetCareerPercent() {
        return targetCareerPercent;
    }

    public int difficulty() {
        return difficulty;
    }

    public int price() {
        return price;
    }

    public int reward() {
        return reward;
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

    public int targetAreaX() {
        return targetAreaX;
    }

    public int targetAreaZ() {
        return targetAreaZ;
    }

    public int targetAreaRadius() {
        return targetAreaRadius;
    }

    public int signalSecondsLeft() {
        return signalSecondsLeft;
    }
}
