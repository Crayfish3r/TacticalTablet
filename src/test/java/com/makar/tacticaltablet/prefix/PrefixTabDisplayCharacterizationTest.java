package com.makar.tacticaltablet.prefix;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixTabDisplayCharacterizationTest {

    private static final Path PREFIX_MANAGER = Path.of(
            "src/main/java/com/makar/tacticaltablet/prefix/PrefixManager.java");
    private static final Path SERVER_EVENTS = Path.of(
            "src/main/java/com/makar/tacticaltablet/game/ServerEvents.java");

    @Test
    void prefixedBaseNameCurrentlyHasNoTeamColorWhileBadgeKeepsItsOwnColors() {
        Component displayName = PrefixDisplayHelper.appendSuffix(
                Component.literal("PlayerName"),
                PrefixRole.OWNER
        );

        Component badge = displayName.getSiblings().get(0);
        Component badgeText = badge.getSiblings().get(0);

        assertNull(displayName.getStyle().getColor());
        assertEquals(PrefixRole.OWNER.backgroundColor(), badge.getStyle().getColor().getValue());
        assertEquals(PrefixRole.OWNER.textColor(), badgeText.getStyle().getColor().getValue());
    }

    @Test
    void tabAndChatUseSeparateEventFlows() throws IOException {
        String prefixManager = Files.readString(PREFIX_MANAGER);
        String serverEvents = Files.readString(SERVER_EVENTS);

        assertTrue(prefixManager.contains("Component buildDisplayName(ServerPlayer player)"));
        assertTrue(prefixManager.contains("Component buildChatName(ServerPlayer player)"));
        assertTrue(prefixManager.contains("PrefixDisplayHelper.appendSuffix(cleanPlayerName(player), getRole(player))"));
        assertTrue(serverEvents.contains("PrefixManager.buildTabDisplayName(player)"));
        assertTrue(serverEvents.contains("PrefixManager.buildChatName(player)"));
    }
}
