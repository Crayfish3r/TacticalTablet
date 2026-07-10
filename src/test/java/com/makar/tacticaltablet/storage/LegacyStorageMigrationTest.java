package com.makar.tacticaltablet.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyStorageMigrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesLegacyFileWithoutRemovingSource() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        Path legacy = paths.legacyDataRoot().resolve("punishments.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "{\"mutes\":{}}");

        LegacyStorageMigration.Result result = LegacyStorageMigration.migrate(paths, ignored -> { });

        assertEquals(LegacyStorageMigration.Status.MIGRATED, result.status());
        assertEquals(1, result.copiedFiles());
        assertTrue(Files.exists(legacy));
        assertEquals("{\"mutes\":{}}", Files.readString(paths.punishmentsFile()));
        assertTrue(Files.exists(result.marker()));
    }

    @Test
    void repeatMigrationDoesNotModifyFilesAgain() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        Path legacy = paths.legacyDataRoot().resolve("clans.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "{\"clans\":[]}");

        LegacyStorageMigration.Result first = LegacyStorageMigration.migrate(paths, ignored -> { });
        Path target = paths.clansFile();
        FileTime beforeRepeat = Files.getLastModifiedTime(target);
        LegacyStorageMigration.Result second = LegacyStorageMigration.migrate(paths, ignored -> { });

        assertEquals(LegacyStorageMigration.Status.MIGRATED, first.status());
        assertEquals(LegacyStorageMigration.Status.ALREADY_MIGRATED, second.status());
        assertEquals(beforeRepeat, Files.getLastModifiedTime(target));
        assertEquals("{\"clans\":[]}", Files.readString(target));
    }

    @Test
    void preservesBothFilesAndWarnsOnConflict() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        Path legacy = paths.legacyDataRoot().resolve("punishments.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy");
        Files.createDirectories(paths.dataRoot());
        Files.writeString(paths.punishmentsFile(), "target");
        List<String> warnings = new ArrayList<>();

        LegacyStorageMigration.Result result = LegacyStorageMigration.migrate(paths, warnings::add);

        assertEquals(LegacyStorageMigration.Status.MIGRATED_WITH_CONFLICTS, result.status());
        assertEquals(1, result.conflicts());
        assertFalse(warnings.isEmpty());
        assertEquals("legacy", Files.readString(legacy));
        assertEquals("target", Files.readString(paths.punishmentsFile()));
    }

    @Test
    void corruptJsonIsCopiedWithoutDestroyingLegacySource() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        Path legacy = paths.legacyDataRoot().resolve("clans.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "{broken json");

        LegacyStorageMigration.Result result = LegacyStorageMigration.migrate(paths, ignored -> { });

        assertEquals(LegacyStorageMigration.Status.MIGRATED, result.status());
        assertEquals("{broken json", Files.readString(legacy));
        assertEquals("{broken json", Files.readString(paths.clansFile()));
    }

    @Test
    void storagePathsCannotEscapeServerRoot() {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);

        assertTrue(paths.dataRoot().startsWith(paths.serverRoot()));
        assertTrue(paths.playersDirectory().startsWith(paths.serverRoot()));
        assertTrue(paths.backupsDirectory().startsWith(paths.serverRoot()));
        assertTrue(paths.transactionsDirectory().startsWith(paths.serverRoot()));
        assertTrue(paths.migrationsDirectory().startsWith(paths.serverRoot()));
        assertThrows(IllegalArgumentException.class, () -> paths.resolveInData("..", "outside.json"));
        assertThrows(IllegalArgumentException.class, () -> paths.resolveWithinRoot(Path.of("..", "outside.json")));
    }
}
