package com.makar.tacticaltablet.game;

public enum SetGameMode {
    CASUAL("Казуал", "screen.tacticaltablet.map_voting.mode.casual", true),
    CHAOS("Хаос", "screen.tacticaltablet.map_voting.mode.chaos", true),
    RACE("Гонка [скоро…]", "screen.tacticaltablet.map_voting.mode.race", false),
    COMPETITIVE("Соревновательный", "screen.tacticaltablet.map_voting.mode.competitive", true);

    private final String displayName;
    private final String translationKey;
    private final boolean selectable;

    SetGameMode(String displayName, String translationKey, boolean selectable) {
        this.displayName = displayName;
        this.translationKey = translationKey;
        this.selectable = selectable;
    }

    public String displayName() { return displayName; }
    public String translationKey() { return translationKey; }
    public boolean selectable() { return selectable; }
}
