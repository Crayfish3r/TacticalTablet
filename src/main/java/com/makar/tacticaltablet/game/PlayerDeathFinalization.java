package com.makar.tacticaltablet.game;

final class PlayerDeathFinalization {
    private PlayerDeathFinalization() {
    }

    static void process(
            boolean activeParticipant,
            Runnable deathAccounting,
            Runnable matchEndCheck
    ) {
        deathAccounting.run();

        if (activeParticipant) {
            matchEndCheck.run();
        }
    }
}
