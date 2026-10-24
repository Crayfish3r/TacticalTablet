package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.UUID;

/** Optional boundary. Its signatures contain no TacticalNPC classes. */
public interface NpcIntegration {
    String MOD_ID = "tacticalnpcai";
    String API_VERSION = "0.2.0";

    interface Policy {
        boolean active();
        boolean canAttack(ServerPlayer player);
    }
    record DeathIdentity(UUID ownerMatchId, String name) { }

    /** Null for ordinary mobs, unowned NPCs and entities already being removed. */
    DeathIdentity deathIdentity(LivingEntity victim);
    record Result(boolean success, boolean invalidWeapon, List<UUID> ids, String diagnostic) {
        public Result { ids = List.copyOf(ids); }
    }

    void register(UUID matchId, Policy policy);
    Result spawn(ServerLevel level, UUID matchId, UUID groupId, Vec3 origin, NpcLoadoutSelector.Selection selection);
    List<UUID> activeIds(UUID matchId);
    void deactivate(UUID matchId);
    int remove(UUID matchId);

    static NpcIntegration connect(MinecraftServer server) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            TacticalTabletMod.LOGGER.info("TacticalNPC absent; optional NPC encounters unavailable");
            return null;
        }
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        if (!API_VERSION.equals(version)) {
            TacticalTabletMod.LOGGER.warn("TacticalNPC API incompatible: detected {}, required {}", version, API_VERSION);
            return null;
        }
        try {
            // First loading point for the optional typed implementation, after the exact-version gate.
            return new TacticalNpcV020Integration(server);
        } catch (RuntimeException | LinkageError exception) {
            TacticalTabletMod.LOGGER.error("TacticalNPC {} API could not load; encounters disabled", version, exception);
            return null;
        }
    }
}
