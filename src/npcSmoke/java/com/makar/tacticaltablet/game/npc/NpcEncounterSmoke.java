package com.makar.tacticaltablet.game.npc;

import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.admin.TestModeManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Isolated dedicated fixture only. Explicit chunk loading/terrain edits belong to this fixture. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID)
public final class NpcEncounterSmoke {
    private static int ticks;
    private static int stage;
    private static FakePlayer player;
    private static NpcIntegration api;
    private static UUID match;
    private static UUID unrelated;
    private static UUID unrelatedNpc;
    private static List<UUID> npcs;
    private static int alive;
    private static UUID orphanMatch;
    private static UUID orphanNpc;
    private static String orphanTeam;
    private static UUID disabledMatch;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (++ticks > 1000) { fail(server, new IllegalStateException("NPC probe timed out stage=" + stage)); return; }
        try {
            if (stage == 0 && ticks >= 5) {
                api = NpcIntegration.connect(server);
                if ("absent".equals(System.getProperty("tacticaltablet.npcProbe"))) {
                    check(api == null, "TacticalNPC unexpectedly present");
                    GameStateManager.forceStopMatch(server);
                    TacticalTabletMod.LOGGER.info("TT_NPC_PROBE_ABSENT_PASS"); stage = 99; server.halt(false); return;
                }
                check(api != null, "TacticalNPC API missing");
                var level = server.overworld();
                if ("restart".equals(System.getProperty("tacticaltablet.npcProbe"))) {
                    var saved = com.google.gson.JsonParser.parseString(Files.readString(
                            server.getWorldPath(LevelResource.ROOT).resolve("npc-probe-orphan.json"))).getAsJsonObject();
                    orphanMatch = UUID.fromString(saved.get("match").getAsString());
                    orphanNpc = UUID.fromString(saved.get("npc").getAsString());
                    orphanTeam = saved.get("team").getAsString();
                    level.getChunk(saved.get("chunkX").getAsInt(), saved.get("chunkZ").getAsInt());
                    stage = 5;
                    return;
                }
                // Bound and deterministic loaded arena, not production planner behavior.
                for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) level.getChunk(x, z);
                for (int x = -100; x <= 100; x++) for (int z = -100; z <= 100; z++)
                    level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.STONE.defaultBlockState());
                var config = new NpcEncounterConfig();
                config.enabled = true; config.maxActiveNpcs = 3;
                config.minGroupSize = config.maxGroupSize = 2;
                config.minSpawnIntervalSeconds = config.maxSpawnIntervalSeconds = 1;
                config.minDistanceFromPlayers = 20; config.maxDistanceFromPlayers = 75;
                config.maxSpawnAttempts = 64;
                write(server, "tacticaltablet_npcs.json", new GsonBuilder().setPrettyPrinting().create().toJson(config));
                write(server, "tacticaltablet_zone.json", "{\"zoneCenterX\":0,\"zoneCenterZ\":0,\"zoneRandomRadius\":0}");
                player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "NpcProbe")) {
                    @Override public boolean hasDisconnected() { return false; }
                };
                var fakeConnection = player.connection;
                // Vanilla PlayerList admission requires an actual channel even for FakePlayer.
                // Embedded transport stays inside the fixture and sends no network traffic.
                new io.netty.channel.embedded.EmbeddedChannel(fakeConnection.connection);
                server.getPlayerList().placeNewPlayer(fakeConnection.connection, player);
                player.connection = fakeConnection;
                player.setGameMode(GameType.SURVIVAL);
                player.getAbilities().invulnerable = true;
                TestModeManager.setSoloStartEnabled(true);
                GameStateManager.startGame(server);
                check(GameStateManager.getLifecycleSnapshot().state() == MatchState.RUNNING, "Real TT start did not commit RUNNING");
                match = GameStateManager.getLifecycleSnapshot().matchId().orElseThrow();
                alive = LivesManager.getAlivePlayerCount(server);
                check(alive == 1, "Player admission failed");
                // Initially in lobby: wait beyond several event deadlines before entering arena.
                stage = 1;
            } else if (stage == 1 && ticks >= 70) {
                check(api.activeIds(match).isEmpty(), "NPC spawned while all participants were in lobby");
                player.teleportTo(server.overworld(), .5, -60, .5, 0, 0);
                player.removeTag("in_lobby"); player.addTag("war.playing");
                stage = 2;
            } else if (stage == 2 && ticks >= 140) {
                npcs = api.activeIds(match);
                check(npcs.size() == 2, "Expected atomic group2 with capacity3: " + npcs.size());
                check(LivesManager.getAlivePlayerCount(server) == alive && GameStateManager.playingPlayers(server) == alive,
                        "NPCs altered winner/lives counts");
                var border = server.overworld().getWorldBorder();
                for (UUID id : npcs) check(border.isWithinBounds(server.overworld().getEntity(id).getBoundingBox()), "NPC outside border");
                var npc = (net.minecraft.world.entity.LivingEntity) server.overworld().getEntity(npcs.get(0));
                check(!blocked(player, npc), "Eligible participant rejected by native damage policy");
                player.addTag("war.eliminated");
                check(blocked(player, npc), "Eliminated target retained damage authorization");
                player.removeTag("war.eliminated");
                player.setGameMode(GameType.SPECTATOR);
                check(blocked(player, npc), "Spectator retained damage authorization");
                player.setGameMode(GameType.SURVIVAL);
                var outsider = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "NpcOutsider")) {
                    @Override public boolean hasDisconnected() { return false; }
                };
                outsider.moveTo(.5, -60, .5, 0, 0); server.overworld().addNewPlayer(outsider);
                check(blocked(outsider, npc), "Unregistered outsider accepted as NPC target");
                outsider.discard();
                var noSpace = new NpcEncounterConfig();
                noSpace.minDistanceFromPlayers = 4000; noSpace.maxDistanceFromPlayers = 4096;
                check(new NpcPlacementPlanner().plan(server.overworld(), List.of(player), List.of(), 4,
                        noSpace, new java.util.Random(1)).isEmpty(), "Planner accepted point outside arena");
                unrelated = UUID.randomUUID();
                api.register(unrelated, new NpcIntegration.Policy() {
                    @Override public boolean active() { return true; }
                    @Override public boolean canAttack(net.minecraft.server.level.ServerPlayer p) { return false; }
                });
                var p = new NpcEncounterConfig().presets.get(0);
                var result = api.spawn(server.overworld(), unrelated, UUID.randomUUID(), new Vec3(-60.5, -60, -60.5),
                        new NpcLoadoutSelector.Selection(p, p.weapons()));
                check(result.success(), "Unrelated fixture NPC spawn failed: " + result.diagnostic());
                unrelatedNpc = result.ids().get(0);
                var invalid = api.spawn(server.overworld(), unrelated, UUID.randomUUID(), new Vec3(-60.5, -60, -60.5),
                        new NpcLoadoutSelector.Selection(p, List.of("tacticaltablet:intentionally_missing_probe_weapon")));
                check(!invalid.success() && invalid.invalidWeapon() && invalid.ids().isEmpty(), "Invalid weapon did not fail atomically");
                var defeated = (net.minecraft.world.entity.LivingEntity) server.overworld().getEntity(npcs.get(1));
                defeated.hurt(server.overworld().damageSources().genericKill(), Float.MAX_VALUE);
                check(GameStateManager.getLifecycleSnapshot().state() == MatchState.RUNNING, "NPC death ended the player match");
                check(LivesManager.getAlivePlayerCount(server) == alive, "NPC death changed player lives");
                GameStateManager.endGame(server, (net.minecraft.server.level.ServerPlayer) null);
                check(GameStateManager.getLifecycleSnapshot().state() == MatchState.ENDING, "Natural end did not reach ENDING");
                check(!api.activeIds(match).isEmpty(), "NPCs should still exist before delayed cleanup");
                check(blocked(player, npc), "ENDING did not immediately revoke NPC damage");
                GameStateManager.forceStopMatch(server);
                check(api.activeIds(match).isEmpty(), "TT cleanup retained current-match NPCs");
                check(api.activeIds(unrelated).contains(unrelatedNpc), "TT cleanup removed another match's NPC");
                NpcEncounterManager.cleanup(server); NpcEncounterManager.cleanup(server);
                check(api.activeIds(unrelated).contains(unrelatedNpc), "Repeat cleanup touched unrelated match");
                api.remove(unrelated);
                // Persist an intentionally unclosed external session to exercise native disk recovery.
                orphanMatch = UUID.randomUUID();
                api.register(orphanMatch, new NpcIntegration.Policy() {
                    @Override public boolean active() { return true; }
                    @Override public boolean canAttack(net.minecraft.server.level.ServerPlayer p) { return false; }
                });
                var orphan = api.spawn(server.overworld(), orphanMatch, UUID.randomUUID(), new Vec3(-60.5, -60, -60.5),
                        new NpcLoadoutSelector.Selection(p, p.weapons()));
                check(orphan.success(), "Cannot save recovery fixture: " + orphan.diagnostic());
                orphanNpc = orphan.ids().get(0);
                var orphanEntity = server.overworld().getEntity(orphanNpc);
                orphanTeam = orphanEntity.getTeam().getName();
                write(server, "npc-probe-orphan.json", "{\"match\":\"" + orphanMatch + "\",\"npc\":\"" + orphanNpc
                        + "\",\"team\":\"" + orphanTeam + "\",\"chunkX\":" + orphanEntity.chunkPosition().x
                        + ",\"chunkZ\":" + orphanEntity.chunkPosition().z + "}");
                var disabled = new NpcEncounterConfig();
                disabled.minSpawnIntervalSeconds = disabled.maxSpawnIntervalSeconds = 1;
                write(server, "tacticaltablet_npcs.json", new GsonBuilder().create().toJson(disabled));
                GameStateManager.startGame(server);
                check(GameStateManager.getLifecycleSnapshot().state() == MatchState.RUNNING, "Disabled-NPC match could not start");
                disabledMatch = GameStateManager.getLifecycleSnapshot().matchId().orElseThrow();
                player.teleportTo(server.overworld(), .5, -60, .5, 0, 0);
                player.removeTag("in_lobby"); player.addTag("war.playing");
                stage = 3;
            } else if (stage == 3 && ticks >= 220) {
                check(api.activeIds(disabledMatch).isEmpty(), "enabled=false spawned NPCs");
                GameStateManager.forceStopMatch(server);
                check(api.activeIds(orphanMatch).contains(orphanNpc), "Disabled-match cleanup removed unrelated recovery NPC");
                server.getPlayerList().remove(player);
                TacticalTabletMod.LOGGER.info("TT_NPC_PROBE_PRESENT_PASS actual TT STARTED/RUNNING, lobby gate, group2, capacity, border/no-space, invalid weapon atomicity, NPC death/player counts, outsider/elimination/spectator dynamic native damage gates, immediate ENDING, force stop, scoped/repeated cleanup, enabled=false, saved orphan");
                stage = 99; server.halt(false);
            } else if (stage == 5 && ticks >= 70) {
                check(api.activeIds(orphanMatch).isEmpty(), "Old session still has active NPCs after disk restart");
                check(server.overworld().getEntity(orphanNpc) == null, "Old NPC survived native admission");
                check(server.getScoreboard().getPlayerTeam(orphanTeam) == null, "Old owned team survived startup recovery");
                check(GameStateManager.getLifecycleSnapshot().matchId().isEmpty(), "TT resumed a previous session");
                boolean closed = false;
                try {
                    api.register(orphanMatch, new NpcIntegration.Policy() {
                        @Override public boolean active() { return true; }
                        @Override public boolean canAttack(net.minecraft.server.level.ServerPlayer p) { return true; }
                    });
                } catch (IllegalArgumentException expected) { closed = true; }
                check(closed, "Previous-session match ID could reopen");
                TacticalTabletMod.LOGGER.info("TT_NPC_PROBE_RESTART_PASS disk restart, old NPC admission rejected, owned team removed, old ID closed, TT idle");
                stage = 99; server.halt(false);
            }
        } catch (Throwable error) { fail(server, error); }
    }

    private static void write(MinecraftServer server, String file, String value) throws Exception {
        Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(file), value);
    }
    private static void check(boolean value, String diagnostic) { if (!value) throw new AssertionError(diagnostic); }
    private static boolean blocked(net.minecraft.server.level.ServerPlayer target, net.minecraft.world.entity.LivingEntity npc) {
        return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingAttackEvent(
                target, target.server.overworld().damageSources().mobAttack(npc), 1));
    }
    private static void fail(MinecraftServer server, Throwable error) {
        stage = 99;
        TacticalTabletMod.LOGGER.error("TT_NPC_PROBE_FAIL", error);
        if (player != null) server.getPlayerList().remove(player);
        server.halt(false);
    }
}
