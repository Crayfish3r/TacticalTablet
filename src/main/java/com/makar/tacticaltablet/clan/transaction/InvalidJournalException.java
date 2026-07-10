package com.makar.tacticaltablet.clan.transaction;

/** Checked boundary for malformed or incompatible transaction journal input. */
public final class InvalidJournalException extends Exception {
    public InvalidJournalException(String message) {
        super(message);
    }

    public InvalidJournalException(String message, Throwable cause) {
        super(message, cause);
    }
}
