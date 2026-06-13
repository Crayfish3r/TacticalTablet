package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.anticheat.AntiCheatManager;
import com.makar.tacticaltablet.anticheat.MovementAntiCheat;
import com.makar.tacticaltablet.anticheat.Severity;
import com.makar.tacticaltablet.anticheat.ViolationType;
import com.makar.tacticaltablet.admin.TestModeManager;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.client.NameTagManager;
import com.makar.tacticaltablet.corpse.CorpseLootManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.teleport.SafeTeleport;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.integration.discord.DiscordLeaderboardService;
import com.makar.tacticaltablet.integration.discord.DiscordWebhookClient;
import com.makar.tacticaltablet.integration.discord.LeaderboardScheduler;
import com.makar.tacticaltablet.integration.online.OnlineWebhookService;
import com.makar.tacticaltablet.inventory.InventoryGuard;
import com.makar.tacticaltablet.inventory.InventoryLockEvents;
import com.makar.tacticaltablet.map.MapRotationManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.XpNotifier;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.tablet.net.TabletPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.Locale;
import java.util.Set;

public class ServerEvents {

    private static final double MAX_MELEE_REACH = 5.5D;
    private static int utilityTickCounter = 0;

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressManager.loadPlayer(player);
            TeamMatchManager.rememberPlayer(player);
            NameTagManager.applyToAll(player.server);
            if (LivesManager.ensureEliminatedIfOutOfLives(player)) {
                TeamMatchManager.applyScoreboardTeams(player.server);
                ClassXPManager.sync(player);
                player.server.execute(() -> GameStateManager.checkForMatchEnd(player.server));
                return;
            }
            LobbyManager.moveToLobby(player);
            ContractManager.ensureTracker(player);
            ContractManager.giveSelectionTrackerIfAvailable(player);
            TeamMatchManager.applyScoreboardTeams(player.server);
            ClassXPManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressManager.loadPlayer(player);

            if (LivesManager.ensureEliminatedIfOutOfLives(player)) {
                ClassXPManager.sync(player);
                return;
            }

            LobbyManager.moveToLobby(player);
            ClassXPManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;

        PlayerProgressManager.loadPlayer(newPlayer);

        for (String tag : oldPlayer.getTags()) {
            if (tag.equals("war.lives_init") || tag.equals("war.eliminated")) {
                newPlayer.addTag(tag);
            }
        }

        PlayerTabletState.reset(newPlayer);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (GameStateManager.getMatchPhase() == MatchPhase.POST_GAME) {
            event.setCanceled(true);
            event.setAmount(0);
            return;
        }

        checkCombatReach(player, event.getSource());

        Set<String> tags = player.getTags();
        boolean inLobby = GameStateManager.isInLobby(player) || tags.contains("in_lobby");
        boolean playing = tags.contains("war.playing");

        if (inLobby && !playing) {
            event.setCanceled(true);
            event.setAmount(0);
            return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        if (attacker.getUUID().equals(player.getUUID())) {
            return;
        }

        if (!attacker.getTags().contains("war.playing") || !playing) {
            return;
        }

        String sourceText = safeLower(event.getSource().getMsgId())
                + " " + entityId(event.getSource().getDirectEntity())
                + " " + entityId(event.getSource().getEntity());
        if (GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.areTeammates(attacker, player)
                && isTeamTrapDamage(sourceText)) {
            event.setCanceled(true);
            event.setAmount(0);
            return;
        }

        if (event.getAmount() > 0.0F) {
            DiscordLeaderboardService.recordMatchDamage(attacker, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getTags().contains("war.playing")) {
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        PlayerProgressManager.tick(event.getServer());
        LeaderboardScheduler.tick(event);
        OnlineWebhookService.tick(event);
        PassiveClassXPManager.tick(event.getServer());
        RtpTimerManager.tick(event.getServer());
        ContractManager.tick(event.getServer());
        GameStateManager.onServerTick(event.getServer());
        InventoryGuard.tick(event.getServer());
        MovementAntiCheat.tick(event.getServer());

        if (++utilityTickCounter >= 100) {
            utilityTickCounter = 0;
            LobbyManager.keepLobbyWeatherClear(event.getServer());
            NameTagManager.applyToAll(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean runningMatchParticipant = GameStateManager.isRunning(player.server)
                    && GameStateManager.getMatchPhase() == MatchPhase.RUNNING
                    && LivesManager.isAliveParticipant(player);

            PlayerProgressManager.savePlayer(player);

            if (runningMatchParticipant) {
                LivesManager.handleDeath(player);
                PlayerTabletState.reset(player);
            } else {
                RtpTimerManager.cancel(player);
                PassiveClassXPManager.clear(player);
                PlayerTabletState.reset(player);
            }

            TabletPacket.reset(player);
            AntiCheatManager.reset(player);
            MovementAntiCheat.reset(player);
            if (!GameStateManager.getCurrentMode().isTeamMode()) {
                NameTagManager.remove(player);
            }
            if (!runningMatchParticipant) {
                player.removeTag("war.playing");
                player.removeTag("in_lobby");
            }
            PlayerProgressManager.unloadPlayer(player);
            player.server.execute(() -> GameStateManager.checkForMatchEnd(player.server));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        DamageSource source = event.getSource();
        Set<String> victimTags = victim.getTags();

        boolean victimWasPlaying = victimTags.contains("war.playing");
        boolean victimWasInLobby = victimTags.contains("in_lobby") || GameStateManager.isInLobby(victim);

        if (victimWasInLobby && !victimWasPlaying) {
            return;
        }

        if (victimWasPlaying) {
            CorpseLootManager.createCorpse(victim);
            PlayerProgressManager.addDeath(victim);
            DiscordLeaderboardService.recordMatchDeath(victim);
            LivesManager.handleDeath(victim);
            ContractManager.onPlayerKilled(victim, source.getEntity() instanceof ServerPlayer killer ? killer : null);
            ClassXPManager.syncAll(victim.server);
            GameStateManager.checkForMatchEnd(victim.server);
        }

        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        if (!killer.getTags().contains("war.playing")) return;
        if (!victimWasPlaying) return;
        if (killer.getUUID().equals(victim.getUUID())) return;

        Entity direct = source.getDirectEntity();

        if (direct instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner != null && owner.getUUID().equals(victim.getUUID())) return;
        }

        PlayerProgressManager.addKill(killer);
        DiscordLeaderboardService.recordMatchKill(killer);
        PlayerProgressManager.addCoins(killer, PlayerProgressManager.KILL_COIN_REWARD);
        ClassXPManager.sync(killer);

        String clazz = PlayerTabletState.getSelectedClass(killer);
        if (clazz == null || clazz.isBlank()) return;

        XPResult result = calculateXP(killer, victim, source, direct);
        if (result.xp <= 0) return;

        ClassXPManager.addXP(killer, clazz, result.xp);
        XpNotifier.send(killer, result.xp, result.reason);
    }



    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        event.getServer().getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, event.getServer());
        GameStateManager.resetRuntime(event.getServer());
        GameStateManager.validateRuntimeRequirements(event.getServer());
        DiscordLeaderboardService.init(event.getServer());
        LeaderboardScheduler.onServerStarted(event.getServer());
        OnlineWebhookService.onServerStarted(event.getServer());
        MapRotationManager.onServerStarted(event.getServer());
        ZoneManager.reset(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MapRotationManager.onServerStopped(event.getServer());
        MapRotationManager.resetRuntime();
        OnlineWebhookService.onServerStopped();
        LeaderboardScheduler.reset();
        DiscordLeaderboardService.resetMatch();
        DiscordWebhookClient.shutdown();
        GameStateManager.resetRuntime(event.getServer());
        TestModeManager.reset();
        AirdropManager.resetRuntime(GameStateManager.getOverworld(event.getServer()));
        ContractManager.reset(event.getServer());
        RtpTimerManager.clearAll();
        PassiveClassXPManager.clearAll();
        ClassCooldownManager.resetAll();
        SafeTeleport.clearPool();
        PlayerTabletState.resetAll();
        TabletPacket.resetAll();
        AntiCheatManager.resetAll();
        MovementAntiCheat.resetAll();
        InventoryLockEvents.resetTracking();
        PlayerProgressManager.resetStorage();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PlayerProgressManager.saveAll();
    }

    private static XPResult calculateXP(ServerPlayer killer, ServerPlayer victim, DamageSource source, Entity direct) {
        String msgId = safeLower(source.getMsgId());
        String directId = entityId(direct);
        String sourceEntityId = entityId(source.getEntity());
        String sourceText = msgId + " " + directId + " " + sourceEntityId;

        if (isMineDamage(sourceText)) {
            return new XPResult(16, "мина");
        }

        if (isPhosphorusMortarDamage(sourceText)) {
            return new XPResult(25, "phosphorus mortar");
        }

        if (isMortarDamage(sourceText)) {
            return new XPResult(22, "миномёт");
        }

        if (isGrenadeDamage(sourceText)) {
            return new XPResult(16, "граната");
        }

        if (isExplosionDamage(sourceText)) {
            return new XPResult(14, "взрыв");
        }

        if (isLongRangeKill(killer, victim) && isRangedDamage(sourceText, direct)) {
            return new XPResult(15, "дальнее убийство");
        }

        if (isFirearmDamage(sourceText, direct)) {
            return new XPResult(12, "огнестрел");
        }

        if (isMeleeDamage(killer, msgId, sourceText, direct)) {
            return new XPResult(25, "ближний бой");
        }

        return new XPResult(10, "убийство");
    }

    private static void checkCombatReach(ServerPlayer victim, DamageSource source) {
        if (victim == null || source == null) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker.getUUID().equals(victim.getUUID())) return;
        if (!attacker.getTags().contains("war.playing")) return;
        if (!victim.getTags().contains("war.playing")) return;

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) return;

        String msgId = safeLower(source.getMsgId());
        String sourceText = msgId + " " + entityId(direct) + " " + entityId(source.getEntity());
        if (!isMeleeDamage(attacker, msgId, sourceText, direct)) return;

        double distance = attacker.distanceTo(victim);
        if (distance <= MAX_MELEE_REACH) return;

        AntiCheatManager.record(
                attacker,
                ViolationType.COMBAT_REACH,
                Severity.HIGH,
                "target=" + victim.getGameProfile().getName()
                        + " distance=" + String.format(Locale.ROOT, "%.2f", distance)
                        + " max=" + String.format(Locale.ROOT, "%.2f", MAX_MELEE_REACH)
        );
    }

    private static boolean isLongRangeKill(ServerPlayer killer, ServerPlayer victim) {
        if (killer == null || victim == null) return false;
        if (!killer.level().dimension().equals(victim.level().dimension())) return false;

        return killer.distanceToSqr(victim) >= 80.0D * 80.0D;
    }

    private static boolean isMineDamage(String sourceText) {
        return containsAny(sourceText,
                "claymore",
                "m18a1",
                "m18_a1",
                "blu43",
                "blu_43",
                "blu-43",
                "dragontooth",
                "dragon_tooth",
                "landmine",
                "land_mine",
                "anti_personnel_mine",
                "anti_tank_mine",
                ":mine",
                "_mine",
                " mine");
    }

    private static boolean isTeamTrapDamage(String sourceText) {
        return isMineDamage(sourceText) || containsAny(sourceText,
                "trap",
                "claymore",
                "barbed",
                "wire",
                "spike");
    }

    private static boolean isMortarDamage(String sourceText) {
        return containsAny(sourceText, "mortar", "mortar_shell");
    }

    private static boolean isPhosphorusMortarDamage(String sourceText) {
        return isMortarDamage(sourceText)
                && containsAny(sourceText,
                "phosphor",
                "phosphorus",
                "white_phosphorus",
                "whitephosphorus",
                "white-phosphorus",
                "wp_shell",
                "wp_mortar",
                "wpmortar");
    }

    private static boolean isGrenadeDamage(String sourceText) {
        return containsAny(sourceText, "grenade", "m67", "frag");
    }

    private static boolean isExplosionDamage(String sourceText) {
        return containsAny(sourceText, "explosion", "explode", "superbwarfare");
    }

    private static boolean isRangedDamage(String sourceText, Entity direct) {
        return direct instanceof Projectile || isFirearmDamage(sourceText, direct);
    }

    private static boolean isFirearmDamage(String sourceText, Entity direct) {
        return direct instanceof Projectile || containsAny(sourceText,
                "tacz",
                "bullet",
                "projectile",
                "firearm",
                "gun",
                "rifle",
                "sniper",
                "m700",
                "arrow",
                "bolt");
    }

    private static boolean isMeleeDamage(ServerPlayer killer, String msgId, String sourceText, Entity direct) {
        if (killer == null) return false;

        if (direct != null && direct.getUUID().equals(killer.getUUID())) {
            return true;
        }

        if (direct == null && containsAny(msgId,
                "player",
                "mob",
                "melee",
                "crowbar",
                "buttstock",
                "butt_stock",
                "punch",
                "sword",
                "knife",
                "bayonet")) {
            return true;
        }

        return containsAny(sourceText,
                "melee",
                "crowbar",
                "buttstock",
                "butt_stock",
                "punch",
                "sword",
                "knife",
                "bayonet");
    }

    private static String entityId(Entity entity) {
        return entity == null ? "" : safeLower(entity.getType().toString());
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isEmpty()) return false;

        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }

        return false;
    }

    private record XPResult(int xp, String reason) {
    }
}
