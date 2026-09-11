package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticPurchaseTest {
    private static final ProgressService SERVICE = new ProgressService(new ProgressCatalog(
            Set.of("scout"), Set.of("scout"), Map.of(), Map.of(), Set.of(), 25));

    @Test
    void priceIsExactlyOneThousandAndBalancesAreServerCalculated() {
        assertEquals(1000, CosmeticCatalog.find("ghillie_suit").orElseThrow().price());

        State poor = new State(999);
        assertEquals(ProgressPurchaseResult.Failure.INSUFFICIENT_FUNDS,
                SERVICE.purchaseCosmetic(poor, "ghillie_suit").failure());
        assertEquals(999, poor.coins());

        State exact = new State(1000);
        assertTrue(SERVICE.purchaseCosmetic(exact, "ghillie_suit").successful());
        assertEquals(0, exact.coins());

        State rich = new State(1500);
        assertTrue(SERVICE.purchaseCosmetic(rich, "ghillie_suit").successful());
        assertEquals(500, rich.coins());
    }

    @Test
    void duplicateAndUnknownPurchasesNeverDeductAgain() {
        State state = new State(1500);
        assertTrue(SERVICE.purchaseCosmetic(state, "ghillie_suit").successful());
        assertEquals(ProgressPurchaseResult.Failure.ALREADY_OWNED,
                SERVICE.purchaseCosmetic(state, "ghillie_suit").failure());
        assertEquals(ProgressPurchaseResult.Failure.INVALID_ITEM,
                SERVICE.purchaseCosmetic(state, "spruce").failure());
        assertEquals(500, state.coins());
    }

    @Test
    void requestPacketCarriesProductIdButNoClientPrice() throws Exception {
        String packet = Files.readString(Path.of(
                "src/main/java/com/makar/tacticaltablet/tablet/net/CosmeticPurchasePacket.java"));
        assertTrue(packet.contains("buffer.writeUtf(productId"));
        assertFalse(packet.contains("buffer.writeInt"));
        assertFalse(packet.contains("private final int price"));
    }

    private static final class State implements MutableCosmeticProgressState {
        private int coins;
        private final Set<String> owned = new HashSet<>();
        private State(int coins) { this.coins = coins; }
        public int coins() { return coins; }
        public void coins(int value) { coins = value; }
        public boolean ownsCosmetic(String productId) { return owned.contains(productId); }
        public void addCosmetic(String productId) { owned.add(productId); }
    }
}
