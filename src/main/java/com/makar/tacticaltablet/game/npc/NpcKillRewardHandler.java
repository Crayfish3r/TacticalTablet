package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.ResponsiblePlayerResolver;
import com.makar.tacticaltablet.game.TacticalKillFeed;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Separate from player death transitions, PvP attribution and match winner accounting. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID)
public final class NpcKillRewardHandler {
    public static final int NPC_KILL_COINS = 3;
    public static final int NPC_KILL_XP = 4;

    private NpcKillRewardHandler() { }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        var victim = event.getEntity();
        if (event.isCanceled() || victim.level().isClientSide || victim instanceof ServerPlayer
                || victim.getHealth() > 0) return;
        var identity = NpcEncounterManager.rewardIdentity(victim);
        if (identity == null) return;
        var source = event.getSource();
        if (source.is(DamageTypes.GENERIC_KILL)) return; // /kill is administrative, even with a supplied owner.
        ServerPlayer killer = ResponsiblePlayerResolver.resolve(source);
        if (killer == null || killer instanceof FakePlayer) return;
        // No proximity/last-attacker fallback, tame mobs or controlled vehicles. TaCZ bullets
        // are Projectiles with their owner captured in the authoritative DamageSource.
        var direct = source.getDirectEntity();
        boolean confirmed = direct == killer || direct instanceof Projectile projectile
                && projectile.getOwner() == killer;
        if (!confirmed || !NpcEncounterManager.canReward(killer, victim)
                || !NpcEncounterManager.claimDeath(identity.ownerMatchId(), victim.getUUID())) return;

        var reward = award(killer);
        com.makar.tacticaltablet.game.arena.ArenaMatchRuntime.npcKill(killer, identity.ownerMatchId());
        ClassXPManager.sync(killer);
        TacticalKillFeed.publishNpcKill(killer, victim.getUUID(), identity.name(), source, reward);
    }

    static TacticalKillFeed.KillReward award(ServerPlayer killer) {
        int before = PlayerProgressManager.getCoins(killer);
        PlayerProgressManager.addCoins(killer, NPC_KILL_COINS);
        int coins = Math.max(0, PlayerProgressManager.getCoins(killer) - before);
        int xp = ClassXPManager.addBaseXPDeferredSync(killer,
                PlayerTabletState.getSelectedClass(killer), NPC_KILL_XP);
        return new TacticalKillFeed.KillReward(coins, xp);
    }
}
