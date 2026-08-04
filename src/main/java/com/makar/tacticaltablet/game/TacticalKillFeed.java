package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.core.TacticalTabletServerConfig;
import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Locale;

public final class TacticalKillFeed {
    private TacticalKillFeed() {
    }

    public static void publish(ServerPlayer victim, ServerPlayer killer, DamageSource deathSource,
                               CombatAttributionLedger.Entry fallback) {
        if (victim == null || !TacticalTabletServerConfig.isKillFeedEnabled()) return;
        KillFeedPacket.Cause cause = classifyCause(deathSource, fallback != null);
        String weaponId = fallback == null
                ? CombatAttributionLedger.extractWeaponId(deathSource)
                : fallback.weaponId();
        KillFeedPacket packet = new KillFeedPacket(
                killer == null ? "" : killer.getGameProfile().getName(),
                victim.getGameProfile().getName(), cause, weaponId);
        for (ServerPlayer viewer : victim.server.getPlayerList().getPlayers()) {
            PacketHandler.sendToPlayer(viewer, packet);
        }
    }

    static KillFeedPacket.Cause classifyCause(DamageSource source, boolean attributedFallback) {
        String id = source == null || source.getMsgId() == null
                ? "" : source.getMsgId().toLowerCase(Locale.ROOT);
        return classifyCause(id, attributedFallback);
    }

    static KillFeedPacket.Cause classifyCause(String sourceId, boolean attributedFallback) {
        String id = sourceId == null ? "" : sourceId.toLowerCase(Locale.ROOT);
        if (id.contains("fall") || id.contains("fly_into_wall")) return KillFeedPacket.Cause.FALL;
        if (id.contains("fire") || id.contains("lava") || id.contains("hot_floor")) return KillFeedPacket.Cause.FIRE;
        if (attributedFallback && (id.contains("magic") || id.contains("indirect_magic")
                || id.contains("wither"))) return KillFeedPacket.Cause.BLEEDING;
        return KillFeedPacket.Cause.NONE;
    }
}
