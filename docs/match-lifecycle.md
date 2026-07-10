# Match lifecycle: фактическое состояние и план state machine

## Scope и границы

Этот документ описывает только lifecycle матча. На момент его создания
production-код не менялся. Цель — вынести orchestration из статического
`GameStateManager`, не переписывая одновременно `airdrop`, contracts,
extraction или packet protocol.

Новая машина отвечает за жизненный цикл активного матча. Существующие
предматчевые UI-фазы (`WAITING`, `VOTING`, `TEAM_SELECT`, `MAP_VOTING`,
`RESTARTING`) временно остаются compatibility-данными facade и сохраняются в
`MatchContext.phaseSpecificState`; они не являются новыми core states.

## 1. Текущее фактическое состояние

### Mutable static state в `GameStateManager`

| Field | Назначение | Риск |
|---|---|---|
| `matchHadEnoughPlayers` | Для solo определяет, был ли достигнут минимум игроков. | Может расходиться с lives/scoreboard после reset или crash. |
| `matchStartingParticipants` | Количество alive участников после start. | Используется для условия окончания, но не связан с идентификатором матча. |
| `tickCounter` | Делает lifecycle-tick раз в секунду. | Не выражает состояние. |
| `startCountdown` | Countdown перед start; `-1` означает отсутствие countdown. | Смешан с `MatchPhase.STARTING`. |
| `postGameDelay` | Задержка после finish. | Смешан с `MatchPhase.POST_GAME`. |
| `matchPhase` | UI/flow фаза: waiting, vote, team select, starting, running, post-game, map vote, restart. | Одновременно используется как API для пакетов и часть lifecycle. |
| `currentMode` | Выбранный `MatchMode`. | Изменяется на start, cleanup, vote и debug-командами. |

Также persistent scoreboard objective `gameState` и score `#state` хранят
`WAITING=0`/`RUNNING=1`. Это второй источник истины: runtime phase и
scoreboard сейчас могут кратковременно расходиться.

### Публичный API facade

`GameStateManager` публикует:

- scoreboard/runtime queries: `getGameState`, `setGameState`, `isRunning`,
  `getMatchPhase`, `getCurrentMode`, `getLivesPerPlayer`;
- world/player helpers: `isTabletAvailableInLobby`, `isInLobby`,
  `getLobbyLevel`, `getOverworld`, `onlinePlayers`, `playingPlayers`;
- orchestration: `onServerTick`, `checkForMatchEnd`, `startGame`,
  `endGame` (two overloads), `resetRuntime`, `forceStopMatch`;
- validation/debug flow: `validateRuntimeRequirements`, `forceStartVoting`,
  `forceStartMapVoting`, `forceStartTeamSelect`, `forceStartClanWar`.

Callers include `ServerEvents`, `TestModeCommand`, vote/team packets,
`MapSetManager`, `LobbyManager`, `LivesManager`, airdrop, extraction,
contracts, spectator/voice managers, anti-cheat and tablet sync. This is why
the static class must remain a compatibility facade during migration.

### Текущий start order (`startGame`)

1. Проверка overworld, lobby dimension и обеих datapack functions.
2. Сброс RTP, passive XP, respawn-control и lives.
3. Немедленно установить scoreboard `RUNNING` и `MatchPhase.RUNNING`.
4. Настроить clan-war teams, обычные teams либо reset teams.
5. Сбросить airdrop scheduler; начать Discord match и contracts.
6. Сбросить counters; выполнить `function war:start_game` без проверки
   command result; объявить старт, применить gamerules, запустить zone.
7. Подготовить SafeTeleport pool.
8. Для каждого online player: lives, match count, tags, lobby teleport,
   contract tracker, tablet sync.
9. Запустить voice team match; зафиксировать participant count; начать
   extraction; sync всех; broadcast.

Следствие: ошибка datapack/zone/player step после пункта 3 оставляет матч
объявленным RUNNING без компенсирующего cleanup.

### Текущий end и cleanup order

`endGame` сначала ставит `POST_GAME`, score `WAITING`, задержку 3 секунды,
вызывает spectator/voice end, cooldowns, contracts finish, extraction reset,
map-set completion, Discord leaderboard и награды победителям. После delay
`onServerTick` вызывает `cleanupMatchRuntime`, затем переходит в map vote либо
`MatchPhase.WAITING`.

`cleanupMatchRuntime` выполняет: scoreboard WAITING; spectator и voice end;
team scoreboard cleanup; airdrop scheduler reset/cancel; contracts/extraction,
zone, respawn, passive XP, RTP и teleport pool reset; clan-war reset; world
dropped-item cleanup; `function war:reset`; gamerules; lives reset; player tags
и lobby teleport; sync; mode/vote/team reset.

`forceStopMatch` очищает counters/phase и вызывает тот же cleanup. `resetRuntime`
сбрасывает только часть subsystem runtime (clan-war, vote, spectator, voice,
teams, extraction) и scoreboard.

### Внешние side effects и вызываемые managers

| Категория | Наблюдаемые side effects |
|---|---|
| Datapack | `function war:start_game`, `function war:reset`; сейчас результат command execution не проверяется. |
| Scoreboard | `gameState/#state`; lives objective; создаваемые/удаляемые `TeamMatchManager` scoreboard teams. |
| Players | tags, game mode/teleport через lobby, lives, inventory/tablet/XP sync, title/chat, class cooldowns, match/win/coin progression. |
| Match subsystems | `LivesManager`, `TeamMatchManager`, `VoteManager`, `ClanWarManager`, `ZoneManager`, `RespawnControlManager`, `RtpTimerManager`, `SafeTeleport`, `PassiveClassXPManager`, `ClassCooldownManager`. |
| Game features | `AirdropManager`, `ContractManager`, `ExtractionPointManager`, `SpectatorCameraManager`, `VoiceChatTeamManager`, `DiscordLeaderboardService`, `MapSetManager`, `WorldCleanupManager`. |
| World/server | gamerules via `DropControlManager`; map voting arms `MapRotationManager`, затем `server.halt(false)`. |

### Server lifecycle, disconnect и restart

- `ServerEvents.onServerStarted` применяет gamerules, вызывает
  `GameStateManager.resetRuntime`, validation, map rotation/set init и reset
  zone/extraction. Поэтому restart не должен восстанавливать static match как
  RUNNING: новый service всегда создаётся в `IDLE` и facade записывает WAITING.
- `onServerStopping` сохраняет persistence; `onServerStopped` вновь вызывает
  `resetRuntime` и отдельно reset-ит voice, airdrop, contracts, extraction,
  timers, player/tablet state и другие runtime managers.
- `MapSetManager.tickRestart` сохраняет player progress и вызывает
  `server.halt(false)`. Это должен быть явный `server-restart` event, который
  переводит active lifecycle в cleanup до остановки, насколько позволяет
  порядок Forge events.
- На disconnect `ServerEvents` сохраняет progress, уведомляет contracts и
  extraction; если игрок — alive RUNNING participant, вызывает
  `LivesManager.handleDeath`, затем откладывает `checkForMatchEnd` на server
  thread. Во время `STARTING` новый lifecycle должен отменить start до
  irreversible player steps, если preconditions больше не выполняются.

## 2. Предлагаемые core states

| State | Значение |
|---|---|
| `IDLE` | Нет активного `MatchContext`; scoreboard WAITING. Lobby/voting UI может существовать как compatibility phase. |
| `PREPARING` | Preconditions проверены, создаётся context и immutable список участников. Side effects ещё не должны означать RUNNING. |
| `STARTING` | Выполняются обратимые/идемпотентные start steps; completed steps фиксируются в context. |
| `RUNNING` | Все обязательные start steps успешно завершены; scoreboard RUNNING и gameplay разрешён. |
| `ENDING` | Зафиксирован исход матча; выполняются награды/показ результата и post-game delay. |
| `CLEANING` | Идёт best-effort cleanup всех зарегистрированных subsystems. |
| `FAILED` | Start/cleanup не достигли требуемого инварианта; context хранит failure stage и diagnostics. Переход разрешён только в CLEANING либо IDLE после доказанного clean state. |

Дополнительные core states не нужны: countdown/vote/map restart остаются
phase-specific compatibility state, чтобы не менять packet protocol и
`MatchPhase` одним diff.

## 3. Разрешённые переходы

| Source | Event | Destination | Preconditions | Side effects | Rollback / idempotency |
|---|---|---|---|---|---|
| IDLE | `requestStart` | PREPARING | runtime valid, datapack functions present, map/mode valid, players satisfy rules | создать `MatchContext` с UUID и participant snapshot | повторный request возвращает typed result с current state, ничего не запускает |
| PREPARING | preflight complete | STARTING | context существует, participants не пусты, если solo debug не включён | начать ordered start steps | до первого step cleanup — no-op |
| PREPARING/STARTING | disconnect меняет preconditions | CLEANING | start ещё не committed RUNNING | пометить diagnostic `participants_changed` | cleanup только completed steps; затем IDLE |
| STARTING | all required steps complete | RUNNING | datapack start, teams, world systems, players и required sync завершены | scoreboard RUNNING, legacy `MatchPhase.RUNNING`, announce | повторный completion no-op |
| STARTING | step failure/exception | FAILED → CLEANING | failure stage зафиксирован | выполнить compensating cleanup | ошибки cleanup агрегируются; не скрывают исходный failure |
| RUNNING | natural winner/no winner | ENDING | context matchId current; не ending/cleaning | freeze outcome, scoreboard WAITING, spectator/voice end, rewards, post-game clock | повторный end возвращает current state, не выдаёт награды повторно |
| RUNNING/PREPARING/STARTING/ENDING/FAILED | force stop, server stopping, restart | CLEANING | context may be partial | отменить active work и cleanup | cleanup steps idempotent; каждый запускается не более одного раза на context |
| ENDING | post-game clock elapsed | CLEANING | outcome already recorded | cleanup | повторный tick no-op |
| CLEANING | all cleanup steps successful | IDLE | scoreboard WAITING, active context detached | legacy lobby/map-vote continuation | repeated cleanup returns success/no-op |
| CLEANING | one or more cleanup failures | FAILED | diagnostics recorded, remaining steps attempted | retain context for diagnostics | later `retryCleanup` or startup reset reaches IDLE |
| FAILED | `retryCleanup` / startup reset | CLEANING or IDLE | no gameplay may run | execute remaining cleanup or prove no active runtime | never infer RUNNING from stale static/scoreboard state |

## 4. Инварианты

1. В service одновременно существует максимум один active `MatchContext`.
2. `RUNNING` достижимо только после успешных обязательных start steps и
   `DatapackGateway.start`.
3. Каждая cleanup операция выполняется как минимум безопасно повторяемо; context
   отмечает completed cleanup steps, поэтому retry не выдаёт награды и не
   повторяет destructive world operation без необходимости.
4. Ошибка datapack function не оставляет RUNNING scoreboard/partial match:
   state становится `FAILED`, затем `CLEANING`.
5. `FAILED` имеет явный route в `CLEANING` либо `IDLE`; facade не маскирует его
   как RUNNING.
6. На server start static runtime не считается доказательством активного матча:
   создаётся IDLE и записывается WAITING, затем выполняется idempotent reset.
7. Все transition и gateway вызовы выполняются на server thread; фоновые потоки
   для lifecycle не вводятся.

## 5. MatchContext

```java
record MatchContext(
    UUID matchId,
    MatchState state,
    String selectedMap,
    MatchMode mode,
    long createdAtTick,
    long stateChangedAtTick,
    List<UUID> participantIds,
    LegacyPhaseState phaseSpecificState,
    EnumSet<StartStep> completedStartSteps,
    EnumSet<CleanupStep> completedCleanupSteps,
    MatchOutcome outcome,
    FailureInfo failure
) {}
```

`participantIds` — immutable UUID snapshot, а не `ServerPlayer`. `selectedMap`
может быть пустым до map rotation; он не должен заменять persistent
`MapSetManager` state. `FailureInfo` содержит stage, diagnostic и
исключение/его class+message для logger, но не secrets.

## 6. Предлагаемые interfaces side effects

```java
interface DatapackGateway {
    GatewayResult validateRequiredFunctions();
    GatewayResult runStart();
    GatewayResult runReset();
}

interface ScoreboardGateway {
    GatewayResult setMatchRunning(boolean running);
    GatewayResult applyTeams(MatchContext context);
    GatewayResult cleanupTeams(MatchContext context);
}

interface MatchPlayerService {
    List<UUID> snapshotEligibleParticipants();
    GatewayResult preparePlayers(MatchContext context);
    GatewayResult cleanupPlayers(MatchContext context);
    GatewayResult awardOutcome(MatchContext context);
}

interface MatchSubsystemCoordinator {
    GatewayResult startStep(StartStep step, MatchContext context);
    GatewayResult cleanupStep(CleanupStep step, MatchContext context);
}

interface MatchClock {
    long currentTick();
    boolean postGameDelayElapsed(MatchContext context);
}

record TransitionResult(
    boolean success,
    MatchState currentState,
    String failureStage,
    String diagnostic,
    Optional<Throwable> cause
) {}
```

Production adapters may hold `MinecraftServer` only on the server thread.
Tests replace all gateways with plain fakes.

## 7. Compatibility strategy

1. Add `MatchState`, `MatchContext`, `MatchLifecycleService` and gateways
   without changing existing public static signatures.
2. `GameStateManager` owns one server-thread service instance and delegates
   `startGame`, `endGame`, `forceStopMatch`, `resetRuntime`, tick and state
   queries. Existing callers still receive legacy return types.
3. Map lifecycle state to legacy API: only `RUNNING` maps to scoreboard RUNNING;
   all other core states map to WAITING. `MatchPhase` remains projected from
   context/legacy lobby flow until packets and callers are migrated.
4. Migrate internal callers incrementally to typed `TransitionResult`; retain
   facade until all call sites no longer need static globals.

## 8. Start steps and compensation

| Start step | Completion evidence | Compensation / idempotency |
|---|---|---|
| Clear RTP/passive XP/respawn/lives | `START_RUNTIME_RESET` | Same reset operations in cleanup; idempotent. |
| Configure team/clan-war assignments | `TEAMS_ASSIGNED` | `TeamMatchManager.reset` + `cleanupScoreboardTeams`; safe repeatedly. |
| Apply scoreboard teams | `SCOREBOARD_TEAMS_APPLIED` | `cleanupScoreboardTeams`; restore remembered original teams. |
| Reset airdrop/Discord/contracts | individual step IDs | airdrop cancel/reset; Discord reset match; `ContractManager.reset`. |
| `war:start_game` | `DATAPACK_STARTED` only on successful gateway result | `war:reset`; if missing/fails, do not publish RUNNING. |
| Gamerules/zone/teleport pool | individual IDs | enforce gamerules; `ZoneManager.reset`; `SafeTeleport.clearPool`. |
| Prepare players/lives/tags/lobby | `PLAYERS_PREPARED` | cleanup player tags/lives/lobby reset; repeat-safe per player. |
| Voice/extraction | individual IDs | `VoiceChatTeamManager.endMatch`; `ExtractionPointManager.reset`. |
| Publish RUNNING and announcement | `RUNNING_PUBLISHED` | set scoreboard WAITING during cleanup; announcement has no rollback and must occur last. |

Rewards and `MapSetManager.onGameCompleted` are ENDING steps, not start steps;
they need their own completion markers to prevent duplicate awards on repeated
`endGame`.

## 9. Characterization tests before behavior changes

Create pure/unit tests around fakes before migration:

- public facade projection: legacy scoreboard/phase for IDLE, RUNNING and
  post-game;
- normal `IDLE → RUNNING`; repeat start is no-op/typed rejection;
- empty participant list and insufficient player precondition;
- each start step failure, including missing `war:start_game`; verify completed
  prior steps are cleaned in reverse dependency order;
- datapack start failure does not publish RUNNING;
- normal stop; repeated stop; `FAILED → CLEANING` retry;
- one cleanup failure does not stop later cleanup steps and result aggregates
  diagnostics;
- disconnect while STARTING cancels before player preparation or cleans completed
  steps;
- no duplicate rewards/map completion on repeated end;
- facade observes the service's current state consistently;
- `MatchContext` participant snapshot contains UUIDs only, never Minecraft
  runtime objects;
- Server-start reset always projects IDLE even when a stale `gameState/#state`
  existed.

## 10. Small implementation patches (5B)

1. **State model and tests.** Add `MatchState`, immutable context, typed
   result, pure transition table and fake-gateway characterization tests. No
   caller migration.
2. **Facade delegation.** Instantiate service behind `GameStateManager`; route
   queries and `resetRuntime` while preserving `MatchPhase`/scoreboard API.
3. **Start transition.** Add `DatapackGateway`, ordered start steps, completion
   markers and rollback; migrate `startGame` and all start entry points.
4. **End/cleanup transition.** Migrate end, force stop, post-game delay,
   disconnect/start cancellation and server shutdown; aggregate cleanup errors.
5. **Remove old state.** After all callers use facade/service projections,
   delete duplicate static counters and direct orchestration, retaining only
   compatibility projections until packet callers are migrated.

No full rewrite is proposed. Airdrop, contracts, extraction and other managers
remain unchanged internally; only their lifecycle calls are wrapped by adapters.
