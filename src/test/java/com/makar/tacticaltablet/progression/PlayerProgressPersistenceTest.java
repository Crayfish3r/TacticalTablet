package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProgressPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void snapshotDtosDoNotReferenceMinecraftRuntimeTypes() {
        assertNoMinecraftFields(PlayerProgressManager.PlayerProgressSnapshot.class);
        assertNoMinecraftFields(PlayerProgressManager.PlayerProgressData.class);
    }

    @Test
    void backupCopyExcludesTemporaryFiles() throws Exception {
        Path source = temporaryDirectory.resolve("players");
        Path target = temporaryDirectory.resolve("backup");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("player.json"), "{}");
        Files.writeString(source.resolve("player.json.tmp"), "incomplete");

        PlayerProgressManager.copyJsonFiles(source, target);

        assertTrue(Files.exists(target.resolve("player.json")));
        assertFalse(Files.exists(target.resolve("player.json.tmp")));
        assertEquals("{}", Files.readString(target.resolve("player.json")));
    }

    private static void assertNoMinecraftFields(Class<?> type) {
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .noneMatch(name -> name.startsWith("net.minecraft.")));
    }
}
