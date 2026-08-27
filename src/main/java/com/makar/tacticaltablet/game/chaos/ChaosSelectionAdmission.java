package com.makar.tacticaltablet.game.chaos;

/**
 * Pure server admission rule for Chaos kit selection.
 *
 * <p>The physical lobby and survival checks deliberately do not trust the transient
 * {@code in_lobby} tag: that tag can be set before the respawn transition has finished.</p>
 */
public final class ChaosSelectionAdmission {
    private ChaosSelectionAdmission() {
    }

    public static boolean canSelect(boolean matchRunning, boolean canContinueMatch,
                                    boolean physicallyInLobby, boolean survivalMode) {
        return matchRunning && canContinueMatch && physicallyInLobby && survivalMode;
    }
}
