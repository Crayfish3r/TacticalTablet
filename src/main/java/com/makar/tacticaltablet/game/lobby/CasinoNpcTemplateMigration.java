package com.makar.tacticaltablet.game.lobby;

import com.makar.tacticaltablet.casino.CasinoSessionManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Adds only missing casino villagers from a structure resource; existing lobby blocks are never placed again. */
final class CasinoNpcTemplateMigration {
    private static final double MATCH_DISTANCE_SQUARED = 2.25D;

    private CasinoNpcTemplateMigration() {
    }

    static Result migrate(ServerLevel lobby, ResourceLocation templateId, BlockPos origin) {
        if (lobby == null || templateId == null || origin == null) return Result.failed();
        ResourceLocation resourceId = new ResourceLocation(
                templateId.getNamespace(),
                "structures/" + templateId.getPath() + ".nbt"
        );
        Resource resource = lobby.getServer().getResourceManager().getResource(resourceId).orElse(null);
        if (resource == null) {
            TacticalTabletMod.LOGGER.error("Casino NPC migration failed: resource {} is unavailable", resourceId);
            return Result.failed();
        }

        try (InputStream input = resource.open()) {
            CompoundTag structure = NbtIo.readCompressed(input);
            List<TemplateNpc> templateNpcs = readCasinoNpcs(structure, origin);
            if (templateNpcs.size() != 4) {
                TacticalTabletMod.LOGGER.error(
                        "Casino NPC migration rejected {}: expected 4 named villagers, found {}",
                        resourceId,
                        templateNpcs.size()
                );
                return new Result(false, templateNpcs.size(), 0, 0);
            }

            // Force-load the four anchor chunks before inspecting saved entities. Without this,
            // getAllEntities() can miss an already saved villager and a duplicate spawn is attempted.
            for (TemplateNpc npc : templateNpcs) {
                lobby.getChunkAt(BlockPos.containing(
                        npc.position().x,
                        npc.position().y,
                        npc.position().z
                ));
            }

            List<Villager> existing = new ArrayList<>();
            for (Entity entity : lobby.getAllEntities()) {
                if (entity instanceof Villager villager && isCasinoVillager(villager)) existing.add(villager);
            }

            List<Villager> unmatchedExisting = new ArrayList<>(existing);
            List<TemplateNpc> missing = new ArrayList<>();
            int present = 0;
            for (TemplateNpc npc : templateNpcs) {
                Villager nearest = null;
                double nearestDistance = MATCH_DISTANCE_SQUARED;
                for (Villager candidate : unmatchedExisting) {
                    double distance = candidate.position().distanceToSqr(npc.position());
                    if (distance <= nearestDistance) {
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
                if (nearest == null) {
                    missing.add(npc);
                    continue;
                }
                stabilize(nearest);
                unmatchedExisting.remove(nearest);
                present++;
            }

            int spawned = 0;
            for (TemplateNpc npc : missing) {
                if (spawn(lobby, npc)) spawned++;
            }
            present += spawned;
            if (!unmatchedExisting.isEmpty()) {
                TacticalTabletMod.LOGGER.warn(
                        "Found {} additional named casino villager(s) outside the four template anchors; preserving them",
                        unmatchedExisting.size()
                );
            }
            return new Result(present == templateNpcs.size(), templateNpcs.size(), present, spawned);
        } catch (IOException | RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Casino NPC migration failed while reading " + resourceId, exception);
            return Result.failed();
        }
    }

    private static List<TemplateNpc> readCasinoNpcs(CompoundTag structure, BlockPos origin) {
        List<TemplateNpc> result = new ArrayList<>();
        ListTag entities = structure.getList("entities", Tag.TAG_COMPOUND);
        for (Tag raw : entities) {
            if (!(raw instanceof CompoundTag entry)) continue;
            CompoundTag entityTag = entry.getCompound("nbt");
            if (!"minecraft:villager".equals(entityTag.getString("id")) || !hasCasinoName(entityTag)) continue;
            ListTag position = entry.getList("pos", Tag.TAG_DOUBLE);
            if (position.size() != 3) continue;
            Vec3 worldPosition = new Vec3(
                    origin.getX() + position.getDouble(0),
                    origin.getY() + position.getDouble(1),
                    origin.getZ() + position.getDouble(2)
            );
            result.add(new TemplateNpc(entityTag.copy(), worldPosition));
        }
        return List.copyOf(result);
    }

    private static boolean hasCasinoName(CompoundTag entityTag) {
        if (!entityTag.contains("CustomName", Tag.TAG_STRING)) return false;
        Component name = Component.Serializer.fromJson(entityTag.getString("CustomName"));
        return name != null && CasinoSessionManager.NPC_NAME.equals(name.getString());
    }

    private static boolean isCasinoVillager(Villager villager) {
        return villager.hasCustomName()
                && CasinoSessionManager.NPC_NAME.equals(villager.getCustomName().getString());
    }

    private static boolean spawn(ServerLevel lobby, TemplateNpc npc) {
        Entity loaded = EntityType.loadEntityRecursive(sanitizeEntityTag(npc.entityTag()), lobby, entity -> {
            entity.moveTo(npc.position().x, npc.position().y, npc.position().z,
                    entity.getYRot(), entity.getXRot());
            return entity;
        });
        if (!(loaded instanceof Villager villager) || !isCasinoVillager(villager)) return false;
        stabilize(villager);
        boolean added = lobby.addFreshEntity(villager);
        if (!added) {
            TacticalTabletMod.LOGGER.error(
                    "Casino NPC spawn was rejected at [{}, {}, {}]",
                    npc.position().x,
                    npc.position().y,
                    npc.position().z
            );
        }
        return added;
    }

    /** Mirrors vanilla StructureTemplate entity placement: template UUIDs must never be reused. */
    static CompoundTag sanitizeEntityTag(CompoundTag source) {
        CompoundTag sanitized = source.copy();
        sanitized.remove("UUID");
        sanitized.remove("UUIDMost");
        sanitized.remove("UUIDLeast");
        return sanitized;
    }

    private static void stabilize(Villager villager) {
        villager.setPersistenceRequired();
        villager.setNoAi(true);
        villager.setInvulnerable(true);
    }

    record Result(boolean successful, int expected, int present, int spawned) {
        static Result failed() {
            return new Result(false, 0, 0, 0);
        }
    }

    private record TemplateNpc(CompoundTag entityTag, Vec3 position) {
    }
}
