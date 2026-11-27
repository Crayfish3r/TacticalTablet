package com.makar.tacticaltablet.game.npc;

import com.google.gson.JsonParser;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.nio.file.Files;
import java.util.UUID;

/** Synthetic event/source probes plus a real melee death; no production test hooks. */
final class NpcKillRewardSmoke {
    static void verify(MinecraftServer server, ServerPlayer player, LivingEntity victim,
                       LivingEntity survivor, LivingEntity foreign) throws Exception {
        int initialCoins = PlayerProgressManager.getCoins(player), kills = PlayerProgressManager.getKills(player);
        PlayerTabletState.setSelectedClass(player, "scout");
        PlayerProgressManager.setXP(player, "scout", 0);
        PlayerProgressManager.setXpBoostEnabled(player, true);
        var melee = player.damageSources().playerAttack(player);
        victim.setHealth(0);
        var canceled = new LivingDeathEvent(victim, melee); canceled.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceled);
        post(victim, player.damageSources().fall());
        post(victim, player.damageSources().mobAttack(survivor));
        post(victim, player.damageSources().genericKill());
        player.addTag("war.eliminated"); post(victim, melee); player.removeTag("war.eliminated");
        var outsider = new ServerPlayer(server, server.overworld(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "RewardOutsider"));
        post(victim, player.damageSources().playerAttack(outsider));
        float foreignHealth = foreign.getHealth(); foreign.setHealth(0);
        post(foreign, melee); foreign.setHealth(foreignHealth);
        var orphan = new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW,
                server.overworld());
        post(victim, new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.ARROW), orphan, null));
        check(PlayerProgressManager.getCoins(player) == initialCoins, "Invalid/canceled death granted coins");
        check(PlayerProgressManager.getXP(player, "scout") == 0, "Invalid/canceled death granted XP");
        victim.setHealth(victim.getMaxHealth());
        check(victim.hurt(melee, Float.MAX_VALUE), "Real melee damage was rejected");
        check(PlayerProgressManager.getCoins(player) == initialCoins + 3, "Melee coins != 3");
        check(PlayerProgressManager.getXP(player, "scout") == 4, "Boost changed NPC XP != 4");
        for (int i = 0; i < 5; i++) post(victim, melee);
        check(PlayerProgressManager.getCoins(player) == initialCoins + 3, "Duplicate rewarded twice");

        // Actual TaCZ entity class, resolved through Minecraft registry to keep test compilation optional.
        var bulletEntity = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("tacz:bullet")).create(server.overworld());
        check(bulletEntity instanceof Projectile, "TaCZ bullet is not a confirmed Projectile");
        var bullet = (Projectile) bulletEntity; bullet.setOwner(player);
        var bulletSource = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.ARROW), bullet, player);
        survivor.setHealth(0);
        post(survivor, bulletSource);
        check(PlayerProgressManager.getCoins(player) == initialCoins + 6, "TaCZ projectile coins != 3");
        check(PlayerProgressManager.getXP(player, "scout") == 8, "TaCZ projectile XP != 4");
        post(survivor, bulletSource);
        survivor.setHealth(survivor.getMaxHealth());
        check(PlayerProgressManager.getCoins(player) == initialCoins + 6, "Repeated bullet rewarded twice");
        bullet.discard();
        check(PlayerProgressManager.getKills(player) == kills, "NPC changed PvP kills");

        for (String invalid : new String[]{"", "does_not_exist", "droneoperator", PlayerProgressManager.getShopClasses()[0]}) {
            PlayerTabletState.setSelectedClass(player, invalid);
            var reward = NpcKillRewardHandler.award(player);
            check(reward.awardedCoins() == 3 && reward.awardedXp() == 0, "Invalid/locked/shop reward: " + invalid);
        }
        PlayerTabletState.setSelectedClass(player, "scout");
        PlayerProgressManager.setXP(player, "scout", 299);
        check(ClassXPManager.addBaseXPDeferredSync(player, "scout", 4) == 1, "Tier cap did not clamp to 1 XP");
        check(ClassXPManager.addBaseXPDeferredSync(player, "scout", 4) == 0, "Tier cap gained XP");
        PlayerProgressManager.setXP(player, "scout", 8);
        PlayerProgressManager.setCoins(player, Integer.MAX_VALUE - 1);
        var clamped = NpcKillRewardHandler.award(player);
        check(clamped.awardedCoins() == 1 && clamped.awardedXp() == 4, "Actual balance cap not reflected");
        PlayerProgressManager.setCoins(player, initialCoins + 6);
        PlayerProgressManager.setXP(player, "scout", 8);
        Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("npc-reward-probe.json"),
                "{\"player\":\"" + player.getUUID() + "\",\"coins\":" + (initialCoins + 6) + ",\"xp\":8}");
        TacticalTabletMod.LOGGER.info("TT_NPC_REWARDS_PASS melee death, TaCZ projectile source, duplicate/canceled events, foreign match, outsider/eliminated, environment/NPC/admin/orphan exclusions, boost bypass, class eligibility and cap, PvP count");
    }

    static void verifyEnding(ServerPlayer player, LivingEntity npc) {
        int before = PlayerProgressManager.getCoins(player);
        npc.setHealth(0); post(npc, player.damageSources().playerAttack(player));
        check(before == PlayerProgressManager.getCoins(player), "ENDING rewarded death");
        npc.setHealth(npc.getMaxHealth());
    }

    static void verifyRestart(MinecraftServer server) throws Exception {
        var expected = JsonParser.parseString(Files.readString(server.getWorldPath(LevelResource.ROOT)
                .resolve("npc-reward-probe.json"))).getAsJsonObject();
        var player = new ServerPlayer(server, server.overworld(), new com.mojang.authlib.GameProfile(
                UUID.fromString(expected.get("player").getAsString()), "NpcProbe"));
        PlayerProgressManager.loadPlayer(player);
        check(PlayerProgressManager.getCoins(player) == expected.get("coins").getAsInt(), "Coins lost after restart");
        check(PlayerProgressManager.getXP(player, "scout") == expected.get("xp").getAsInt(), "XP lost after restart");
        TacticalTabletMod.LOGGER.info("TT_NPC_REWARDS_RESTART_PASS coins and XP restored by existing persistence");
    }

    private static void post(LivingEntity npc, DamageSource source) { MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(npc, source)); }
    private static void check(boolean condition, String diagnostic) { if (!condition) throw new AssertionError(diagnostic); }
}
