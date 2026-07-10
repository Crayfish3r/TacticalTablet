package com.makar.tacticaltablet.clan.transaction;

public enum TransactionState {
    PREPARED,
    PLAYER_APPLIED,
    CLAN_APPLIED,
    COMMITTED,
    ROLLBACK_REQUIRED;

    public boolean isTerminal() {
        return this == COMMITTED || this == ROLLBACK_REQUIRED;
    }

    public boolean isCommitted() {
        return this == COMMITTED;
    }

    public boolean requiresManualRecovery() {
        return this == ROLLBACK_REQUIRED;
    }

    public boolean isAutoRecoverable() {
        return this == PREPARED || this == PLAYER_APPLIED || this == CLAN_APPLIED;
    }
}
