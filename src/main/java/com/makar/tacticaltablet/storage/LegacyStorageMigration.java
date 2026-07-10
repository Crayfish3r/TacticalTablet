package com.makar.tacticaltablet.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** One-way, copy-only migration from the pre-1.0 Tactical Tablet data directory. */
public final class LegacyStorageMigration {

    private static final String MARKER_FILE = "legacy-tacticaltabletdata-v1.complete";

    private LegacyStorageMigration() {
    }

    public enum Status {
        MIGRATED,
        MIGRATED_WITH_CONFLICTS,
        ALREADY_MIGRATED,
        FAILED
    }

    public record Result(Status status, Path marker, int copiedFiles, int conflicts, FileSaveResult markerWrite) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(marker, "marker");
        }
    }

    public static Result migrate(TacticalTabletStoragePaths paths, Consumer<String> warningSink) {
        Objects.requireNonNull(paths, "paths");
        Consumer<String> warnings = warningSink == null ? ignored -> { } : warningSink;
        Path marker = paths.migrationsDirectory().resolve(MARKER_FILE);
        if (Files.exists(marker)) {
            return new Result(Status.ALREADY_MIGRATED, marker, 0, 0, null);
        }

        int copied = 0;
        int conflicts = 0;
        Path legacyRoot = paths.legacyDataRoot();
        try {
            Files.createDirectories(paths.dataRoot());
            if (Files.exists(legacyRoot)) {
                try (Stream<Path> stream = Files.walk(legacyRoot)) {
                    List<Path> legacyFiles = stream.filter(Files::isRegularFile).toList();
                    for (Path legacyFile : legacyFiles) {
                        Path relative = legacyRoot.relativize(legacyFile);
                        Path target = paths.resolveInData(relative.toString());
                        if (Files.exists(target)) {
                            if (Files.mismatch(legacyFile, target) != -1L) {
                                conflicts++;
                                warnings.accept("Legacy storage conflict: kept both " + legacyFile + " and " + target);
                            }
                            continue;
                        }

                        Files.createDirectories(target.getParent());
                        Files.copy(legacyFile, target);
                        copied++;
                    }
                }
            }

            FileSaveResult markerWrite = new AtomicFileStore().writeUtf8(
                    marker,
                    "migration=legacy-tacticaltabletdata-v1\n"
            );
            if (markerWrite.status() != FileSaveResult.Status.SUCCESS) {
                return new Result(Status.FAILED, marker, copied, conflicts, markerWrite);
            }
            return new Result(
                    conflicts == 0 ? Status.MIGRATED : Status.MIGRATED_WITH_CONFLICTS,
                    marker,
                    copied,
                    conflicts,
                    markerWrite
            );
        } catch (IOException | RuntimeException exception) {
            warnings.accept("Legacy storage migration failed without removing source data: " + exception.getMessage());
            return new Result(
                    Status.FAILED,
                    marker,
                    copied,
                    conflicts,
                    FileSaveResult.failure(marker, "Failed to migrate legacy Tactical Tablet storage", exception)
            );
        }
    }
}
