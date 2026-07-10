package com.makar.tacticaltablet.clan.transaction;

import com.makar.tacticaltablet.clan.ClanManager;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Server-thread orchestration for durable cross-store clan creation. */
public final class ClanEconomyService {

    public record CreateClanRequest(UUID playerUuid, String playerName, String name, String tag, int color, int createCost) {
        public CreateClanRequest {
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tag, "tag");
            if (createCost <= 0) throw new IllegalArgumentException("createCost must be positive");
        }
    }

    public record PreflightResult(ClanManager.Result status, ClanCreationPayload payload) {
        public boolean isReady() {
            return status == ClanManager.Result.SUCCESS && payload != null;
        }
    }

    public interface PlayerProgressRepository {
        int currentBalance(UUID playerUuid, String playerName);

        RepositoryResult applyDebit(CreateClanTransaction transaction);

        RepositoryResult verifyDebit(CreateClanTransaction transaction);
    }

    public interface ClanRepository {
        PreflightResult preflight(CreateClanRequest request, String clanId);

        RepositoryResult applyCreate(CreateClanTransaction transaction);

        RepositoryResult verifyCreate(CreateClanTransaction transaction);
    }

    @FunctionalInterface
    public interface ClientSynchronizer {
        void synchronize();
    }

    private final PlayerProgressRepository playerRepository;
    private final ClanRepository clanRepository;
    private final TransactionJournal journal;
    private final ClientSynchronizer clientSynchronizer;
    private final Supplier<UUID> idSupplier;
    private final Clock clock;
    private final Consumer<String> log;

    public ClanEconomyService(
            PlayerProgressRepository playerRepository,
            ClanRepository clanRepository,
            TransactionJournal journal,
            ClientSynchronizer clientSynchronizer,
            Supplier<UUID> idSupplier,
            Clock clock,
            Consumer<String> log
    ) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.clanRepository = Objects.requireNonNull(clanRepository, "clanRepository");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.clientSynchronizer = Objects.requireNonNull(clientSynchronizer, "clientSynchronizer");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = log == null ? ignored -> { } : log;
    }

    public TransactionResult createClan(CreateClanRequest request) {
        String clanId = idSupplier.get().toString();
        PreflightResult preflight = clanRepository.preflight(request, clanId);
        if (!preflight.isReady()) {
            return TransactionResult.rejected(preflight.status());
        }

        int oldBalance = playerRepository.currentBalance(request.playerUuid(), request.playerName());
        if (oldBalance < request.createCost()) {
            return TransactionResult.rejected(ClanManager.Result.NOT_ENOUGH_COINS);
        }
        int newBalance = oldBalance - request.createCost();

        long now = clock.millis();
        CreateClanTransaction transaction = new CreateClanTransaction(
                CreateClanTransaction.SCHEMA_VERSION,
                idSupplier.get(),
                CreateClanTransaction.OPERATION_TYPE,
                request.playerUuid(),
                request.playerName(),
                clanId,
                oldBalance,
                newBalance,
                preflight.payload(),
                CreateClanTransaction.payloadHash(preflight.payload()),
                TransactionState.PREPARED,
                now,
                now,
                ""
        );
        TransactionJournal.JournalResult prepared = journal.prepare(transaction);
        if (prepared.status() != TransactionJournal.JournalResult.Status.SUCCESS) {
            return journalFailure(transaction.transactionId(), "PREPARED", prepared);
        }

        TransactionResult applied = applyAndCommit(transaction);
        if (applied.status() != TransactionResult.Status.SUCCESS) {
            return applied;
        }
        clientSynchronizer.synchronize();
        return applied;
    }

    public RecoveryReport recoverPendingTransactions() {
        TransactionJournal.JournalLoadResult loaded = journal.loadPending();
        int committed = 0;
        int blocked = 0;
        for (CreateClanTransaction transaction : loaded.transactions()) {
            if (transaction.state().isTerminal()) continue;
            TransactionResult result = applyAndCommit(transaction);
            if (result.status() == TransactionResult.Status.SUCCESS) {
                committed++;
            } else {
                blocked++;
            }
        }
        return new RecoveryReport(committed, blocked, loaded.diagnostics());
    }

    public record RecoveryReport(int committed, int blocked, java.util.List<String> diagnostics) {
        public RecoveryReport {
            diagnostics = diagnostics == null ? java.util.List.of() : java.util.List.copyOf(diagnostics);
        }
    }

    private TransactionResult applyAndCommit(CreateClanTransaction transaction) {
        if (!CreateClanTransaction.OPERATION_TYPE.equals(transaction.operationType())
                || transaction.schemaVersion() != CreateClanTransaction.SCHEMA_VERSION) {
            return markRollbackRequired(transaction, "Unsupported transaction schema or operation");
        }

        RepositoryResult playerResult = playerRepository.applyDebit(transaction);
        if (!playerResult.isAppliedOrAlreadyApplied()) {
            return handleRepositoryFailure(transaction, "player", playerResult);
        }
        if (transaction.state() == TransactionState.PREPARED) {
            TransactionJournal.JournalResult updated = journal.advance(
                    transaction, TransactionState.PLAYER_APPLIED, "player debit durable"
            );
            if (updated.status() != TransactionJournal.JournalResult.Status.SUCCESS) {
                return journalFailure(transaction.transactionId(), "PLAYER_APPLIED", updated);
            }
            transaction = transaction.withState(TransactionState.PLAYER_APPLIED, clock.millis(), "player debit durable");
        }

        RepositoryResult clanResult = clanRepository.applyCreate(transaction);
        if (!clanResult.isAppliedOrAlreadyApplied()) {
            return handleRepositoryFailure(transaction, "clan", clanResult);
        }
        if (transaction.state() == TransactionState.PREPARED || transaction.state() == TransactionState.PLAYER_APPLIED) {
            TransactionJournal.JournalResult updated = journal.advance(
                    transaction, TransactionState.CLAN_APPLIED, "clan durable"
            );
            if (updated.status() != TransactionJournal.JournalResult.Status.SUCCESS) {
                return journalFailure(transaction.transactionId(), "CLAN_APPLIED", updated);
            }
            transaction = transaction.withState(TransactionState.CLAN_APPLIED, clock.millis(), "clan durable");
        }

        RepositoryResult verifiedPlayer = playerRepository.verifyDebit(transaction);
        if (!verifiedPlayer.isAppliedOrAlreadyApplied()) {
            return handleRepositoryFailure(transaction, "player verification", verifiedPlayer);
        }
        RepositoryResult verifiedClan = clanRepository.verifyCreate(transaction);
        if (!verifiedClan.isAppliedOrAlreadyApplied()) {
            return handleRepositoryFailure(transaction, "clan verification", verifiedClan);
        }

        TransactionJournal.JournalResult committed = journal.advance(
                transaction, TransactionState.COMMITTED, "player and clan durable"
        );
        if (committed.status() != TransactionJournal.JournalResult.Status.SUCCESS) {
            return journalFailure(transaction.transactionId(), "COMMITTED", committed);
        }
        log.accept("Clan creation transaction committed: " + transaction.transactionId());
        return TransactionResult.success(transaction.transactionId());
    }

    private TransactionResult handleRepositoryFailure(
            CreateClanTransaction transaction,
            String repository,
            RepositoryResult result
    ) {
        if (result.status() == RepositoryResult.Status.CONFLICT) {
            return markRollbackRequired(transaction, repository + " conflict: " + result.diagnostic());
        }
        log.accept("Clan creation transaction " + transaction.transactionId() + " failed in " + repository
                + ": " + result.diagnostic());
        return TransactionResult.storageError(transaction.transactionId(), result.diagnostic(), result.exception().orElse(null));
    }

    private TransactionResult markRollbackRequired(CreateClanTransaction transaction, String diagnostic) {
        TransactionJournal.JournalResult result = journal.advance(
                transaction, TransactionState.ROLLBACK_REQUIRED, diagnostic
        );
        log.accept("Clan creation transaction " + transaction.transactionId() + " requires recovery: " + diagnostic);
        if (result.status() != TransactionJournal.JournalResult.Status.SUCCESS) {
            return journalFailure(transaction.transactionId(), "ROLLBACK_REQUIRED", result);
        }
        return TransactionResult.recoveryRequired(transaction.transactionId(), diagnostic);
    }

    private static TransactionResult journalFailure(
            UUID transactionId,
            String stage,
            TransactionJournal.JournalResult result
    ) {
        return TransactionResult.storageError(
                transactionId,
                "Journal " + stage + " failed: " + result.diagnostic(),
                result.exception()
        );
    }
}
