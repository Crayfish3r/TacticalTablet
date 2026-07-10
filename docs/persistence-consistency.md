# Persistence consistency

## Problem and ticket API

Global `ModPersistenceExecutor.flush()` waited for unrelated writes. A create-clan WAL debit could therefore time out behind another player's autosave, despite its own snapshot already being durable. Each queued immutable write now has a `SaveTicket` (`target`, `revision`, `completion`) and a terminal `DurableSaveResult`: `WRITTEN`, `SUPERSEDED`, `STALE_REJECTED`, `FAILED`, `QUEUE_REJECTED`, or `EXECUTOR_STOPPED`.

The single persistence worker coalesces only queued writes for the same normalized target. Replaced tickets complete as `SUPERSEDED`; rejected and shutdown tickets also complete, so no caller is left waiting indefinitely.

## Revision ordering and WAL

Player snapshots use the same monotonic revision source for autosave and the synchronous WAL repository adapter. A transaction submits its debit snapshot through the common executor and waits only for that ticket with a bounded timeout. `WRITTEN` is required before the player step advances; `SUPERSEDED` is not success. The WAL sequence is PREPARED → player ticket durable → PLAYER_APPLIED → clan durable → CLAN_APPLIED → verified facts → COMMITTED → client sync.

An older queued revision cannot replace a later queued revision. A currently executing old revision can finish first, but the single worker then writes the later revision, making it the final file.

## Backup and lifecycle

The existing backup writer uses one `BackupCoordinator` and an `.incomplete` directory before publishing its manifest. Completed backups are therefore distinguishable from interrupted copies. A full cross-store generation barrier and copying of clan/WAL state remain required before backups can be represented as restore-consistent snapshots; this change does not claim that guarantee yet.

Startup must finish migration, repository loading and WAL recovery before accepting clan/economy operations. Shutdown stops normal intake, submits final snapshots, waits their bounded completion, then uses the global flush only to drain/stop the executor.

## Crash limits

Atomic file replacement protects an individual file. Without `ATOMIC_MOVE` the store falls back to replace/move; without directory fsync neither mode can promise persistence across sudden power loss after a successful API return. The WAL remains the recovery source when a transaction cannot reach COMMITTED.
