package com.makar.tacticaltablet.game.npc;

import dev.zuma.tacticalnpc.api.MatchNpcPolicy;
import dev.zuma.tacticalnpc.api.SpawnGroupRequest;
import dev.zuma.tacticalnpc.api.SpawnMemberSpec;
import dev.zuma.tacticalnpc.api.SpawnResult;
import dev.zuma.tacticalnpc.api.TacticalNpcMatchApi;
import dev.zuma.tacticalnpc.core.OrderState;
import dev.zuma.tacticalnpc.core.NpcDifficulty;
import dev.zuma.tacticalnpc.entity.TacticalNpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

/** The only class allowed to link TacticalNPC. Never discovered as a Forge event subscriber. */
final class TacticalNpcV020Integration implements NpcIntegration {
    private static final boolean API_SUPPORTED = verifyDifficultyApi();
    private final TacticalNpcMatchApi api;

    TacticalNpcV020Integration(MinecraftServer server) {
        if (!API_SUPPORTED) throw new IllegalStateException("TacticalNPC difficulty API unavailable");
        api = TacticalNpcMatchApi.get(server);
    }

    /** Same version number can name an older binary. Verify once, inside the optional typed boundary. */
    private static boolean verifyDifficultyApi() {
        try {
            NpcDifficulty.valueOf("HARD"); NpcDifficulty.valueOf("DEFAULT");
            SpawnMemberSpec.class.getConstructor(ResourceLocation.class, TacticalNpcEntity.AmmoPolicy.class,
                    int.class, OrderState.Kind.class, double.class, NpcDifficulty.class);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            throw new IllegalStateException("TacticalNPC 0.2.0 requires NpcDifficulty HARD/DEFAULT and six-argument SpawnMemberSpec; update the runtime JAR", exception);
        }
    }

    @Override public DeathIdentity deathIdentity(LivingEntity victim) {
        if (!(victim instanceof TacticalNpcEntity npc) || npc.ownerMatchId() == null
                || npc.groupId() == null || npc.isRemoved() || !npc.isAddedToWorld()) return null;
        return new DeathIdentity(npc.ownerMatchId(), npc.npcName());
    }

    @Override public void register(UUID matchId, Policy policy) {
        api.registerPolicy(matchId, new MatchNpcPolicy() {
            @Override public boolean isActive() { return policy.active(); }
            @Override public boolean canAttack(ServerPlayer player) { return policy.canAttack(player); }
        });
    }

    @Override public Result spawn(ServerLevel level, UUID matchId, UUID groupId, Vec3 origin,
                                  NpcLoadoutSelector.Selection selection) {
        var preset = selection.preset();
        OrderState.Kind order = switch (preset.behavior()) {
            case "ATTACK" -> OrderState.Kind.ASSAULT;
            case "DEFEND" -> OrderState.Kind.DEFEND_AREA;
            case "STAND" -> OrderState.Kind.HOLD_POSITION;
            case "PATROL" -> OrderState.Kind.PATROL;
            default -> throw new IllegalArgumentException("Unsupported NPC behavior " + preset.behavior());
        };
        var members = selection.weapons().stream().map(weapon -> new SpawnMemberSpec(
                new ResourceLocation(weapon), TacticalNpcEntity.AmmoPolicy.valueOf(preset.ammoPolicy()),
                preset.reserve(), order, preset.radius(), NpcDifficulty.valueOf(preset.difficulty()))).toList();
        var result = api.spawn(new SpawnGroupRequest(level, matchId, groupId, origin, 0, 0, members));
        return new Result(result.success(), result.status() == SpawnResult.Status.INVALID_WEAPON,
                result.entityIds(), result.status() + ": " + result.diagnostic());
    }

    @Override public List<UUID> activeIds(UUID matchId) { return api.activeNpcIds(matchId); }
    @Override public void deactivate(UUID matchId) { api.deactivate(matchId); }
    @Override public int remove(UUID matchId) { return api.remove(matchId); }
}
