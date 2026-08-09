package com.makar.tacticaltablet.game;

public enum SetGameMode {
    CASUAL("Казуал", true),
    CHAOS("Хаос", true),
    RACE("Гонка [скоро…]", false);

    private final String displayName;
    private final boolean selectable;

    SetGameMode(String displayName, boolean selectable) {
        this.displayName = displayName;
        this.selectable = selectable;
    }

    public String displayName() { return displayName; }
    public boolean selectable() { return selectable; }
}
