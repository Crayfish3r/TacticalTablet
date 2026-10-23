# NPC integration: проверка 2026-10-23

Ветка `TT-2.0`. До начала задачи working tree уже содержал изменения удаления ClanWar,
профиля планшета и других систем. Они сохранены. Изменения этой задачи не коммитились.

## Покрытие требований

| Сценарий | Автоматическая проверка |
|---|---|
| STARTED → RUNNING, отсутствие спавна до боевого состояния | Реальный TT start в dedicated smoke; unit с настоящим MatchLifecycleService для PREPARING/STARTING/ENDING/CLEANING/IDLE/нового UUID |
| enabled=false | Dedicated smoke: второй реальный RUNNING матч с интервалом 1 секунда и выключенным enabled |
| отсутствие TacticalNPC | Dedicated smoke без TacticalNPC и TaCZ: запуск/force stop/выключение сервера, без class loading errors |
| лобби | После старта игрок остаётся в лобби более трёх deadline; NPC отсутствуют до перехода в арену |
| границы и отсутствие места | Native spawned bodies внутри WorldBorder; planner отказывает для площадки вне карты; unit для всех четырёх границ и запаса группы |
| группа / оружие | Реальная атомарная группа2; unit сохраняет индивидуальные weapon slots; INVALID_WEAPON атомарно возвращает пустой список |
| maxActiveNpcs | Группа2 при лимите3: следующая группа2 не создаётся; randomized unit для ёмкости0–12; резерв unloaded UUID |
| цель вне матча | Реальный Forge LivingAttackEvent от match NPC по присутствующему outsider отменён native guard |
| исключение / Spectator | То же событие разрешено для действующего участника и немедленно запрещено после изменения elimination/Spectator состояния |
| ENDING | Реальный GameStateManager.endGame: NPC ещё существуют до post-game cleanup, а native damage guard уже запрещает урон |
| cleanup только своего UUID | Внешний match-owned NPC другого UUID сохраняется при cleanup TT |
| повтор cleanup | Два повторных вызова безопасны и не затрагивают внешний UUID |
| рестарт | Два обычных dedicated процесса на одном сохранённом мире: старый NPC не допущен в загружаемый чанк, team удалена, старый ID закрыт, TT не возобновляет матч |
| ClanWar / специальные режимы | Unit: default запрет ClanWar/Chaos/Competitive/unknown, отдельный opt-in; обычные SOLO/DUO/TRIO/SQUADS разрешены |
| победитель / lives / смерть NPC | При наличии группы2 player count остаётся1; смерть NPC не завершает RUNNING и не меняет число живых игроков |
| невалидная конфигурация | Unit: лимиты, NaN/дистанции, интервалы, измерение, отрицательные веса, дубли/неизвестные orders, радиусы, пустые profiles, сломанный JSON, ошибка commit, сохранение файла |

Smoke использует настоящий GameStateManager, публичный match API и Forge event bus.
Тестовый FakePlayer регистрируется через публичный PlayerList.placeNewPlayer с EmbeddedChannel;
доступ к приватным полям и reflection optional API не используются. Synthetic LivingAttackEvent
проверяет действительный native guard, но не является сетевым тестом полёта TaCZ пули.

Отдельный recovery NPC намеренно принадлежит внешнему UUID и сохраняется между процессами;
он нужен только для проверки дискового восстановления TacticalNPC. Production TT не создаёт
таких параллельных матчей. Fixtures не входят в production JAR.

## Команды и результаты

Предварительная `.\gradlew.bat test --offline --console=plain` **FAILED до изменений**:
`GameStateManagerTextTest.java:10` ссылается на отсутствующий `ClanWarWinnerLabel`,
который уже удалён в пользовательском working tree.

`.\gradlew.bat compileJava --offline --console=plain` — **PASS**, Forge 47.4.20,
реальная compile-only публикация TacticalNPC 0.2.0. Только существующие/Forge deprecation warnings.

`.\gradlew.bat npcTest --offline --console=plain` — **PASS**, финальные **16 тестов**.

`.\tools\testing\run-npc-smoke.ps1` — **PASS**, все три профиля `absent`, `present`,
`restart`, exit0, явные PASS markers и отсутствие FAIL marker. Итоговые логи:

- `dev-runs/npc-smoke-20261023-172425/absent.log`.
- `dev-runs/npc-smoke-20261023-172425/present.log`.
- `dev-runs/npc-smoke-20261023-172425/restart.log`.
- Общий результат: `dev-runs/npc-integration/final-smoke.log`.

`.\gradlew.bat clean build --offline --console=plain` — **FAILED**, exit1,
на том же `compileTestJava` / `ClanWarWinnerLabel`, что и до изменений. Production
`compileJava`, сборка JAR и reobfuscation выполнены; зелёной полной сборки working tree нет.
Лог: `dev-runs/npc-integration/clean-build.log`.

Дополнительный диагностический прогон — **PASS, 103 tests, 19 suites, 0 failures/errors/skips**:

```powershell
.\gradlew.bat test -I dev-runs/npc-integration/skip-existing-broken-test.init.gradle `
  --tests 'com.makar.tacticaltablet.game.npc.*' `
  --tests 'com.makar.tacticaltablet.game.lifecycle.*' `
  --tests 'com.makar.tacticaltablet.game.lives.*' `
  --tests 'com.makar.tacticaltablet.game.zone.*' `
  --tests 'com.makar.tacticaltablet.game.MatchAdmission*' `
  --tests 'com.makar.tacticaltablet.game.ActivePvpParticipant*' `
  verifyProcessedModsToml verifyJarModsToml verifyEmbeddedClientResourceSources `
  verifyJarEmbeddedClientResources verifyJarEmbeddedServerData `
  verifyModernDamageRussianLocalization verifyProductionJarReobfuscated `
  verifyTabletPrototypeSource --offline --console=plain
```

Временный init script в игнорируемом `dev-runs/` исключает из компиляции **только**
`GameStateManagerTextTest.java`; исходники этого теста не менялись. Этот результат
не заменяет обычную полную suite. Лог: `dev-runs/npc-integration/related-regression.log`.
Все перечисленные проверки metadata/ресурсов/production reobfuscation — **PASS**.

`git diff --check` — **PASS**, exit0; только предупреждения Git о преобразовании LF/CRLF.
Дополнительная проверка ZIP entries — **PASS**: в JAR нет реализаций TacticalNPC/TaCZ
и `NpcEncounterSmoke`/его inner classes.

Собранный artifact: `build/libs/tacticaltablet-2.0.0.jar` (учитывает уже существовавшие
изменения working tree; это не утверждение успешной полной `clean build`).

## Изменённые файлы этой задачи

- `build.gradle`, `gradle.properties`, `src/main/resources/META-INF/mods.toml`.
- `src/main/java/com/makar/tacticaltablet/game/GameStateManager.java` — только NPC hooks
  в STARTED, ENDING, cleanup/reset и сохранение retry при ошибке NPC cleanup.
- `src/main/java/com/makar/tacticaltablet/game/ServerEvents.java` — единственный NPC tick,
  startup/shutdown и событие уничтожения NPC.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcEncounterManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcEncounterConfig.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcPlacementPlanner.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcLoadoutSelector.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcIntegration.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/TacticalNpcV020Integration.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcEncounterSchedule.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcTargetEligibility.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcModePolicy.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcPlacementBounds.java`.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcEncounterConfigTest.java`.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcEncounterScheduleTest.java`.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcLoadoutSelectorTest.java`.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcTargetEligibilityTest.java`.
- `src/test/java/com/makar/tacticaltablet/game/npc/NpcModeAndBoundsTest.java`.
- `src/npcSmoke/java/com/makar/tacticaltablet/game/npc/NpcEncounterSmoke.java`.
- `tools/testing/run-npc-smoke.ps1`.
- `docs/NPC-INTEGRATION.md`, `docs/NPC-VALIDATION.md`.

## Что требует ручного плейтеста

Реальные TaCZ выстрелы/пули в полёте через сеть, поведение AI/PATROL и разные gun packs,
сложные поверхности и FIXED_Y_BOX карты, задержки и reconnect клиентов, производительность
при длительном матче, выгрузка/возвращение NPC с консервативным лимитом. Совместный запуск
NPC с MDC-профилем и настоящий клиентский запуск в этом прогоне не выполнялись. Default ClanWar
не затронут; активного ClanWar в текущей рабочей копии уже нет, играть его в ней нельзя.
Список действий приведён в `NPC-INTEGRATION.md`.
