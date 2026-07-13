package com.makar.tacticaltablet.game.respawn;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpSourceEncodingTest {

    @Test
    void rtpTimerMessagesAreUtf8AndContainNoKnownMojibake() throws IOException {
        Path source = Path.of("src/main/java/com/makar/tacticaltablet/game/respawn/RtpTimerManager.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);

        assertTrue(text.contains("Безопасная командная точка RTP не найдена"));
        assertFalse(text.contains("Рљ"));
        assertFalse(text.contains("Рµ"));
        assertFalse(text.contains("РЅ"));
        assertFalse(text.contains("Рџ"));
        assertFalse(text.contains("РЎ"));
    }
}
