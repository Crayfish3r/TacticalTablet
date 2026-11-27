package com.makar.tacticaltablet.tablet.client.prototype;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TabletPrototypeBoundaryTest {
    private static final Path CLIENT = Path.of("src/main/java/com/makar/tacticaltablet/tablet/client");

    @Test void legacyScreensAndIncompletePanelsAreOutsideActiveSources() {
        for (String name : List.of("TabletScreen", "MapVotingScreen", "TeamSelectScreen", "VotingScreen", "BaseClassPanel")) {
            assertFalse(Files.exists(CLIENT.resolve(name + ".java")), name);
            assertTrue(Files.isRegularFile(Path.of("legacy/tablet-v1").resolve(CLIENT).resolve(name + ".java")), name);
        }
    }

    @Test void itemUsesThePrototypeDirectlyAndMatchSnapshotsCannotOpenLegacyScreens() throws Exception {
        var item = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/tacticaltablet/models/item/tactical_tablet.json"))).getAsJsonObject();
        assertEquals("builtin/entity", item.get("parent").getAsString());
        assertFalse(item.has("overrides"));
        assertFalse(Files.readString(CLIENT.resolve("MapVoteClientState.java")).contains("setScreen"));
        assertFalse(Files.readString(CLIENT.resolve("ClientEvents.java")).contains("setScreen"));
        String lifecycle = Files.readString(CLIENT.resolve("prototype/TabletPrototypeClient.java"));
        assertTrue(lifecycle.contains("ScreenEvent.Opening"));
        assertFalse(lifecycle.contains("sendToServer"));
    }
}
