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
}
