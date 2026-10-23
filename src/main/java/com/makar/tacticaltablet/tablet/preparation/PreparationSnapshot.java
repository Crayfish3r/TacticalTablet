package com.makar.tacticaltablet.tablet.preparation;

import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

/** Bounded authoritative extension of TabletStatePacket; no world/client objects. */
public record PreparationSnapshot(long requestId, boolean rejected, String message, String selectedClass,
                                  boolean rtpUsed, boolean rtpPending, String rtpReason,
                                  List<ClassAvailability> classes) {
    private static final int MAX_CLASSES = 64, MAX_TEXT = 192;
    public record ClassAvailability(int actionId, String reason) { }
    public PreparationSnapshot { classes = List.copyOf(classes); }
    public static PreparationSnapshot read(FriendlyByteBuf buf) {
        long id = buf.readVarLong();
        if (id <= 0) throw new IllegalArgumentException("Invalid preparation request ID");
        boolean rejected = buf.readBoolean();
        String message = buf.readUtf(MAX_TEXT), selected = buf.readUtf(32);
        boolean used = buf.readBoolean(), pending = buf.readBoolean();
        String reason = buf.readUtf(MAX_TEXT);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CLASSES) throw new IllegalArgumentException("Too many preparation classes");
        List<ClassAvailability> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) rows.add(new ClassAvailability(buf.readVarInt(), buf.readUtf(MAX_TEXT)));
        return new PreparationSnapshot(id, rejected, message, selected, used, pending, reason, rows);
    }
    public void write(FriendlyByteBuf buf) {
        if (classes.size() > MAX_CLASSES) throw new IllegalArgumentException("Too many preparation classes");
        buf.writeVarLong(requestId); buf.writeBoolean(rejected); buf.writeUtf(message, MAX_TEXT);
        buf.writeUtf(selectedClass, 32); buf.writeBoolean(rtpUsed); buf.writeBoolean(rtpPending);
        buf.writeUtf(rtpReason, MAX_TEXT); buf.writeVarInt(classes.size());
        for (ClassAvailability row : classes) { buf.writeVarInt(row.actionId); buf.writeUtf(row.reason, MAX_TEXT); }
    }
}
