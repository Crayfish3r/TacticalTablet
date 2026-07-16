package com.makar.tacticaltablet.progression;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProgressPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void snapshotDtosDoNotReferenceMinecraftRuntimeTypes() {
        assertNoMinecraftFields(ProgressSnapshot.class);
        assertNoMinecraftFields(ProgressSnapshot.Data.class);
    }

    @Test
    void extractedSnapshotDefensivelyCopiesCollectionsAndKeepsJsonFieldNames() {
        Map<String, Integer> classes = new HashMap<>(Map.of("scout", 300));
        ProgressSnapshot.Data data = new ProgressSnapshot.Data(
                11, "Player", "uuid", classes, Map.of("scout", 1), Map.of("scout", 1),
                1, 2, 3, 4, 5, 6, true, false,
                Map.of("boomguy", 1), Map.of(), Map.of(), List.of(), 7L, 8L
        );
        ProgressSnapshot snapshot = new ProgressSnapshot("player", 9L, data);

        classes.put("scout", 999);
        assertEquals(300, snapshot.data().classes().get("scout"));

        JsonObject json = new Gson().toJsonTree(snapshot.data()).getAsJsonObject();
        assertEquals(Set.of(
                "dataVersion", "name", "uuid", "classes", "classTiers", "unlockedBaseClasses",
                "wins", "kills", "deaths", "matchesPlayed", "coins", "battlePassXp",
                "xpBoost", "sadTromboneKills", "purchasedClasses", "donations", "stats",
                "appliedTransactionReceipts", "firstSeen", "lastSeen"
        ), json.keySet());
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
