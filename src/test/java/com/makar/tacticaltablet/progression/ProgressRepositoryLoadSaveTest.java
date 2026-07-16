package com.makar.tacticaltablet.progression;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.DurableSaveResult;
import com.makar.tacticaltablet.storage.SaveTicket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressRepositoryLoadSaveTest {
    private static final Set<String> JSON_FIELDS = Set.of(
            "dataVersion", "name", "uuid", "classes", "classTiers", "unlockedBaseClasses",
            "wins", "kills", "deaths", "matchesPlayed", "coins", "battlePassXp",
            "xpBoost", "sadTromboneKills", "purchasedClasses", "donations", "stats",
            "appliedTransactionReceipts", "firstSeen", "lastSeen"
    );

    @TempDir
    Path temporaryRoot;

    @Test
    void missingProfileReturnsEmpty() {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            assertTrue(repository.loadByKey("missing").isEmpty());
        }
    }

    @Test
    void savedProfileLoadsWithExactJsonFieldsAndDataVersion() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            await(repository.save(ProgressRepositoryTestSupport.snapshot("Игрок", 1, 25), false));

            Path file = repository.playerFile("Игрок");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals(JSON_FIELDS, json.keySet());
            assertEquals(11, json.get("dataVersion").getAsInt());
            assertEquals(25, repository.loadByKey("Игрок").orElseThrow().data().coins());
            try (Stream<Path> files = Files.list(repository.playersRoot())) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
        }
    }

    @Test
    void legacyAndInvalidFieldsNormalizeWithoutInferringTierFromXp() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            Files.writeString(repository.playerFile("legacy"), """
                    {
                      "dataVersion": 4,
                      "name": "legacy",
                      "uuid": "123456781234123412341234567890ab",
                      "classes": {"medic": 800},
                      "classTiers": null,
                      "unlockedBaseClasses": null,
                      "coins": -20,
                      "purchasedClasses": null,
                      "donations": null,
                      "stats": null,
                      "appliedTransactionReceipts": null,
                      "unknownFutureField": "ignored"
                    }
                    """, StandardCharsets.UTF_8);

            ProgressRepository.LoadedProfile loaded = repository.loadByKey("legacy").orElseThrow();

            assertTrue(loaded.requiresSave());
            assertEquals(11, loaded.data().dataVersion());
            assertEquals(0, loaded.data().coins());
            assertEquals(800, loaded.data().classes().get("medic"));
            assertEquals(ClassTier.BASIC.id(), loaded.data().classTiers().get("medic"));
            assertEquals(1, loaded.data().unlockedBaseClasses().get("medic"));
            assertTrue(loaded.data().appliedTransactionReceipts().isEmpty());
        }
    }

    @Test
    void initializationMigratesUuidFilenameToExistingNameKey() throws Exception {
        String compactUuid = "123456781234123412341234567890ab";
        Path players = temporaryRoot.resolve("tacticaltablet_data/players");
        Files.createDirectories(players);
        Path legacy = players.resolve(compactUuid + ".json");
        Files.writeString(legacy, """
                {
                  "dataVersion": 11,
                  "name": "Alice",
                  "uuid": "123456781234123412341234567890ab",
                  "classes": {},
                  "classTiers": {}
                }
                """, StandardCharsets.UTF_8);

        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            assertFalse(Files.exists(legacy));
            assertTrue(Files.exists(repository.playerFile("alice")));
            assertEquals("Alice", repository.loadByKey("alice").orElseThrow().data().name());
        }
    }

    @Test
    void corruptJsonIsBackedUpAndDoesNotLoad() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            Path profile = repository.playerFile("broken");
            Files.writeString(profile, "{broken", StandardCharsets.UTF_8);

            assertTrue(repository.loadByKey("broken").isEmpty());
            try (Stream<Path> backups = Files.list(repository.backupsRoot())) {
                assertTrue(backups.anyMatch(path -> path.getFileName().toString()
                        .matches("corrupt_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_broken\\.json")));
            }
            assertEquals("{broken", Files.readString(profile));
        }
    }

    @Test
    void findByUuidUsesJsonUuidEvenWhenFilenameDoesNotMatch() throws Exception {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            await(repository.save(ProgressRepositoryTestSupport.snapshot("different-name", 1, 9), false));

            assertEquals(9, repository.findByUuid(uuid).orElseThrow().data().coins());
        }
    }

    @Test
    void overwriteAndEmptyCollectionsRemainReadable() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            await(repository.save(ProgressRepositoryTestSupport.snapshot("player", 1, 10), false));
            await(repository.save(ProgressRepositoryTestSupport.snapshot("player", 2, 20), false));

            assertEquals(20, repository.loadByKey("player").orElseThrow().data().coins());
        }
    }

    @Test
    void failedAtomicReplacementPreservesPreviousValidFile() throws Exception {
        Path profile;
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            await(repository.save(ProgressRepositoryTestSupport.snapshot("player", 1, 10), false));
            profile = repository.playerFile("player");
        }
        String previous = Files.readString(profile);
        AtomicFileStore failingStore = new AtomicFileStore((source, target, options) -> {
            throw new IOException("injected move failure");
        });
        try (ProgressRepository repository = new ProgressRepository(
                temporaryRoot,
                ProgressRepositoryTestSupport.CONFIGURATION,
                ProgressRepositoryTestSupport.CLOCK,
                failingStore,
                ProgressRepository.RepositoryLog.noop(),
                64
        )) {
            repository.initialize();
            DurableSaveResult failed = await(repository.save(
                    ProgressRepositoryTestSupport.snapshot("player", 2, 99), false));

            assertEquals(DurableSaveResult.Status.FAILED, failed.status());
            assertEquals(previous, Files.readString(profile));
        }
    }

    private static DurableSaveResult await(SaveTicket ticket) throws Exception {
        return ticket.completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
