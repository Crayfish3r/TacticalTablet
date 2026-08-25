package com.makar.tacticaltablet.game.lobby;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/** Installs lobby:spawn once and records the decision in dimension SavedData. */
public final class LobbyBootstrapManager {
    static final int CURRENT_VERSION = 1;
    static final ResourceLocation LOBBY_SPAWN_TEMPLATE = new ResourceLocation("lobby", "spawn");
    static final BlockPos LOBBY_SPAWN_ORIGIN = new BlockPos(-10, 64, -10);
    private static final Vec3i LEGACY_STRUCTURE_SIZE = new Vec3i(20, 20, 20);

    private LobbyBootstrapManager() {
    }

    public static boolean bootstrap(MinecraftServer server) {
        ServerLevel lobby = GameStateManager.getLobbyLevel(server);
        if (lobby == null) {
            TacticalTabletMod.LOGGER.error(
                    "Lobby bootstrap skipped: required dimension lobby:lobby is unavailable");
            return false;
        }

        LobbyBootstrapSavedData data = lobby.getDataStorage().computeIfAbsent(
                LobbyBootstrapSavedData::load,
                LobbyBootstrapSavedData::new,
                LobbyBootstrapSavedData.DATA_NAME
        );
        if (data.version() >= CURRENT_VERSION) {
            TacticalTabletMod.LOGGER.info(
                    "Lobby bootstrap v{} already committed; preserving lobby blocks",
                    data.version());
            return true;
        }
        Optional<StructureTemplate> template = lobby.getStructureManager().get(LOBBY_SPAWN_TEMPLATE);
        boolean hasContent = targetVolumeHasContent(lobby, template.orElse(null));
        LobbyBootstrapPolicy.Action action = LobbyBootstrapPolicy.decide(
                data.version(), CURRENT_VERSION, hasContent, template.isPresent());

        return switch (action) {
            case SKIP_ALREADY_BOOTSTRAPPED -> {
                TacticalTabletMod.LOGGER.info(
                        "Lobby bootstrap v{} already committed; preserving lobby blocks",
                        data.version());
                yield true;
            }
            case MARK_EXISTING_CONTENT -> {
                data.markVersion(CURRENT_VERSION);
                TacticalTabletMod.LOGGER.info(
                        "Lobby bootstrap migration detected existing content; recorded v{} without placing lobby:spawn",
                        CURRENT_VERSION);
                yield true;
            }
            case PLACE_STRUCTURE -> placeAndMark(lobby, template.orElseThrow(), data);
            case FAIL_MISSING_TEMPLATE -> {
                TacticalTabletMod.LOGGER.error(
                        "Lobby bootstrap failed: embedded structure lobby:spawn is unavailable");
                yield false;
            }
        };
    }

    static boolean targetVolumeHasContent(ServerLevel lobby, StructureTemplate template) {
        Vec3i size = template == null ? LEGACY_STRUCTURE_SIZE : template.getSize();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    if (!lobby.getBlockState(LOBBY_SPAWN_ORIGIN.offset(x, y, z)).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Restores only missing fragile template blocks; never overwrites existing lobby blocks. */
    public static int repairMissingFragileBlocks(MinecraftServer server) {
        ServerLevel lobby = GameStateManager.getLobbyLevel(server);
        if (lobby == null) {
            TacticalTabletMod.LOGGER.error("Lobby fragile-block repair failed: lobby:lobby is unavailable");
            return -1;
        }

        Optional<StructureTemplate> template = lobby.getStructureManager().get(LOBBY_SPAWN_TEMPLATE);
        if (template.isEmpty()) {
            TacticalTabletMod.LOGGER.error("Lobby fragile-block repair failed: lobby:spawn is unavailable");
            return -1;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setKnownShape(true);
        int repaired = 0;
        for (StructureTemplate.StructureBlockInfo block :
                template.orElseThrow().filterBlocks(LOBBY_SPAWN_ORIGIN, settings, Blocks.MOSS_CARPET)) {
            if (!lobby.getBlockState(block.pos()).isAir()) continue;
            if (lobby.setBlock(block.pos(), block.state(), Block.UPDATE_CLIENTS)) repaired++;
        }

        TacticalTabletMod.LOGGER.info(
                "Lobby fragile-block repair restored {} missing moss carpet block(s)",
                repaired
        );
        return repaired;
    }

    private static boolean placeAndMark(
            ServerLevel lobby,
            StructureTemplate template,
            LobbyBootstrapSavedData data
    ) {
        boolean placed = template.placeInWorld(
                lobby,
                LOBBY_SPAWN_ORIGIN,
                LOBBY_SPAWN_ORIGIN,
                new StructurePlaceSettings().setIgnoreEntities(false).setKnownShape(true),
                RandomSource.create(),
                Block.UPDATE_CLIENTS
        );
        if (!placed) {
            TacticalTabletMod.LOGGER.error(
                    "Lobby bootstrap failed while placing lobby:spawn at {}",
                    LOBBY_SPAWN_ORIGIN);
            return false;
        }
        data.markVersion(CURRENT_VERSION);
        TacticalTabletMod.LOGGER.info(
                "Lobby bootstrap placed lobby:spawn at {} and recorded v{}",
                LOBBY_SPAWN_ORIGIN, CURRENT_VERSION);
        return true;
    }
}
