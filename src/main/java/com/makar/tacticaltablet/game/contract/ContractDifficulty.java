package com.makar.tacticaltablet.game.contract;

public enum ContractDifficulty {
    LOW(2, 10, 0xFF66FF66, "Лёгкий"),
    MEDIUM(5, 20, 0xFFFFD966, "Средний"),
    HIGH(15, 40, 0xFFFF5555, "Высокий");

    private final int price;
    private final int reward;
    private final int color;
    private final String displayName;

    ContractDifficulty(int price, int reward, int color, String displayName) {
        this.price = price;
        this.reward = reward;
        this.color = color;
        this.displayName = displayName;
    }

    public int price() {
        return price;
    }

    public int reward() {
        return reward;
    }

    public int color() {
        return color;
    }

    public String displayName() {
        return displayName;
    }

    public static ContractDifficulty forCareerPercent(int percent) {
        if (percent >= 60) {
            return HIGH;
        }

        if (percent >= 30) {
            return MEDIUM;
        }

        return LOW;
    }
}
