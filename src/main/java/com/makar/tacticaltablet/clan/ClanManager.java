package com.makar.tacticaltablet.clan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.anticheat.AntiCheatManager;
import com.makar.tacticaltablet.anticheat.Severity;
import com.makar.tacticaltablet.anticheat.ViolationType;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.makar.tacticaltablet.storage.LegacyStorageMigration;
import com.makar.tacticaltablet.storage.TacticalTabletStoragePaths;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.clan.transaction.ClanCreationPayload;
import com.makar.tacticaltablet.clan.transaction.ClanEconomyService;
import com.makar.tacticaltablet.clan.transaction.CreateClanTransaction;
import com.makar.tacticaltablet.clan.transaction.FileTransactionJournal;
import com.makar.tacticaltablet.clan.transaction.RepositoryResult;
import com.makar.tacticaltablet.clan.transaction.TransactionResult;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ClanManager {

    public static final int CREATE_COST = ClanConstants.CREATE_COST;
    public static final int[] ALLOWED_COLORS = ClanConstants.ALLOWED_COLORS;
    public static final String MARINE_CLASS = "marine";
    public static final int MARINE_CLASS_COST = 20;
    public static final int CHANGE_COLOR_COST = 10;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String CLANS_FILE = "clans.json";
    private static final int COLOR_SCHEMA_VERSION = 2;
    private static final long JOIN_REQUEST_COOLDOWN_MS = 5_000L;
    private static final int MAX_PENDING_REQUESTS_PER_PLAYER = 5;
    private static final DateTimeFormatter BROKEN_FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final AtomicFileStore FILE_STORE = new AtomicFileStore();
    private static Path clansFile;
    private static TacticalTabletStoragePaths storagePaths;
    private static ClanStorage storage = new ClanStorage();
    private static final Map<UUID, Long> lastJoinRequestTimes = new HashMap<>();
    private static boolean loaded;

    public static synchronized void sync(ServerPlayer player) {
        if (player == null) return;

        init(player.server);
        PacketHandler.sendToPlayer(player, new ClanListPacket(buildEntries(player)));
    }

    public static synchronized void syncAll(MinecraftServer server) {
        if (server == null) return;

        init(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static Result createClan(ServerPlayer player, String rawName, int color, String rawTag) {
        if (player == null) return Result.INVALID;

        TacticalTabletStoragePaths paths;
        synchronized (ClanManager.class) {
            init(player.server);
            paths = storagePaths;
        }
        ClanEconomyService service = createEconomyService(player.server, paths, () -> {
            ClassXPManager.sync(player);
            syncAll(player.server);
        });
        TransactionResult result = service.createClan(new ClanEconomyService.CreateClanRequest(
                player.getUUID(), player.getGameProfile().getName(), rawName, rawTag, color, ClanConstants.CREATE_COST
        ));
        return result.clanResult();
    }

    public static void recoverCreateClanTransactions(MinecraftServer server) {
        if (server == null) return;
        TacticalTabletStoragePaths paths;
        synchronized (ClanManager.class) {
            init(server);
            paths = storagePaths;
        }
        ClanEconomyService.RecoveryReport report = createEconomyService(server, paths, () -> { })
                .recoverPendingTransactions();
        for (String diagnostic : report.diagnostics()) {
            TacticalTabletMod.LOGGER.warn("Clan transaction recovery diagnostic: {}", diagnostic);
        }
        if (report.rollbackRequired() > 0) {
            TacticalTabletMod.LOGGER.error("Clan transaction recovery found {} rollback-required record(s)",
                    report.rollbackRequired());
        }
        if (report.quarantined() > 0) {
            TacticalTabletMod.LOGGER.error("Clan transaction recovery quarantined {} corrupt record(s)",
                    report.quarantined());
        }
        if (report.archiveFailures() > 0) {
            TacticalTabletMod.LOGGER.error("Clan transaction recovery failed to archive {} committed record(s)",
                    report.archiveFailures());
        }
        if (report.blocked() > 0) {
            TacticalTabletMod.LOGGER.error("Clan transaction recovery blocked {} record(s): {}",
                    report.blocked(), report.diagnostics());
        } else if (report.recoveredCommitted() > 0) {
            TacticalTabletMod.LOGGER.info("Recovered {} pending clan creation transaction(s)", report.committed());
        }
    }

    public static synchronized Result changeClanColor(ServerPlayer owner, String clanId, int color) {
        if (owner == null || clanId == null || clanId.isBlank() || !isAllowedColor(color)) return Result.INVALID;

        init(owner.server);
        ClanData clan = findClan(clanId);
        if (clan == null) return Result.NOT_FOUND;
        if (!owner.getUUID().toString().equals(clan.ownerUuid)) return Result.NOT_OWNER;
        if (clan.color == color) return Result.SUCCESS;
        if (isColorTaken(color, clan.id)) return Result.COLOR_TAKEN;
        if (clan.clanCoins < CHANGE_COLOR_COST) return Result.NOT_ENOUGH_COINS;

        ClanStorage previous = snapshotStorage();
        clan.clanCoins -= CHANGE_COLOR_COST;
        clan.color = color;
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;

        if (MapSetManager.isClanWarSet() && GameStateManager.isRunning(owner.server)) {
            TeamMatchManager.assignClanWarTeams(owner.server);
            TeamMatchManager.applyScoreboardTeams(owner.server);
        }

        syncAll(owner.server);
        return Result.SUCCESS;
    }

    public static synchronized Result requestJoin(ServerPlayer player, String clanId) {
        if (player == null || clanId == null || clanId.isBlank()) return Result.INVALID;

        init(player.server);
        ClanData clan = findClan(clanId);
        if (clan == null) return Result.NOT_FOUND;

        String playerId = player.getUUID().toString();
        long now = System.currentTimeMillis();
        Long lastRequest = lastJoinRequestTimes.get(player.getUUID());
        if (lastRequest != null && now - lastRequest < JOIN_REQUEST_COOLDOWN_MS) {
            AntiCheatManager.record(
                    player,
                    ViolationType.PACKET_SPAM,
                    Severity.MEDIUM,
                    "clan join request cooldown"
            );
            return Result.RATE_LIMITED;
        }
        lastJoinRequestTimes.put(player.getUUID(), now);

        if (findClanByMember(playerId) != null) return Result.ALREADY_IN_CLAN;
        if (containsUuid(clan.pending, playerId)) return Result.ALREADY_PENDING;
        if (countPendingRequests(playerId) >= MAX_PENDING_REQUESTS_PER_PLAYER) {
            AntiCheatManager.record(
                    player,
                    ViolationType.PACKET_SPAM,
                    Severity.MEDIUM,
                    "too many active clan join requests"
            );
            return Result.PENDING_LIMIT_REACHED;
        }
        if (clan.pending.size() >= ClanConstants.MAX_PENDING) return Result.CLAN_PENDING_FULL;

        ClanStorage previous = snapshotStorage();
        clan.pending.add(new ClanPlayerEntry(playerId, player.getGameProfile().getName()));
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;

        ServerPlayer owner = getOnlinePlayer(player.server, clan.ownerUuid);
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("[WAR] " + player.getGameProfile().getName()
                    + " отправил заявку в клан " + clan.name + "."));
            sync(owner);
        }

        sync(player);
        return Result.SUCCESS;
    }

    public static synchronized Result acceptJoin(ServerPlayer owner, String clanId, String applicantUuid) {
        if (owner == null || clanId == null || applicantUuid == null) return Result.INVALID;

        init(owner.server);
        ClanData clan = findClan(clanId);
        if (clan == null) return Result.NOT_FOUND;
        if (!owner.getUUID().toString().equals(clan.ownerUuid)) return Result.NOT_OWNER;

        ClanPlayerEntry applicant = findEntry(clan.pending, applicantUuid);
        if (applicant == null) return Result.NOT_FOUND;
        if (clan.members.size() >= ClanConstants.MAX_MEMBERS) return Result.CLAN_FULL;
        if (findClanByMember(applicantUuid) != null) {
            ClanStorage previous = snapshotStorage();
            removeEntry(clan.pending, applicantUuid);
            if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;
            sync(owner);
            return Result.ALREADY_IN_CLAN;
        }

        ClanStorage previous = snapshotStorage();
        removeEntry(clan.pending, applicantUuid);
        clan.members.add(new ClanPlayerEntry(applicant.uuid, applicant.name));
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;

        ServerPlayer applicantPlayer = getOnlinePlayer(owner.server, applicantUuid);
        if (applicantPlayer != null) {
            applicantPlayer.sendSystemMessage(Component.literal("[WAR] Ваша заявка в клан " + clan.name + " принята."));
            if (MapSetManager.isClanWarSet() && GameStateManager.isRunning(owner.server)) {
                TeamMatchManager.assignClanWarPlayer(owner.server, applicantPlayer);
                TeamMatchManager.applyScoreboardTeams(owner.server);
                applicantPlayer.removeTag("war.eliminated");
                if (!applicantPlayer.getTags().contains("war.playing")) {
                    LobbyManager.moveToLobby(applicantPlayer);
                }
            }
            sync(applicantPlayer);
        }
        sync(owner);
        return Result.SUCCESS;
    }

    public static synchronized Result rejectJoin(ServerPlayer owner, String clanId, String applicantUuid) {
        if (owner == null || clanId == null || applicantUuid == null) return Result.INVALID;

        init(owner.server);
        ClanData clan = findClan(clanId);
        if (clan == null) return Result.NOT_FOUND;
        if (!owner.getUUID().toString().equals(clan.ownerUuid)) return Result.NOT_OWNER;

        ClanPlayerEntry applicant = findEntry(clan.pending, applicantUuid);
        if (applicant == null) return Result.NOT_FOUND;

        ClanStorage previous = snapshotStorage();
        removeEntry(clan.pending, applicantUuid);
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;

        ServerPlayer applicantPlayer = getOnlinePlayer(owner.server, applicantUuid);
        if (applicantPlayer != null) {
            applicantPlayer.sendSystemMessage(Component.literal("[WAR] Заявка в клан " + clan.name + " отклонена."));
            sync(applicantPlayer);
        }
        sync(owner);
        return Result.SUCCESS;
    }

    public static synchronized Result leaveCurrentClan(ServerPlayer player) {
        if (player == null) return Result.INVALID;

        init(player.server);
        if (MapSetManager.isClanWarSet() && GameStateManager.isRunning(player.server)) return Result.CLAN_WAR_LOCKED;

        String playerId = player.getUUID().toString();
        ClanData clan = findClanByMember(playerId);
        if (clan == null) return Result.NOT_FOUND;
        if (playerId.equals(clan.ownerUuid)) return Result.OWNER_CANNOT_LEAVE;

        ClanStorage previous = snapshotStorage();
        removeEntry(clan.members, playerId);
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;
        syncAll(player.server);
        return Result.SUCCESS;
    }

    public static synchronized Result disbandClan(ServerPlayer owner, String clanId) {
        if (owner == null || clanId == null || clanId.isBlank()) return Result.INVALID;

        init(owner.server);
        if (MapSetManager.isClanWarSet() && GameStateManager.isRunning(owner.server)) return Result.CLAN_WAR_LOCKED;

        ClanData clan = findClan(clanId);
        if (clan == null) return Result.NOT_FOUND;
        if (!owner.getUUID().toString().equals(clan.ownerUuid)) return Result.NOT_OWNER;

        ClanStorage previous = snapshotStorage();
        storage.clans.remove(clan);
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;

        for (ClanPlayerEntry member : clan.members) {
            if (member.uuid.equals(owner.getUUID().toString())) continue;
            ServerPlayer player = getOnlinePlayer(owner.server, member.uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal("[WAR] Клан " + clan.name + " распущен."));
            }
        }
        syncAll(owner.server);
        return Result.SUCCESS;
    }

    public static synchronized Result kickMember(ServerPlayer owner, String clanId, String memberUuid) {
        if (owner == null || clanId == null || memberUuid == null) return Result.INVALID;

        init(owner.server);
        if (MapSetManager.isClanWarSet() && GameStateManager.isRunning(owner.server)) return Result.CLAN_WAR_LOCKED;

        ClanData clan = findClan(clanId);
        if (clan == null) return Result.NOT_FOUND;
        if (!owner.getUUID().toString().equals(clan.ownerUuid)) return Result.NOT_OWNER;
        if (memberUuid.equals(clan.ownerUuid)) return Result.CANNOT_KICK_OWNER;

        ClanPlayerEntry member = findEntry(clan.members, memberUuid);
        if (member == null) return Result.NOT_FOUND;

        ClanStorage previous = snapshotStorage();
        removeEntry(clan.members, memberUuid);
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;

        ServerPlayer kicked = getOnlinePlayer(owner.server, memberUuid);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.literal("[WAR] Вас исключили из клана " + clan.name + "."));
            sync(kicked);
        }
        sync(owner);
        return Result.SUCCESS;
    }

    public static synchronized String getClanIdForPlayer(ServerPlayer player) {
        if (player == null) return "";
        init(player.server);
        ClanData clan = findClanByMember(player.getUUID().toString());
        return clan == null ? "" : clan.id;
    }

    public static synchronized String getClanNameForPlayer(ServerPlayer player) {
        if (player == null) return "";
        init(player.server);
        ClanData clan = findClanByMember(player.getUUID().toString());
        return clan == null ? "" : clan.name;
    }

    public static synchronized String getClanIdForPlayerUuid(MinecraftServer server, String playerUuid) {
        if (server == null || playerUuid == null || playerUuid.isBlank()) return "";
        init(server);
        ClanData clan = findClanByMember(playerUuid);
        return clan == null ? "" : clan.id;
    }

    public static synchronized String getClanNameById(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return "";
        init(server);
        ClanData clan = findClan(clanId);
        return clan == null ? "" : clan.name;
    }

    public static synchronized int getClanCoinsById(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return 0;
        init(server);
        ClanData clan = findClan(clanId);
        return clan == null ? 0 : Math.max(0, clan.clanCoins);
    }

    public static synchronized int getClanColorById(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return 0;
        init(server);
        ClanData clan = findClan(clanId);
        return clan == null ? 0 : clan.color;
    }

    public static synchronized boolean addClanCoins(MinecraftServer server, String clanId, int amount) {
        if (server == null || clanId == null || clanId.isBlank() || amount == 0) return false;
        init(server);
        ClanData clan = findClan(clanId);
        if (clan == null) return false;
        ClanStorage previous = snapshotStorage();
        clan.clanCoins = Math.max(0, clan.clanCoins + amount);
        if (!saveOrRestore(previous)) return false;
        syncAll(server);
        return true;
    }

    public static synchronized boolean isClanClassUnlocked(ServerPlayer player, String classKey) {
        if (player == null || classKey == null || classKey.isBlank()) return false;
        init(player.server);
        ClanData clan = findClanByMember(player.getUUID().toString());
        return clan != null && clan.unlockedClasses.contains(classKey);
    }

    public static synchronized Result purchaseClanClass(ServerPlayer player, String classKey) {
        if (player == null || classKey == null || classKey.isBlank()) return Result.INVALID;
        init(player.server);

        if (!MARINE_CLASS.equals(classKey)) return Result.INVALID;

        ClanData clan = findClanByMember(player.getUUID().toString());
        if (clan == null) return Result.NOT_FOUND;
        if (!player.getUUID().toString().equals(clan.ownerUuid)) return Result.NOT_OWNER;
        if (clan.unlockedClasses.contains(classKey)) return Result.ALREADY_IN_CLAN;
        if (clan.clanCoins < MARINE_CLASS_COST) return Result.NOT_ENOUGH_COINS;

        ClanStorage previous = snapshotStorage();
        clan.clanCoins -= MARINE_CLASS_COST;
        clan.unlockedClasses.add(classKey);
        if (!saveOrRestore(previous)) return Result.STORAGE_ERROR;
        syncAll(player.server);
        return Result.SUCCESS;
    }

    public static synchronized boolean isClanOwner(ServerPlayer player) {
        if (player == null) return false;
        init(player.server);
        ClanData clan = findClanByMember(player.getUUID().toString());
        return clan != null && player.getUUID().toString().equals(clan.ownerUuid);
    }

    private static List<ClanListPacket.ClanEntry> buildEntries(ServerPlayer viewer) {
        String viewerId = viewer.getUUID().toString();
        List<ClanListPacket.ClanEntry> entries = new ArrayList<>();
        storage.clans.stream()
                .sorted(Comparator.comparing(clan -> clan.name.toLowerCase(Locale.ROOT)))
                .limit(ClanConstants.MAX_CLANS)
                .forEach(clan -> entries.add(new ClanListPacket.ClanEntry(
                        clan.id,
                        clan.name,
                        clan.tag,
                        clan.color,
                        clan.ownerName,
                        clan.ownerUuid,
                        clan.members.size(),
                        Math.max(0, clan.clanCoins),
                        clan.ownerUuid.equals(viewerId),
                        containsUuid(clan.members, viewerId),
                        containsUuid(clan.pending, viewerId),
                        clan.unlockedClasses.contains(MARINE_CLASS),
                        buildPendingEntries(clan, viewerId),
                        buildMemberEntries(clan)
                )));
        return entries;
    }

    private static List<ClanListPacket.PendingEntry> buildPendingEntries(ClanData clan, String viewerId) {
        List<ClanListPacket.PendingEntry> entries = new ArrayList<>();
        if (!clan.ownerUuid.equals(viewerId)) return entries;

        for (ClanPlayerEntry pending : clan.pending) {
            entries.add(new ClanListPacket.PendingEntry(pending.uuid, pending.name));
            if (entries.size() >= ClanConstants.MAX_PENDING) break;
        }
        return entries;
    }

    private static List<ClanListPacket.MemberEntry> buildMemberEntries(ClanData clan) {
        List<ClanListPacket.MemberEntry> entries = new ArrayList<>();
        for (ClanPlayerEntry member : clan.members) {
            entries.add(new ClanListPacket.MemberEntry(member.uuid, member.name));
            if (entries.size() >= ClanConstants.MAX_MEMBERS) break;
        }
        return entries;
    }

    private static void init(MinecraftServer server) {
        if (loaded || server == null) return;

        storagePaths = TacticalTabletStoragePaths.fromServer(server);
        LegacyStorageMigration.Result migration = LegacyStorageMigration.migrate(
                storagePaths,
                message -> TacticalTabletMod.LOGGER.warn("{}", message)
        );
        if (migration.status() == LegacyStorageMigration.Status.FAILED) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet legacy storage migration failed: {}",
                    migration.markerWrite() == null ? migration.marker() : migration.markerWrite().diagnostic());
        }

        Path dataRoot = storagePaths.dataRoot();
        clansFile = storagePaths.clansFile();

        ClanStorage loadedStorage = new ClanStorage();
        boolean readOk = true;
        try {
            Files.createDirectories(dataRoot);
            if (Files.exists(clansFile)) {
                try (Reader reader = Files.newBufferedReader(clansFile, StandardCharsets.UTF_8)) {
                    ClanStorage read = GSON.fromJson(reader, ClanStorage.class);
                    loadedStorage = read == null ? new ClanStorage() : read;
                }
            }
        } catch (JsonSyntaxException | IOException exception) {
            readOk = false;
            TacticalTabletMod.LOGGER.error("Failed to load Tactical Tablet clans from {}", clansFile, exception);
            backupBrokenFile(clansFile);
        }

        storage = loadedStorage;
        boolean changed = normalizeStorage();
        loaded = true;
        if (readOk && changed) {
            save();
        }
    }

    private static ClanEconomyService createEconomyService(
            MinecraftServer server,
            TacticalTabletStoragePaths paths,
            ClanEconomyService.ClientSynchronizer synchronizer
    ) {
        return new ClanEconomyService(
                new PlayerProgressRepositoryAdapter(server),
                new ClanRepositoryAdapter(server),
                new FileTransactionJournal(paths, FILE_STORE),
                synchronizer,
                UUID::randomUUID,
                Clock.systemUTC(),
                message -> TacticalTabletMod.LOGGER.info("{}", message)
        );
    }

    private static synchronized ClanEconomyService.PreflightResult preflightCreateClan(
            MinecraftServer server,
            ClanEconomyService.CreateClanRequest request,
            String clanId
    ) {
        init(server);
        String name = normalizeName(request.name());
        String tag = normalizeTag(request.tag());
        if (name.length() < 3 || name.length() > ClanConstants.MAX_NAME_LENGTH
                || tag.isBlank() || tag.length() > ClanConstants.MAX_TAG_LENGTH || !isAllowedColor(request.color())) {
            return new ClanEconomyService.PreflightResult(Result.INVALID, null);
        }

        String playerId = request.playerUuid().toString();
        if (findClanByMember(playerId) != null) {
            return new ClanEconomyService.PreflightResult(Result.ALREADY_IN_CLAN, null);
        }
        if (storage.clans.size() >= ClanConstants.MAX_CLANS) {
            return new ClanEconomyService.PreflightResult(Result.CLAN_LIMIT_REACHED, null);
        }

        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedTag = tag.toLowerCase(Locale.ROOT);
        for (ClanData clan : storage.clans) {
            if (clan.name.toLowerCase(Locale.ROOT).equals(normalizedName)
                    || clan.tag.toLowerCase(Locale.ROOT).equals(normalizedTag)) {
                return new ClanEconomyService.PreflightResult(Result.NAME_TAKEN, null);
            }
        }
        if (isColorTaken(request.color(), null)) {
            return new ClanEconomyService.PreflightResult(Result.COLOR_TAKEN, null);
        }

        return new ClanEconomyService.PreflightResult(
                Result.SUCCESS,
                new ClanCreationPayload(
                        clanId,
                        name,
                        tag.toUpperCase(Locale.ROOT),
                        request.color(),
                        request.playerUuid(),
                        request.playerName()
                )
        );
    }

    private static synchronized RepositoryResult applyTransactionClanCreate(
            MinecraftServer server,
            CreateClanTransaction transaction
    ) {
        init(server);
        ClanCreationPayload payload = transaction.clanPayload();
        ClanData existing = findClan(payload.clanId());
        if (existing != null) {
            return clanMatchesPayload(existing, payload)
                    ? RepositoryResult.alreadyApplied()
                    : RepositoryResult.conflict("Clan ID exists with a different payload");
        }

        if (findClanByMember(payload.ownerUuid().toString()) != null) {
            return RepositoryResult.conflict("Clan owner already belongs to another clan");
        }
        for (ClanData clan : storage.clans) {
            if (clan.name.equalsIgnoreCase(payload.name()) || clan.tag.equalsIgnoreCase(payload.tag())) {
                return RepositoryResult.conflict("Clan name or tag already exists");
            }
        }
        if (storage.clans.size() >= ClanConstants.MAX_CLANS || isColorTaken(payload.color(), null)) {
            return RepositoryResult.conflict("Clan limits or colour changed after prepare");
        }

        ClanData clan = new ClanData();
        clan.id = payload.clanId();
        clan.name = payload.name();
        clan.tag = payload.tag();
        clan.color = payload.color();
        clan.ownerUuid = payload.ownerUuid().toString();
        clan.ownerName = payload.ownerName();
        clan.members.add(new ClanPlayerEntry(clan.ownerUuid, clan.ownerName));
        ClanStorage previous = snapshotStorage();
        storage.clans.add(clan);
        if (!saveOrRestore(previous)) {
            return RepositoryResult.failed("Failed to persist clans.json", null);
        }
        return RepositoryResult.applied();
    }

    private static boolean clanMatchesPayload(ClanData clan, ClanCreationPayload payload) {
        return clan.id.equals(payload.clanId())
                && clan.name.equals(payload.name())
                && clan.tag.equals(payload.tag())
                && clan.color == payload.color()
                && clan.ownerUuid.equals(payload.ownerUuid().toString())
                && clan.ownerName.equals(payload.ownerName())
                && clan.members.size() == 1
                && containsUuid(clan.members, payload.ownerUuid().toString())
                && clan.pending.isEmpty()
                && clan.unlockedClasses.isEmpty();
    }

    private static final class PlayerProgressRepositoryAdapter implements ClanEconomyService.PlayerProgressRepository {
        private final MinecraftServer server;

        private PlayerProgressRepositoryAdapter(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public int currentBalance(UUID playerUuid, String playerName) {
            return PlayerProgressManager.getCoins(server, playerUuid, playerName);
        }

        @Override
        public RepositoryResult applyDebit(CreateClanTransaction transaction) {
            return PlayerProgressManager.applyTransactionDebit(server, transaction);
        }

        @Override
        public RepositoryResult verifyDebit(CreateClanTransaction transaction) {
            return PlayerProgressManager.verifyTransactionDebit(server, transaction);
        }
    }

    private static final class ClanRepositoryAdapter implements ClanEconomyService.ClanRepository {
        private final MinecraftServer server;

        private ClanRepositoryAdapter(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public ClanEconomyService.PreflightResult preflight(ClanEconomyService.CreateClanRequest request, String clanId) {
            return preflightCreateClan(server, request, clanId);
        }

        @Override
        public RepositoryResult applyCreate(CreateClanTransaction transaction) {
            return applyTransactionClanCreate(server, transaction);
        }

        @Override
        public RepositoryResult verifyCreate(CreateClanTransaction transaction) {
            synchronized (ClanManager.class) {
                init(server);
                ClanData clan = findClan(transaction.clanId());
                return clan != null && clanMatchesPayload(clan, transaction.clanPayload())
                        ? RepositoryResult.alreadyApplied()
                        : RepositoryResult.conflict("Clan was not durably created with the journal payload");
            }
        }
    }

    private static FileSaveResult save() {
        if (clansFile == null) {
            return FileSaveResult.failure(Path.of(CLANS_FILE), "Clan storage path has not been initialized", null);
        }

        FileSaveResult result = FILE_STORE.write(clansFile, writer -> GSON.toJson(storage, writer));
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet clans to {}: {}",
                    result.target(), result.diagnostic(), result.exception().orElse(null));
        }
        return result;
    }

    private static boolean saveOrRestore(ClanStorage previous) {
        FileSaveResult result = save();
        if (result.status() == FileSaveResult.Status.SUCCESS) {
            return true;
        }
        storage = previous;
        return false;
    }

    private static ClanStorage snapshotStorage() {
        ClanStorage snapshot = GSON.fromJson(GSON.toJson(storage), ClanStorage.class);
        return snapshot == null ? new ClanStorage() : snapshot;
    }

    private static void backupBrokenFile(Path file) {
        if (file == null || !Files.exists(file)) return;

        String timestamp = BROKEN_FILE_TIMESTAMP.format(java.time.LocalDateTime.now());
        Path broken = storagePaths == null
                ? file.resolveSibling(CLANS_FILE + ".broken." + timestamp)
                : storagePaths.backupsDirectory().resolve(CLANS_FILE + ".broken." + timestamp);
        try {
            Files.createDirectories(broken.getParent());
            Files.copy(file, broken, StandardCopyOption.REPLACE_EXISTING);
            TacticalTabletMod.LOGGER.warn("Backed up broken Tactical Tablet clans file to {}", broken);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to back up broken Tactical Tablet clans file {}", file, exception);
        }
    }

    private static boolean normalizeStorage() {
        boolean changed = false;
        if (storage.clans == null) {
            storage.clans = new ArrayList<>();
            changed = true;
        }

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> tags = new HashSet<>();
        int clanCount = 0;
        Iterator<ClanData> iterator = storage.clans.iterator();
        while (iterator.hasNext()) {
            ClanData clan = iterator.next();
            if (clan == null) {
                iterator.remove();
                changed = true;
                continue;
            }

            changed |= migrateLegacyEntries(clan);
            changed |= normalizeClan(clan);

            String nameKey = clan.name.toLowerCase(Locale.ROOT);
            String tagKey = clan.tag.toLowerCase(Locale.ROOT);
            if (!ids.add(clan.id) || !names.add(nameKey) || !tags.add(tagKey)) {
                TacticalTabletMod.LOGGER.warn("Removing duplicate Tactical Tablet clan {} [{}]", clan.name, clan.tag);
                iterator.remove();
                changed = true;
                continue;
            }

            if (++clanCount > ClanConstants.MAX_CLANS) {
                TacticalTabletMod.LOGGER.warn("Removing Tactical Tablet clan {} [{}]: server clan limit is {}", clan.name, clan.tag, ClanConstants.MAX_CLANS);
                iterator.remove();
                changed = true;
                continue;
            }

            if (clan.name.isBlank() || clan.tag.isBlank() || parseUuidOrNull(clan.ownerUuid) == null) {
                TacticalTabletMod.LOGGER.warn("Removing invalid Tactical Tablet clan with id {}", clan.id);
                iterator.remove();
                changed = true;
            }
        }

        if (storage.colorSchemaVersion < COLOR_SCHEMA_VERSION) {
            assignColorsByCreationOrder();
            storage.colorSchemaVersion = COLOR_SCHEMA_VERSION;
            changed = true;
        } else if (normalizeClanColors()) {
            changed = true;
        }
        return changed;
    }

    private static boolean migrateLegacyEntries(ClanData clan) {
        boolean changed = false;
        if (clan.members == null) clan.members = new ArrayList<>();
        if (clan.pending == null) clan.pending = new ArrayList<>();

        if (clan.memberUuids != null) {
            for (int i = 0; i < clan.memberUuids.size(); i++) {
                String uuid = clan.memberUuids.get(i);
                String name = clan.memberNames != null && i < clan.memberNames.size() ? clan.memberNames.get(i) : uuid;
                if (!containsUuid(clan.members, uuid)) {
                    clan.members.add(new ClanPlayerEntry(uuid, name));
                }
            }
            clan.memberUuids = null;
            clan.memberNames = null;
            changed = true;
        }

        if (clan.pendingUuids != null) {
            for (int i = 0; i < clan.pendingUuids.size(); i++) {
                String uuid = clan.pendingUuids.get(i);
                String name = clan.pendingNames != null && i < clan.pendingNames.size() ? clan.pendingNames.get(i) : uuid;
                if (!containsUuid(clan.pending, uuid)) {
                    clan.pending.add(new ClanPlayerEntry(uuid, name));
                }
            }
            clan.pendingUuids = null;
            clan.pendingNames = null;
            changed = true;
        }
        return changed;
    }

    private static boolean normalizeClan(ClanData clan) {
        boolean changed = false;

        if (parseUuidOrNull(clan.id) == null) {
            clan.id = UUID.randomUUID().toString();
            changed = true;
        }
        String normalizedName = normalizeName(clan.name);
        if (!Objects.equals(clan.name, normalizedName)) {
            clan.name = normalizedName;
            changed = true;
        }
        String normalizedTag = normalizeTag(clan.tag).toUpperCase(Locale.ROOT);
        if (!Objects.equals(clan.tag, normalizedTag)) {
            clan.tag = normalizedTag;
            changed = true;
        }
        if (clan.clanCoins < 0) {
            clan.clanCoins = 0;
            changed = true;
        }
        if (clan.unlockedClasses == null) {
            clan.unlockedClasses = new ArrayList<>();
            changed = true;
        }
        if (normalizeUnlockedClasses(clan.unlockedClasses)) {
            changed = true;
        }
        if (clan.ownerName == null || clan.ownerName.isBlank()) {
            clan.ownerName = "Unknown";
            changed = true;
        }

        changed |= normalizeEntries(clan.members);
        changed |= normalizeEntries(clan.pending);
        if (clan.pending.size() > ClanConstants.MAX_PENDING) {
            clan.pending = new ArrayList<>(clan.pending.subList(0, ClanConstants.MAX_PENDING));
            changed = true;
        }

        if (parseUuidOrNull(clan.ownerUuid) != null && !containsUuid(clan.members, clan.ownerUuid)) {
            clan.members.add(0, new ClanPlayerEntry(clan.ownerUuid, clan.ownerName));
            changed = true;
        }
        if (parseUuidOrNull(clan.ownerUuid) == null && !clan.members.isEmpty()) {
            ClanPlayerEntry first = clan.members.get(0);
            clan.ownerUuid = first.uuid;
            clan.ownerName = first.name;
            changed = true;
        }
        if (removeMembersFromPending(clan)) {
            changed = true;
        }
        return changed;
    }

    private static void assignColorsByCreationOrder() {
        for (int i = 0; i < storage.clans.size(); i++) {
            ClanData clan = storage.clans.get(i);
            clan.color = ClanConstants.ALLOWED_COLORS[Math.min(i, ClanConstants.ALLOWED_COLORS.length - 1)];
        }
    }

    private static boolean normalizeClanColors() {
        boolean changed = false;
        Set<Integer> used = new HashSet<>();

        for (ClanData clan : storage.clans) {
            if (isAllowedColor(clan.color) && used.add(clan.color)) {
                continue;
            }

            int color = firstAvailableColor(used);
            if (color != clan.color) {
                clan.color = color;
                changed = true;
            }
            used.add(color);
        }

        return changed;
    }

    private static int firstAvailableColor(Set<Integer> used) {
        for (int allowed : ClanConstants.ALLOWED_COLORS) {
            if (used == null || !used.contains(allowed)) {
                return allowed;
            }
        }
        return ClanConstants.ALLOWED_COLORS[0];
    }

    private static boolean normalizeUnlockedClasses(List<String> classes) {
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        Iterator<String> iterator = classes.iterator();
        while (iterator.hasNext()) {
            String classKey = iterator.next();
            if (!MARINE_CLASS.equals(classKey) || !seen.add(classKey)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean normalizeEntries(List<ClanPlayerEntry> entries) {
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        Iterator<ClanPlayerEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            ClanPlayerEntry entry = iterator.next();
            if (entry == null || parseUuidOrNull(entry.uuid) == null || !seen.add(entry.uuid)) {
                iterator.remove();
                changed = true;
                continue;
            }
            String name = normalizeName(entry.name);
            if (name.isBlank()) name = entry.uuid;
            if (!Objects.equals(entry.name, name)) {
                entry.name = name;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeMembersFromPending(ClanData clan) {
        boolean changed = false;
        Iterator<ClanPlayerEntry> iterator = clan.pending.iterator();
        while (iterator.hasNext()) {
            ClanPlayerEntry pending = iterator.next();
            if (containsUuid(clan.members, pending.uuid)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static ClanData findClan(String clanId) {
        for (ClanData clan : storage.clans) {
            if (clan.id.equals(clanId)) return clan;
        }
        return null;
    }

    private static ClanData findClanByMember(String playerId) {
        for (ClanData clan : storage.clans) {
            if (containsUuid(clan.members, playerId)) return clan;
        }
        return null;
    }

    private static int countPendingRequests(String playerId) {
        int count = 0;
        for (ClanData clan : storage.clans) {
            if (containsUuid(clan.pending, playerId)) {
                count++;
            }
        }
        return count;
    }

    private static ClanPlayerEntry findEntry(List<ClanPlayerEntry> entries, String uuid) {
        for (ClanPlayerEntry entry : entries) {
            if (entry.uuid.equals(uuid)) return entry;
        }
        return null;
    }

    private static boolean containsUuid(List<ClanPlayerEntry> entries, String uuid) {
        return findEntry(entries, uuid) != null;
    }

    private static void removeEntry(List<ClanPlayerEntry> entries, String uuid) {
        entries.removeIf(entry -> entry.uuid.equals(uuid));
    }

    private static ServerPlayer getOnlinePlayer(MinecraftServer server, String uuid) {
        UUID parsed = parseUuidOrNull(uuid);
        return parsed == null ? null : server.getPlayerList().getPlayer(parsed);
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeTag(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[^A-Za-z0-9А-Яа-я]", "");
    }

    private static boolean isAllowedColor(int color) {
        for (int allowed : ClanConstants.ALLOWED_COLORS) {
            if (allowed == color) return true;
        }
        return false;
    }

    private static boolean isColorTaken(int color, String ignoredClanId) {
        for (ClanData clan : storage.clans) {
            if (clan.color != color) continue;
            if (ignoredClanId != null && ignoredClanId.equals(clan.id)) continue;
            return true;
        }
        return false;
    }

    public enum Result {
        SUCCESS,
        INVALID,
        NOT_FOUND,
        NOT_OWNER,
        ALREADY_IN_CLAN,
        ALREADY_PENDING,
        NAME_TAKEN,
        NOT_ENOUGH_COINS,
        OWNER_CANNOT_LEAVE,
        CANNOT_KICK_OWNER,
        CLAN_FULL,
        CLAN_WAR_LOCKED,
        CLAN_LIMIT_REACHED,
        COLOR_TAKEN,
        RATE_LIMITED,
        PENDING_LIMIT_REACHED,
        CLAN_PENDING_FULL,
        STORAGE_ERROR
    }

    private static class ClanStorage {
        private List<ClanData> clans = new ArrayList<>();
        private int colorSchemaVersion;
    }

    private static class ClanData {
        private String id = "";
        private String name = "";
        private String tag = "";
        private int color = ClanConstants.ALLOWED_COLORS[0];
        private int clanCoins;
        private String ownerUuid = "";
        private String ownerName = "";
        private List<ClanPlayerEntry> members = new ArrayList<>();
        private List<ClanPlayerEntry> pending = new ArrayList<>();
        private List<String> unlockedClasses = new ArrayList<>();
        private List<String> memberUuids;
        private List<String> memberNames;
        private List<String> pendingUuids;
        private List<String> pendingNames;
    }

    private static class ClanPlayerEntry {
        private String uuid = "";
        private String name = "";

        private ClanPlayerEntry() {
        }

        private ClanPlayerEntry(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }
}
