package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabletStatusFormatterTest {
    @Test
    void formatsCardSecondLines() {
        assertEquals("Редкий • 120/250 XP", TabletStatusFormatter.progress("Редкий", 120, 250));
        assertEquals("Покупка • 1000 монет", TabletStatusFormatter.purchase(1000));
        assertEquals("Улучшение • 500 монет", TabletStatusFormatter.upgrade(500));
        assertEquals("КД • 01:24", TabletStatusFormatter.cooldown("01:24"));
    }
}
