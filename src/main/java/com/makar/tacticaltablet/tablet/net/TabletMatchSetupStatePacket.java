package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public record TabletMatchSetupStatePacket(
        MatchPhase matchPhase,
        MatchMode matchMode,
        MatchMode selectedVote,
        int voteTimeLeft,
        int soloVotes,
        int duoVotes,
        int trioVotes,
        int squadVotes,
        int voteOptionsMask,
        int teamSelectTimeLeft,
        int teamSlotSize,
        int selectedTeam,
        Map<String, String> teamSlots,
        boolean competitiveSet,
        boolean clanWarSet
) {
    static final int MAX_TEAM_SLOT_ENTRIES = 32;
    static final int MAX_TEAM_SLOT_KEY_LENGTH = 8;
    static final int MAX_PLAYER_NAME_LENGTH = 32;

    public TabletMatchSetupStatePacket {
        matchPhase = matchPhase == null ? MatchPhase.WAITING : matchPhase;
        matchMode = matchMode == null ? MatchMode.SOLO : matchMode;
        voteTimeLeft = Math.max(0, voteTimeLeft);
        soloVotes = Math.max(0, soloVotes);
        duoVotes = Math.max(0, duoVotes);
        trioVotes = Math.max(0, trioVotes);
        squadVotes = Math.max(0, squadVotes);
        teamSelectTimeLeft = Math.max(0, teamSelectTimeLeft);
        teamSlotSize = Math.max(1, teamSlotSize);
        selectedTeam = Math.max(-1, selectedTeam);
        teamSlots = boundedTeamSlots(teamSlots);
    }

    public TabletMatchSetupStatePacket(FriendlyByteBuf buf) {
        this(
                PacketCodecs.readEnumOrdinal(buf, MatchPhase.values(), "match phase"),
                PacketCodecs.readEnumOrdinal(buf, MatchMode.values(), "match mode"),
                PacketCodecs.readOptionalEnumOrdinal(buf, MatchMode.values(), "selected vote"),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                readTeamSlots(buf),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(matchPhase.ordinal());
        buf.writeByte(matchMode.ordinal());
        buf.writeByte(selectedVote == null ? -1 : selectedVote.ordinal());
        buf.writeInt(voteTimeLeft);
        buf.writeInt(soloVotes);
        buf.writeInt(duoVotes);
        buf.writeInt(trioVotes);
        buf.writeInt(squadVotes);
        buf.writeInt(voteOptionsMask);
        buf.writeInt(teamSelectTimeLeft);
        buf.writeInt(teamSlotSize);
        buf.writeInt(selectedTeam);
        buf.writeInt(teamSlots.size());
        for (var entry : teamSlots.entrySet()) {
            buf.writeUtf(entry.getKey(), MAX_TEAM_SLOT_KEY_LENGTH);
            buf.writeUtf(entry.getValue(), MAX_PLAYER_NAME_LENGTH);
        }
        buf.writeBoolean(competitiveSet);
        buf.writeBoolean(clanWarSet);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            TabletClientState.updateMatchSetup(
                    matchPhase,
                    matchMode,
                    selectedVote,
                    voteTimeLeft,
                    soloVotes,
                    duoVotes,
                    trioVotes,
                    squadVotes,
                    voteOptionsMask,
                    teamSelectTimeLeft,
                    teamSlotSize,
                    selectedTeam,
                    teamSlots
            );
            TabletClientState.updateCompetitiveSet(competitiveSet);
            TabletClientState.updateClanWarSet(clanWarSet);
        });
        ctx.get().setPacketHandled(true);
    }

    private static Map<String, String> readTeamSlots(FriendlyByteBuf buf) {
        int size = PacketCodecs.readBoundedIntSize(buf, MAX_TEAM_SLOT_ENTRIES, "teamSlots");
        Map<String, String> slots = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            slots.put(
                    buf.readUtf(MAX_TEAM_SLOT_KEY_LENGTH),
                    buf.readUtf(MAX_PLAYER_NAME_LENGTH)
            );
        }
        return slots;
    }

    private static Map<String, String> boundedTeamSlots(Map<String, String> input) {
        Map<String, String> result = new HashMap<>();
        if (input == null || input.isEmpty()) return Map.of();

        for (var entry : input.entrySet()) {
            if (result.size() >= MAX_TEAM_SLOT_ENTRIES) break;
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = truncate(entry.getKey(), MAX_TEAM_SLOT_KEY_LENGTH);
            String playerName = truncate(entry.getValue(), MAX_PLAYER_NAME_LENGTH);
            result.put(key, playerName);
        }
        return Map.copyOf(result);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
