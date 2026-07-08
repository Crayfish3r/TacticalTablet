package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class TabletStatePacket {

    private static final int MAX_COOLDOWNS = 32;
    private static final int MAX_CLASS_ENTRIES = 64;
    private static final int MAX_CLASS_KEY_LENGTH = 64;
    private static final int MAX_TEAM_SLOT_ENTRIES = 32;
    private static final int MAX_TEAM_SLOT_KEY_LENGTH = 8;
    private static final int MAX_PLAYER_NAME_LENGTH = 32;

    private final Map<Integer, Long> cooldowns;
    private final boolean kitUsed;
    private final boolean rtpUsed;
    private final long rtpEndTime;
    private final Map<String, Integer> classLevels;
    private final Map<String, Integer> classXP;
    private final Map<String, Integer> classTiers;
    private final Map<String, Integer> unlockedBaseClasses;
    private final Map<String, Integer> purchasedClasses;
    private final boolean gameRunning;
    private final int wins;
    private final int kills;
    private final int deaths;
    private final int matchesPlayed;
    private final int coins;
    private final int careerProgressPercent;
    private final int lives;
    private final int alivePlayers;
    private final int remainingLivesTotal;
    private final int tabletAppearanceTier;
    private final MatchPhase matchPhase;
    private final MatchMode matchMode;
    private final MatchMode selectedVote;
    private final int voteTimeLeft;
    private final int soloVotes;
    private final int duoVotes;
    private final int trioVotes;
    private final int squadVotes;
    private final int voteOptionsMask;
    private final int teamSelectTimeLeft;
    private final int teamSlotSize;
    private final int selectedTeam;
    private final Map<String, String> teamSlots;
    private final boolean competitiveSet;
    private final boolean clanWarSet;

    public TabletStatePacket(
            Map<Integer, Long> cooldowns,
            boolean kitUsed,
            boolean rtpUsed,
            long rtpEndTime,
            Map<String, Integer> classLevels,
            Map<String, Integer> classXP,
            Map<String, Integer> classTiers,
            Map<String, Integer> unlockedBaseClasses,
            Map<String, Integer> purchasedClasses,
            boolean gameRunning,
            int wins,
            int kills,
            int deaths,
            int matchesPlayed,
            int coins,
            int careerProgressPercent,
            int lives,
            int alivePlayers,
            int remainingLivesTotal,
            int tabletAppearanceTier
    ) {
        this(
                cooldowns,
                kitUsed,
                rtpUsed,
                rtpEndTime,
                classLevels,
                classXP,
                classTiers,
                unlockedBaseClasses,
                purchasedClasses,
                gameRunning,
                wins,
                kills,
                deaths,
                matchesPlayed,
                coins,
                careerProgressPercent,
                lives,
                alivePlayers,
                remainingLivesTotal,
                tabletAppearanceTier,
                MatchPhase.WAITING,
                MatchMode.SOLO,
                null,
                0,
                0,
                0,
                0,
                0,
                MatchMode.voteMaskFor(0, false),
                0,
                1,
                -1,
                new HashMap<>(),
                false,
                false
        );
    }

    public TabletStatePacket(
            Map<Integer, Long> cooldowns,
            boolean kitUsed,
            boolean rtpUsed,
            long rtpEndTime,
            Map<String, Integer> classLevels,
            Map<String, Integer> classXP,
            Map<String, Integer> classTiers,
            Map<String, Integer> unlockedBaseClasses,
            Map<String, Integer> purchasedClasses,
            boolean gameRunning,
            int wins,
            int kills,
            int deaths,
            int matchesPlayed,
            int coins,
            int careerProgressPercent,
            int lives,
            int alivePlayers,
            int remainingLivesTotal,
            int tabletAppearanceTier,
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
        this.cooldowns = copyIntLongMap(cooldowns, MAX_COOLDOWNS);
        this.kitUsed = kitUsed;
        this.rtpUsed = rtpUsed;
        this.rtpEndTime = rtpEndTime;
        this.classLevels = copyStringIntMap(classLevels, MAX_CLASS_ENTRIES);
        this.classXP = copyStringIntMap(classXP, MAX_CLASS_ENTRIES);
        this.classTiers = copyStringIntMap(classTiers, MAX_CLASS_ENTRIES);
        this.unlockedBaseClasses = copyStringIntMap(unlockedBaseClasses, MAX_CLASS_ENTRIES);
        this.purchasedClasses = copyStringIntMap(purchasedClasses, MAX_CLASS_ENTRIES);
        this.gameRunning = gameRunning;
        this.wins = wins;
        this.kills = kills;
        this.deaths = deaths;
        this.matchesPlayed = matchesPlayed;
        this.coins = coins;
        this.careerProgressPercent = careerProgressPercent;
        this.lives = Math.max(0, lives);
        this.alivePlayers = Math.max(0, alivePlayers);
        this.remainingLivesTotal = Math.max(0, remainingLivesTotal);
        this.tabletAppearanceTier = Math.max(0, tabletAppearanceTier);
        this.matchPhase = matchPhase == null ? MatchPhase.WAITING : matchPhase;
        this.matchMode = matchMode == null ? MatchMode.SOLO : matchMode;
        this.selectedVote = selectedVote;
        this.voteTimeLeft = Math.max(0, voteTimeLeft);
        this.soloVotes = Math.max(0, soloVotes);
        this.duoVotes = Math.max(0, duoVotes);
        this.trioVotes = Math.max(0, trioVotes);
        this.squadVotes = Math.max(0, squadVotes);
        this.voteOptionsMask = voteOptionsMask;
        this.teamSelectTimeLeft = Math.max(0, teamSelectTimeLeft);
        this.teamSlotSize = Math.max(1, teamSlotSize);
        this.selectedTeam = Math.max(-1, selectedTeam);
        this.teamSlots = copyStringStringMap(teamSlots, MAX_TEAM_SLOT_ENTRIES);
        this.competitiveSet = competitiveSet;
        this.clanWarSet = clanWarSet;
    }

    public TabletStatePacket(
            Map<Integer, Long> cooldowns,
            boolean kitUsed,
            boolean rtpUsed,
            long rtpEndTime,
            Map<String, Integer> classLevels,
            Map<String, Integer> classXP,
            boolean gameRunning
    ) {
        this(
                cooldowns,
                kitUsed,
                rtpUsed,
                rtpEndTime,
                classLevels,
                classXP,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                gameRunning,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    public TabletStatePacket(FriendlyByteBuf buf) {
        this.cooldowns = new HashMap<>();

        int cdSize = readBoundedSize(buf, MAX_COOLDOWNS, "cooldowns");
        for (int i = 0; i < cdSize; i++) {
            cooldowns.put(buf.readInt(), buf.readLong());
        }

        this.kitUsed = buf.readBoolean();
        this.rtpUsed = buf.readBoolean();
        this.rtpEndTime = buf.readLong();

        this.classLevels = new HashMap<>();
        int levelSize = readBoundedSize(buf, MAX_CLASS_ENTRIES, "classLevels");
        for (int i = 0; i < levelSize; i++) {
            classLevels.put(buf.readUtf(MAX_CLASS_KEY_LENGTH), buf.readInt());
        }

        this.classXP = new HashMap<>();
        int xpSize = readBoundedSize(buf, MAX_CLASS_ENTRIES, "classXP");
        for (int i = 0; i < xpSize; i++) {
            classXP.put(buf.readUtf(MAX_CLASS_KEY_LENGTH), buf.readInt());
        }

        this.classTiers = new HashMap<>();
        int tierSize = readBoundedSize(buf, MAX_CLASS_ENTRIES, "classTiers");
        for (int i = 0; i < tierSize; i++) {
            classTiers.put(buf.readUtf(MAX_CLASS_KEY_LENGTH), buf.readInt());
        }

        this.unlockedBaseClasses = new HashMap<>();
        int unlockedSize = readBoundedSize(buf, MAX_CLASS_ENTRIES, "unlockedBaseClasses");
        for (int i = 0; i < unlockedSize; i++) {
            unlockedBaseClasses.put(buf.readUtf(MAX_CLASS_KEY_LENGTH), buf.readInt());
        }

        this.purchasedClasses = new HashMap<>();
        int purchasedSize = readBoundedSize(buf, MAX_CLASS_ENTRIES, "purchasedClasses");
        for (int i = 0; i < purchasedSize; i++) {
            purchasedClasses.put(buf.readUtf(MAX_CLASS_KEY_LENGTH), buf.readInt());
        }

        this.gameRunning = buf.readBoolean();
        this.wins = buf.readInt();
        this.kills = buf.readInt();
        this.deaths = buf.readInt();
        this.matchesPlayed = buf.readInt();
        this.coins = buf.readInt();
        this.careerProgressPercent = buf.readInt();
        this.lives = buf.readInt();
        this.alivePlayers = buf.readInt();
        this.remainingLivesTotal = buf.readInt();
        this.tabletAppearanceTier = buf.readInt();
        this.matchPhase = MatchPhase.byId(buf.readByte());
        this.matchMode = MatchMode.byId(buf.readByte());
        int selectedVoteId = buf.readByte();
        this.selectedVote = selectedVoteId < 0 ? null : MatchMode.byId(selectedVoteId);
        this.voteTimeLeft = buf.readInt();
        this.soloVotes = buf.readInt();
        this.duoVotes = buf.readInt();
        this.trioVotes = buf.readInt();
        this.squadVotes = buf.readInt();
        this.voteOptionsMask = buf.readInt();
        this.teamSelectTimeLeft = buf.readInt();
        this.teamSlotSize = buf.readInt();
        this.selectedTeam = buf.readInt();

        this.teamSlots = new HashMap<>();
        int teamSlotEntries = readBoundedSize(buf, MAX_TEAM_SLOT_ENTRIES, "teamSlots");
        for (int i = 0; i < teamSlotEntries; i++) {
            teamSlots.put(
                    buf.readUtf(MAX_TEAM_SLOT_KEY_LENGTH),
                    buf.readUtf(MAX_PLAYER_NAME_LENGTH)
            );
        }
        this.competitiveSet = buf.readBoolean();
        this.clanWarSet = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(cooldowns.size());
        for (var entry : cooldowns.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeLong(entry.getValue());
        }

        buf.writeBoolean(kitUsed);
        buf.writeBoolean(rtpUsed);
        buf.writeLong(rtpEndTime);

        buf.writeInt(classLevels.size());
        for (var entry : classLevels.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(classXP.size());
        for (var entry : classXP.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(classTiers.size());
        for (var entry : classTiers.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(unlockedBaseClasses.size());
        for (var entry : unlockedBaseClasses.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(purchasedClasses.size());
        for (var entry : purchasedClasses.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeBoolean(gameRunning);
        buf.writeInt(wins);
        buf.writeInt(kills);
        buf.writeInt(deaths);
        buf.writeInt(matchesPlayed);
        buf.writeInt(coins);
        buf.writeInt(careerProgressPercent);
        buf.writeInt(lives);
        buf.writeInt(alivePlayers);
        buf.writeInt(remainingLivesTotal);
        buf.writeInt(tabletAppearanceTier);
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
            boolean hadKitUsed = TabletClientState.isKitUsed();
            boolean hadRtpUsed = TabletClientState.isRtpUsed();

            TabletClientState.update(cooldowns, kitUsed, rtpUsed, rtpEndTime);
            TabletClientState.updateLevels(classLevels);
            TabletClientState.updateXP(classXP);
            TabletClientState.updateClassTiers(classTiers);
            TabletClientState.updateUnlockedBaseClasses(unlockedBaseClasses);
            TabletClientState.updatePurchasedClasses(purchasedClasses);
            TabletClientState.updateGameRunning(gameRunning);
            TabletClientState.updateProfileStats(wins, kills, deaths, matchesPlayed, coins, careerProgressPercent);
            TabletClientState.updateLives(lives);
            TabletClientState.updateMatchCounts(alivePlayers, remainingLivesTotal);
            TabletClientState.updateTabletAppearanceTier(tabletAppearanceTier);
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

            if (kitUsed && rtpUsed && hadKitUsed && !hadRtpUsed) {
                TabletClientState.requestClose();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static int readBoundedSize(FriendlyByteBuf buf, int max, String field) {
        int size = buf.readInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid " + field + " size: " + size + " max=" + max);
        }
        return size;
    }

    private static Map<Integer, Long> copyIntLongMap(Map<Integer, Long> input, int maxEntries) {
        Map<Integer, Long> result = new HashMap<>();
        if (input == null || input.isEmpty()) return result;

        for (var entry : input.entrySet()) {
            if (result.size() >= maxEntries) break;
            if (entry.getKey() == null || entry.getValue() == null) continue;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map<String, Integer> copyStringIntMap(Map<String, Integer> input, int maxEntries) {
        Map<String, Integer> result = new HashMap<>();
        if (input == null || input.isEmpty()) return result;

        for (var entry : input.entrySet()) {
            if (result.size() >= maxEntries) break;
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey();
            if (key.length() > MAX_CLASS_KEY_LENGTH) {
                key = key.substring(0, MAX_CLASS_KEY_LENGTH);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Map<String, String> copyStringStringMap(Map<String, String> input, int maxEntries) {
        Map<String, String> result = new HashMap<>();
        if (input == null || input.isEmpty()) return result;

        for (var entry : input.entrySet()) {
            if (result.size() >= maxEntries) break;
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey();
            if (key.length() > MAX_TEAM_SLOT_KEY_LENGTH) {
                key = key.substring(0, MAX_TEAM_SLOT_KEY_LENGTH);
            }

            String value = entry.getValue();
            if (value.length() > MAX_PLAYER_NAME_LENGTH) {
                value = value.substring(0, MAX_PLAYER_NAME_LENGTH);
            }
            result.put(key, value);
        }
        return result;
    }
}

