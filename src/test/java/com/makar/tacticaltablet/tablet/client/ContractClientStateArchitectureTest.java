package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractClientStateArchitectureTest {

    @Test
    void snapshotsPacketListsOnceAndUsesRevisionForUiRefresh() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/tablet/client/ContractClientState.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("targets = entries == null ? List.of() : List.copyOf(entries);"));
        assertTrue(source.contains("trackerTargets = entries == null ? List.of() : List.copyOf(entries);"));
        assertTrue(source.contains("public static List<ContractSelectionStatePacket.TargetEntry> getTargets() {\n"
                + "        return targets;"));
        assertTrue(source.contains("public static List<ContractTrackerStatePacket.TargetEntry> getTrackerTargets() {\n"
                + "        return trackerTargets;"));
        assertTrue(source.contains("revision++;"));
    }
}
