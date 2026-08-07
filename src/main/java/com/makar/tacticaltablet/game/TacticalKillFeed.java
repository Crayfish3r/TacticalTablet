package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.core.TacticalTabletServerConfig;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Locale;

public final class TacticalKillFeed {
    private TacticalKillFeed() {
    }

    public static void publish(ServerPlayer victim, ServerPlayer killer, DamageSource deathSource,
                               CombatAttributionLedger.Entry fallback, KillReward reward) {
        if (victim == null || !TacticalTabletServerConfig.isKillFeedEnabled()) return;
        KillFeedPacket.Cause cause = classifyCause(deathSource, fallback != null);
        String weaponId = fallback == null
                ? CombatAttributionLedger.extractWeaponId(deathSource)
                : fallback.weaponId();
        TeamId killerTeam = killer == null ? null : TeamMatchManager.getTeam(killer);
        TeamId victimTeam = TeamMatchManager.getTeam(victim);
        KillReward safeReward = reward == null ? KillReward.NONE : reward;

        for (ServerPlayer viewer : victim.server.getPlayerList().getPlayers()) {
            if (!isMatchViewer(viewer, victim)) continue;
            boolean rewardViewer = killer != null && viewer.getUUID().equals(killer.getUUID());
            PacketHandler.sendToPlayer(viewer, new KillFeedPacket(
                    killer == null ? null : killer.getUUID(),
                    killer == null ? "" : killer.getGameProfile().getName(),
                    color(killerTeam), victim.getUUID(), victim.getGameProfile().getName(), color(victimTeam),
                    cause, weaponDisplayName(weaponId), victim.server.getTickCount(),
                    rewardViewer ? safeReward.awardedCoins() : 0,
                    rewardViewer ? safeReward.awardedXp() : 0));
        }
    }

    static KillFeedPacket.Cause classifyCause(DamageSource source, boolean attributedFallback) {
        String id = source == null || source.getMsgId() == null
                ? "" : source.getMsgId().toLowerCase(Locale.ROOT);
        return classifyCause(id, attributedFallback);
    }

    static KillFeedPacket.Cause classifyCause(String sourceId, boolean attributedFallback) {
        String id = sourceId == null ? "" : sourceId.toLowerCase(Locale.ROOT);
        if (id.contains("lava")) return KillFeedPacket.Cause.LAVA;
        if (id.contains("outsideborder") || id.contains("outside_border")
                || id.contains("worldborder") || id.contains("zone")) return KillFeedPacket.Cause.ZONE;
        if (id.contains("fall") || id.contains("fly_into_wall")) return KillFeedPacket.Cause.FALL;
        if (id.contains("fire") || id.contains("hot_floor")) return KillFeedPacket.Cause.FIRE;
        if (attributedFallback && (id.contains("magic") || id.contains("indirect_magic")
                || id.contains("wither") || id.contains("injury") || id.contains("bleed"))) {
            return KillFeedPacket.Cause.BLEEDING;
        }
        return KillFeedPacket.Cause.NONE;
    }

    static String weaponDisplayName(String weaponId) {
        if (weaponId == null || weaponId.isBlank()) return "";
        int separator = weaponId.indexOf(':');
        String path = separator >= 0 ? weaponId.substring(separator + 1) : weaponId;
        return path.replace('_', '-').replace('/', ' ').toUpperCase(Locale.ROOT);
    }

    private static boolean isMatchViewer(ServerPlayer player, ServerPlayer victim) {
        if (player == null || MatchAdmissionManager.isLateSpectator(player)) return false;
        if (victim != null && player.getUUID().equals(victim.getUUID())) return true;
        return player.getTags().contains("war.playing")
                || MatchAdmissionManager.isCurrentMatchParticipant(player.getUUID());
    }

    private static int color(TeamId team) {
        if (team == null) return KillFeedPacket.NO_TEAM_COLOR;
        int color = team.textColor();
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        return red * 299 + green * 587 + blue * 114 < 70_000
                ? KillFeedPacket.NO_TEAM_COLOR : color;
    }

    public record KillReward(int awardedCoins, int awardedXp) {
        public static final KillReward NONE = new KillReward(0, 0);

        public KillReward {
            awardedCoins = Math.max(0, awardedCoins);
            awardedXp = Math.max(0, awardedXp);
        }
    }
}
