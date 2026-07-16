package com.makar.tacticaltablet.game.set;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetRewardSummaryPersistenceTest {
    private static final Gson GSON = new Gson();

    @Test
    void oldJsonWithoutPerPlaceFieldsRetainsLegacyEqualPayoutRecovery() {
        SetRewardSummary oldSummary = new SetRewardSummary(UUID.randomUUID(), 6, 35, List.of(
                placement(1), placement(2), placement(3)));
        JsonObject json = GSON.toJsonTree(oldSummary).getAsJsonObject();
        json.remove("payoutPolicyVersion");
        json.remove("coinsByPlace");

        SetRewardSummary loaded = GSON.fromJson(json, SetRewardSummary.class);

        assertTrue(loaded.usesLegacyEqualPayouts());
        assertEquals(List.of(35, 35, 35), loaded.placements().stream()
                .map(placement -> loaded.coinsForPlace(placement.place())).toList());
        assertEquals(Map.of(), loaded.coinsByPlace());
    }

    @Test
    void newJsonRoundTripKeepsExplicitImmutablePayouts() {
        SetRewardSummary summary = SetRewardSummary.withPerPlacePayouts(UUID.randomUUID(), 6, 35, List.of(
                placement(1), placement(2), placement(3)), Map.of(1, 58, 2, 31, 3, 16));

        SetRewardSummary loaded = GSON.fromJson(GSON.toJson(summary), SetRewardSummary.class);

        assertFalse(loaded.usesLegacyEqualPayouts());
        assertEquals(SetRewardSummary.PER_PLACE_PAYOUT_VERSION, loaded.payoutPolicyVersion());
        assertEquals(Map.of(1, 58, 2, 31, 3, 16), loaded.coinsByPlace());
        assertThrows(UnsupportedOperationException.class, () -> loaded.coinsByPlace().put(1, 0));
    }

    private static SetPlacement placement(int place) {
        return new SetPlacement(place, UUID.randomUUID(), "P" + place, 0, 0, 0, 0, 0.0D, 0);
    }
}
