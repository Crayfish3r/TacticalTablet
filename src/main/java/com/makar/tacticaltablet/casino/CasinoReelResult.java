package com.makar.tacticaltablet.casino;

/** Presentation only. Symbols never participate in reward selection or payment. */
public record CasinoReelResult(int left, int center, int right) {
    public static final CasinoReelResult IDLE = new CasinoReelResult(0, 1, 2);

    public CasinoReelResult {
        if (left < 0 || left > 7 || center < 0 || center > 7 || right < 0 || right > 7) {
            throw new IllegalArgumentException("Reel positions must be in 0..7");
        }
    }

    public int symbol(int reel) {
        return switch (reel) { case 0 -> left; case 1 -> center; case 2 -> right;
            default -> throw new IllegalArgumentException("Unknown reel"); };
    }

    public static CasinoReelResult fromReward(CasinoReward reward, long animationSeed) {
        return fromReward(reward.kind(), reward.coins(), animationSeed);
    }

    public static CasinoReelResult fromReward(CasinoRewardKind kind, int coins, long animationSeed) {
        int symbol = switch (kind) {
            case COINS -> coins <= 0 ? -1 : 0;
            case SHOP_CLASS -> 5;
            case VIP_CLASS -> 6;
            case SAD_TROMBONE -> 7;
        };
        if (symbol >= 0) return new CasinoReelResult(symbol, symbol, symbol);
        int first = Math.floorMod(animationSeed, 5);
        return new CasinoReelResult(first, (first + 1) % 5, (first + 2) % 5);
    }
}
