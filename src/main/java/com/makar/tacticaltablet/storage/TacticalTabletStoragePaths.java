package com.makar.tacticaltablet.storage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable, server-scoped paths for Tactical Tablet persistent data. */
public final class TacticalTabletStoragePaths {

    public static final String DATA_DIRECTORY = "tacticaltablet_data";
    public static final String LEGACY_DATA_DIRECTORY = "tacticaltabletdata";

    private final Path serverRoot;

    public TacticalTabletStoragePaths(Path serverRoot) {
        this.serverRoot = Objects.requireNonNull(serverRoot, "serverRoot").toAbsolutePath().normalize();
    }

    public static TacticalTabletStoragePaths fromServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path parent = worldRoot.getParent();
        return new TacticalTabletStoragePaths(parent == null ? worldRoot : parent);
    }

    public Path serverRoot() {
        return serverRoot;
    }

    public Path dataRoot() {
        return resolveWithinRoot(Path.of(DATA_DIRECTORY));
    }

    public Path legacyDataRoot() {
        return resolveWithinRoot(Path.of(LEGACY_DATA_DIRECTORY));
    }

    public Path playersDirectory() {
        return resolveInData("players");
    }

    public Path clansFile() {
        return resolveInData("clans.json");
    }

    public Path punishmentsFile() {
        return resolveInData("punishments.json");
    }

    public Path backupsDirectory() {
        return resolveInData("backups");
    }

    public Path transactionsDirectory() {
        return resolveInData("transactions");
    }

    public Path migrationsDirectory() {
        return resolveInData("migrations");
    }

    public Path resolveInData(String first, String... more) {
        Objects.requireNonNull(first, "first");
        return resolveWithin(dataRoot(), Path.of(first, more));
    }

    public Path resolveWithinRoot(Path relativePath) {
        return resolveWithin(serverRoot, relativePath);
    }

    private static Path resolveWithin(Path base, Path relativePath) {
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("Storage path must be relative: " + relativePath);
        }
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Storage path escapes its root: " + relativePath);
        }
        return resolved;
    }
}
