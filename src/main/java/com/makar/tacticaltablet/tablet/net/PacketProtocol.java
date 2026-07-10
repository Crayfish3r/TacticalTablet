package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.airdrop.net.AirdropNoticePacket;
import com.makar.tacticaltablet.airdrop.net.AirdropSmokeStatePacket;
import com.makar.tacticaltablet.clan.*;
import com.makar.tacticaltablet.prefix.PrefixListPacket;
import net.minecraftforge.network.NetworkDirection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable protocol-31 map; deliberately independent of Forge channel bootstrap. */
public final class PacketProtocol {
    public record Entry(int id, Class<?> packetClass, NetworkDirection direction) { }

    private static final List<Entry> ENTRIES = List.of(
            entry(0, TabletPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(1, TabletStatePacket.class, NetworkDirection.PLAY_TO_CLIENT),
            entry(2, VoteModePacket.class, NetworkDirection.PLAY_TO_SERVER), entry(3, JoinTeamPacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(4, VoteMapPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(5, MapVoteStatePacket.class, NetworkDirection.PLAY_TO_CLIENT),
            entry(6, SetCompetitivePacket.class, NetworkDirection.PLAY_TO_SERVER), entry(7, SetClanWarPacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(8, ContractSelectionStatePacket.class, NetworkDirection.PLAY_TO_CLIENT), entry(9, ContractSelectTargetPacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(10, ContractOpenTrackerPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(11, ContractTrackerStatePacket.class, NetworkDirection.PLAY_TO_CLIENT),
            entry(12, TrackerWatchPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(13, AirdropSmokeStatePacket.class, NetworkDirection.PLAY_TO_CLIENT),
            entry(14, AirdropNoticePacket.class, NetworkDirection.PLAY_TO_CLIENT), entry(15, DeathScreenPacket.class, NetworkDirection.PLAY_TO_CLIENT),
            entry(16, SpectatorCameraSwitchPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(17, SpectatorCameraLockStatePacket.class, NetworkDirection.PLAY_TO_CLIENT),
            entry(18, ClanListPacket.class, NetworkDirection.PLAY_TO_CLIENT), entry(19, ClanCreatePacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(20, ClanJoinRequestPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(21, ClanAcceptJoinPacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(22, ClanLeavePacket.class, NetworkDirection.PLAY_TO_SERVER), entry(23, ClanDisbandPacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(24, ClanRejectJoinPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(25, ClanKickMemberPacket.class, NetworkDirection.PLAY_TO_SERVER),
            entry(26, ClanChangeColorPacket.class, NetworkDirection.PLAY_TO_SERVER), entry(27, PrefixListPacket.class, NetworkDirection.PLAY_TO_CLIENT)
    );

    private PacketProtocol() { }

    public static List<Entry> entries() { return ENTRIES; }

    public static void verify() {
        Set<Integer> ids = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();
        for (Entry entry : ENTRIES) {
            if (!ids.add(entry.id())) throw new IllegalStateException("Duplicate protocol packet ID " + entry.id());
            if (!classes.add(entry.packetClass())) throw new IllegalStateException("Duplicate protocol packet class " + entry.packetClass().getName());
        }
    }

    private static Entry entry(int id, Class<?> packetClass, NetworkDirection direction) {
        return new Entry(id, packetClass, direction);
    }
}
