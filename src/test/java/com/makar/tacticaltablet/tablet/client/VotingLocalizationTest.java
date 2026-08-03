package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingLocalizationTest {
    private static final Pattern TRANSLATION = Pattern.compile("Component\\.translatable\\(\"([^\"]+)\"");
    private static final Path CLIENT = Path.of("src/main/java/com/makar/tacticaltablet/tablet/client");
    private static final Path LANG = Path.of("src/main/resources/assets/tacticaltablet/lang");

    @Test
    void everyStaticVotingTranslationExistsInRussianAndEnglish() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String file : Set.of("VotingScreen.java", "TeamSelectScreen.java", "MapVotingScreen.java")) {
            Matcher matcher = TRANSLATION.matcher(Files.readString(CLIENT.resolve(file)));
            while (matcher.find()) {
                String key = matcher.group(1);
                if (!key.endsWith(".")) keys.add(key);
            }
        }
        keys.addAll(Set.of(
                "screen.tacticaltablet.voting.mode.solo",
                "screen.tacticaltablet.voting.mode.duo",
                "screen.tacticaltablet.voting.mode.trio",
                "screen.tacticaltablet.voting.mode.squads",
                "screen.tacticaltablet.team_select.team.alfa",
                "screen.tacticaltablet.team_select.team.beta",
                "screen.tacticaltablet.team_select.team.gamma",
                "screen.tacticaltablet.team_select.team.delta"));
        assertFalse(keys.isEmpty());

        String english = Files.readString(LANG.resolve("en_us.json"));
        String russian = Files.readString(LANG.resolve("ru_ru.json"));
        for (String key : keys) {
            assertTrue(english.contains("\"" + key + "\""), "Missing en_us key: " + key);
            assertTrue(russian.contains("\"" + key + "\""), "Missing ru_ru key: " + key);
        }
    }
}
