package com.makar.tacticaltablet.clan.transaction;

import com.makar.tacticaltablet.clan.ClanManager;

import java.util.Optional;
import java.util.UUID;

public record TransactionResult(Status status, ClanManager.Result clanResult, UUID transactionId,
                                String diagnostic, Optional<Throwable> exception) {

    public enum Status {
        SUCCESS,
        REJECTED,
        STORAGE_ERROR,
        RECOVERY_REQUIRED
    }

    public TransactionResult {
        diagnostic = diagnostic == null ? "" : diagnostic;
        exception = exception == null ? Optional.empty() : exception;
    }

    public static TransactionResult rejected(ClanManager.Result result) {
        return new TransactionResult(Status.REJECTED, result, null, "", Optional.empty());
    }

    public static TransactionResult success(UUID transactionId) {
        return new TransactionResult(Status.SUCCESS, ClanManager.Result.SUCCESS, transactionId, "", Optional.empty());
    }

    public static TransactionResult storageError(UUID transactionId, String diagnostic, Throwable exception) {
        return new TransactionResult(Status.STORAGE_ERROR, ClanManager.Result.STORAGE_ERROR,
                transactionId, diagnostic, Optional.ofNullable(exception));
    }

    public static TransactionResult recoveryRequired(UUID transactionId, String diagnostic) {
        return new TransactionResult(Status.RECOVERY_REQUIRED, ClanManager.Result.STORAGE_ERROR,
                transactionId, diagnostic, Optional.empty());
    }
}
