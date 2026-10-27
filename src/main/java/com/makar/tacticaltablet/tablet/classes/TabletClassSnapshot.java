package com.makar.tacticaltablet.tablet.classes;

import com.makar.tacticaltablet.progression.TabletProgressOperation.*;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Bounded server presentation model. Ownership, mode permission and transient kit admission are distinct. */
public record TabletClassSnapshot(UUID session, long requestId, long revision, int coins,
                                  Result operation, Result pending, List<Row> rows) {
    public record Row(String classKey, int actionId, boolean owned, boolean modeAllowed, String kitReason,
                      int tier, int nextTier, float progress, String upgradeReason, int upgradePrice,
                      Kind purchaseKind, int price, String purchaseReason, boolean pending) {
        public boolean visible() { return modeAllowed; }
        public boolean canUpgrade() { return nextTier >= 0 && upgradeReason.isEmpty() && !pending; }
        public boolean canPurchase() { return purchaseKind != Kind.FETCH && purchaseReason.isEmpty() && !pending; }
    }
    public TabletClassSnapshot { rows = List.copyOf(rows); }
    public void write(FriendlyByteBuf b) {
        if (rows.size() > 64) throw new IllegalArgumentException("Too many tablet classes");
        b.writeUUID(session); b.writeVarLong(requestId); b.writeVarLong(revision); b.writeVarInt(coins);
        writeResult(b, operation); writeResult(b, pending); b.writeVarInt(rows.size());
        for (Row r : rows) {
            b.writeUtf(r.classKey, 32); b.writeVarInt(r.actionId); b.writeBoolean(r.owned); b.writeBoolean(r.modeAllowed);
            b.writeUtf(r.kitReason, 192); b.writeVarInt(r.tier); b.writeVarInt(r.nextTier); b.writeFloat(r.progress);
            b.writeUtf(r.upgradeReason, 192); b.writeVarInt(r.upgradePrice); b.writeEnum(r.purchaseKind);
            b.writeVarInt(r.price); b.writeUtf(r.purchaseReason, 192); b.writeBoolean(r.pending);
        }
    }
    public static TabletClassSnapshot read(FriendlyByteBuf b) {
        UUID session = b.readUUID(); long request = b.readVarLong(), revision = b.readVarLong(); int coins = b.readVarInt();
        if (request <= 0 || revision <= 0 || coins < 0) throw new IllegalArgumentException("Invalid tablet revision");
        Result operation = readResult(b), pending = readResult(b);
        int count = b.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Too many tablet classes");
        List<Row> rows = new ArrayList<>(count); var seen = new HashSet<String>();
        for (int i = 0; i < count; i++) {
            String key = b.readUtf(32); int action = b.readVarInt(); boolean owned = b.readBoolean(), allowed = b.readBoolean();
            String kit = b.readUtf(192); int tier = b.readVarInt(), next = b.readVarInt(); float progress = b.readFloat();
            String upgrade = b.readUtf(192); int upgradePrice = b.readVarInt(); Kind kind = b.readEnum(Kind.class);
            int price = b.readVarInt(); String purchase = b.readUtf(192); boolean waiting = b.readBoolean();
            if (!seen.add(key) || action < 0 || tier < 0 || tier > 4 || next < -1 || next > 4
                    || !Float.isFinite(progress) || progress < 0 || progress > 1 || price < 0 || upgradePrice < 0)
                throw new IllegalArgumentException("Invalid tablet class");
            rows.add(new Row(key, action, owned, allowed, kit, tier, next, progress, upgrade, upgradePrice, kind, price, purchase, waiting));
        }
        return new TabletClassSnapshot(session, request, revision, coins, operation, pending, rows);
    }
    private static void writeResult(FriendlyByteBuf b, Result r) {
        b.writeBoolean(r != null); if (r == null) return;
        Identity id = r.identity(); b.writeUUID(id.session()); b.writeVarLong(id.requestId()); b.writeEnum(id.kind());
        b.writeUtf(id.classKey(), 32); b.writeVarInt(id.targetTier()); b.writeEnum(r.status()); b.writeVarLong(r.revision()); b.writeUtf(r.message(), 192);
    }
    private static Result readResult(FriendlyByteBuf b) {
        if (!b.readBoolean()) return null;
        Identity id = new Identity(b.readUUID(), b.readVarLong(), b.readEnum(Kind.class), b.readUtf(32), b.readVarInt());
        Status status = b.readEnum(Status.class); long revision = b.readVarLong(); String message = b.readUtf(192);
        if (id.requestId() <= 0 || id.targetTier() < -1 || id.targetTier() > 4 || revision < 0)
            throw new IllegalArgumentException("Invalid tablet operation");
        return new Result(id, status, revision, message);
    }
}
