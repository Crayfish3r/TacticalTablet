package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.core.TacticalTabletServerConfig;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-thread-only, match-scoped attribution for damage sources which later lose their player owner.
 */
public final class CombatAttributionLedger {
    public static final int DEFAULT_ATTRIBUTION_WINDOW_TICKS =
            TacticalTabletServerConfig.DEFAULT_COMBAT_ATTRIBUTION_WINDOW_SECONDS * 20;

    private static final CombatAttributionState STATE = new CombatAttributionState();
    private static final Map<UUID, PendingHit> PENDING_HITS = new java.util.HashMap<>();

    private CombatAttributionLedger() {
    }

    public static void observeIncomingAttack(ServerPlayer victim, DamageSource source, float reportedDamage,
                                             boolean canceled) {
        if (!canRecordVictim(victim) || canceled || reportedDamage <= 0.0F) return;
        Entry previous = STATE.get(victim.getUUID());
        if (recordValidated(victim, source, reportedDamage)) {
            PENDING_HITS.put(victim.getUUID(), new PendingHit(source, previous));
        }
    }

    public static void observeOriginalDamage(ServerPlayer victim, DamageSource source, float reportedDamage,
                                             boolean canceled) {
        if (!canRecordVictim(victim) || !canceled) return;
        if (reportedDamage > 0.0F && isSupportedCanceledOriginalSource(source)) {
            recordValidated(victim, source, reportedDamage);
            PENDING_HITS.remove(victim.getUUID());
        } else {
            rollbackPending(victim, source);
        }
    }

    /** Exact integration point for MDC once it can call TacticalTablet after applying damage. */
    public static void recordAppliedDamage(ServerPlayer victim, DamageSource source, float effectiveDamage) {
        if (!canRecordVictim(victim) || effectiveDamage <= 0.0F) return;
        if (recordValidated(victim, source, effectiveDamage) && victim != null) {
            PENDING_HITS.remove(victim.getUUID());
        }
    }

    public static void rejectAppliedDamage(ServerPlayer victim, DamageSource source) {
        rollbackPending(victim, source);
    }

    public static Optional<Entry> findFresh(ServerPlayer victim) {
        if (victim == null) return Optional.empty();
        return STATE.findFresh(victim.getUUID(), victim.server.getTickCount(),
                TacticalTabletServerConfig.getCombatAttributionWindowTicks());
    }

    public static ServerPlayer resolveFreshAttacker(ServerPlayer victim) {
        return findFresh(victim)
                .map(entry -> victim.server.getPlayerList().getPlayer(entry.attackerId()))
                .orElse(null);
    }

    public static void clear(UUID victimId) {
        if (victimId != null) {
            STATE.clear(victimId);
            PENDING_HITS.remove(victimId);
        }
    }

    public static void reset() {
        STATE.reset();
        PENDING_HITS.clear();
    }

    public static String extractWeaponId(DamageSource source) {
        return source == null ? "" : extractWeaponId(source.getDirectEntity()).orElse("");
    }

    static boolean shouldRecord(UUID victimId, UUID attackerId, boolean victimEligible,
                                boolean attackerEligible, boolean teammates, float effectiveDamage) {
        return victimId != null && attackerId != null && !victimId.equals(attackerId)
                && victimEligible && attackerEligible && !teammates && effectiveDamage > 0.0F;
    }

    static boolean isVictimRecordable(boolean alive, boolean deadOrDying) {
        return alive && !deadOrDying;
    }

    private static boolean recordValidated(ServerPlayer victim, DamageSource source, float damage) {
        if (!canRecordVictim(victim) || source == null) return false;
        ServerPlayer attacker = ResponsiblePlayerResolver.resolve(source);
        boolean teammates = attacker != null && GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.areTeammates(attacker, victim);
        if (!shouldRecord(victim.getUUID(), attacker == null ? null : attacker.getUUID(),
                ActivePvpParticipant.isEligible(victim), ActivePvpParticipant.isEligible(attacker),
                teammates, damage)) return false;

        Entity direct = source.getDirectEntity();
        Entity ownerOrController = ownerOrController(direct);
        if (ownerOrController == null) ownerOrController = ownerOrController(source.getEntity());
        STATE.put(victim.getUUID(), new Entry(
                attacker.getUUID(), attacker.getGameProfile().getName(), victim.server.getTickCount(),
                safe(source.getMsgId()), entityTypeId(direct), entityUuid(direct), entityUuid(ownerOrController),
                extractWeaponId(direct).orElse("")
        ));
        return true;
    }

    private static boolean canRecordVictim(ServerPlayer victim) {
        if (victim == null) return false;
        if (!isVictimRecordable(victim.isAlive(), victim.isDeadOrDying())) {
            clear(victim.getUUID());
            return false;
        }
        return true;
    }

    private static void rollbackPending(ServerPlayer victim, DamageSource source) {
        if (victim == null) return;
        PendingHit pending = PENDING_HITS.get(victim.getUUID());
        if (pending == null || pending.source() != source) return;
        PENDING_HITS.remove(victim.getUUID());
        if (pending.previous() == null) STATE.clear(victim.getUUID());
        else STATE.put(victim.getUUID(), pending.previous());
    }

    private static boolean isSupportedCanceledOriginalSource(DamageSource source) {
        if (source == null) return false;
        String marker = (safe(source.getMsgId()) + " " + entityTypeId(source.getDirectEntity())
                + " " + entityTypeId(source.getEntity())).toLowerCase(java.util.Locale.ROOT);
        return marker.contains("tacz") || marker.contains("timeless") || marker.contains("modern_warfare");
    }

    private static Entity ownerOrController(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getOwner() != null) return projectile.getOwner();
        if (entity instanceof OwnableEntity ownable && ownable.getOwner() != null) return ownable.getOwner();
        return entity == null ? null : entity.getControllingPassenger();
    }

    private static Optional<String> extractWeaponId(Entity direct) {
        if (direct == null) return Optional.empty();
        for (String methodName : new String[]{"getGunId", "getWeaponId"}) {
            try {
                Method method = direct.getClass().getMethod(methodName);
                Object value = method.invoke(direct);
                if (value instanceof ResourceLocation id) return Optional.of(id.toString());
                if (value instanceof String text && ResourceLocation.tryParse(text) != null) return Optional.of(text);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Optional integration: the direct entity may not expose weapon metadata.
            }
        }
        return Optional.empty();
    }

    private static String entityTypeId(Entity entity) {
        if (entity == null) return "";
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? "" : id.toString();
    }

    private static UUID entityUuid(Entity entity) {
        return entity == null ? null : entity.getUUID();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Entry(UUID attackerId, String attackerName, int serverTick, String damageSourceType,
                        String directEntityType, UUID directEntityId, UUID ownerOrControllerId,
                        String weaponId) {
    }

    private record PendingHit(DamageSource source, Entry previous) {
    }
}
