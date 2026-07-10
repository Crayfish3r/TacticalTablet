# Clan/economy transactions

## Scope and current behaviour

This document designs a durable transaction for operations that modify both a
player-progress JSON file and `clans.json`. It deliberately does not change the
existing JSON files or production code.

The current `ClanManager.createClan` sequence is:

1. validate name, tag, colour, membership, limits, and current coin balance;
2. subtract `ClanConstants.CREATE_COST` from the in-memory player progress;
3. call `PlayerProgressManager.savePlayer(player)`;
4. call `ClassXPManager.sync(player)` (which sends tablet state and clan state);
5. create the in-memory clan object and append it to clan storage;
6. atomically save `clans.json` and then call `syncAll`.

`PlayerProgressManager.savePlayer` currently logs a failed write without
returning a result. `ClanManager` now returns `STORAGE_ERROR` when its own
write fails, but the player debit may already have been persisted. Therefore an
in-memory rollback is not sufficient and UI can observe the debit before the
cross-store operation has committed.

`ClanCreatePacket` is the only C2S entry point that invokes `createClan`.
There is no command that creates a clan. It schedules the call on the server
thread and sends its text result after `createClan` returns.

## Invariants

- A player never permanently loses clan-creation coins without the matching
  clan being created.
- A clan never permanently exists without its creation payment.
- Repeated recovery never creates a second clan or applies a second debit.
- `SUCCESS`, tablet state, and clan-list UI are sent only after durable commit.
- A crash at every boundary has a deterministic recovery action before players
  can use clan or economy operations.
- Journal payloads contain only immutable data (`UUID`, strings, numbers,
  timestamps, and JSON-compatible DTOs), never `MinecraftServer`, `ServerPlayer`,
  `Level`, or other Minecraft runtime objects.

## Cross-store operations found

| Operation | Stores | Transaction priority |
| --- | --- | --- |
| Clan creation | one player progress file and `clans.json` | Implement first; currently unsafe. |
| Clan-war set reward | `map_set_stats.json` lifecycle and `clans.json` clan-coin award | Follow-up; requires a separate operation type. |
| Server stopping | player progress, prefixes, punishments | Coordinated flush, not one business transaction; do not claim atomicity across all stores. |
| Match completion | several player progress files plus match/set runtime storage | Future batch/reward design; do not fold into clan creation. |

`ClassXPManager` is not an independent durable store; it reads player progress
and performs client synchronization. Its sync must remain after transaction
commit. Clan colour/class purchases and normal coin commands currently change
one durable store each, so they are out of this first transaction scope.

## Failure table: clan creation

The transaction uses a fixed transaction ID and a preallocated clan ID. Recovery
runs on the server thread during startup, after progress and clan repositories
are loaded, and before clients are allowed to mutate either store.

| Failure boundary | On-disk facts | Required recovery/action |
| --- | --- | --- |
| Before journal `PREPARED` | No journal is durable. | Treat as never started; no debit or clan creation. |
| After `PREPARED` | Immutable intent is durable; neither store need be changed. | Apply the player debit conditionally, then continue forward. |
| After saving player | Player has `newBalance`; clan may be absent. | Detect the expected debit and create the fixed clan exactly once. Never debit again. |
| After saving clan | Clan may exist and player debit may exist; status may lag. | Verify both expected facts and write `COMMITTED`; do not duplicate either fact. |
| Before commit marker | Both stores can be durable but journal is not committed. | Same verification; complete the commit marker. |
| After commit marker | Both facts and commit marker are durable. | Archive/remove journal later; never reapply effects. |
| During client sync | Durable transaction is committed. | Retry/supply normal state sync on reconnect; never undo the transaction. |
| During shutdown | A journal may be at any non-terminal state. | Stop accepting new transactions, leave its durable journal, and recover synchronously on next start. Do not call `System.exit`. |

If recovery finds a clan with the transaction's ID but a payload hash different
from the journal, or a player balance that is neither `oldBalance` nor
`newBalance`, it must move the journal to `ROLLBACK_REQUIRED`/quarantine,
log the transaction ID and paths, block automatic replay of that journal, and
surface an operator diagnostic. This is deterministic and avoids guessing over
possibly manually edited data.

## Options considered

| Approach | Advantages | Failure mode | Decision |
| --- | --- | --- | --- |
| In-memory rollback | Small diff; familiar control flow. | Cannot repair a crash or a successful first write followed by failed second write. | Reject. |
| Two snapshot files | Can restore both files after a failed step. | Requires choosing which snapshot wins after a crash; conflicts with unrelated writes and scales poorly to multiple player files. | Reject for primary design. |
| File write-ahead journal | Uses existing JSON, preserves intent before mutation, supports idempotent forward recovery, and can be introduced operation-by-operation. | Requires repository conditional writes and startup recovery. | Choose. |
| SQLite | Strong transactional semantics. | New production dependency, migration/operational scope, and a full persistence redesign. | Do not choose without separate approval. |

## Chosen architecture: write-ahead transaction journal

Add a `transactions/` subdirectory under `TacticalTabletStoragePaths`. Each
active transaction is one immutable JSON record written with `AtomicFileStore`.
State advances are replacements of that record through the same atomic writer.
The journal implementation must additionally force the temporary file contents
before rename, and force the final file after rename where the platform allows
it; rename alone is not a durable commit guarantee after power loss.

Implementation alignment note: the current `AtomicFileStore` already provides
same-directory temporary files, atomic-move fallback, and typed diagnostics, but
does not yet force file contents. The create-clan implementation below extends
that common writer with file forcing; it does not claim that the pre-existing
writer alone supplies power-loss durability.

Only the server thread invokes `ClanEconomyService`. It acquires a logical lock
for the player UUID and the clan name/tag keys while a transaction is active.
No asynchronous I/O is introduced. Repositories work with immutable DTO
snapshots, not Minecraft objects.

### Transaction record

Illustrative JSON (all fields are required unless marked optional):

```json
{
  "schemaVersion": 1,
  "transactionId": "8d0ef68f-5343-4ec7-9f04-fcaa4e0d7446",
  "operationType": "CREATE_CLAN",
  "playerUuid": "…",
  "clanId": "…",
  "expectedOldBalance": 250,
  "newBalance": 150,
  "clanPayload": {
    "id": "…",
    "name": "Example",
    "tag": "TAG",
    "color": 123456,
    "ownerUuid": "…",
    "ownerName": "Player",
    "members": [{"uuid": "…", "name": "Player"}],
    "pending": [],
    "unlockedClasses": []
  },
  "clanPayloadSha256": "…",
  "state": "PREPARED",
  "createdAt": 1730000000000,
  "updatedAt": 1730000000000,
  "diagnostic": ""
}
```

`clanPayload` is immutable once prepared. A reference to an immutable payload
file is acceptable only if that file is written before `PREPARED` is committed
and is addressed by content hash. Keeping the small payload in the journal is
the simpler first implementation.

### States and transitions

```text
PREPARED -> PLAYER_APPLIED -> CLAN_APPLIED -> COMMITTED -> ARCHIVED
     |             |                |
     +-------------+----------------+-> ROLLBACK_REQUIRED
```

- `PREPARED`: immutable intent is durable; no user-visible success.
- `PLAYER_APPLIED`: repository has durably written exactly `newBalance` after
  confirming `expectedOldBalance` or has proven the same debit was already
  applied by this transaction.
- `CLAN_APPLIED`: repository has durably created the exact immutable payload,
  or has proven it already exists with equal ID and payload hash.
- `COMMITTED`: both durable facts were verified. UI synchronization and success
  reporting are now allowed.
- `ARCHIVED`: the record is moved/copy-archived only after a retention period;
  it is not part of recovery.
- `ROLLBACK_REQUIRED`: automatic replay is unsafe because persisted facts
  conflict with the journal. It is terminal until an explicit administrator
  repair tool resolves it.

No transition goes backward during normal recovery. Forward completion is
preferred because the durable journal contains enough intent to complete the
business operation without guessing. `FAILED` is reserved for a failure before
`PREPARED` becomes durable and is not persisted as an active transaction.

### Normal create algorithm

1. Validate C2S input, permissions, clan name/tag/colour uniqueness, and read
   one immutable player-progress snapshot. Do not mutate either manager.
2. Allocate `transactionId` and `clanId`; build immutable clan DTO and record
   `expectedOldBalance`/`newBalance`.
3. Atomically write and force `PREPARED`. If that fails, return storage error
   without changing progress, clan state, or UI.
4. `PlayerProgressRepository.applyClanDebit(record)` conditionally writes the
   player JSON only when its balance equals `expectedOldBalance`; if it already
   equals `newBalance`, it verifies the same transaction's stage instead of
   debiting again. Advance journal to `PLAYER_APPLIED`.
5. `ClanRepository.applyCreate(record)` inserts only the preallocated clan ID
   and only if absent; an existing equal payload is idempotent. Advance journal
   to `CLAN_APPLIED`.
6. Re-read/verify both persisted facts, atomically write and force `COMMITTED`.
7. Update in-memory manager caches from the committed DTO, then call
   `ClassXPManager.sync`/`ClanManager.syncAll` and return `SUCCESS`.

If a normal step fails after `PREPARED`, leave the journal in its last durable
state and return a storage error. Do not perform best-effort in-memory rollback
as the source of truth.

### Startup recovery

1. `ServerEvents.onServerStarted` loads the JSON repositories and runs
   `TransactionJournal.recoverPending()` before enabling packet-driven clan or
   economy mutations. It must happen before normal player joins can invoke
   `ClanManager`.
2. Enumerate journal files in stable transaction-ID order. Ignore only valid
   `COMMITTED`/`ARCHIVED` records; a malformed journal is quarantined and
   blocks only that operation, not server startup.
3. For each active record, inspect durable player balance and clan ID/payload:
   - old balance + missing clan: apply debit, then create clan;
   - new balance + missing clan: create clan only;
   - old balance + equal clan: apply debit only;
   - new balance + equal clan: write `COMMITTED` only.
4. Any exact stage already present is verified, never applied twice. Conflicting
   facts enter `ROLLBACK_REQUIRED` with diagnostics rather than overwriting
   user data.
5. Archive committed journals after a configurable retention period or delete
   them only after a durable archive/receipt exists. Keep active and quarantined
   journals indefinitely until resolved.

## Proposed interfaces

```java
interface ClanEconomyService {
    TransactionResult createClan(CreateClanRequest request);
    RecoveryReport recoverPendingTransactions();
}

interface PlayerProgressRepository {
    PlayerProgressSnapshot read(UUID playerUuid);
    RepositoryResult applyClanDebit(CreateClanTransaction transaction);
    boolean matchesClanDebit(CreateClanTransaction transaction);
}

interface ClanRepository {
    ClanSnapshot findById(String clanId);
    RepositoryResult applyCreate(CreateClanTransaction transaction);
    boolean matchesCreate(CreateClanTransaction transaction);
}

interface TransactionJournal {
    JournalResult prepare(CreateClanTransaction transaction);
    JournalResult advance(UUID transactionId, TransactionState state);
    List<CreateClanTransaction> activeTransactions();
    JournalResult archive(UUID transactionId);
}

record TransactionResult(
        Status status,
        UUID transactionId,
        String diagnostic,
        Optional<Throwable> exception
) { }
```

Repository and journal results are typed statuses plus diagnostics; no `void`
or bare `boolean` represents a persistence outcome. Forge adapters convert a
`ServerPlayer` to an immutable request before calling the service and perform
client synchronization only for `COMMITTED` success.

## Incremental implementation plan

1. Introduce immutable DTOs, journal record/state codec, fsync-capable journal
   writer, and pure recovery tests. No call sites change yet.
2. Add repository adapters around the existing `PlayerProgressManager` and
   `ClanManager`; run recovery at server startup while retaining existing APIs.
3. Route only `ClanCreatePacket` through `ClanEconomyService`, defer sync until
   commit, then add clan-war reward transactions as a separate operation type.

## Unit and fault-injection tests

### Journal and codec

- schema round trip; unknown schema is quarantined;
- immutable payload hash changes when any clan field changes;
- invalid state transition is rejected;
- journal atomic-write, replace, forced-write failure, atomic-move fallback,
  temporary-file cleanup, and path traversal rejection.

### Repository/idempotency

- debit from expected balance succeeds exactly once;
- repeated debit when `newBalance` is already present is a verified no-op;
- debit with an unexpected balance returns conflict without writing;
- create with absent clan inserts the fixed ID;
- repeated equal create is a no-op; same ID/different payload is conflict;
- JSON data remains compatible with current player and clan files.

### Fault boundaries

Inject failures immediately before/after every durable write and state advance:

- before/after `PREPARED`;
- after player write and before `PLAYER_APPLIED`;
- after `PLAYER_APPLIED` and before clan write;
- after clan write and before `CLAN_APPLIED`;
- after `CLAN_APPLIED` and before `COMMITTED`;
- after `COMMITTED` and during UI sync;
- shutdown between every pair of stages;
- restart recovery for all fact combinations in the failure table.

Each test asserts one clan at most, one debit at most, deterministic terminal
state, source JSON preserved on conflict/corruption, and no packet/UI success
before `COMMITTED`.
