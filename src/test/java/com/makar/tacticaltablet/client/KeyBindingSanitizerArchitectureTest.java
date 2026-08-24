package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBindingSanitizerArchitectureTest {

    @Test
    void taczCrawlIsUnboundOnceOnTheClientAndPersisted() throws IOException {
        String sanitizer = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/client/event/ClientKeyBindingSanitizer.java"));

        assertTrue(sanitizer.contains("value = Dist.CLIENT"));
        assertTrue(sanitizer.contains("TickEvent.Phase.END"));
        assertTrue(sanitizer.contains("KeyBindingVisibilityPolicy.mustBeUnbound"));
        assertTrue(sanitizer.contains("InputConstants.UNKNOWN"));
        assertTrue(sanitizer.contains("KeyMapping.resetMapping()"));
        assertTrue(sanitizer.contains("options.save()"));
    }
}
