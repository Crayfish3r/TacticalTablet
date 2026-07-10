package com.makar.tacticaltablet.clan.transaction;

import com.makar.tacticaltablet.clan.ClanManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClanEconomyServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    @Test
    void createsClanAndDebitsPlayerOnce() {
        FakePlayerRepository player = new FakePlayerRepository(250);
        FakeClanRepository clans = new FakeClanRepository();
        FakeJournal journal = new FakeJournal();
        AtomicInteger syncs = new AtomicInteger();

        TransactionResult result = service(player, clans, journal, syncs::incrementAndGet).createClan(request());

        assertEquals(TransactionResult.Status.SUCCESS, result.status());
        assertEquals(150, player.balance);
        assertEquals(1, player.receipts.size());
        assertNotNull(clans.created);
        assertEquals(1, player.appliedCount);
        assertEquals(1, clans.appliedCount);
        assertEquals(1, syncs.get());
        assertEquals(TransactionState.COMMITTED, journal.onlyTransaction().state());
    }

    @Test
    void rejectsInsufficientBalanceBeforePreparingJournal() {
        FakeJournal journal = new FakeJournal();
        TransactionResult result = service(new FakePlayerRepository(99), new FakeClanRepository(), journal, () -> { })
                .createClan(request());

        assertEquals(ClanManager.Result.NOT_ENOUGH_COINS, result.clanResult());
        assertEquals(0, journal.transactions.size());
    }

    @Test
    void rejectsDuplicateNameOrTagBeforePreparingJournal() {
        FakeClanRepository clans = new FakeClanRepository();
        clans.preflightStatus = ClanManager.Result.NAME_TAKEN;
        FakeJournal journal = new FakeJournal();

        TransactionResult result = service(new FakePlayerRepository(250), clans, journal, () -> { }).createClan(request());

        assertEquals(ClanManager.Result.NAME_TAKEN, result.clanResult());
        assertEquals(0, journal.transactions.size());
    }

    @Test
    void doesNotMutateRepositoriesWhenPreparedWriteFails() {
        FakeJournal journal = new FakeJournal();
        journal.failPrepare = true;
        FakePlayerRepository player = new FakePlayerRepository(250);
        FakeClanRepository clans = new FakeClanRepository();

        TransactionResult result = service(player, clans, journal, () -> { }).createClan(request());

        assertEquals(TransactionResult.Status.STORAGE_ERROR, result.status());
        assertEquals(250, player.balance);
        assertEquals(null, clans.created);
    }

    @Test
    void leavesPreparedJournalWhenPlayerSaveFails() {
        FakePlayerRepository player = new FakePlayerRepository(250);
        player.failApply = true;
        FakeJournal journal = new FakeJournal();

        TransactionResult result = service(player, new FakeClanRepository(), journal, () -> { }).createClan(request());

        assertEquals(TransactionResult.Status.STORAGE_ERROR, result.status());
        assertEquals(250, player.balance);
        assertEquals(TransactionState.PREPARED, journal.onlyTransaction().state());
    }

    @Test
    void leavesPlayerAppliedJournalWhenClanSaveFails() {
        FakeClanRepository clans = new FakeClanRepository();
        clans.failApply = true;
        FakeJournal journal = new FakeJournal();

        TransactionResult result = service(new FakePlayerRepository(250), clans, journal, () -> { }).createClan(request());

        assertEquals(TransactionResult.Status.STORAGE_ERROR, result.status());
        assertEquals(TransactionState.PLAYER_APPLIED, journal.onlyTransaction().state());
    }

    @Test
    void recoversCrashAfterPlayerAppliedWithoutSecondDebit() {
        FakePlayerRepository player = new FakePlayerRepository(250);
        FakeClanRepository clans = new FakeClanRepository();
        FakeJournal journal = new FakeJournal();
        journal.failAdvanceAt = TransactionState.PLAYER_APPLIED;
        ClanEconomyService initial = service(player, clans, journal, () -> { });

        assertEquals(TransactionResult.Status.STORAGE_ERROR, initial.createClan(request()).status());
        assertEquals(150, player.balance);
        assertEquals(TransactionState.PREPARED, journal.onlyTransaction().state());

        journal.failAdvanceAt = null;
        ClanEconomyService.RecoveryReport recovery = service(player, clans, journal, () -> { }).recoverPendingTransactions();

        assertEquals(1, recovery.committed());
        assertEquals(150, player.balance);
        assertEquals(1, player.receipts.size());
        assertEquals(1, player.appliedCount);
        assertEquals(1, clans.appliedCount);
        assertEquals(TransactionState.COMMITTED, journal.onlyTransaction().state());
    }

    @Test
    void recoversCrashAfterClanAppliedWithoutDuplicateClan() {
        FakePlayerRepository player = new FakePlayerRepository(250);
        FakeClanRepository clans = new FakeClanRepository();
        FakeJournal journal = new FakeJournal();
        journal.failAdvanceAt = TransactionState.CLAN_APPLIED;

        assertEquals(TransactionResult.Status.STORAGE_ERROR,
                service(player, clans, journal, () -> { }).createClan(request()).status());
        assertNotNull(clans.created);
        assertEquals(TransactionState.PLAYER_APPLIED, journal.onlyTransaction().state());

        journal.failAdvanceAt = null;
        service(player, clans, journal, () -> { }).recoverPendingTransactions();

        assertEquals(150, player.balance);
        assertEquals(1, clans.appliedCount);
        assertEquals(TransactionState.COMMITTED, journal.onlyTransaction().state());
    }

    @Test
    void recoversEveryIntermediateStateIdempotently() {
        for (TransactionState state : List.of(TransactionState.PREPARED, TransactionState.PLAYER_APPLIED, TransactionState.CLAN_APPLIED)) {
            FakePlayerRepository player = new FakePlayerRepository(state == TransactionState.PREPARED ? 250 : 150);
            FakeClanRepository clans = new FakeClanRepository();
            CreateClanTransaction transaction = transaction(state);
            if (state != TransactionState.PREPARED) {
                player.addReceipt(transaction);
            }
            if (state == TransactionState.CLAN_APPLIED) {
                clans.created = transaction.clanPayload();
                clans.appliedCount = 1;
            }
            FakeJournal journal = new FakeJournal();
            journal.transactions.put(transaction.transactionId(), transaction);

            ClanEconomyService.RecoveryReport recovery = service(player, clans, journal, () -> { }).recoverPendingTransactions();

            assertEquals(1, recovery.committed(), state.name());
            assertEquals(150, player.balance, state.name());
            assertEquals(TransactionState.COMMITTED, journal.onlyTransaction().state(), state.name());
        }
    }

    @Test
    void repeatedRecoveryDoesNotReapplyEffects() {
        FakePlayerRepository player = new FakePlayerRepository(250);
        FakeClanRepository clans = new FakeClanRepository();
        FakeJournal journal = new FakeJournal();
        CreateClanTransaction prepared = transaction(TransactionState.PREPARED);
        journal.transactions.put(prepared.transactionId(), prepared);
        ClanEconomyService service = service(player, clans, journal, () -> { });

        service.recoverPendingTransactions();
        ClanEconomyService.RecoveryReport second = service.recoverPendingTransactions();

        assertEquals(150, player.balance);
        assertEquals(0, second.committed());
        assertEquals(1, player.appliedCount);
        assertEquals(1, clans.appliedCount);
    }

    @Test
    void balanceAtNewValueWithoutReceiptIsConflict() {
        FakePlayerRepository player = new FakePlayerRepository(150);
        FakeJournal journal = new FakeJournal();
        CreateClanTransaction prepared = transaction(TransactionState.PREPARED);
        journal.transactions.put(prepared.transactionId(), prepared);

        ClanEconomyService.RecoveryReport recovery = service(player, new FakeClanRepository(), journal, () -> { })
                .recoverPendingTransactions();

        assertEquals(0, recovery.committed());
        assertEquals(1, recovery.blocked());
        assertEquals(TransactionState.ROLLBACK_REQUIRED, journal.onlyTransaction().state());
    }

    @Test
    void matchingReceiptPreventsSecondDebit() {
        FakePlayerRepository player = new FakePlayerRepository(150);
        CreateClanTransaction transaction = transaction(TransactionState.PLAYER_APPLIED);
        player.addReceipt(transaction);

        assertEquals(RepositoryResult.Status.ALREADY_APPLIED, player.applyDebit(transaction).status());
        assertEquals(150, player.balance);
        assertEquals(0, player.appliedCount);
    }

    @Test
    void rollbackRequiredIsReportedAndNotAutoRecovered() {
        FakePlayerRepository player = new FakePlayerRepository(250);
        FakeClanRepository clans = new FakeClanRepository();
        FakeJournal journal = new FakeJournal();
        journal.transactions.put(transaction(TransactionState.ROLLBACK_REQUIRED).transactionId(),
                transaction(TransactionState.ROLLBACK_REQUIRED));

        ClanEconomyService.RecoveryReport recovery = service(player, clans, journal, () -> { }).recoverPendingTransactions();

        assertEquals(0, recovery.committed());
        assertEquals(0, recovery.blocked());
        assertEquals(1, recovery.rollbackRequired());
        assertEquals(250, player.balance);
        assertEquals(0, clans.appliedCount);
    }

    @Test
    void synchronizesOnlyAfterCommittedJournal() {
        AtomicInteger syncs = new AtomicInteger();
        FakeJournal journal = new FakeJournal();
        journal.failAdvanceAt = TransactionState.COMMITTED;

        TransactionResult result = service(new FakePlayerRepository(250), new FakeClanRepository(), journal, syncs::incrementAndGet)
                .createClan(request());

        assertEquals(TransactionResult.Status.STORAGE_ERROR, result.status());
        assertEquals(0, syncs.get());
    }

    @Test
    void sequentialRequestsCannotCreateTwoClans() {
        FakePlayerRepository player = new FakePlayerRepository(300);
        FakeClanRepository clans = new FakeClanRepository();
        FakeJournal journal = new FakeJournal();
        ClanEconomyService service = service(player, clans, journal, () -> { });

        assertEquals(TransactionResult.Status.SUCCESS, service.createClan(request()).status());
        assertEquals(ClanManager.Result.ALREADY_IN_CLAN, service.createClan(request()).clanResult());
        assertEquals(200, player.balance);
        assertEquals(1, clans.appliedCount);
    }

    @Test
    void balanceArithmeticCannotOverflowOrUnderflow() {
        FakePlayerRepository player = new FakePlayerRepository(Integer.MAX_VALUE);
        TransactionResult result = service(player, new FakeClanRepository(), new FakeJournal(), () -> { }).createClan(request());

        assertEquals(TransactionResult.Status.SUCCESS, result.status());
        assertEquals(Integer.MAX_VALUE - 100, player.balance);
        assertEquals(ClanManager.Result.NOT_ENOUGH_COINS,
                service(new FakePlayerRepository(0), new FakeClanRepository(), new FakeJournal(), () -> { })
                        .createClan(request()).clanResult());
    }

    private static ClanEconomyService service(
            FakePlayerRepository player,
            FakeClanRepository clans,
            FakeJournal journal,
            ClanEconomyService.ClientSynchronizer synchronizer
    ) {
        AtomicInteger ids = new AtomicInteger();
        return new ClanEconomyService(
                player,
                clans,
                journal,
                synchronizer,
                () -> UUID.nameUUIDFromBytes(("id-" + ids.incrementAndGet()).getBytes(StandardCharsets.UTF_8)),
                CLOCK,
                ignored -> { }
        );
    }

    private static ClanEconomyService.CreateClanRequest request() {
        return new ClanEconomyService.CreateClanRequest(PLAYER_ID, "Player", "Example Clan", "TAG", 0x11AA11, 100);
    }

    private static CreateClanTransaction transaction(TransactionState state) {
        UUID clanId = UUID.nameUUIDFromBytes("id-1".getBytes(StandardCharsets.UTF_8));
        UUID transactionId = UUID.nameUUIDFromBytes("id-2".getBytes(StandardCharsets.UTF_8));
        ClanCreationPayload payload = new ClanCreationPayload(clanId.toString(), "Example Clan", "TAG", 0x11AA11, PLAYER_ID, "Player");
        return new CreateClanTransaction(1, transactionId, CreateClanTransaction.OPERATION_TYPE, PLAYER_ID, "Player",
                clanId.toString(), 250, 150, payload, CreateClanTransaction.payloadHash(payload), state,
                CLOCK.millis(), CLOCK.millis(), "");
    }

    private static final class FakePlayerRepository implements ClanEconomyService.PlayerProgressRepository {
        private int balance;
        private int appliedCount;
        private boolean failApply;
        private final Map<UUID, Receipt> receipts = new HashMap<>();

        private FakePlayerRepository(int balance) {
            this.balance = balance;
        }

        @Override
        public int currentBalance(UUID playerUuid, String playerName) {
            return balance;
        }

        @Override
        public RepositoryResult applyDebit(CreateClanTransaction transaction) {
            if (failApply) return RepositoryResult.failed("player write failed", null);
            Receipt receipt = receipts.get(transaction.transactionId());
            if (receipt != null) {
                return receipt.matches(transaction) && balance == transaction.newBalance()
                        ? RepositoryResult.alreadyApplied()
                        : RepositoryResult.conflict("receipt conflict");
            }
            if (balance == transaction.newBalance()) {
                return RepositoryResult.conflict("balance has new value without receipt");
            }
            if (balance != transaction.expectedOldBalance()) return RepositoryResult.conflict("unexpected balance");
            balance = transaction.newBalance();
            addReceipt(transaction);
            appliedCount++;
            return RepositoryResult.applied();
        }

        @Override
        public RepositoryResult verifyDebit(CreateClanTransaction transaction) {
            Receipt receipt = receipts.get(transaction.transactionId());
            return receipt != null && receipt.matches(transaction) && balance == transaction.newBalance()
                    ? RepositoryResult.alreadyApplied()
                    : RepositoryResult.conflict("player balance or receipt was not updated");
        }

        private void addReceipt(CreateClanTransaction transaction) {
            receipts.put(transaction.transactionId(), new Receipt(
                    transaction.operationType(),
                    transaction.expectedOldBalance(),
                    transaction.newBalance(),
                    transaction.payloadHash()
            ));
        }
    }

    private record Receipt(String operationType, int expectedOldBalance, int newBalance, String payloadHash) {
        private boolean matches(CreateClanTransaction transaction) {
            return operationType.equals(transaction.operationType())
                    && expectedOldBalance == transaction.expectedOldBalance()
                    && newBalance == transaction.newBalance()
                    && payloadHash.equals(transaction.payloadHash());
        }
    }

    private static final class FakeClanRepository implements ClanEconomyService.ClanRepository {
        private ClanManager.Result preflightStatus = ClanManager.Result.SUCCESS;
        private ClanCreationPayload created;
        private int appliedCount;
        private boolean failApply;

        @Override
        public ClanEconomyService.PreflightResult preflight(ClanEconomyService.CreateClanRequest request, String clanId) {
            if (preflightStatus != ClanManager.Result.SUCCESS) {
                return new ClanEconomyService.PreflightResult(preflightStatus, null);
            }
            if (created != null) return new ClanEconomyService.PreflightResult(ClanManager.Result.ALREADY_IN_CLAN, null);
            return new ClanEconomyService.PreflightResult(ClanManager.Result.SUCCESS,
                    new ClanCreationPayload(clanId, request.name(), request.tag(), request.color(), request.playerUuid(), request.playerName()));
        }

        @Override
        public RepositoryResult applyCreate(CreateClanTransaction transaction) {
            if (failApply) return RepositoryResult.failed("clan write failed", null);
            if (created == null) {
                created = transaction.clanPayload();
                appliedCount++;
                return RepositoryResult.applied();
            }
            return created.equals(transaction.clanPayload()) ? RepositoryResult.alreadyApplied()
                    : RepositoryResult.conflict("different clan");
        }

        @Override
        public RepositoryResult verifyCreate(CreateClanTransaction transaction) {
            return transaction.clanPayload().equals(created) ? RepositoryResult.alreadyApplied()
                    : RepositoryResult.conflict("clan was not created");
        }
    }

    private static final class FakeJournal implements TransactionJournal {
        private final Map<UUID, CreateClanTransaction> transactions = new HashMap<>();
        private boolean failPrepare;
        private TransactionState failAdvanceAt;

        @Override
        public JournalResult prepare(CreateClanTransaction transaction) {
            if (failPrepare) return JournalResult.failure("prepared write failed", null);
            transactions.put(transaction.transactionId(), transaction);
            return JournalResult.success();
        }

        @Override
        public JournalResult advance(CreateClanTransaction transaction, TransactionState targetState, String diagnostic) {
            if (targetState == failAdvanceAt) return JournalResult.failure("advance failed", null);
            CreateClanTransaction current = transactions.get(transaction.transactionId());
            transactions.put(transaction.transactionId(), current.withState(targetState, CLOCK.millis(), diagnostic));
            return JournalResult.success();
        }

        @Override
        public JournalLoadResult loadPending() {
            List<CreateClanTransaction> pending = new ArrayList<>();
            List<CreateClanTransaction> rollback = new ArrayList<>();
            List<CreateClanTransaction> committed = new ArrayList<>();
            for (CreateClanTransaction transaction : transactions.values()) {
                if (transaction.state().isAutoRecoverable()) {
                    pending.add(transaction);
                } else if (transaction.state().requiresManualRecovery()) {
                    rollback.add(transaction);
                } else if (transaction.state().isCommitted()) {
                    committed.add(transaction);
                }
            }
            return new JournalLoadResult(pending, rollback, committed, 0, 0, 0, List.of());
        }

        private CreateClanTransaction onlyTransaction() {
            return transactions.values().iterator().next();
        }
    }
}
