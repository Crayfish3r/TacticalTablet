package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.storage.AtomicFileStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProgressRepositoryTestSupport {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC);
    static final ProgressRepository.Configuration CONFIGURATION = new ProgressRepository.Configuration(
            11,
            2000,
            Set.of("scout"),
            Set.of("scout", "medic"),
            Set.of("sniper"),
            Set.of("killer"),
            Set.of("scout", "medic", "sniper", "killer")
    );

    private ProgressRepositoryTestSupport() {
    }

    static ProgressRepository repository(Path root) {
        ProgressRepository repository = new ProgressRepository(
                root,
                CONFIGURATION,
                CLOCK,
                new AtomicFileStore(),
                ProgressRepository.RepositoryLog.noop(),
                64
        );
        repository.initialize();
        return repository;
    }

    static ProgressSnapshot snapshot(String key, long revision, int coins) {
        return new ProgressSnapshot(key, revision, new ProgressSnapshot.Data(
                11,
                key,
                "123456781234123412341234567890ab",
                Map.of("scout", 300, "medic", 0, "sniper", 0, "killer", 0),
                Map.of("scout", 1, "medic", 0),
                Map.of("scout", 1, "medic", 0),
                1,
                2,
                3,
                4,
                coins,
                5,
                true,
                false,
                Map.of("sniper", 0, "killer", 0),
                Map.of(),
                Map.of(),
                List.of(),
                100L,
                200L
        ));
    }
}
