package com.makar.tacticaltablet.game.set;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static SetPlacement placement(int place, String name) {
        return new SetPlacement(place, UUID.randomUUID(), name, 12, 1, 2, 1, 20.0D, 3);
    }
}
