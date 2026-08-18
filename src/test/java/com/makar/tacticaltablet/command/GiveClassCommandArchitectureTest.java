package com.makar.tacticaltablet.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveClassCommandArchitectureTest {
    @Test
    void smartStormtrooperIsSuggestedNormalizedAndDisplayedByGiveClass() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/command/GiveClassCommand.java"));

        assertTrue(source.contains("\"smartstormtrooper\""));
        assertTrue(source.contains("\"smart-stormtrooper\""));
        assertTrue(source.contains("case \"smartstormtrooper\" -> \"Smart-Штурмовик\""));
    }
}
