package com.makar.tacticaltablet.game.set;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetRewardPresentationTest {
    @Test
    void threePlacePodiumUsesGoldSilverAndBronzeComponents() {
        SetRewardSummary summary = new SetRewardSummary(UUID.randomUUID(), 8, 45, List.of(
                placement(1, "Gold"), placement(2, "Silver"), placement(3, "Bronze")));

        Component subtitle = SetRewardPresentation.subtitle(summary);
        List<Component> namedComponents = subtitle.getSiblings().stream()
                .filter(component -> component.getString().matches("[123]\\. .*"))
                .toList();

        assertEquals("ПРИЗЁРЫ СЕТА", SetRewardPresentation.title(summary).getString());
        assertEquals(List.of(SetRewardPresentation.GOLD, SetRewardPresentation.SILVER, SetRewardPresentation.BRONZE),
                namedComponents.stream().map(component -> component.getStyle().getColor().getValue()).toList());
        assertTrue(subtitle.getString().contains("1. Gold"));
        assertTrue(subtitle.getString().contains("2. Silver"));
        assertTrue(subtitle.getString().contains("3. Bronze"));
    }

    @Test
    void competitiveZeroPayoutPresentationShowsResultsWithoutPromisingCoins() {
        SetRewardSummary summary = new SetRewardSummary(UUID.randomUUID(), 8, 0, List.of(
                placement(1, "Winner"), placement(2, "Second"), placement(3, "Third")));

        String title = SetRewardPresentation.title(summary).getString();
        String subtitle = SetRewardPresentation.subtitle(summary).getString();

        assertTrue(title.contains("СЕТА"));
        assertTrue(subtitle.contains("1. Winner"));
        assertTrue(SetRewardPresentation.chat(summary, Set.of()).isEmpty());
        assertFalse((title + subtitle).toLowerCase(java.util.Locale.ROOT).contains("coin"));
    }

    private static SetPlacement placement(int place, String name) {
        return new SetPlacement(place, UUID.randomUUID(), name, 12, 1, 2, 1, 20.0D, 3);
    }
}
