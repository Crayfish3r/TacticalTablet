package com.makar.tacticaltablet.progression;

interface MutableCosmeticProgressState {
    int coins();
    void coins(int value);
    boolean ownsCosmetic(String productId);
    void addCosmetic(String productId);
}
