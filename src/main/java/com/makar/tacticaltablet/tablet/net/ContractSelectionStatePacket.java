package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ContractSelectionStatePacket {

    private static final int MAX_TARGETS = 16;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_CLASS_LENGTH = 32;

    private final boolean selectionActive;
    private final int selectionSecondsLeft;
    private final long cooldownLeftMs;
    private final boolean hasActiveContract;
    private final boolean soloMode;
    private final List<TargetEntry> targets;

    public ContractSelectionStatePacket(
            boolean selectionActive,
            int selectionSecondsLeft,
            long cooldownLeftMs,
            boolean hasActiveContract,
            boolean soloMode,
            List<TargetEntry> targets
    ) {
        this.selectionActive = selectionActive;
        this.selectionSecondsLeft = Math.max(0, selectionSecondsLeft);
        this.cooldownLeftMs = Math.max(0L, cooldownLeftMs);
        this.hasActiveContract = hasActiveContract;
        this.soloMode = soloMode;
        this.targets = copyTargets(targets);
    }

    public ContractSelectionStatePacket(FriendlyByteBuf buf) {
        this.selectionActive = buf.readBoolean();
        this.selectionSecondsLeft = buf.readInt();
        this.cooldownLeftMs = buf.readLong();
        this.hasActiveContract = buf.readBoolean();
        this.soloMode = buf.readBoolean();

        int size = PacketCodecs.readBoundedIntSize(buf, MAX_TARGETS, "contract target count");

        List<TargetEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new TargetEntry(
                    buf.readUUID(),
                    buf.readUtf(MAX_NAME_LENGTH),
                    buf.readUtf(MAX_CLASS_LENGTH),
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
        buf.writeBoolean(selectionActive);
        buf.writeInt(selectionSecondsLeft);
        buf.writeLong(cooldownLeftMs);
        buf.writeBoolean(hasActiveContract);
        buf.writeBoolean(soloMode);
        buf.writeInt(targets.size());
        for (TargetEntry target : targets) {
            buf.writeUUID(target.uuid());
            buf.writeUtf(target.name(), MAX_NAME_LENGTH);
            buf.writeUtf(target.selectedClass(), MAX_CLASS_LENGTH);
            buf.writeInt(target.kills());
            buf.writeInt(target.wins());
            buf.writeInt(target.careerPercent());
            buf.writeInt(target.difficulty());
            buf.writeInt(target.price());
            buf.writeInt(target.reward());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> invokeClientHandler()));
        ctx.get().setPacketHandled(true);
    }

    private void invokeClientHandler() {
        try {
            Class<?> handler = Class.forName("com.makar.tacticaltablet.tablet.client.ContractClientPacketHandler");
            handler.getMethod("handleSelection", ContractSelectionStatePacket.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to handle contract selection packet on client", exception);
        }
    }

    public boolean selectionActive() {
        return selectionActive;
    }

    public int selectionSecondsLeft() {
        return selectionSecondsLeft;
    }

    public long cooldownLeftMs() {
        return cooldownLeftMs;
    }

    public boolean hasActiveContract() {
        return hasActiveContract;
    }

    public boolean soloMode() {
        return soloMode;
    }

    public List<TargetEntry> targets() {
        return targets;
    }

    private static List<TargetEntry> copyTargets(List<TargetEntry> input) {
        List<TargetEntry> result = new ArrayList<>();
        if (input == null || input.isEmpty()) return result;
        for (TargetEntry entry : input) {
            if (entry == null) continue;
            if (result.size() >= MAX_TARGETS) break;
            result.add(entry);
        }
        return List.copyOf(result);
    }

    public record TargetEntry(
            UUID uuid,
            String name,
            String selectedClass,
            int kills,
            int wins,
            int careerPercent,
            int difficulty,
            int price,
            int reward
    ) {
    }
}
