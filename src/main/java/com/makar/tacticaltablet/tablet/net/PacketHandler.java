package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.airdrop.net.AirdropNoticePacket;
import com.makar.tacticaltablet.airdrop.net.AirdropSmokeStatePacket;
import com.makar.tacticaltablet.clan.*;
import com.makar.tacticaltablet.prefix.PrefixListPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Stable Tactical Tablet protocol registry. IDs are wire-compatible with protocol 31. */
public final class PacketHandler {
    /** No wire format or packet semantics changed in this hardening patch. */
    public static final String VERSION = "31";

    public static final int TABLET = 0, TABLET_STATE = 1, VOTE_MODE = 2, JOIN_TEAM = 3, VOTE_MAP = 4,
            MAP_VOTE_STATE = 5, SET_COMPETITIVE = 6, SET_CLAN_WAR = 7, CONTRACT_SELECTION_STATE = 8,
            CONTRACT_SELECT_TARGET = 9, CONTRACT_OPEN_TRACKER = 10, CONTRACT_TRACKER_STATE = 11,
            TRACKER_WATCH = 12, AIRDROP_SMOKE_STATE = 13, AIRDROP_NOTICE = 14, DEATH_SCREEN = 15,
            SPECTATOR_CAMERA_SWITCH = 16, SPECTATOR_CAMERA_LOCK_STATE = 17, CLAN_LIST = 18,
            CLAN_CREATE = 19, CLAN_JOIN_REQUEST = 20, CLAN_ACCEPT_JOIN = 21, CLAN_LEAVE = 22,
            CLAN_DISBAND = 23, CLAN_REJECT_JOIN = 24, CLAN_KICK_MEMBER = 25,
            CLAN_CHANGE_COLOR = 26, PREFIX_LIST = 27;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("tacticaltablet", "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static final C2SRateLimiter C2S_LIMITER = new C2SRateLimiter();
    private static final Map<Class<?>, Registration> REGISTRATIONS = new LinkedHashMap<>();
    private static boolean registered;

    public enum C2SAction {
        TABLET(3, 2_000_000_000L), VOTE(6, 1_000_000_000L), CLAN_MUTATION(4, 2_000_000_000L),
        CONTRACT_SELECT(3, 2_000_000_000L), TRACKER(8, 1_000_000_000L),
        SPECTATOR(8, 1_000_000_000L), ADMIN_MAP(3, 2_000_000_000L);
        private final C2SRateLimiter.Budget budget;
        C2SAction(int count, long windowNanos) { this.budget = new C2SRateLimiter.Budget(count, windowNanos); }
    }

    public record Registration(int id, Class<?> packetClass, NetworkDirection direction) { }

    private PacketHandler() { }

    public static synchronized void register() {
        if (registered) return;
        register(TABLET, TabletPacket.class, TabletPacket::encode, TabletPacket::new, TabletPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(TABLET_STATE, TabletStatePacket.class, TabletStatePacket::encode, TabletStatePacket::new, TabletStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(VOTE_MODE, VoteModePacket.class, VoteModePacket::encode, VoteModePacket::new, VoteModePacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(JOIN_TEAM, JoinTeamPacket.class, JoinTeamPacket::encode, JoinTeamPacket::new, JoinTeamPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(VOTE_MAP, VoteMapPacket.class, VoteMapPacket::encode, VoteMapPacket::new, VoteMapPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(MAP_VOTE_STATE, MapVoteStatePacket.class, MapVoteStatePacket::encode, MapVoteStatePacket::new, MapVoteStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(SET_COMPETITIVE, SetCompetitivePacket.class, SetCompetitivePacket::encode, SetCompetitivePacket::new, SetCompetitivePacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(SET_CLAN_WAR, SetClanWarPacket.class, SetClanWarPacket::encode, SetClanWarPacket::new, SetClanWarPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CONTRACT_SELECTION_STATE, ContractSelectionStatePacket.class, ContractSelectionStatePacket::encode, ContractSelectionStatePacket::new, ContractSelectionStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(CONTRACT_SELECT_TARGET, ContractSelectTargetPacket.class, ContractSelectTargetPacket::encode, ContractSelectTargetPacket::new, ContractSelectTargetPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CONTRACT_OPEN_TRACKER, ContractOpenTrackerPacket.class, ContractOpenTrackerPacket::encode, ContractOpenTrackerPacket::new, ContractOpenTrackerPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CONTRACT_TRACKER_STATE, ContractTrackerStatePacket.class, ContractTrackerStatePacket::encode, ContractTrackerStatePacket::new, ContractTrackerStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(TRACKER_WATCH, TrackerWatchPacket.class, TrackerWatchPacket::encode, TrackerWatchPacket::new, TrackerWatchPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(AIRDROP_SMOKE_STATE, AirdropSmokeStatePacket.class, AirdropSmokeStatePacket::encode, AirdropSmokeStatePacket::new, AirdropSmokeStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(AIRDROP_NOTICE, AirdropNoticePacket.class, AirdropNoticePacket::encode, AirdropNoticePacket::new, AirdropNoticePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(DEATH_SCREEN, DeathScreenPacket.class, DeathScreenPacket::encode, DeathScreenPacket::new, DeathScreenPacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(SPECTATOR_CAMERA_SWITCH, SpectatorCameraSwitchPacket.class, SpectatorCameraSwitchPacket::encode, SpectatorCameraSwitchPacket::new, SpectatorCameraSwitchPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(SPECTATOR_CAMERA_LOCK_STATE, SpectatorCameraLockStatePacket.class, SpectatorCameraLockStatePacket::encode, SpectatorCameraLockStatePacket::new, SpectatorCameraLockStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(CLAN_LIST, ClanListPacket.class, ClanListPacket::encode, ClanListPacket::new, ClanListPacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        register(CLAN_CREATE, ClanCreatePacket.class, ClanCreatePacket::encode, ClanCreatePacket::new, ClanCreatePacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_JOIN_REQUEST, ClanJoinRequestPacket.class, ClanJoinRequestPacket::encode, ClanJoinRequestPacket::new, ClanJoinRequestPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_ACCEPT_JOIN, ClanAcceptJoinPacket.class, ClanAcceptJoinPacket::encode, ClanAcceptJoinPacket::new, ClanAcceptJoinPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_LEAVE, ClanLeavePacket.class, ClanLeavePacket::encode, ClanLeavePacket::new, ClanLeavePacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_DISBAND, ClanDisbandPacket.class, ClanDisbandPacket::encode, ClanDisbandPacket::new, ClanDisbandPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_REJECT_JOIN, ClanRejectJoinPacket.class, ClanRejectJoinPacket::encode, ClanRejectJoinPacket::new, ClanRejectJoinPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_KICK_MEMBER, ClanKickMemberPacket.class, ClanKickMemberPacket::encode, ClanKickMemberPacket::new, ClanKickMemberPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(CLAN_CHANGE_COLOR, ClanChangeColorPacket.class, ClanChangeColorPacket::encode, ClanChangeColorPacket::new, ClanChangeColorPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        register(PREFIX_LIST, PrefixListPacket.class, PrefixListPacket::encode, PrefixListPacket::new, PrefixListPacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        verifyUniqueIds();
        registered = true;
    }

    /** Typed registration helper: no raw casts and no implicit sequence-derived ID. */
    private static <T> void register(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, T> decoder,
                                     BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                                     NetworkDirection direction) {
        if (REGISTRATIONS.containsKey(type) || REGISTRATIONS.values().stream().anyMatch(entry -> entry.id() == id)) {
            throw new IllegalStateException("Duplicate packet registration id=" + id + " type=" + type.getName());
        }
        Function<FriendlyByteBuf, T> safeDecoder = buf -> {
            try { return decoder.apply(buf); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("Malformed " + type.getSimpleName() + " payload", exception); }
        };
        INSTANCE.registerMessage(id, type, encoder, safeDecoder, handler, Optional.of(direction));
        REGISTRATIONS.put(type, new Registration(id, type, direction));
    }

    public static boolean allowC2S(ServerPlayer player, C2SAction action) {
        return player != null && C2S_LIMITER.tryAcquire(player.getUUID(), action.name(), action.budget);
    }
    public static void clearC2SRateLimits(ServerPlayer player) { if (player != null) C2S_LIMITER.clear(player.getUUID()); }
    public static void clearAllC2SRateLimits() { C2S_LIMITER.clearAll(); }
    public static void clearExpiredC2SRateLimits() { C2S_LIMITER.clearExpired(2_000_000_000L); }
    public static Map<Class<?>, Registration> protocolMap() { return Map.copyOf(REGISTRATIONS); }
    public static boolean isExpectedDirection(Class<?> type, NetworkDirection direction) {
        Registration registration = REGISTRATIONS.get(type);
        return registration != null && registration.direction() == direction;
    }
    public static void verifyUniqueIds() {
        PacketProtocol.verify();
        Set<Integer> ids = new java.util.HashSet<>();
        for (Registration entry : REGISTRATIONS.values()) if (!ids.add(entry.id())) throw new IllegalStateException("Duplicate packet ID " + entry.id());
        if (REGISTRATIONS.size() != PacketProtocol.entries().size()) {
            throw new IllegalStateException("Packet registration count does not match protocol metadata");
        }
        for (PacketProtocol.Entry expected : PacketProtocol.entries()) {
            Registration actual = REGISTRATIONS.get(expected.packetClass());
            if (actual == null || actual.id() != expected.id() || actual.direction() != expected.direction()) {
                throw new IllegalStateException("Packet registration does not match protocol metadata for " + expected.packetClass().getName());
            }
        }
    }
    public static boolean isRegistered() { return registered; }
    public static void sendToServer(Object msg) { INSTANCE.sendToServer(msg); }
    public static void sendToPlayer(ServerPlayer player, Object msg) { if (player != null) INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg); }
}
