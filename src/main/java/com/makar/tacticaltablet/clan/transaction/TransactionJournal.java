package com.makar.tacticaltablet.clan.transaction;

import java.util.List;

public interface TransactionJournal {

    JournalResult prepare(CreateClanTransaction transaction);

    JournalResult advance(CreateClanTransaction transaction, TransactionState targetState, String diagnostic);

    JournalLoadResult loadPending();

    record JournalResult(Status status, String diagnostic, Throwable exception) {
        public enum Status {
            SUCCESS,
            FAILED
        }

        public static JournalResult success() {
            return new JournalResult(Status.SUCCESS, "", null);
        }

        public static JournalResult failure(String diagnostic, Throwable exception) {
            return new JournalResult(Status.FAILED, diagnostic == null ? "" : diagnostic, exception);
        }
    }

    record JournalLoadResult(List<CreateClanTransaction> transactions, List<String> diagnostics) {
        public JournalLoadResult {
            transactions = transactions == null ? List.of() : List.copyOf(transactions);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
}
