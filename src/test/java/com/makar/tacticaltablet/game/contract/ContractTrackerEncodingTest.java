package com.makar.tacticaltablet.game.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractTrackerEncodingTest {
    @Test
    void cooldownMessageIsReadableUtf8() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/game/contract/ContractManager.java"));

        assertTrue(source.contains("[WAR] Трекер перезаряжается: "));
        assertFalse(source.contains("РўСЂРµРєРµСЂ"));
    }
}
