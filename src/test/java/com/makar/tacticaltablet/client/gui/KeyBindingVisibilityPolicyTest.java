package com.makar.tacticaltablet.client.gui;

import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBindingVisibilityPolicyTest {

    @Test
    void keepsRequestedVanillaBindingsAndHidesClutter() {
        assertVisible("key.forward", KeyMapping.CATEGORY_MOVEMENT);
        assertVisible("key.military_equipment.voicez", KeyMapping.CATEGORY_GAMEPLAY);
        assertVisible("key.screenshot", KeyMapping.CATEGORY_MISC);
        assertTrue(KeyBindingVisibilityPolicy.classify(
                "key.pickItem", KeyMapping.CATEGORY_GAMEPLAY).isEmpty());
        assertTrue(KeyBindingVisibilityPolicy.classify(
                "key.command", KeyMapping.CATEGORY_MULTIPLAYER).isEmpty());
        assertTrue(KeyBindingVisibilityPolicy.classify(
                "key.togglePerspective", KeyMapping.CATEGORY_MISC).isEmpty());
        assertTrue(KeyBindingVisibilityPolicy.classify(
                "key.hotbar.1", KeyMapping.CATEGORY_CREATIVE).isEmpty());
    }

    @Test
    void appliesRequestedModAllowlist() {
        assertVisible("key.parcool.WallSlide", "key.categories.parcool");
        assertVisible("key.pingwheel.ping_location", "key.category.pingwheel.name");
        assertVisible("key.tacz.reload.desc", "key.category.tacz");
        assertVisible("key.disable_voice_chat", "key.categories.voicechat");
        assertVisible("key.thermal_vision.toggle_thermal_vision", "key.categories.thermal_vision");

        assertHidden("key.parcool.openSetting", "key.categories.parcool");
        assertHidden("key.pingwheel.open_settings", "key.category.pingwheel.name");
        assertHidden("key.tacz.crawl.desc", "key.category.tacz");
        assertHidden("key.curios.open.desc", "key.curios.category");
        assertHidden("key.warborn.open_backpack", "key.categories.warborn");
        assertHidden("key.superbwarfare.reload", "key.categories.superbwarfare");
    }

    @Test
    void assignsDescriptionsToImportantMechanics() {
        var wallSlide = KeyBindingVisibilityPolicy.classify(
                "key.parcool.WallSlide", "key.categories.parcool").orElseThrow();
        assertEquals("screen.tacticaltablet.keybind.description.parcool.wall_slide",
                wallSlide.descriptionKey());

        var shout = KeyBindingVisibilityPolicy.classify(
                "key.military_equipment.voicez", KeyMapping.CATEGORY_GAMEPLAY).orElseThrow();
        assertEquals("screen.tacticaltablet.keybind.description.shout", shout.descriptionKey());
    }

    @Test
    void onlyTaczCrawlIsForcedUnbound() {
        assertTrue(KeyBindingVisibilityPolicy.mustBeUnbound("key.tacz.crawl.desc"));
        assertFalse(KeyBindingVisibilityPolicy.mustBeUnbound("key.parcool.Crawl"));
        assertFalse(KeyBindingVisibilityPolicy.mustBeUnbound("key.tacz.reload.desc"));
    }
    private static void assertVisible(String name, String category) {
        assertTrue(KeyBindingVisibilityPolicy.classify(name, category).isPresent());
    }

    private static void assertHidden(String name, String category) {
        assertTrue(KeyBindingVisibilityPolicy.classify(name, category).isEmpty());
    }
}
