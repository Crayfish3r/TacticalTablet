package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.chaos.ChaosSetManager;
import com.makar.tacticaltablet.tablet.client.ChaosClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ChaosStatePacket {
    private static final int MAX_CLASSES = 3;
    private static final int MAX_ID = 64;
    private final boolean active;
    private final int gameNumber;
    private final List<String> offered;
    private final Map<String, Integer> tiers;
    private final Set<String> spent;
    private final String selected;
    private final boolean requiresSelection;

    public ChaosStatePacket(ChaosSetManager.Snapshot snapshot) {
        active = snapshot.active();
        gameNumber = Math.max(0, snapshot.gameNumber());
        offered = sanitize(snapshot.offered());
        Map<String, Integer> safeTiers = new LinkedHashMap<>();
        for (String classId : offered) safeTiers.put(classId, snapshot.tiers().getOrDefault(classId, 0));
        tiers = Map.copyOf(safeTiers);
        spent = Set.copyOf(sanitize(new ArrayList<>(snapshot.spent())));
        selected = snapshot.selected() == null ? "" : snapshot.selected();
        requiresSelection = snapshot.requiresSelection();
    }

    public ChaosStatePacket(FriendlyByteBuf buf) {
        active = buf.readBoolean();
        gameNumber = Math.max(0, buf.readInt());
        int offeredSize = PacketCodecs.readBoundedIntSize(buf, MAX_CLASSES, "chaos offered classes");
        List<String> decoded = new ArrayList<>();
        Map<String, Integer> decodedTiers = new LinkedHashMap<>();
        for (int i = 0; i < offeredSize; i++) {
            String classId = buf.readUtf(MAX_ID);
            decoded.add(classId);
            decodedTiers.put(classId, buf.readInt());
        }
        offered = List.copyOf(decoded);
        tiers = Map.copyOf(decodedTiers);
        int spentSize = PacketCodecs.readBoundedIntSize(buf, MAX_CLASSES, "chaos spent classes");
        Set<String> decodedSpent = new LinkedHashSet<>();
        for (int i = 0; i < spentSize; i++) decodedSpent.add(buf.readUtf(MAX_ID));
        spent = Set.copyOf(decodedSpent);
        selected = buf.readUtf(MAX_ID);
        requiresSelection = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeInt(gameNumber);
        buf.writeInt(offered.size());
        for (String id : offered) {
            buf.writeUtf(id, MAX_ID);
            buf.writeInt(tiers.getOrDefault(id, 0));
        }
        buf.writeInt(spent.size());
        for (String id : spent) buf.writeUtf(id, MAX_ID);
        buf.writeUtf(selected, MAX_ID);
        buf.writeBoolean(requiresSelection);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ChaosClientState.update(active, gameNumber, offered, tiers, spent, selected, requiresSelection));
        context.setPacketHandled(true);
    }

    private static List<String> sanitize(List<String> values) {
        if (values == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (result.size() >= MAX_CLASSES) break;
            if (value != null && !value.isBlank()) result.add(value.length() > MAX_ID ? value.substring(0, MAX_ID) : value);
        }
        return List.copyOf(result);
    }
}
