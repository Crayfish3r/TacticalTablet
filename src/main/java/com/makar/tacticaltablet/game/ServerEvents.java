package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.admin.TestModeManager;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.client.NameTagManager;
import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.corpse.CorpseLootManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.extraction.ExtractionPointManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.respawn.DeathTransitionManager;
import com.makar.tacticaltablet.game.respawn.PostRtpProtectionManager;
import com.makar.tacticaltablet.game.teleport.SafeTeleport;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.set.SetMatchRuntime;
import com.makar.tacticaltablet.game.set.CombatAssistTracker;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.integration.discord.MatchDamageAccounting;
import com.makar.tacticaltablet.integration.discord.DiscordLeaderboardService;
import com.makar.tacticaltablet.integration.discord.DiscordWebhookClient;
import com.makar.tacticaltablet.integration.discord.LeaderboardScheduler;
import com.makar.tacticaltablet.integration.online.OnlineWebhookService;
import com.makar.tacticaltablet.inventory.InventoryGuard;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.inventory.InventoryLockEvents;
import com.makar.tacticaltablet.map.MapRotationManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.moderation.PunishmentManager;
import com.makar.tacticaltablet.moderation.PunishmentRecord;
import com.makar.tacticaltablet.prefix.PrefixManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.XpNotifier;
import com.makar.tacticaltablet.progression.kit.KitRotationManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletPacket;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class ServerEvents {

    private static final int TEAM_KILL_BAN_THRESHOLD = 3;
    private static final long TEAM_KILL_BAN_MILLIS = 15L * 60L * 1000L;
    private static int utilityTickCounter = 0;

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PunishmentRecord tempBan = PunishmentManager.getTempBan(player.getUUID());
            if (tempBan != null) {
                player.connection.disconnect(Component.literal(
                        "[Moderation] Temporarily banned until "
                                + PunishmentManager.formatExpiresAt(tempBan.expiresAt())
                                + ". Reason: " + tempBan.reason()
                ));
                return;
            }

            PlayerProgressManager.loadPlayer(player);
            PrefixManager.updateLastKnownName(player.getUUID(), player.getGameProfile().getName());
            TeamMatchManager.rememberPlayer(player);
            NameTagManager.applyToAll(player.server);
            LivesManager.reconcileMatchStateOnJoin(player);
            if (LivesManager.ensureEliminatedIfOutOfLives(player)) {
                TeamMatchManager.applyScoreboardTeams(player.server);
                ClassXPManager.sync(player);
                syncPrefixes(player.server);
                player.server.execute(() -> GameStateManager.checkForMatchEnd(player.server));
                return;
            }
            if (GameStateManager.isRunning(player.server)
                    && GameStateManager.getMatchPhase() == MatchPhase.RUNNING
                    && GameStateManager.getCurrentMode().isTeamMode()) {
                TeamId team = MapSetManager.isClanWarSet()
                        ? TeamMatchManager.assignClanWarPlayer(player.server, player)
                        : TeamMatchManager.assignLateJoiner(
                        player.server,
                        player,
                        GameStateManager.getCurrentMode()
                );
                if (team != null) {
                    LivesManager.ensureStarted(player);
                    VoiceChatTeamManager.assignPlayerToVoiceGroup(player);
                    player.sendSystemMessage(Component.literal(
                            "[WAR] Вы присоединены к команде " + team.displayName() + "."
                    ).withStyle(team.chatColor()));
                }
            }
            LobbyManager.moveToLobby(player);
            MapSetManager.sync(player, MapSetManager.isVoting());
            GameStateManager.showCurrentSetRewardOnJoin(player);
            ContractManager.ensureTracker(player);
            ContractManager.giveSelectionTrackerIfAvailable(player);
            TeamMatchManager.applyScoreboardTeams(player.server);
            ClassXPManager.sync(player);
            syncPrefixes(player.server);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressManager.loadPlayer(player);

            if (DeathTransitionManager.begin(player)) {
                return;
            }

            if (LivesManager.ensureEliminatedIfOutOfLives(player)) {
                ClassXPManager.sync(player);
                return;
            }

            LobbyManager.moveToLobby(player);
            ExtractionPointManager.onPlayerRespawn(player);
            VoiceChatTeamManager.assignPlayerToVoiceGroup(player);
            ClassXPManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getNewGameMode() != GameType.SPECTATOR) return;
        if (!GameStateManager.isRunning(player.server)) return;
        if (!GameStateManager.getCurrentMode().isTeamMode()) return;

        VoiceChatTeamManager.removePlayerFromVoiceGroup(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;

        PlayerProgressManager.loadPlayer(newPlayer);

        for (String tag : oldPlayer.getTags()) {
            if (tag.equals("war.lives_init")
                    || tag.equals("war.eliminated")
                    || tag.equals(ClanWarManager.TAG_SPECTATING)
                    || tag.equals(ClanWarManager.TAG_REGROUP_PENDING)) {
                newPlayer.addTag(tag);
            }
        }

        LivesManager.copyMatchBinding(oldPlayer, newPlayer);
        PlayerTabletState.reset(newPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPostRtpAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!PostRtpProtectionManager.isProtected(player)) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long started = System.nanoTime();

        try {
            if (GameStateManager.getMatchPhase() == MatchPhase.POST_GAME
                    || GameStateManager.getMatchPhase() == MatchPhase.SET_REWARDING) {
                event.setCanceled(true);
                event.setAmount(0);
                return;
            }

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
            boolean friendlyDamage = GameStateManager.getCurrentMode().isTeamMode()
                    && TeamMatchManager.areTeammates(attacker, player);
            if (friendlyDamage) {
                if (isTeamTrapDamage(sourceText)) {
                    event.setCanceled(true);
                    event.setAmount(0);
                }
                return;
            }

        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            if (elapsedMs >= 5L) {
                TacticalTabletMod.LOGGER.warn("[PERF] onLivingHurt took {} ms victim={}", elapsedMs, player.getGameProfile().getName());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker.getUUID().equals(victim.getUUID())) return;

        boolean attackerParticipant = attacker.getTags().contains("war.playing")
                && LivesManager.isAliveParticipant(attacker);
        boolean victimParticipant = victim.getTags().contains("war.playing")
                && LivesManager.isAliveParticipant(victim);
        boolean friendlyFire = GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.areTeammates(attacker, victim);
        double actualHealthLost = MatchDamageAccounting.actualHealthLostFromFinalDamage(
                victim.getHealth(),
                event.getAmount()
        );

        if (MatchDamageAccounting.shouldRecordDamage(
                event.isCanceled(),
                friendlyFire,
                attackerParticipant,
                victimParticipant,
                actualHealthLost
        )) {
            DiscordLeaderboardService.recordMatchDamage(attacker, actualHealthLost);
            SetMatchRuntime.recordEffectivePvpDamage(attacker.getUUID(), attacker.getGameProfile().getName(),
                    victim.getUUID(), attacker.server.getTickCount());
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
        ExtractionPointManager.tick(event.getServer());
        DeathTransitionManager.tick(event.getServer());
        SpectatorCameraManager.onServerTick(event.getServer());
        GameStateManager.onServerTick(event.getServer());
        InventoryGuard.tick(event.getServer());

        if (++utilityTickCounter >= 100) {
            utilityTickCounter = 0;
            PacketHandler.clearExpiredC2SRateLimits();
            LobbyManager.keepLobbyWeatherClear(event.getServer());
            NameTagManager.applyToAll(event.getServer());
            PunishmentManager.cleanupExpired();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PostRtpProtectionManager.clear(player);
            PacketHandler.clearC2SRateLimits(player);
            boolean runningMatchParticipant = GameStateManager.isRunning(player.server)
                    && GameStateManager.getMatchPhase() == MatchPhase.RUNNING
                    && LivesManager.isAliveParticipant(player);

            PlayerProgressManager.savePlayer(player);
            DeathTransitionManager.clear(player);
            ContractManager.onPlayerDisconnect(player);
            ExtractionPointManager.onPlayerDeathOrLogout(player);
            ModerModeManager.clear(player);

            if (runningMatchParticipant) {
                TeamId disconnectedTeam = TeamMatchManager.getTeam(player);
                SetMatchRuntime.recordPlayerEliminated(player.getUUID(), player.server.getTickCount());
                LivesManager.handleDeath(player);
                if (disconnectedTeam != null && !TeamMatchManager.hasAliveParticipant(player.server, disconnectedTeam)) {
                    SetMatchRuntime.recordTeamEliminated(disconnectedTeam, player.server.getTickCount());
                }
                PlayerTabletState.reset(player);
            } else {
                RtpTimerManager.cancel(player);
                PassiveClassXPManager.clear(player);
                PlayerTabletState.reset(player);
            }

            TabletPacket.reset(player);
            if (!GameStateManager.getCurrentMode().isTeamMode()) {
                NameTagManager.remove(player);
            }
            if (!runningMatchParticipant) {
                player.removeTag("war.playing");
                player.removeTag("in_lobby");
            }
            PlayerProgressManager.unloadPlayer(player);
            player.server.execute(() -> syncPrefixes(player.server));
            player.server.execute(() -> GameStateManager.checkForMatchEnd(player.server));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        PostRtpProtectionManager.clear(victim);
        long started = System.nanoTime();

        try {
            boolean participating = isActiveMatchParticipant(victim);
            if (participating && !SetMatchRuntime.claimDeath(victim.getUUID(), victim.server.getTickCount())) return;
            PlayerDeathFinalization.process(
                    participating,
                    () -> processPlayerDeath(victim, event.getSource()),
                    () -> GameStateManager.checkForMatchEnd(victim.server)
            );
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            if (elapsedMs >= 10L) {
                TacticalTabletMod.LOGGER.warn("[PERF] onDeath took {} ms victim={}", elapsedMs, victim.getGameProfile().getName());
            }
        }
    }

    private static boolean isActiveMatchParticipant(ServerPlayer player) {
        return player != null && player.getTags().contains("war.playing");
    }

    private static void processPlayerDeath(ServerPlayer victim, DamageSource source) {
        SpectatorCameraManager.onPlayerDeath(victim);

        if (GameStateManager.isRunning(victim.server)
                && GameStateManager.getCurrentMode().isTeamMode()) {
            VoiceChatTeamManager.removePlayerFromVoiceGroup(victim);
        }

        Set<String> victimTags = victim.getTags();
        boolean victimWasPlaying = victimTags.contains("war.playing");
        boolean victimWasInLobby = victimTags.contains("in_lobby") || GameStateManager.isInLobby(victim);

        if (victimWasInLobby && !victimWasPlaying) {
            return;
        }

        if (!victimWasPlaying) {
            return;
        }

        ServerPlayer killer = source.getEntity() instanceof ServerPlayer sourceKiller ? sourceKiller : null;

        DeathTransitionManager.recordDeath(victim, source);
        CorpseLootManager.createCorpse(victim);
        PlayerProgressManager.addDeath(victim);
        DiscordLeaderboardService.recordMatchDeath(victim);
        TeamId victimTeam = TeamMatchManager.getTeam(victim);
        LivesManager.handleDeath(victim);
        if (LivesManager.isEliminated(victim)) {
            SetMatchRuntime.recordPlayerEliminated(victim.getUUID(), victim.server.getTickCount());
        }
        if (victimTeam != null && !TeamMatchManager.hasAliveParticipant(victim.server, victimTeam)) {
            SetMatchRuntime.recordTeamEliminated(victimTeam, victim.server.getTickCount());
        }

        try {
            processKillerConsequences(victim, source, killer);
        } finally {
            SetMatchRuntime.clearVictimAttribution(victim.getUUID());
        }

        ContractManager.onPlayerKilled(victim, killer);
        ExtractionPointManager.onPlayerDeathOrLogout(victim);
        ClassXPManager.sync(victim);
        if (killer != null && !killer.getUUID().equals(victim.getUUID())) {
            ClassXPManager.sync(killer);
        }
    }

    private static void processKillerConsequences(ServerPlayer victim, DamageSource source, ServerPlayer killer) {
        Entity direct = source.getDirectEntity();
        boolean victimOwnedProjectile = direct instanceof Projectile projectile
                && projectile.getOwner() != null
                && projectile.getOwner().getUUID().equals(victim.getUUID());
        boolean teammates = killer != null && GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.areTeammates(killer, victim);
        KillCreditPolicy.Outcome outcome = KillCreditPolicy.classify(
                killer != null,
                killer != null && killer.getTags().contains("war.playing"),
                killer != null && killer.getUUID().equals(victim.getUUID()),
                victimOwnedProjectile,
                teammates
        );
        if (outcome == KillCreditPolicy.Outcome.IGNORE) return;

        if (outcome == KillCreditPolicy.Outcome.TEAM_KILL) {
            int teamKills = DiscordLeaderboardService.recordTeamKill(killer.server, killer);
            killer.sendSystemMessage(Component.literal(
                    "[WAR] Тимкилл не засчитан. Тимкиллы за сет: "
                            + teamKills + "/" + TEAM_KILL_BAN_THRESHOLD + "."
            ));
            if (teamKills >= TEAM_KILL_BAN_THRESHOLD) {
                banTeamKiller(killer, teamKills);
            }
            return;
        }

        PlayerProgressManager.addKill(killer);
        DiscordLeaderboardService.recordMatchKill(killer);
        PlayerProgressManager.addCoins(killer, PlayerProgressManager.KILL_COIN_REWARD);
        for (CombatAssistTracker.AssistCredit assist : SetMatchRuntime.resolveAssists(
                victim.getUUID(), killer.getUUID(), victim.server.getTickCount())) {
            DiscordLeaderboardService.recordMatchAssist(assist.playerId(), assist.playerName());
        }

        String clazz = PlayerTabletState.getSelectedClass(killer);
        if (clazz == null || clazz.isBlank()) return;

        XPResult result = calculateXP(killer, victim, source, direct);
        if (result.xp <= 0) return;

        int awardedXp = ClassXPManager.addXP(killer, clazz, result.xp);
        XpNotifier.send(killer, awardedXp, result.reason);
    }


    private static void banTeamKiller(ServerPlayer player, int teamKills) {
        Date createdAt = new Date();
        Date expiresAt = new Date(createdAt.getTime() + TEAM_KILL_BAN_MILLIS);
        String reason = "Тимкилл: " + teamKills + " убийства союзников за сет.";
        player.server.getPlayerList().getBans().add(new UserBanListEntry(
                player.getGameProfile(),
                createdAt,
                "TacticalTablet",
                expiresAt,
                reason
        ));
        player.connection.disconnect(Component.literal(
                "[WAR] Бан на 15 минут за тимкиллы: " + teamKills + "/" + TEAM_KILL_BAN_THRESHOLD + "."
        ));
    }



    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PlayerProgressManager.onServerStarted(event.getServer());
        event.getServer().getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, event.getServer());
        event.getServer().getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, event.getServer());
        DropControlManager.enforceGameRules(event.getServer());
        GameStateManager.resetRuntime(event.getServer());
        GameStateManager.validateRuntimeRequirements(event.getServer());
        MapRotationManager.onServerStarted(event.getServer());
        KitRotationManager.onServerStarted(event.getServer());
        MapSetManager.onServerStarted(event.getServer());
        PunishmentManager.load(event.getServer());
        ClanManager.recoverCreateClanTransactions(event.getServer());
        DiscordLeaderboardService.init(event.getServer());
        LeaderboardScheduler.onServerStarted(event.getServer());
        OnlineWebhookService.onServerStarted(event.getServer());
        ZoneManager.reset(event.getServer());
        ExtractionPointManager.reset(event.getServer());
        DeathTransitionManager.clearAll();
        PrefixManager.load(event.getServer());
        PrefixManager.cleanupExpired();
        PrefixManager.updateTabNames(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MapRotationManager.onServerStopped(event.getServer());
        MapSetManager.onServerStopped();
        KitRotationManager.resetRuntime();
        MapRotationManager.resetRuntime();
        PunishmentManager.resetRuntime();
        ModerModeManager.resetAll();
        OnlineWebhookService.onServerStopped();
        LeaderboardScheduler.reset();
        DiscordLeaderboardService.resetMatch();
        DiscordWebhookClient.shutdown();
        GameStateManager.resetRuntime(event.getServer());
        VoiceChatTeamManager.shutdown(event.getServer());
        TestModeManager.reset();
        AirdropManager.resetRuntime(GameStateManager.getOverworld(event.getServer()));
        ContractManager.reset(event.getServer());
        ExtractionPointManager.reset(event.getServer());
        DeathTransitionManager.clearAll();
        RtpTimerManager.clearAll();
        PassiveClassXPManager.clearAll();
        ClassCooldownManager.resetAll();
        SafeTeleport.clearPool();
        PlayerTabletState.resetAll();
        TabletPacket.resetAll();
        InventoryLockEvents.resetTracking();
        PlayerProgressManager.resetStorage();
        PrefixManager.clearRuntime();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PlayerProgressManager.flushForShutdown();
        PrefixManager.save();
        PunishmentManager.saveAtomic();
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        PunishmentRecord mute = PunishmentManager.getMute(player.getUUID());
        if (mute != null) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    "[Moderation] You are muted for "
                            + PunishmentManager.formatRemaining(mute.expiresAt())
                            + ". Reason: " + mute.reason()
            ));
            return;
        }

        if (!PrefixManager.getRole(player).visible()) return;

        Component formatted = Component.literal("")
                .append(PrefixManager.buildChatName(player))
                .append(Component.literal(": "))
                .append(event.getMessage());
        event.setCanceled(true);
        player.server.getPlayerList().broadcastSystemMessage(formatted, false);
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!PrefixManager.getRole(player).visible()) return;

        event.setDisplayName(PrefixManager.buildDisplayName(player));
    }

    private static void syncPrefixes(net.minecraft.server.MinecraftServer server) {
        PrefixManager.cleanupExpired();
        PrefixManager.syncAll(server);
        PrefixManager.updateTabNames(server);
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
