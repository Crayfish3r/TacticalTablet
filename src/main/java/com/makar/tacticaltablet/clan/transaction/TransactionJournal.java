package com.makar.tacticaltablet.clan.transaction;

import java.util.List;

public interface TransactionJournal {

    JournalResult prepare(CreateClanTransaction transaction);

    JournalResult advance(CreateClanTransaction transaction, TransactionState targetState, String diagnostic);

    JournalLoadResult loadPending();

    default JournalResult archiveCommitted(CreateClanTransaction transaction) {
        return JournalResult.success();
    }

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

    record JournalLoadResult(
            List<CreateClanTransaction> transactions,
            List<CreateClanTransaction> rollbackRequired,
            List<CreateClanTransaction> committed,
            int quarantined,
            int quarantineFailures,
            int backupFailures,
            int reasonWriteFailures,
            int archived,
            int archiveFailures,
            List<String> diagnostics
    ) {
        public JournalLoadResult(List<CreateClanTransaction> transactions, List<String> diagnostics) {
            this(transactions, List.of(), List.of(), 0, 0, 0, 0, 0, 0, diagnostics);
        }

        public JournalLoadResult {
            transactions = transactions == null ? List.of() : List.copyOf(transactions);
            rollbackRequired = rollbackRequired == null ? List.of() : List.copyOf(rollbackRequired);
            committed = committed == null ? List.of() : List.copyOf(committed);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
}
