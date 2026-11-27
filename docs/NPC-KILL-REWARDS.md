# NPC kill rewards (2026-10-24)

## Обработка

`NpcKillRewardHandler` слушает серверный `LivingDeathEvent` на LOWEST и явно
отбрасывает отменённое событие. Существующий player death pipeline не изменён.
Обычные мобы и смерти игроков выходят сразу. Адаптер TacticalNPC 0.2.0 получает
`ownerMatchId`, `groupId` и серверное `npcName()` только у `TacticalNpcEntity`.
Дополнительно UUID жертвы должен принадлежать успешному spawn текущего TT manager:
чужие, manual и откаченные NPC награду не дают.

Требуются RUNNING lifecycle с тем же matchId и активная боевая фаза, включённая
NPC-сессия и разрешённый mode. Убийца — подключённый ServerPlayer, не FakePlayer,
живой участник текущего lifecycle с lives binding, `war.playing`, без spectator,
creative, moderator и lobby. Игрок и NPC находятся в одном настроенном измерении
внутри world border. Removed entities и `/kill` исключены. Cleanup/despawn/rollback
используют discard без LivingDeathEvent; после закрытия сессии обработчик недоступен.

Атрибуция использует `ResponsiblePlayerResolver`, затем требует прямого игрока
или Projectile с подтверждённым тем же владельцем. TaCZ EntityKineticBullet
наследует Projectile. Нет nearby/last-attacker attribution, новых TaCZ callbacks,
PvP assists, team kills, lives/death transition или winner checks.

`NpcDeathClaims` хранится внутри менеджера сессии, ключ — `(matchId, victim UUID)`.
Claim выполняется перед изменением прогресса. Удаление сущности не освобождает claim;
deactivate/cleanup/server replacement очищают реестр. После ENDING сессия закрыта,
поэтому очистка не допускает повторного начисления. Нет нового tick manager, поиска
NPC/игроков для атрибуции, загрузки чанков или внешних запросов.

## Прогресс и kill feed

`PlayerProgressManager.addCoins(ServerPlayer, 3)` помечает существующий профиль dirty.
Разница баланса определяет фактическую награду (0–3 при integer cap).
`ClassXPManager.addBaseXPDeferredSync` вызывает существующий `PlayerProgressManager.addXP`
без applyBoost. Chaos запрещён wrapper-ом; competitive, неизвестный/закрытый класс
и class tier/max XP cap проверяются существующей прогрессией. Shop-классы XP не получают.
Без выбранного класса XP = 0; монеты начисляются независимо. Нет sync/save на каждый XP
или отдельного профиля NPC. После обеих мутаций вызывается один `ClassXPManager.sync`.
Сохранение идёт штатными autosave/logout/shutdown через immutable snapshots и atomic
repository writes; подтверждение дискового commit на каждое убийство не ожидается.
Как и другие dirty rewards, ещё не сохранённые изменения могут потеряться при аварии.

`TacticalKillFeed.publishNpcKill` использует тот же фильтр `isMatchViewer`, PacketHandler,
KillFeedPacket и клиентский UI. Late spectators исключены по существующим правилам.
Получатели видят killer name, серверное имя NPC (fallback `NPC`), существующие правила
weapon/cause. Только killer UUID получает фактические awardedCoins/awardedXp.
Packet format и IDs не меняются. Client state/overlay не требуют PlayerInfo для жертвы.

## Проверки

Unit tests проверяют pair key/dedup/clear, NPC packet identity/fallback/personalized
reward, клиентскую запись без PlayerInfo, обычную классификацию PvP kill feed,
границы optional API и отсутствие PvP/save вызовов в handler.

Dedicated fixture `NpcKillRewardSmoke` добавляет настоящий melee damage/death,
synthetic LivingDeathEvent с настоящим TaCZ projectile и подтверждённым owner,
duplicate/canceled/environment/NPC/admin/orphan/outsider/eliminated/foreign exclusions,
XP Boost bypass, отсутствие/ошибку/lock/shop класса, tier cap, balance cap, ENDING
и восстановление монет/XP вторым серверным процессом. Synthetic projectile event
проверяет атрибуцию, но не заменяет стрельбу автоматическим оружием с клиентом.
Оба smoke-класса исключены из production JAR.

Реальные результаты на рабочей ветке `TT-2.0`:

- `./gradlew.bat npcTest --offline --console=plain` — PASS до финальных architecture additions;
  окончательный прогон приведён ниже.
- `./gradlew.bat test --offline --console=plain` — FAIL на компиляции существующего
  `GameStateManagerTextTest`: отсутствует `ClanWarWinnerLabel`. Production code компилируется.
- Диагностический `test -I dev-runs/npc-integration/skip-existing-broken-test.init.gradle`
  с фильтрами `game.npc.*`, `game.*KillFeed*`, `client.KillFeedClientStateTest`,
  `progression.*`, `game.lifecycle.*`, `game.MatchAdmission*`, `game.lives.*` —
  248 tests, 247 PASS, 1 FAIL. Падение существующего
  `CompetitiveClassTierPolicyTest.casualAndClanWarModesKeepTheSavedTier`:
  expected tier 3, actual 4 при competitive+legacy ClanWar. Тест и policy не менялись.
  XML summary сохранён в `dev-runs/npc-rewards-relevant-results.json`.
- `./tools/testing/run-npc-smoke.ps1 -AcceptedEula run/eula.txt` — absent PASS,
  present сначала FAIL из-за некорректной fixture стрелы: vanilla `setOwner(null)`
  не убрал уже назначенного owner. Fixture исправлена созданием стрелы без owner.
- Повтор `./tools/testing/run-npc-smoke.ps1 -Profiles present,restart -AcceptedEula run/eula.txt`
  — present PASS, restart PASS. Маркеры `TT_NPC_REWARDS_PASS`,
  `TT_NPC_REWARDS_RESTART_PASS`, `TT_NPC_PROBE_PRESENT_PASS`, `TT_NPC_PROBE_RESTART_PASS`,
  без `TT_NPC_PROBE_FAIL`. Логи: `dev-runs/npc-smoke-20261024-082611/`;
  absent: `dev-runs/npc-smoke-20261024-082253/absent.log`.
- `./gradlew.bat clean build --offline --console=plain` — FAIL на том же существующем
  `GameStateManagerTextTest` / `ClanWarWinnerLabel`. До ошибки успешно выполнены
  compileJava, jar, reobfJar, assemble; full build не считается прошедшим.
  Лог `dev-runs/npc-rewards-clean-build.log`.
- ZIP inspection `build/libs/tacticaltablet-2.0.0.jar` — PASS: production handler есть,
  классов smoke, TacticalNPC и TaCZ implementations нет.
- `git diff --check` — PASS (только уведомления Git о будущей LF/CRLF нормализации).

Финальный focused прогон после clean build:

```powershell
.\gradlew.bat npcTest test -I dev-runs/npc-integration/skip-existing-broken-test.init.gradle `
  --offline --console=plain --tests 'com.makar.tacticaltablet.game.npc.*' `
  --tests 'com.makar.tacticaltablet.game.*KillFeed*' `
  --tests 'com.makar.tacticaltablet.client.KillFeedClientStateTest'
```

PASS: npcTest 20/20, focused test 35/35 (эти 35 включают те же 20 NPC tests),
0 failures/errors. Лог `dev-runs/npc-rewards-final-tests.log`. Из compileTestJava
исключён только существующий `GameStateManagerTextTest`; production sources,
новые tests и фильтры test task не подменялись.

Ручные сценарии: настоящее автоматическое оружие TaCZ и сетевая задержка,
визуальное отображение kill feed у двух клиентов, совместная игра с MDC,
competitive/chaos opt-in с NPC и аварийное отключение до autosave.

## Изменённые файлы

- `src/main/java/com/makar/tacticaltablet/game/npc/NpcKillRewardHandler.java` — отдельный death subscriber и пакет награды.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcDeathClaims.java` — pair key и dedup сессии.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcEncounterManager.java` — eligibility/ownership, хранение и очистка claims.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcIntegration.java` — optional DTO boundary для identity.
- `src/main/java/com/makar/tacticaltablet/game/npc/TacticalNpcV020Integration.java` — тип и серверное имя NPC.
- `src/main/java/com/makar/tacticaltablet/progression/ClassXPManager.java` — base XP без Boost.
- `src/main/java/com/makar/tacticaltablet/game/TacticalKillFeed.java` — NPC publication и personalized packet.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcDeathClaimsTest.java`.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcKillRewardArchitectureTest.java`.
- `src/test/java/com/makar/tacticaltablet/game/TacticalKillFeedTest.java`.
- `src/test/java/com/makar/tacticaltablet/client/KillFeedClientStateTest.java`.
- `src/npcSmoke/java/com/makar/tacticaltablet/game/npc/NpcEncounterSmoke.java` — connected ServerPlayer fixture и reward/restart hooks.
- `src/npcSmoke/java/com/makar/tacticaltablet/game/npc/NpcKillRewardSmoke.java` — dedicated regression probes.
- `build.gradle` — исключение дополнительного smoke helper из production JAR.
- `docs/NPC-INTEGRATION.md` — актуальные правила награды.
- `docs/NPC-KILL-REWARDS.md` — этот отчёт.
