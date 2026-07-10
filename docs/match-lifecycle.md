# Match lifecycle state machine

This document records the current `GameStateManager` behavior and the staged plan
for replacing the implicit static lifecycle with an explicit finite state machine.

Current implementation status:

- Phase 1 implemented: pure lifecycle model, transition policy, typed results,
  read-only snapshots, stale match-id protection, and characterization tests.
- Production side effects still remain in `GameStateManager` in their existing
  order. This is intentional for the first safe patch.
- The static `GameStateManager` remains the compatibility facade.

## 1. Current factual state

### Mutable static fields in `GameStateManager`

| Field | Current role | Risk |
|---|---|---|
| `matchHadEnoughPlayers` | Solo end-condition flag after the match reached the required player count. | Can diverge from lives/scoreboard after forced reset or partial start failure. |
| `matchStartingParticipants` | Alive participant count captured after `startGame`. | Used in end detection but not tied to a durable match id. |
| `tickCounter` | Runs lifecycle logic once per second. | Timing state is mixed with lifecycle state. |
| `startCountdown` | Countdown before match start; `-1` means no countdown. | Duplicates `MatchPhase.STARTING`. |
| `postGameDelay` | Delay before cleanup after `endGame`. | Duplicates `MatchPhase.POST_GAME`/`ENDING`. |
| `matchPhase` | UI and flow phase: waiting, voting, team select, starting, running, post-game, map voting, restarting. | Used both as public API and internal lifecycle state. |
| `currentMode` | Current `MatchMode`. | Mutated by voting, start, debug commands, clan war, and cleanup. |

There is also persistent scoreboard state:

- objective: `gameState`;
- score holder: `#state`;
- values: `WAITING=0`, `RUNNING=1`.

The scoreboard and `matchPhase` are two separate sources of truth today.

### Public `GameStateManager` API

- Scoreboard/runtime queries: `getGameState`, `setGameState`, `isRunning`,
  `getMatchPhase`, `getCurrentMode`, `getLivesPerPlayer`.
- World/player helpers: `isTabletAvailableInLobby`, `isInLobby`,
  `getLobbyLevel`, `getOverworld`, `onlinePlayers`, `playingPlayers`.
- Lifecycle/orchestration: `onServerTick`, `checkForMatchEnd`, `startGame`,
  `endGame`, `resetRuntime`, `forceStopMatch`.
- Validation/debug/admin flow: `validateRuntimeRequirements`,
  `forceStartVoting`, `forceStartMapVoting`, `forceStartTeamSelect`,
  `forceStartClanWar`.

Main call-site groups:

- `ServerEvents`: server tick, start/stop, join/logout/death, combat, respawn.
- `TestModeCommand`: direct admin start/stop/vote/team/clan-war control.
- C2S packets: vote mode/map, team join, map-set flags, contract actions,
  tablet actions.
- Match subsystems: lobby, lives, teams, voice chat, spectator camera, contracts,
  extraction, airdrop, anti-cheat, name tags, class XP, clan locks.
- Diagnostics and integrations: Discord leaderboard and integration checks.

### Current start order

`startGame(server)` currently performs these steps synchronously on the server
thread:

1. Validate overworld, lobby dimension, `war:start_game`, and `war:reset`.
2. Clear RTP timers, passive XP, respawn control, and lives.
3. Set scoreboard state to `RUNNING`.
4. Set `matchPhase = MatchPhase.RUNNING`.
5. Configure clan-war teams, ordinary teams, or reset teams.
6. Reset airdrop scheduler.
7. Start Discord match tracking.
8. Start contracts.
9. Reset match counters.
10. Execute datapack function `war:start_game`.
11. Announce game start through `MapSetManager`.
12. Enforce game rules.
13. Start zone management.
14. Prepare safe teleport pool.
15. For each online player: initialize lives, increment match count, change tags,
    move to lobby, give contract tracker, sync class XP.
16. Start voice team match.
17. Capture `matchStartingParticipants` and `matchHadEnoughPlayers`.
18. Start extraction points.
19. Sync all class XP.
20. Broadcast match-start message.

Important current behavior: scoreboard and phase become `RUNNING` before the
datapack function and player/subsystem steps complete. Phase 1 does not change
that order.

### Current end and cleanup order

`endGame` currently:

1. Clears enough-player and participant counters.
2. Clears start countdown.
3. Sets `postGameDelay = 3`.
4. Sets `matchPhase = POST_GAME`.
5. Sets scoreboard state to `WAITING`.
6. Ends spectator camera and voice match.
7. Applies selected class cooldowns.
8. Finishes contracts and resets extraction.
9. Advances map-set state.
10. Sends Discord leaderboard.
11. Awards competitive-set coins if applicable.
12. Awards winners wins, coins, XP, saves player progress, and syncs XP.
13. Shows winner title.

Actual cleanup is delayed. On a later tick, `cleanupMatchRuntime`:

1. Sets scoreboard state to `WAITING`.
2. Ends spectator camera and voice match.
3. Cleans team scoreboard teams.
4. Resets/cancels airdrops.
5. Resets contracts and extraction.
6. Resets zone, respawn control, passive XP, RTP timers, safe teleport pool, and
   clan-war runtime.
7. Clears dropped items through `WorldCleanupManager`.
8. Executes datapack function `war:reset`.
9. Enforces game rules.
10. Resets lives.
11. For each online player: removes match tags, moves to lobby, syncs class XP.
12. Resets mode to `SOLO`, vote state, and team state.

`forceStopMatch` clears counters/phase and calls `cleanupMatchRuntime`.
`resetRuntime` clears a smaller set of runtime managers and sets scoreboard
`WAITING`.

### External side effects

| Area | Side effects |
|---|---|
| Datapack | Executes `function war:start_game` and `function war:reset`. Command execution result is not currently modeled as a typed transition result. |
| Scoreboard | Creates/updates `gameState/#state`; team scoreboard setup and cleanup through `TeamMatchManager`. |
| Players | Tags, lobby teleports, lives, inventory/tablet state, class XP sync, titles, chat, selected-class cooldowns, progression rewards. |
| Match subsystems | Lives, teams, vote, clan war, zone, respawn, RTP, safe teleport, passive/class XP. |
| Game features | Airdrop, contracts, extraction, spectator camera, voice chat, Discord leaderboard, map set, world cleanup. |
| Server lifecycle | Gamerules, map rotation/set startup, server halt during map restart. |

### Server lifecycle, disconnect, restart

- `ServerEvents.onServerStarted` calls `GameStateManager.resetRuntime`, validates
  runtime requirements, loads map/kit/punishment/clan-related state, and resets
  zone/extraction runtime. A restart should not reconstruct an active match from
  damaged static runtime state.
- `ServerEvents.onServerStopping` flushes persistence. It does not currently run
  an explicit match lifecycle shutdown transition.
- `ServerEvents.onServerStopped` calls `resetRuntime` and then resets many
  subsystems directly.
- Player logout during a running match can turn an alive participant into a
  death, then schedules `checkForMatchEnd` on the server thread.
- Disconnect during `STARTING` is currently handled indirectly by the countdown
  checks; the future service should model this as precondition re-evaluation
  before irreversible start steps.

## 2. Proposed states

| State | Meaning |
|---|---|
| `IDLE` | No active match context. Voting/team-select/map-voting remain compatibility UI phases until migrated. |
| `PREPARING` | Preconditions passed and a match context exists, but irreversible start side effects have not begun. |
| `STARTING` | Start side effects are executing. Completed steps are tracked. |
| `RUNNING` | Required start steps completed; gameplay is active. |
| `ENDING` | Winner/end processing is executing or post-game delay is active. |
| `CLEANING` | Cleanup side effects are executing. |
| `FAILED` | A start/end/cleanup stage failed and diagnostic state is retained. |

No additional core states are needed for Phase 1. Existing UI phases
`VOTING`, `TEAM_SELECT`, `MAP_VOTING`, and `RESTARTING` remain outside the core
match lifecycle for now.

## 3. Allowed transitions

| Source | Event | Destination | Preconditions | Side effects in final architecture | Rollback/compensation | Idempotency behavior |
|---|---|---|---|---|---|---|
| `IDLE` | start request accepted | `PREPARING` | No active context; validation can run before side effects. | Create `MatchContext`. | Drop context if no side effects occurred. | Repeated request while active returns `NO_OP`. |
| `PREPARING` | begin start steps | `STARTING` | Expected match id matches active context. | None in pure model; future facade starts ordered side effects. | `CLEANING` is safe because no or few side effects exist. | Repeated same transition returns `NO_OP`. |
| `STARTING` | all required start steps durable/successful | `RUNNING` | Expected match id; required steps completed. | Future facade may set scoreboard/phase only after mandatory steps. | Failed stage goes to `FAILED`, then `CLEANING`. | Repeated same transition returns `NO_OP`. |
| `RUNNING` | natural or forced end | `ENDING` | Expected match id; active running context. | Winner, rewards, cooldowns, post-game processing. | Cleanup continues even if one end subsystem fails. | Repeated stop returns `NO_OP`. |
| `RUNNING` | forced cleanup/reset | `CLEANING` | Expected match id or admin reset policy. | Full cleanup. | Cleanup steps are idempotent or guarded. | Repeated cleanup returns `NO_OP`. |
| `ENDING` | post-game delay elapsed | `CLEANING` | Expected match id. | Full cleanup. | Continue remaining cleanup steps. | Repeated cleanup returns `NO_OP`. |
| `CLEANING` | cleanup complete | `IDLE` | All required cleanup steps attempted. | Clear context. | If cleanup failed, stay `FAILED` with diagnostics. | Repeated idle marker is `NO_OP`. |
| `PREPARING`, `STARTING`, `RUNNING`, `ENDING`, `CLEANING` | failure | `FAILED` | Expected match id. | Record diagnostic. | Follow with `CLEANING` where possible. | Repeated failure is `NO_OP` unless diagnostics policy changes. |
| `FAILED` | recover/cleanup | `CLEANING` | Expected match id. | Best-effort cleanup. | Cleanup continues through subsystem failures. | Repeated cleanup returns `NO_OP`. |
| `FAILED` | no side effects need cleanup | `IDLE` | Explicit recovery decision. | Clear context. | None. | Repeated idle marker is `NO_OP`. |

Invalid examples:

- `IDLE -> RUNNING`;
- `IDLE -> CLEANING` as a real transition;
- `RUNNING -> STARTING`;
- stale match id mutating the active context.

## 4. Invariants

- At most one active `MatchContext` exists.
- `RUNNING` is reachable only through `PREPARING -> STARTING -> RUNNING`.
- Cleanup can be requested repeatedly without duplicating cleanup effects.
- Start failure must not leave the match reported as successfully running in the
  final architecture.
- `FAILED` always has a path to `CLEANING` or `IDLE`.
- Server restart must initialize lifecycle state as `IDLE`; damaged runtime
  static state must not be interpreted as an active match.
- Stale match ids cannot mutate a newer active context.
- Client-facing success should be sent only after the relevant transition and
  required side effects have completed.

## 5. MatchContext

Phase 1 `MatchContext` contains:

- `matchId`;
- explicit `state`;
- selected `mapId`;
- selected `modeId`;
- `startReason`;
- initiating admin UUID if any;
- participant UUID snapshot;
- completed lifecycle steps;
- `createdAt`;
- `stateEnteredAt`;
- optional `MatchFailure`;
- monotonic `revision`.

The context does not contain Minecraft runtime objects (`MinecraftServer`,
`ServerPlayer`, `Level`, `Entity`, `ItemStack`) and is suitable for ordinary
unit tests.

## 6. Interfaces for Phase 2 side effects

These interfaces should be introduced when production orchestration starts
moving out of `GameStateManager`:

- `DatapackGateway`: validate and execute `war:start_game` / `war:reset` with a
  typed result.
- `ScoreboardGateway`: create/update `gameState` and manage match scoreboard
  effects.
- `MatchPlayerService`: apply player tags, lives, lobby movement, inventory and
  sync operations.
- `MatchSubsystemCoordinator`: call lives, teams, clan war, airdrop, contracts,
  extraction, spectator, voice, zone, cooldown, RTP, teleport and cleanup
  subsystems in a controlled order.
- `MatchClock`: isolate tick/time decisions from Minecraft server tick logic.

## 7. Compatibility strategy

`GameStateManager` remains the public static facade until all call sites are
migrated. The first implementation patch adds:

- `MatchState`;
- `MatchContext`;
- `MatchLifecycleService`;
- typed `MatchTransitionResult`;
- transition policy;
- read-only snapshot;
- legacy-state mapper for tests.

The facade still owns existing side effects. Future patches may let the facade
delegate transition decisions to `MatchLifecycleService` while keeping the same
public method names.

## 8. Rollback and compensating actions

| Start step | Compensation or idempotency rule |
|---|---|
| Clear timers/XP/respawn/lives | Reset operations are idempotent and repeated during cleanup. |
| Set scoreboard `RUNNING` | Set scoreboard back to `WAITING`. |
| Set `matchPhase = RUNNING` | Set phase to `WAITING` or `POST_GAME` according to end path. |
| Configure teams | `TeamMatchManager.cleanupScoreboardTeams` and `TeamMatchManager.reset`. |
| Clan-war start/teams | `ClanWarManager.resetRuntime` plus team reset. |
| Airdrop scheduler | `AirdropManager.resetAutoScheduler` and cancel active airdrop in overworld. |
| Discord match start | Reset current match/leaderboard state if start fails before running. |
| Contracts start | `ContractManager.reset`. |
| Datapack `war:start_game` | Execute `war:reset`; if reset fails, remain `FAILED` with diagnostics. |
| Zone start | `ZoneManager.reset`. |
| Safe teleport pool | `SafeTeleport.clearPool`. |
| Per-player tags/lives/lobby movement | Cleanup removes tags, resets lives, moves players to lobby, syncs XP. |
| Voice team match | `VoiceChatTeamManager.endMatch`. |
| Extraction start | `ExtractionPointManager.reset`. |

## 9. Characterization tests before behavior changes

Phase 1 includes:

- public API inventory test for `GameStateManager` using reflection without
  initializing Minecraft statics;
- legacy scoreboard/phase to shadow lifecycle mapping test;
- lifecycle transition policy tests;
- normal `IDLE -> PREPARING -> STARTING -> RUNNING -> ENDING -> CLEANING -> IDLE`;
- repeated start and repeated transitions;
- invalid transitions;
- stale match id rejection;
- failure-to-cleanup path;
- completed-step idempotency;
- snapshot immutability;
- lifecycle package type-boundary test for no Minecraft/Forge exposed types.

Further characterization tests before moving side effects:

- current `startGame` side-effect order using fakes/gateways;
- current `endGame` reward and post-game delay behavior;
- forced stop cleanup order;
- cleanup continues through independent subsystem failures;
- server start/stopping reset behavior;
- disconnect during `STARTING` and `RUNNING`;
- missing datapack function handling.

## 10. Failure matrix

| Boundary | Current behavior | Target behavior |
|---|---|---|
| Before context creation | No explicit context; operation can simply return. | Stay `IDLE`; no side effects. |
| After `PREPARING` | Not represented today. | Context can be discarded or moved to `CLEANING`; no player-visible success. |
| After scoreboard `RUNNING` | Match may appear running even if later start step fails. | Scoreboard changes become a recorded start step with compensation. |
| During datapack `war:start_game` | Command result is not typed in lifecycle. | Failure records `FAILED`, executes cleanup/reset, returns diagnostic. |
| During player setup | Partial tags/lives/teleports possible if exception escapes. | Completed player step tracked; cleanup removes/normalizes state. |
| After `RUNNING` | Normal gameplay. | Same, but with match id and revision. |
| During `endGame` rewards | Some rewards/progress may be applied before later end step failure. | End steps produce typed results; partial failures are reported and cleanup still runs. |
| During post-game delay | Cleanup deferred by tick. | `ENDING` models the delay explicitly. |
| During cleanup | One exception could interrupt later cleanup if not caught at boundary. | Cleanup coordinator attempts all steps and aggregates errors. |
| During shutdown | Runtime reset is split across events/managers. | Shutdown requests bounded cleanup and leaves diagnostics if incomplete. |

## 11. Implementation breakdown

## 11. Phase 2 implemented start flow

Phase 2 integrates only the start transition:

`IDLE -> PREPARING -> STARTING -> RUNNING`.

Normal `RUNNING -> ENDING -> CLEANING -> IDLE` orchestration is still deferred.
The existing `endGame`, post-game delay, winner processing, and
`cleanupMatchRuntime` side effects remain in `GameStateManager`.

### Source of truth

- `MatchLifecycleService` is the source of truth for whether a start attempt is
  accepted, starting, committed, failed, or rolled back.
- Legacy scoreboard `gameState/#state` remains the gameplay compatibility signal
  for existing callers of `GameStateManager.isRunning`.
- During start, legacy `RUNNING` is written only at the final commit step
  `SET_LEGACY_RUNNING`.
- Existing cleanup/reset boundaries clear the lifecycle context after the old
  cleanup path completes, so future matches can start without migrating normal
  end flow in this patch.

### Production start order

| Order | Step ID | Side effect | Commit classification |
|---:|---|---|---|
| 1 | `RESET_TRANSIENT_RUNTIME` | Clear RTP, passive XP, respawn control, lives; set phase `STARTING`. | Reversible preparation |
| 2 | `CONFIGURE_TEAMS` | Clan-war start/team assignment, team auto-balance, or team reset. | Commit-critical |
| 3 | `RESET_AIRDROP_SCHEDULER` | Reset airdrop scheduler. | Reversible preparation |
| 4 | `START_DISCORD_TRACKING` | Initialize in-memory Discord match stats. | Best-effort reversible |
| 5 | `START_CONTRACTS` | Initialize contract selection runtime. | Reversible |
| 6 | `RESET_MATCH_COUNTERS` | Clear match counters. | Reversible |
| 7 | `EXECUTE_START_DATAPACK` | Execute `function war:start_game`. | Commit-critical |
| 8 | `ANNOUNCE_MAP_START` | Send map-set title announcement. | Notification; rollback is no-op |
| 9 | `ENFORCE_GAME_RULES` | Apply game rules. | Idempotent |
| 10 | `START_ZONE` | Start zone manager. | Reversible |
| 11 | `PREPARE_SAFE_TELEPORT` | Prepare safe teleport pool. | Reversible |
| 12 | `INITIALIZE_PLAYERS` | Lives, match-played progression, tags, lobby move, contract tracker, XP sync. | Commit-critical, partially reversible |
| 13 | `START_VOICE_MATCH` | Start voice team match. | Reversible |
| 14 | `CAPTURE_PARTICIPANTS` | Capture alive participant count/enough-player flag. | Reversible |
| 15 | `START_EXTRACTION` | Initialize extraction runtime. | Reversible |
| 16 | `SYNC_CLASS_XP` | Sync all class XP. | Idempotent |
| 17 | `SET_LEGACY_RUNNING` | Set scoreboard `RUNNING` and phase `RUNNING`. | Commit point |

Final chat broadcast is post-commit. If it fails, the match remains `RUNNING`
and the warning is returned in `MatchStartResult`.

### Preflight

Preflight runs before `beginPreparation` and before start side effects. It
checks:

- lifecycle is `IDLE`;
- server exists;
- current mode is valid;
- required player count is available according to `TestModeManager`;
- overworld and lobby dimensions exist;
- `war:start_game` and `war:reset` functions exist.

Rejected preflight leaves lifecycle `IDLE`, does not apply gateway steps, and
does not increment lifecycle revision.

### Rollback order and compensation

Rollback runs completed steps in exact reverse apply order. The failed step is
not recorded as completed and is not rolled back by the coordinator.

| Step | Compensation |
|---|---|
| `SET_LEGACY_RUNNING` | Set scoreboard `WAITING`, phase `WAITING`. |
| `SYNC_CLASS_XP` | No-op; sync is idempotent. |
| `START_EXTRACTION` | `ExtractionPointManager.reset`. |
| `CAPTURE_PARTICIPANTS` | Clear participant counters. |
| `START_VOICE_MATCH` | `VoiceChatTeamManager.endMatch`. |
| `INITIALIZE_PLAYERS` | Remove match lobby tags, move players to lobby, sync XP. Match-played progression is not fully reversible. |
| `PREPARE_SAFE_TELEPORT` | `SafeTeleport.clearPool`. |
| `START_ZONE` | `ZoneManager.reset`. |
| `ENFORCE_GAME_RULES` | Re-apply game rules; idempotent. |
| `ANNOUNCE_MAP_START` | No-op; title/chat notification cannot be withdrawn. |
| `EXECUTE_START_DATAPACK` | Execute `function war:reset`. |
| `RESET_MATCH_COUNTERS` | Clear counters. |
| `START_CONTRACTS` | `ContractManager.reset`. |
| `START_DISCORD_TRACKING` | `DiscordLeaderboardService.resetMatch`. |
| `RESET_AIRDROP_SCHEDULER` | Reset scheduler and cancel active airdrop if present. |
| `CONFIGURE_TEAMS` | Reset clan-war runtime and team state; mode resets to `SOLO`. |
| `RESET_TRANSIENT_RUNTIME` | Reset phase/countdowns, respawn, lives, passive XP, RTP. |

If every compensation succeeds, lifecycle moves from `FAILED` to `IDLE` and a
new start is allowed. If any compensation fails, lifecycle remains `FAILED` and
new start requests are rejected until an explicit cleanup/reset path clears it.

### Failure table

| Failure point | Already applied | Successful rollback result | Failed rollback result |
|---|---|---|---|
| Before first step | Context `PREPARING/STARTING`, no side effect. | `IDLE`; no legacy running state. | `FAILED`; diagnostic retained. |
| Any start step apply | All previous completed steps only. | Previous steps rolled back in reverse order; `IDLE`. | `FAILED`; rollback failures retained. |
| `SET_LEGACY_RUNNING` apply | All previous gameplay setup completed; legacy running may be partial only if the step throws internally. | Best-effort previous-step rollback; `IDLE` if clean. | `FAILED`; manual cleanup required. |
| `markRunning` after steps | All steps completed including legacy `RUNNING`. | `SET_LEGACY_RUNNING` and previous steps rolled back; `IDLE`. | `FAILED`; legacy state may require manual cleanup. |
| Post-commit broadcast | Lifecycle and legacy state already `RUNNING`. | No rollback. | Not applicable; warning only. |

### Deferred Phase 3

Not included in this patch:

- normal end transition;
- post-game delay state migration;
- winner processing;
- cleanup of a running match as lifecycle-owned orchestration;
- server shutdown cleanup;
- player disconnect cleanup;
- restart recovery for `FAILED` lifecycle after process restart.

Maximum three production patches after Phase 1:

1. **Facade delegation without side-effect reorder.**
   Wire `GameStateManager` to a singleton `MatchLifecycleService` for shadow
   transitions and diagnostics while preserving existing start/end/cleanup
   order.
2. **Start transition migration.**
   Introduce `DatapackGateway`, `ScoreboardGateway`, `MatchPlayerService`, and
   `MatchSubsystemCoordinator`; move `startGame` orchestration behind typed
   steps and compensation.
3. **End/cleanup migration and old-state removal.**
   Move `endGame`, `forceStopMatch`, server shutdown cleanup, and post-game
   delay into lifecycle service. Then remove obsolete mutable static fields when
   all call sites use the explicit snapshot/API.
