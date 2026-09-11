package com.makar.tacticaltablet.progression;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CosmeticCatalog {
    public static final String GHILLIE_SUIT_ID = "ghillie_suit";
    public static final int GHILLIE_SUIT_PRICE = 1000;

    private static final Map<String, Entry> PRODUCTS = Map.of(
            GHILLIE_SUIT_ID, new Entry(GHILLIE_SUIT_ID, GHILLIE_SUIT_PRICE, ClassTier.MONSTER));

    private CosmeticCatalog() { }

    public static Optional<Entry> find(String productId) {
        if (productId == null) return Optional.empty();
        return Optional.ofNullable(PRODUCTS.get(productId.trim().toLowerCase(Locale.ROOT)));
    }

    public record Entry(String id, int price, ClassTier tier) { }
}
