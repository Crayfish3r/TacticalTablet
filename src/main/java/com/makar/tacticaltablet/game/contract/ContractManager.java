package com.makar.tacticaltablet.game.contract;

import com.makar.tacticaltablet.core.ModItems;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.lives.LivesManager;
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
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class ContractManager {

    public static final int SELECTION_SECONDS = 60;
    public static final int MIN_PLAYERS = 3;
    public static final int SIGNAL_SECONDS = 30;
    public static final int TARGET_AREA_RADIUS = 25;
    private static final int MAX_SELECTION_TARGETS = 16;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Contract> contractsByOwner = new HashMap<>();
    private static final Map<UUID, Long> pickCooldownUntil = new HashMap<>();
    private static final Set<UUID> trackerOwners = new HashSet<>();
    private static final Set<UUID> debugArmorStands = new HashSet<>();
    private static int selectionSecondsLeft = 0;
    private static int signalSecondsLeft = SIGNAL_SECONDS;
    private static boolean selectionActive = false;
    private static boolean soloDebugEnabled = false;
    private static int tickCounter = 0;

    private ContractManager() {
    }

    public static void onMatchStart(MinecraftServer server) {
        contractsByOwner.clear();
        trackerOwners.clear();
        pickCooldownUntil.clear();
        signalSecondsLeft = SIGNAL_SECONDS;
        tickCounter = 0;

        boolean enabled = server != null
                && GameStateManager.getCurrentMode() == MatchMode.SOLO
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
        contractsByOwner.clear();
        trackerOwners.clear();
        debugArmorStands.clear();
        pickCooldownUntil.clear();
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
        if (GameStateManager.getCurrentMode() != MatchMode.SOLO) return;
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

        if (GameStateManager.getCurrentMode() != MatchMode.SOLO) {
            owner.sendSystemMessage(Component.literal("[WAR] Контракты пока доступны только в соло."));
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
        contractsByOwner.put(owner.getUUID(), contract);
        updateTargetArea(server, contract);
        giveTracker(owner);

        owner.sendSystemMessage(Component.literal("[WAR] Контракт принят: цель " + contract.targetName()
                + ". Награда: " + difficulty.reward() + " монет."));
        target.sendSystemMessage(Component.literal("[WAR] Игрок \"" + owner.getName().getString()
                + "\" начал охоту за вами в этой игре."));
        syncSelection(owner);
        syncSelection(target);
        sendTrackerState(owner, true);
        return true;
    }

    public static void onTrackerUsed(ServerPlayer player) {
        if (player == null) return;
        if (!contractsByOwner.containsKey(player.getUUID())) {
            if (canSelectContract(player)) {
                syncSelection(player);
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
        contractsByOwner.put(player.getUUID(), contract);
        updateTargetArea(player.server, contract);
        giveTracker(player);
        sendTrackerState(player, true);
        syncSelection(player);
        return true;
    }

    public static boolean createDebugArmorStandContract(ServerPlayer player) {
        if (player == null) return false;

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
        contractsByOwner.put(player.getUUID(), contract);
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

        List<UUID> toRemove = new ArrayList<>();
        for (Contract contract : contractsByOwner.values()) {
            if (contract.ownerUuid().equals(victim.getUUID())) {
                ServerPlayer owner = server.getPlayerList().getPlayer(contract.ownerUuid());
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("[WAR] Контракт провален: ты выбыл из матча."));
                    removeTracker(owner);
                }
                rewardSurvivingTarget(server, contract, "Ты пережил контракт: охотник выбыл");
                toRemove.add(contract.ownerUuid());
                continue;
            }

            if (!contract.targetUuid().equals(victim.getUUID())) continue;

            ServerPlayer owner = server.getPlayerList().getPlayer(contract.ownerUuid());
            boolean completed = killer != null && killer.getUUID().equals(contract.ownerUuid());
            if (completed && owner != null) {
                PlayerProgressManager.addCoins(owner, contract.difficulty().reward());
                PlayerProgressManager.savePlayer(owner);
                owner.sendSystemMessage(Component.literal("[WAR] Контракт выполнен. Награда: "
                        + contract.difficulty().reward() + " монет."));
            } else if (isSuicide(victim, killer) && owner != null) {
                PlayerProgressManager.addCoins(owner, contract.difficulty().price());
                PlayerProgressManager.savePlayer(owner);
                owner.sendSystemMessage(Component.literal("[WAR] Цель контракта погибла сама. Компенсация: "
                        + contract.difficulty().price() + " монет."));
            } else if (owner != null) {
                owner.sendSystemMessage(Component.literal("[WAR] Цель контракта выбыла. Контракт закрыт."));
            }
            if (owner != null) {
                removeTracker(owner);
            }
            toRemove.add(contract.ownerUuid());
        }

        for (UUID uuid : toRemove) {
            contractsByOwner.remove(uuid);
        }
    }

    public static void syncSelection(ServerPlayer player) {
        if (player == null) return;
        PacketHandler.sendToPlayer(player, selectionState(player));
    }

    public static void finishMatch(MinecraftServer server) {
        if (server == null || contractsByOwner.isEmpty()) return;

        for (Contract contract : new ArrayList<>(contractsByOwner.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(contract.ownerUuid());
            if (owner != null) {
                removeTracker(owner);
                owner.sendSystemMessage(Component.literal("[WAR] Контракт закрыт: матч завершён."));
            }
            rewardSurvivingTarget(server, contract, "Ты пережил контракт до конца матча");
        }

        contractsByOwner.clear();
        syncSelectionAll(server);
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
                0L,
                contractsByOwner.containsKey(viewer == null ? null : viewer.getUUID()),
                GameStateManager.getCurrentMode() == MatchMode.SOLO,
                entries
        );
    }

    public static void sendTrackerState(ServerPlayer player, boolean open) {
        if (player == null) return;
        PacketHandler.sendToPlayer(player, trackerState(player, open));
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
        Contract contract = contractsByOwner.get(player.getUUID());
        ServerLevel overworld = GameStateManager.getOverworld(player.server);
        WorldBorder border = overworld == null ? null : overworld.getWorldBorder();
        int zoneCenterX = border == null ? 0 : (int) Math.round(border.getCenterX());
        int zoneCenterZ = border == null ? 0 : (int) Math.round(border.getCenterZ());
        int zoneRadius = border == null ? 180 : Math.max(1, (int) Math.round(border.getSize() / 2.0D));

        if (contract == null) {
            return ContractTrackerStatePacket.empty(open, zoneCenterX, zoneCenterZ, zoneRadius);
        }

        return new ContractTrackerStatePacket(
                true,
                open,
                contract.targetName(),
                contract.targetClass(),
                contract.targetKills(),
                contract.targetWins(),
                contract.targetCareerPercent(),
                contract.difficulty().ordinal(),
                contract.difficulty().price(),
                contract.difficulty().reward(),
                zoneCenterX,
                zoneCenterZ,
                zoneRadius,
                (int) Math.round(player.getX()),
                (int) Math.round(player.getZ()),
                contract.targetAreaX(),
                contract.targetAreaZ(),
                TARGET_AREA_RADIUS,
                signalSecondsLeft
        );
    }

    private static boolean canSelectContract(ServerPlayer player) {
        return player != null
                && selectionActive
                && selectionSecondsLeft > 0
                && GameStateManager.isRunning(player.server)
                && GameStateManager.getCurrentMode() == MatchMode.SOLO
                && LivesManager.canContinueMatch(player)
                && !contractsByOwner.containsKey(player.getUUID());
    }

    private static boolean isValidTarget(ServerPlayer owner, ServerPlayer target) {
        return owner != null
                && target != null
                && !owner.getUUID().equals(target.getUUID())
                && LivesManager.isAliveParticipant(target);
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

    private static void rewardSurvivingTarget(MinecraftServer server, Contract contract, String reason) {
        ServerPlayer target = server.getPlayerList().getPlayer(contract.targetUuid());
        if (target == null || !LivesManager.isAliveParticipant(target)) return;

        int reward = Math.max(1, contract.difficulty().reward() / 2);
        PlayerProgressManager.addCoins(target, reward);
        PlayerProgressManager.savePlayer(target);
        target.sendSystemMessage(Component.literal("[WAR] " + reason + ". Награда: " + reward + " монет."));
    }

    private static void updateTargetArea(MinecraftServer server, Contract contract) {
        ServerPlayer target = server.getPlayerList().getPlayer(contract.targetUuid());
        if (target == null) return;

        int roundedX = Math.round(target.getBlockX() / 50.0F) * 50;
        int roundedZ = Math.round(target.getBlockZ() / 50.0F) * 50;
        contract.setTargetArea(roundedX + RANDOM.nextInt(41) - 20, roundedZ + RANDOM.nextInt(41) - 20);
    }

    private static void syncTrackers(MinecraftServer server) {
        for (UUID ownerUuid : contractsByOwner.keySet()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                ensureTracker(owner);
                sendTrackerState(owner, false);
            }
        }
    }

    private static void removeUnclaimedSelectionTrackers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!contractsByOwner.containsKey(player.getUUID())) {
                removeTracker(player);
            }
        }
    }

    public static void ensureTracker(ServerPlayer player) {
        if (player == null || !contractsByOwner.containsKey(player.getUUID())) return;
        if (hasTracker(player)) return;
        giveTracker(player);
    }

    private static void giveTracker(ServerPlayer player) {
        if (player == null || hasTracker(player)) return;
        ItemStack stack = new ItemStack(ModItems.CONTRACT_TRACKER.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        trackerOwners.add(player.getUUID());
        InventoryManager.syncInventory(player);
    }

    private static boolean hasTracker(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == ModItems.CONTRACT_TRACKER.get()) {
                return true;
            }
        }
        return false;
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
        trackerOwners.remove(player.getUUID());
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
            this.ownerUuid = ownerUuid;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.targetClass = targetClass;
            this.difficulty = difficulty;
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
