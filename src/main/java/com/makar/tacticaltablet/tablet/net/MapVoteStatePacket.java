package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.tablet.client.MapVoteClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MapVoteStatePacket {

    private static final int MAX_MAPS = 16;
    private static final int MAX_MAP_NAME_LENGTH = 64;

    private final boolean active;
    private final boolean openScreen;
    private final boolean operator;
    private final boolean nextSetCompetitive;
    private final boolean nextSetClanWar;
    private final int secondsLeft;
    private final String selectedMap;
    private final List<String> maps;
    private final Map<String, Integer> voteCounts;

    public MapVoteStatePacket(
            boolean active,
            boolean openScreen,
            boolean operator,
            boolean nextSetCompetitive,
            boolean nextSetClanWar,
            int secondsLeft,
            String selectedMap,
            List<String> maps,
            Map<String, Integer> voteCounts
    ) {
        this.active = active;
        this.openScreen = openScreen;
        this.operator = operator;
        this.nextSetCompetitive = nextSetCompetitive;
        this.nextSetClanWar = nextSetClanWar;
        this.secondsLeft = Math.max(0, secondsLeft);
        this.selectedMap = selectedMap == null ? "" : selectedMap;
        this.maps = sanitizeMaps(maps);
        this.voteCounts = sanitizeCounts(this.maps, voteCounts);
    }

    public MapVoteStatePacket(FriendlyByteBuf buf) {
        active = buf.readBoolean();
        openScreen = buf.readBoolean();
        operator = buf.readBoolean();
        nextSetCompetitive = buf.readBoolean();
        nextSetClanWar = buf.readBoolean();
        secondsLeft = Math.max(0, buf.readInt());
        selectedMap = buf.readUtf(MAX_MAP_NAME_LENGTH);

        int size = PacketCodecs.readBoundedIntSize(buf, MAX_MAPS, "map vote pool");

        List<String> decodedMaps = new ArrayList<>();
        Map<String, Integer> decodedCounts = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String map = buf.readUtf(MAX_MAP_NAME_LENGTH);
            decodedMaps.add(map);
            decodedCounts.put(map, Math.max(0, buf.readInt()));
        }
        maps = List.copyOf(decodedMaps);
        voteCounts = Map.copyOf(decodedCounts);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeBoolean(openScreen);
        buf.writeBoolean(operator);
        buf.writeBoolean(nextSetCompetitive);
        buf.writeBoolean(nextSetClanWar);
        buf.writeInt(secondsLeft);
        buf.writeUtf(selectedMap, MAX_MAP_NAME_LENGTH);
        buf.writeInt(maps.size());
        for (String map : maps) {
            buf.writeUtf(map, MAX_MAP_NAME_LENGTH);
            buf.writeInt(voteCounts.getOrDefault(map, 0));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> MapVoteClientState.update(
                active,
                openScreen,
                operator,
                nextSetCompetitive,
                nextSetClanWar,
                secondsLeft,
                selectedMap,
                maps,
                voteCounts
        ));
        context.setPacketHandled(true);
    }

    private static List<String> sanitizeMaps(List<String> input) {
        if (input == null || input.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String map : input) {
            if (result.size() >= MAX_MAPS) break;
            if (map != null && !map.isBlank()) result.add(map.trim());
        }
        return List.copyOf(result);
    }

    private static Map<String, Integer> sanitizeCounts(List<String> maps, Map<String, Integer> input) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String map : maps) {
            result.put(map, Math.max(0, input == null ? 0 : input.getOrDefault(map, 0)));
        }
        return Map.copyOf(result);
    }
}
