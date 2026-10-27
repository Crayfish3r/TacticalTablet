package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** All methods run on the server thread; no entity or world work is sent to background tasks. */
public final class NpcEncounterManager {
    private static NpcEncounterManager current;
    private static MinecraftServer preparedServer;
    private static NpcEncounterConfig preparedArena;
    private static NpcIntegration preparedIntegration;
    private final MinecraftServer server;
    private final NpcIntegration integration;
    private final NpcEncounterConfig config;
    private final ResourceKey<Level> dimension;
    private final NpcEncounterSchedule schedule = new NpcEncounterSchedule();
    private final NpcDeathClaims deathClaims = new NpcDeathClaims();
    private final Random random = new Random();
    private final NpcLoadoutSelector selector = new NpcLoadoutSelector();
    private final NpcPlacementPlanner planner = new NpcPlacementPlanner();
    private final Set<String> rejectedPresets = new HashSet<>();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private boolean stopped;

    public static void serverStarted(MinecraftServer server) {
        requireThread(server);
        preparedServer = null; preparedArena = null; preparedIntegration = null;
        if (current != null && current.server != server) {
            // No API/entity access to a previous server instance. TacticalNPC recovery closes
            // persisted ownership; callbacks are never restored or transferred to a new server.
            current.schedule.deactivate();
            current.deathClaims.clear();
            current = null;
        }
    }

    private NpcEncounterManager(MinecraftServer server, NpcIntegration integration, NpcEncounterConfig config) {
        this.server = server; this.integration = integration; this.config = config;
        dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(config.dimension));
    }

    public static void started(MinecraftServer server) {
        if (MapSetManager.isArenaSet()) return; // Registered as a rollback-capable start stage.
        started(server, false);
    }

    public static String prepareArena(MinecraftServer server) {
        requireThread(server);
        preparedServer = null; preparedArena = null; preparedIntegration = null;
        if (!net.minecraftforge.fml.ModList.get().isLoaded(NpcIntegration.MOD_ID)) return "Арена: TacticalNPC не установлен.";
        var diagnostics = new ArrayList<String>();
        var config = NpcEncounterConfig.load(server.getWorldPath(LevelResource.ROOT).resolve("tacticaltablet_arena_npcs.json"),
                diagnostics::add, NpcEncounterConfig.arenaDefaults());
        if (!config.enabled) return "Арена: NPC-профиль отключён или некорректен. " + String.join("; ", diagnostics);
        if (!"minecraft:overworld".equals(config.dimension)) return "Арена: NPC должны использовать minecraft:overworld активной карты.";
        var integration = NpcIntegration.connect(server);
        if (integration == null) return "Арена: требуется совместимый TacticalNPC " + NpcIntegration.API_VERSION + " и TaCZ 1.1.8-hotfix2.";
        preparedServer = server; preparedArena = config; preparedIntegration = integration;
        return "";
    }

    public static void prepareArenaSession(MinecraftServer server) {
        if (preparedServer != server || preparedArena == null || preparedIntegration == null)
            throw new IllegalStateException("Arena NPC preflight was not completed");
        started(server, true);
        if (current == null || current.stopped || current.server != server
                || GameStateManager.getLifecycleSnapshot().matchId().filter(id -> id.equals(current.schedule.matchId())).isEmpty())
            throw new IllegalStateException("Arena NPC policy registration failed");
    }

    private static void started(MinecraftServer server, boolean arenaStart) {
        requireThread(server);
        if (current != null) {
            TacticalTabletMod.LOGGER.error("NPC activation refused: previous session requires cleanup");
            return;
        }
        var snapshot = GameStateManager.getLifecycleSnapshot();
        if (snapshot.state() != (arenaStart ? MatchState.STARTING : MatchState.RUNNING) || snapshot.matchId().isEmpty()) return;
        var config = arenaStart ? preparedArena : NpcEncounterConfig.load(server.getWorldPath(LevelResource.ROOT).resolve("tacticaltablet_npcs.json"),
                message -> TacticalTabletMod.LOGGER.warn("{}", message));
        var integration = arenaStart ? preparedIntegration : NpcIntegration.connect(server);
        if (!config.enabled || integration == null || !modeAllowed(config)) return;
        var manager = new NpcEncounterManager(server, integration, config);
        if (server.getLevel(manager.dimension) == null) {
            TacticalTabletMod.LOGGER.warn("NPC encounters disabled: configured dimension {} is unavailable", config.dimension);
            return;
        }
        current = manager; // Keep the ID/adapter even if registration fails, for retryable cleanup.
        UUID matchId = snapshot.matchId().orElseThrow();
        manager.schedule.start(matchId, Integer.toUnsignedLong(server.getTickCount()), config, manager.random);
        try {
            integration.register(matchId, new NpcIntegration.Policy() {
                @Override public boolean active() { return manager.combatRunning(); }
                @Override public boolean canAttack(ServerPlayer player) { return manager.canAttack(player); }
            });
            TacticalTabletMod.LOGGER.info("NPC encounter manager activated matchId={}", matchId);
        } catch (RuntimeException | LinkageError exception) {
            manager.stopped = true;
            manager.schedule.deactivate();
            TacticalTabletMod.LOGGER.error("NPC policy registration failed matchId={}; encounters disabled", matchId, exception);
        }
    }

    private static boolean modeAllowed(NpcEncounterConfig c) {
        var mode = GameStateManager.getCurrentMode();
        return NpcModePolicy.allowed(c, MapSetManager.isClanWarSet(), MapSetManager.isChaosSet() || MapSetManager.isCompetitiveSet(),
                mode == null ? null : mode.name(), MapSetManager.isArenaSet());
    }

    private boolean combatRunning() {
        return config.enabled && modeAllowed(config) && GameStateManager.isRunning(server)
                && (!MapSetManager.isArenaSet() || com.makar.tacticaltablet.game.arena.ArenaMatchRuntime.combatOpen())
                && schedule.running(GameStateManager.getLifecycleSnapshot(), GameStateManager.getMatchPhase() == MatchPhase.RUNNING);
    }

    private boolean canAttack(ServerPlayer p) {
        if (p == null || p.server != server) return false;
        return NpcTargetEligibility.canAttack(schedule.matchId(), GameStateManager.getLifecycleSnapshot(), p.getUUID(),
                combatRunning(), LivesManager.isBoundToCurrentMatch(p), LivesManager.isAliveParticipant(p),
                p.isAlive() && !p.isDeadOrDying() && !p.hasDisconnected(), p.level().dimension().equals(dimension) && !GameStateManager.isInLobby(p),
                p.isSpectator(), p.isCreative(), ModerModeManager.isInModerMode(p), p.getTags().contains("war.playing"));
    }

    /** O(1) ownership check, using only the already-connected optional adapter. */
    static NpcIntegration.DeathIdentity rewardIdentity(LivingEntity victim) {
        var m = current;
        if (m == null || victim.getServer() != m.server || !m.schedule.owns(victim.getUUID())) return null;
        requireThread(m.server);
        if (!m.combatRunning() || !victim.level().dimension().equals(m.dimension)
                || !victim.level().getWorldBorder().isWithinBounds(victim.getBoundingBox())) return null;
        var identity = m.integration.deathIdentity(victim);
        return identity != null && m.schedule.matchId().equals(identity.ownerMatchId()) ? identity : null;
    }

    static boolean canReward(ServerPlayer killer, LivingEntity victim) {
        var m = current;
        return m != null && m.canAttack(killer) && m.server.getPlayerList().getPlayer(killer.getUUID()) == killer
                && killer.level() == victim.level()
                && killer.level().getWorldBorder().isWithinBounds(killer.getBoundingBox());
    }

    static boolean claimDeath(UUID matchId, UUID victimId) {
        var m = current;
        return m != null && m.combatRunning() && m.schedule.matchId().equals(matchId)
                && m.deathClaims.claim(matchId, victimId);
    }

    public static void tick(MinecraftServer server) {
        requireThread(server);
        var m = current;
        if (m == null || m.server != server) return;
        if (!m.combatRunning()) { deactivate(); return; }
        long now = Integer.toUnsignedLong(server.getTickCount());
        if (!m.schedule.due(now)) return;
        // Reschedule before attempting any API/world work, including failures and lack of players/space.
        m.schedule.defer(now, m.config, m.random);
        try { m.encounter(); }
        catch (RuntimeException | LinkageError exception) {
            m.schedule.deactivate();
            TacticalTabletMod.LOGGER.error("NPC encounter API failed; disabling matchId={}", m.schedule.matchId(), exception);
            deactivate();
            if (MapSetManager.isArenaSet()) {
                server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Арена остановлена без зачёта: TacticalNPC недоступен. " + exception.getMessage()), false);
                GameStateManager.forceStopMatch(server);
            }
        }
    }

    private void encounter() {
        var phase = ZoneManager.getCurrentPhaseNumber();
        if (!MapSetManager.isArenaSet() && (phase.isEmpty() || phase.getAsInt() >= config.stopSpawningAtZonePhase)) return;
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream().filter(this::canAttack).toList();
        if (players.isEmpty()) return;
        var active = integration.activeIds(schedule.matchId());
        int capacity = schedule.available(config.maxActiveNpcs, active.size());
        if (capacity < config.minGroupSize) {
            TacticalTabletMod.LOGGER.debug("NPC limit reached matchId={}", schedule.matchId()); return;
        }
        var selection = selector.select(config, capacity, rejectedPresets, random);
        if (selection.isEmpty()) return;
        var loadout = selection.orElseThrow();
        // Use current positions for moving groups; unloaded IDs keep the last position and capacity.
        for (UUID id : active) {
            for (ServerLevel world : server.getAllLevels()) {
                Entity entity = world.getEntity(id);
                if (entity != null) { lastPositions.put(id, entity.position()); break; }
            }
        }
        var origin = planner.plan(level, players, new ArrayList<>(lastPositions.values()), loadout.weapons().size(), config, random);
        if (origin.isEmpty()) {
            TacticalTabletMod.LOGGER.debug("NPC site unavailable matchId={} preset={}", schedule.matchId(), loadout.preset().id()); return;
        }
        UUID groupId = UUID.randomUUID();
        TacticalTabletMod.LOGGER.debug("NPC encounter selected matchId={} preset={} groupId={}", schedule.matchId(), loadout.preset().id(), groupId);
        var result = integration.spawn(level, schedule.matchId(), groupId, origin.orElseThrow(), loadout);
        if (result.success()) {
            schedule.spawned(result.ids());
            result.ids().forEach(id -> lastPositions.put(id, origin.orElseThrow()));
            TacticalTabletMod.LOGGER.info("NPC group created matchId={} groupId={} preset={} count={}",
                    schedule.matchId(), groupId, loadout.preset().id(), result.ids().size());
        } else if (result.invalidWeapon()) {
            rejectedPresets.add(loadout.preset().id());
            TacticalTabletMod.LOGGER.warn("NPC preset {} disabled for this match: {}", loadout.preset().id(), result.diagnostic());
            if (MapSetManager.isArenaSet())
                throw new IllegalStateException("Оружейный пресет Арены " + loadout.preset().id()
                        + " отклонён TacticalNPC: " + result.diagnostic());
        } else TacticalTabletMod.LOGGER.debug("NPC spawn refused matchId={}: {}", schedule.matchId(), result.diagnostic());
    }

    public static void deactivate() {
        var m = current;
        if (m == null) return;
        requireThread(m.server);
        if (m.stopped) return;
        m.stopped = true;
        m.schedule.deactivate(); // Callback closes first, even if the optional API fails.
        m.deathClaims.clear();
        try {
            m.integration.deactivate(m.schedule.matchId());
            TacticalTabletMod.LOGGER.info("NPC encounters deactivated matchId={}", m.schedule.matchId());
        } catch (RuntimeException | LinkageError exception) {
            TacticalTabletMod.LOGGER.error("NPC deactivation failed; policy remains closed, cleanup will retry", exception);
        }
    }

    /** Throws on failure so cleanup.npcs remains pending and can retry with the same adapter/ID. */
    public static void cleanup(MinecraftServer server) {
        requireThread(server);
        var m = current;
        if (m == null || m.server != server) return;
        m.schedule.deactivate();
        m.deathClaims.clear();
        try {
            int removed = m.integration.remove(m.schedule.matchId());
            TacticalTabletMod.LOGGER.info("NPC cleanup matchId={} removed={}", m.schedule.matchId(), removed);
        } catch (LinkageError error) {
            throw new IllegalStateException("TacticalNPC cleanup API unavailable", error);
        }
        m.schedule.clear(); m.lastPositions.clear(); current = null;
    }

    public static void onEntityRemoved(Entity entity) {
        var m = current;
        if (m == null || entity.level().isClientSide || !m.schedule.owns(entity.getUUID())) return;
        requireThread(m.server);
        // UNLOADED_TO_CHUNK is deliberately retained: activeNpcCount excludes unloaded actors.
        if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
            m.schedule.removed(entity.getUUID()); m.lastPositions.remove(entity.getUUID());
        }
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("NPC encounters require the server thread");
    }
}
