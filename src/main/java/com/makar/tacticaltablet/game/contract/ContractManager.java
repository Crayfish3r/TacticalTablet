package com.makar.tacticaltablet.game.contract;

import com.makar.tacticaltablet.core.ModItems;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;
import com.makar.tacticaltablet.tablet.net.ContractTrackerStatePacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContractManager {

    public static final int SELECTION_SECONDS = 60;
    public static final int MIN_PLAYERS = 3;
    public static final int SIGNAL_SECONDS = 15;
    public static final int TARGET_AREA_RADIUS = 25;
    private static final int MAX_SELECTION_TARGETS = 16;
    private static final int ZONE_GRID_SIZE = 50;
    private static final int TARGET_AREA_OFFSET = 20;
    private static final int TRACKER_COOLDOWN_SECONDS = 3;
    private static final long TRACKER_COOLDOWN_MS = TRACKER_COOLDOWN_SECONDS * 1000L;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Contract> contractsByOwner = new HashMap<>();
    private static final Map<UUID, Set<UUID>> targetToOwners = new HashMap<>();
    private static final Map<UUID, Long> pickCooldownUntil = new HashMap<>();
    private static final Set<UUID> debugArmorStands = new HashSet<>();
    private static final Set<UUID> trackerViewers = ConcurrentHashMap.newKeySet();
    private static int selectionSecondsLeft = 0;
    private static int signalSecondsLeft = SIGNAL_SECONDS;
    private static boolean selectionActive = false;
    private static boolean soloDebugEnabled = false;
    private static int tickCounter = 0;

    private ContractManager() {
    }

    public static void onMatchStart(MinecraftServer server) {
        clearContracts();
        pickCooldownUntil.clear();
        trackerViewers.clear();
        signalSecondsLeft = SIGNAL_SECONDS;
        tickCounter = 0;

        boolean enabled = server != null
                && (GameStateManager.onlinePlayers(server) >= MIN_PLAYERS || soloDebugEnabled);

        selectionActive = enabled;
        selectionSecondsLeft = enabled ? SELECTION_SECONDS : 0;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !GameStateManager.isRunning(server)) return;
        if (GameStateManager.getMatchPhase() != MatchPhase.RUNNING) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        if (selectionActive) {
            if (selectionSecondsLeft > 0) {
                selectionSecondsLeft--;
            }
            if (selectionSecondsLeft <= 0) {
                selectionActive = false;
                removeUnclaimedSelectionTrackers(server);
            }
            syncSelectionAll(server);
        }

        if (contractsByOwner.isEmpty()) return;

        if (signalSecondsLeft > 0) {
            signalSecondsLeft--;
        }

        if (signalSecondsLeft <= 0) {
            updateTargetAreas(server);
            signalSecondsLeft = SIGNAL_SECONDS;
            syncTrackers(server);
        }
    }

    public static void reset(MinecraftServer server) {
        if (server != null) {
            removeAllTrackers(server);
            removeDebugArmorStands(server);
        }
        clearContracts();
        debugArmorStands.clear();
        pickCooldownUntil.clear();
        trackerViewers.clear();
        selectionActive = false;
        selectionSecondsLeft = 0;
        signalSecondsLeft = SIGNAL_SECONDS;
        tickCounter = 0;
    }

    public static void setSoloDebugEnabled(boolean enabled) {
        soloDebugEnabled = enabled;
    }

    public static boolean isSoloDebugEnabled() {
        return soloDebugEnabled;
    }

    public static void forceStartSelection(MinecraftServer server) {
        if (server == null || !GameStateManager.isRunning(server)) return;
        selectionActive = true;
        selectionSecondsLeft = SELECTION_SECONDS;
        giveSelectionTrackers(server);
        syncSelectionAll(server);
    }

    public static boolean selectTarget(ServerPlayer owner, UUID targetUuid) {
        if (owner == null || targetUuid == null) return false;
        MinecraftServer server = owner.server;

        if (!selectionActive || selectionSecondsLeft <= 0) {
            owner.sendSystemMessage(Component.literal("[WAR] Выбор контракта уже закрыт."));
            syncSelection(owner);
            return false;
        }

        if (!LivesManager.canContinueMatch(owner)) {
            owner.sendSystemMessage(Component.literal("[WAR] Нельзя выбрать контракт после выбывания."));
            syncSelection(owner);
            return false;
        }

        if (contractsByOwner.containsKey(owner.getUUID())) {
            owner.sendSystemMessage(Component.literal("[WAR] У тебя уже есть активный контракт."));
            syncSelection(owner);
            return false;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (!isValidTarget(owner, target)) {
            owner.sendSystemMessage(Component.literal("[WAR] Эта цель недоступна."));
            syncSelection(owner);
            return false;
        }

        ContractDifficulty difficulty = difficultyFor(target);
        if (PlayerProgressManager.getCoins(owner) < difficulty.price()) {
            owner.sendSystemMessage(Component.literal("[WAR] Не хватает монет для контракта. Нужно " + difficulty.price() + "."));
            syncSelection(owner);
            return false;
        }

        PlayerProgressManager.addCoins(owner, -difficulty.price());
        PlayerProgressManager.savePlayer(owner);

        Contract contract = new Contract(
                owner.getUUID(),
                target.getUUID(),
                target.getName().getString(),
                selectedClassName(target),
                difficulty,
                PlayerProgressManager.getKills(target),
                PlayerProgressManager.getWins(target),
                PlayerProgressManager.getCareerProgressPercent(target)
        );
        putContract(contract);
        updateTargetArea(server, contract);
        giveTracker(owner);

        owner.sendSystemMessage(Component.literal("[WAR] Контракт принят: цель " + contract.targetName()
                + ". Награда: " + difficulty.reward() + " монет."));
        target.sendSystemMessage(Component.literal("[WAR] Игрок \"" + owner.getName().getString()
                + "\" начал охоту за вами в этой игре."));
        refreshContractAccess(server);
        sendTrackerState(owner, true);
        return true;
    }

    public static void onTrackerUsed(ServerPlayer player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        long cooldownUntil = pickCooldownUntil.getOrDefault(player.getUUID(), 0L);
        if (cooldownUntil > now) {
            long secondsLeft = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            player.sendSystemMessage(Component.literal("[WAR] РўСЂРµРєРµСЂ РїРµСЂРµР·Р°СЂСЏР¶Р°РµС‚СЃСЏ: " + secondsLeft + " СЃ."));
            syncSelection(player);
            return;
        }
        pickCooldownUntil.put(player.getUUID(), now + TRACKER_COOLDOWN_MS);

        if (!contractsByOwner.containsKey(player.getUUID())) {
            if (canSelectContract(player)) {
                syncSelection(player);
                sendTrackerState(player, true);
                return;
            }
            if (!visibleContracts(player).isEmpty()) {
                sendTrackerState(player, true);
                return;
            }
            removeTracker(player);
            player.sendSystemMessage(Component.literal("[WAR] У тебя нет активного контракта."));
            return;
        }
        sendTrackerState(player, true);
    }

    public static boolean createDebugSelfContract(ServerPlayer player) {
        if (player == null) return false;
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("Доступно только администраторам"));
            return false;
        }

        ContractDifficulty difficulty = difficultyFor(player);
        Contract contract = new Contract(
                player.getUUID(),
                player.getUUID(),
                player.getName().getString(),
                selectedClassName(player),
                difficulty,
                PlayerProgressManager.getKills(player),
                PlayerProgressManager.getWins(player),
                PlayerProgressManager.getCareerProgressPercent(player)
        );
        putContract(contract);
        updateTargetArea(player.server, contract);
        giveTracker(player);
        sendTrackerState(player, true);
        syncSelection(player);
        return true;
    }

    public static boolean createDebugArmorStandContract(ServerPlayer player) {
        if (player == null) return false;
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("Доступно только администраторам"));
            return false;
        }

        ServerLevel level = GameStateManager.getOverworld(player.server);
        if (level == null) return false;

        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) return false;

        WorldBorder border = level.getWorldBorder();
        int radius = Math.max(20, (int) Math.round(border.getSize() / 2.0D) - 10);
        int x = (int) Math.round(border.getCenterX()) + RANDOM.nextInt(radius * 2 + 1) - radius;
        int z = (int) Math.round(border.getCenterZ()) + RANDOM.nextInt(radius * 2 + 1) - radius;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        stand.moveTo(x + 0.5D, y, z + 0.5D, RANDOM.nextFloat() * 360.0F, 0.0F);
        stand.setCustomName(Component.literal("Тестовая цель контракта"));
        stand.setCustomNameVisible(true);
        stand.setNoGravity(false);
        stand.setInvulnerable(false);
        level.addFreshEntity(stand);
        debugArmorStands.add(stand.getUUID());

        ContractDifficulty difficulty = ContractDifficulty.MEDIUM;
        Contract contract = new Contract(
                player.getUUID(),
                stand.getUUID(),
                "Тестовая цель",
                "Armor Stand",
                difficulty,
                0,
                0,
                50
        );
        putContract(contract);
        contract.setTargetArea(x, z);
        giveTracker(player);
        sendTrackerState(player, true);
        syncSelection(player);
        player.sendSystemMessage(Component.literal("[WAR] Тестовая цель создана: X " + x + " Z " + z + "."));
        return true;
    }

    public static void onPlayerKilled(ServerPlayer victim, ServerPlayer killer) {
        if (victim == null) return;
        MinecraftServer server = victim.server;
        Set<UUID> toRemove = new HashSet<>();

        for (Contract contract : new ArrayList<>(contractsByOwner.values())) {
            if (contract.targetUuid().equals(victim.getUUID())) {
                if (killer != null && isContractHunter(contract, killer.getUUID())) {
                    rewardTeam(
                            server,
                            contract.ownerUuid(),
                            contract.difficulty().reward(),
                            "Контракт выполнен"
                    );
                } else if (isSuicide(victim, killer)) {
                    ServerPlayer payer = server.getPlayerList().getPlayer(contract.ownerUuid());
                    if (payer != null) {
                        PlayerProgressManager.addCoins(payer, contract.difficulty().price());
                        payer.sendSystemMessage(Component.literal(
                                "[WAR] Цель контракта погибла сама. Возврат: "
                                        + contract.difficulty().price() + " монет."
                        ));
                    }
                } else {
                    notifyTeam(server, contract.ownerUuid(), "Цель выбыла. Контракт закрыт.");
                }
                toRemove.add(contract.ownerUuid());
                continue;
            }

            if (isContractHunter(contract, victim.getUUID()) && !hasAliveContractHunter(server, contract)) {
                notifyTeam(server, contract.ownerUuid(), "Контракт провален: команда выбыла из матча.");
                rewardSurvivingTarget(server, contract, "Твоя команда пережила контракт");
                toRemove.add(contract.ownerUuid());
            }
        }

        for (UUID ownerUuid : toRemove) {
            removeContract(ownerUuid);
        }
        if (!toRemove.isEmpty()) {
            refreshContractAccess(server);
        }
    }
    public static void syncSelection(ServerPlayer player) {
        if (player == null) return;
        PacketHandler.sendToPlayer(player, selectionState(player));
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player == null) return;
        MinecraftServer server = player.server;
        UUID uuid = player.getUUID();
        trackerViewers.remove(uuid);
        removeTracker(player);

        Set<UUID> toRemove = new HashSet<>();
        for (Contract contract : new ArrayList<>(contractsByOwner.values())) {
            if (contract.targetUuid().equals(uuid)) {
                notifyTeam(server, contract.ownerUuid(), "Цель вышла с сервера. Контракт закрыт.");
                toRemove.add(contract.ownerUuid());
                continue;
            }

            if (isContractHunter(contract, uuid)
                    && !hasAliveContractHunterExcluding(server, contract, uuid)) {
                rewardSurvivingTarget(server, contract, "Твоя команда пережила контракт");
                toRemove.add(contract.ownerUuid());
            }
        }

        for (UUID ownerUuid : toRemove) {
            removeContract(ownerUuid);
        }
        refreshContractAccess(server);
    }
    public static void finishMatch(MinecraftServer server) {
        if (server == null) return;
        if (contractsByOwner.isEmpty()) {
            trackerViewers.clear();
            return;
        }

        for (Contract contract : new ArrayList<>(contractsByOwner.values())) {
            notifyTeam(server, contract.ownerUuid(), "Контракт закрыт: матч завершён.");
            rewardSurvivingTarget(server, contract, "Твоя команда пережила контракт до конца матча");
        }

        clearContracts();
        selectionActive = false;
        selectionSecondsLeft = 0;
        trackerViewers.clear();
        refreshContractAccess(server);
    }
    public static void resetPickCooldowns(MinecraftServer server) {
        pickCooldownUntil.clear();
        syncSelectionAll(server);
    }

    public static void resetPickCooldown(ServerPlayer player) {
        if (player == null) return;
        pickCooldownUntil.remove(player.getUUID());
        syncSelection(player);
    }

    public static void syncSelectionAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncSelection(player);
        }
    }

    public static ContractSelectionStatePacket selectionState(ServerPlayer viewer) {
        List<ContractSelectionStatePacket.TargetEntry> entries = new ArrayList<>();
        boolean canSelect = canSelectContract(viewer);

        if (viewer != null && canSelect) {
            List<ServerPlayer> targets = new ArrayList<>(viewer.server.getPlayerList().getPlayers());
            targets.sort(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));
            for (ServerPlayer target : targets) {
                if (entries.size() >= MAX_SELECTION_TARGETS) break;
                if (!isValidTarget(viewer, target)) continue;
                ContractDifficulty difficulty = difficultyFor(target);
                entries.add(new ContractSelectionStatePacket.TargetEntry(
                        target.getUUID(),
                        target.getName().getString(),
                        selectedClassName(target),
                        PlayerProgressManager.getKills(target),
                        PlayerProgressManager.getWins(target),
                        PlayerProgressManager.getCareerProgressPercent(target),
                        difficulty.ordinal(),
                        difficulty.price(),
                        difficulty.reward()
                ));
            }
        }

        return new ContractSelectionStatePacket(
                selectionActive,
                selectionSecondsLeft,
                cooldownLeftMs(viewer),
                contractsByOwner.containsKey(viewer == null ? null : viewer.getUUID()),
                GameStateManager.getCurrentMode() != null,
                entries
        );
    }

    public static void sendTrackerState(ServerPlayer player, boolean open) {
        if (player == null) return;
        PacketHandler.sendToPlayer(player, trackerState(player, open));
    }

    public static void addTrackerViewer(ServerPlayer player) {
        if (player == null) return;
        trackerViewers.add(player.getUUID());
        sendTrackerState(player, false);
    }

    public static void removeTrackerViewer(ServerPlayer player) {
        if (player == null) return;
        trackerViewers.remove(player.getUUID());
    }

    public static void giveSelectionTrackerIfAvailable(ServerPlayer player) {
        if (player == null || !canSelectContract(player)) return;
        giveTracker(player);
        syncSelection(player);
    }

    private static void giveSelectionTrackers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            giveSelectionTrackerIfAvailable(player);
        }
    }

    private static ContractTrackerStatePacket trackerState(ServerPlayer player, boolean open) {
        List<Contract> contracts = visibleContracts(player);
        ServerLevel overworld = GameStateManager.getOverworld(player.server);
        WorldBorder border = overworld == null ? null : overworld.getWorldBorder();
        int zoneCenterX = border == null ? 0 : (int) Math.round(border.getCenterX());
        int zoneCenterZ = border == null ? 0 : (int) Math.round(border.getCenterZ());
        int zoneRadius = border == null ? 180 : Math.max(1, (int) Math.round(border.getSize() / 2.0D));

        if (canSelectContract(player) || contracts.isEmpty()) {
            return ContractTrackerStatePacket.empty(open, zoneCenterX, zoneCenterZ, zoneRadius);
        }

        List<ContractTrackerStatePacket.TargetEntry> targets = new ArrayList<>();
        for (Contract contract : contracts) {
            targets.add(new ContractTrackerStatePacket.TargetEntry(
                    contract.targetName(),
                    contract.targetClass(),
                    contract.targetKills(),
                    contract.targetWins(),
                    contract.targetCareerPercent(),
                    contract.difficulty().ordinal(),
                    contract.difficulty().price(),
                    contract.difficulty().reward(),
                    contract.targetAreaX(),
                    contract.targetAreaZ(),
                    TARGET_AREA_RADIUS
            ));
        }

        return new ContractTrackerStatePacket(
                true,
                open,
                zoneCenterX,
                zoneCenterZ,
                zoneRadius,
                (int) Math.round(player.getX()),
                (int) Math.round(player.getZ()),
                signalSecondsLeft,
                targets
        );
    }

    private static boolean canSelectContract(ServerPlayer player) {
        return player != null
                && selectionActive
                && selectionSecondsLeft > 0
                && (GameStateManager.isRunning(player.server) || GameStateManager.isStartTransitionPlayerSetup())
                && LivesManager.canContinueMatch(player)
                && !contractsByOwner.containsKey(player.getUUID());
    }

    private static long cooldownLeftMs(ServerPlayer player) {
        if (player == null) return 0L;
        return Math.max(0L, pickCooldownUntil.getOrDefault(player.getUUID(), 0L) - System.currentTimeMillis());
    }

    private static boolean isValidTarget(ServerPlayer owner, ServerPlayer target) {
        return owner != null
                && target != null
                && !owner.getUUID().equals(target.getUUID())
                && !TeamMatchManager.areTeammates(owner, target)
                && !isTargetClaimedByTeam(owner, target.getUUID())
                && LivesManager.isAliveParticipant(target);
    }

    private static boolean isTargetClaimedByTeam(ServerPlayer owner, UUID targetUuid) {
        if (owner == null || targetUuid == null) return false;
        for (Contract contract : contractsByOwner.values()) {
            if (contract.targetUuid().equals(targetUuid)
                    && sameContractTeam(owner.getUUID(), contract.ownerUuid())) {
                return true;
            }
        }
        return false;
    }

    private static List<Contract> visibleContracts(ServerPlayer player) {
        if (player == null) return List.of();

        List<Contract> result = new ArrayList<>();
        for (Contract contract : contractsByOwner.values()) {
            if (sameContractTeam(player.getUUID(), contract.ownerUuid())) {
                result.add(contract);
            }
        }
        result.sort(Comparator.comparing(Contract::targetName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static boolean sameContractTeam(UUID first, UUID second) {
        if (first == null || second == null) return false;
        if (first.equals(second)) return true;
        return GameStateManager.getCurrentMode() != null
                && GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.areTeammates(first, second);
    }

    private static List<ServerPlayer> contractTeam(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) return List.of();

        if (GameStateManager.getCurrentMode() != null && GameStateManager.getCurrentMode().isTeamMode()) {
            List<ServerPlayer> team = TeamMatchManager.getOnlineTeamMembers(server, playerUuid);
            if (!team.isEmpty()) return team;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        return player == null ? List.of() : List.of(player);
    }

    private static ContractDifficulty difficultyFor(ServerPlayer target) {
        return ContractDifficulty.forCareerPercent(PlayerProgressManager.getCareerProgressPercent(target));
    }

    private static void updateTargetAreas(MinecraftServer server) {
        for (Contract contract : contractsByOwner.values()) {
            updateTargetArea(server, contract);
        }
    }

    private static boolean isSuicide(ServerPlayer victim, ServerPlayer killer) {
        return killer == null || killer.getUUID().equals(victim.getUUID());
    }

    private static boolean isContractHunter(Contract contract, UUID playerUuid) {
        return contract != null
                && playerUuid != null
                && sameContractTeam(contract.ownerUuid(), playerUuid);
    }

    private static boolean hasAliveContractHunter(MinecraftServer server, Contract contract) {
        for (ServerPlayer player : contractTeam(server, contract.ownerUuid())) {
            if (LivesManager.isAliveParticipant(player)) return true;
        }
        return false;
    }

    private static boolean hasAliveContractHunterExcluding(
            MinecraftServer server,
            Contract contract,
            UUID excludedUuid
    ) {
        for (ServerPlayer player : contractTeam(server, contract.ownerUuid())) {
            if (player.getUUID().equals(excludedUuid)) continue;
            if (LivesManager.isAliveParticipant(player)) return true;
        }
        return false;
    }

    private static void notifyTeam(MinecraftServer server, UUID playerUuid, String message) {
        for (ServerPlayer member : contractTeam(server, playerUuid)) {
            member.sendSystemMessage(Component.literal("[WAR] " + message));
        }
    }

    private static void rewardTeam(MinecraftServer server, UUID playerUuid, int totalReward, String reason) {
        List<ServerPlayer> recipients = new ArrayList<>(contractTeam(server, playerUuid));
        if (recipients.isEmpty() || totalReward <= 0) return;

        recipients.sort(Comparator.comparing(ServerPlayer::getStringUUID));
        int baseShare = totalReward / recipients.size();
        int remainder = totalReward % recipients.size();

        for (int i = 0; i < recipients.size(); i++) {
            ServerPlayer recipient = recipients.get(i);
            int share = baseShare + (i < remainder ? 1 : 0);
            if (share <= 0) continue;

            PlayerProgressManager.addCoins(recipient, share);
            recipient.sendSystemMessage(Component.literal(
                    "[WAR] " + reason + ". Твоя доля: " + share + " монет."
            ));
        }
    }

    private static void putContract(Contract contract) {
        if (contract == null) return;
        removeContract(contract.ownerUuid());
        contractsByOwner.put(contract.ownerUuid(), contract);
        targetToOwners.computeIfAbsent(contract.targetUuid(), ignored -> new HashSet<>()).add(contract.ownerUuid());
    }

    private static Contract removeContract(UUID ownerUuid) {
        if (ownerUuid == null) return null;
        Contract removed = contractsByOwner.remove(ownerUuid);
        if (removed != null) {
            Set<UUID> owners = targetToOwners.get(removed.targetUuid());
            if (owners != null) {
                owners.remove(ownerUuid);
                if (owners.isEmpty()) {
                    targetToOwners.remove(removed.targetUuid());
                }
            }
        }
        return removed;
    }

    private static void clearContracts() {
        contractsByOwner.clear();
        targetToOwners.clear();
    }

    private static void rewardSurvivingTarget(MinecraftServer server, Contract contract, String reason) {
        ServerPlayer target = server.getPlayerList().getPlayer(contract.targetUuid());
        if (target == null || !LivesManager.isAliveParticipant(target)) return;

        int reward = Math.max(1, contract.difficulty().reward() / 2);
        rewardTeam(server, contract.targetUuid(), reward, reason);
    }

    private static void updateTargetArea(MinecraftServer server, Contract contract) {
        ServerPlayer target = server.getPlayerList().getPlayer(contract.targetUuid());
        if (target == null) return;

        int roundedX = Math.round(target.getBlockX() / (float) ZONE_GRID_SIZE) * ZONE_GRID_SIZE;
        int roundedZ = Math.round(target.getBlockZ() / (float) ZONE_GRID_SIZE) * ZONE_GRID_SIZE;
        contract.setTargetArea(
                roundedX + RANDOM.nextInt(TARGET_AREA_OFFSET * 2 + 1) - TARGET_AREA_OFFSET,
                roundedZ + RANDOM.nextInt(TARGET_AREA_OFFSET * 2 + 1) - TARGET_AREA_OFFSET
        );
    }

    private static void syncTrackers(MinecraftServer server) {
        trackerViewers.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!visibleContracts(player).isEmpty() || trackerViewers.contains(player.getUUID())) {
                ensureTracker(player);
                sendTrackerState(player, false);
            }
        }
    }

    private static void removeUnclaimedSelectionTrackers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (visibleContracts(player).isEmpty()) {
                removeTracker(player);
            }
        }
    }

    public static void ensureTracker(ServerPlayer player) {
        if (player == null || visibleContracts(player).isEmpty()) return;
        if (hasTracker(player)) return;
        giveTracker(player);
    }

    private static void refreshContractAccess(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean needsTracker = canSelectContract(player) || !visibleContracts(player).isEmpty();
            if (needsTracker) {
                giveTracker(player);
            } else {
                removeTracker(player);
            }
            syncSelection(player);
            if (trackerViewers.contains(player.getUUID())) {
                sendTrackerState(player, false);
            }
        }
    }

    private static void giveTracker(ServerPlayer player) {
        if (player == null || hasTracker(player)) return;
        ItemStack stack = new ItemStack(ModItems.CONTRACT_TRACKER.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        InventoryManager.syncInventory(player);
    }

    private static boolean hasTracker(ServerPlayer player) {
        return !findTracker(player).isEmpty();
    }

    /** Server-side packet authorization hook; never trust the client tracker screen. */
    public static boolean hasTrackerItem(ServerPlayer player) {
        return hasTracker(player);
    }

    private static ItemStack findTracker(ServerPlayer player) {
        if (player == null) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ModItems.CONTRACT_TRACKER.get()) {
                return player.getInventory().getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void removeTracker(ServerPlayer player) {
        if (player == null) return;
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() == ModItems.CONTRACT_TRACKER.get()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ModItems.CONTRACT_TRACKER.get()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        InventoryManager.syncInventory(player);
    }

    private static void removeAllTrackers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeTracker(player);
        }
    }

    private static void removeDebugArmorStands(MinecraftServer server) {
        ServerLevel level = GameStateManager.getOverworld(server);
        if (level == null || debugArmorStands.isEmpty()) return;

        for (UUID uuid : new HashSet<>(debugArmorStands)) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        debugArmorStands.clear();
    }

    private static String selectedClassName(ServerPlayer player) {
        String selected = PlayerTabletState.getSelectedClass(player);
        if (selected == null || selected.isBlank()) {
            return "Не выбран";
        }
        return displayClassName(selected);
    }

    private static String displayClassName(String kit) {
        if ("boomguy".equals(kit)) return "Подрывник";
        if ("dream".equals(kit)) return "Дрим";
        if ("rpgtrooper".equals(kit)) return "РПГ-боец";
        if ("droneoperator".equals(kit)) return "Оператор дрона";
        if ("machinegunner".equals(kit)) return "Пулемётчик";
        if ("mortarman".equals(kit)) return "Миномётчик";
        if ("stormtrooper".equals(kit)) return "Штурмовик";
        if ("sniper".equals(kit)) return "Снайпер";
        if ("scout".equals(kit)) return "Разведчик";
        if ("tagilla".equals(kit)) return "Тагилла";
        if ("blackops".equals(kit)) return "Спецназ";
        if ("cowboy".equals(kit)) return "Ковбой";
        if ("solider".equals(kit)) return "Солдат";
        if ("rebel".equals(kit)) return "Повстанец";
        if ("saboteur".equals(kit)) return "Диверсант";
        if ("killer".equals(kit)) return "Киллер";
        if ("miniboss".equals(kit)) return "Мини-Босс";
        if ("shahed".equals(kit)) return "Шахед оп.";
        if ("krot".equals(kit)) return "Крот";
        if ("medic".equals(kit)) return "Медик";
        if ("microwave".equals(kit)) return "Микровэйв";
        if ("railgunner".equals(kit)) return "Рэйл-ганнер";
        return kit;
    }

    private static final class Contract {
        private final UUID ownerUuid;
        private final UUID targetUuid;
        private final String targetName;
        private final String targetClass;
        private final ContractDifficulty difficulty;
        private final int targetKills;
        private final int targetWins;
        private final int targetCareerPercent;
        private int targetAreaX;
        private int targetAreaZ;

        private Contract(
                UUID ownerUuid,
                UUID targetUuid,
                String targetName,
                String targetClass,
                ContractDifficulty difficulty,
                int targetKills,
                int targetWins,
                int targetCareerPercent
        ) {
            this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
            this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid");
            this.targetName = Objects.requireNonNull(targetName, "targetName");
            this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
            this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
            this.targetKills = targetKills;
            this.targetWins = targetWins;
            this.targetCareerPercent = targetCareerPercent;
        }

        private UUID ownerUuid() {
            return ownerUuid;
        }

        private UUID targetUuid() {
            return targetUuid;
        }

        private String targetName() {
            return targetName;
        }

        private String targetClass() {
            return targetClass;
        }

        private ContractDifficulty difficulty() {
            return difficulty;
        }

        private int targetKills() {
            return targetKills;
        }

        private int targetWins() {
            return targetWins;
        }

        private int targetCareerPercent() {
            return targetCareerPercent;
        }

        private int targetAreaX() {
            return targetAreaX;
        }

        private int targetAreaZ() {
            return targetAreaZ;
        }

        private void setTargetArea(int x, int z) {
            this.targetAreaX = x;
            this.targetAreaZ = z;
        }
    }
}
