package com.makar.tacticaltablet.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinServerAddressPolicyTest {

    @Test
    void acceptsMinecraftHostAndOptionalPortAndTrimsInput() {
        assertEquals("deluxewarfare.sosal.today",
                JoinServerAddressPolicy.normalize("  deluxewarfare.sosal.today  ").orElseThrow());
        assertEquals("localhost:25565",
                JoinServerAddressPolicy.normalize("localhost:25565").orElseThrow());
    }

    @Test
    void rejectsUrlsPathsWhitespaceAndEmptyValues() {
        assertTrue(JoinServerAddressPolicy.normalize("").isEmpty());
        assertTrue(JoinServerAddressPolicy.normalize("https://example.org").isEmpty());
        assertTrue(JoinServerAddressPolicy.normalize("example.org/path").isEmpty());
        assertTrue(JoinServerAddressPolicy.normalize("example .org").isEmpty());
    }
}
