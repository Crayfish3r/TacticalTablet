# Автоматические NPC-встречи Battle Royale

## Зависимости и установка

Minecraft **1.20.1**, Java **17**, Forge **47.4.20**. Проверенный публичный match API v1:
`dev.zuma.tacticalnpc:tacticalnpcai:0.2.0`, mod ID **tacticalnpcai**.
Исходный контракт: соседний проект TacticalNPC, `docs/MATCH-NPC-API.md`.
SHA-256 использованного production JAR:
`6faa7cb48c012b9959e9386094a560765707e89dfbfb6cc28837525c69c4c806`.

Для сборки сначала опубликовать обычный production artifact TacticalNPC:

```powershell
# В TacticalNPC/forge-addon, без combatTests/profile:
.\gradlew.bat build publishTacticalNpcPublicationToIntegrationRepository --offline --console=plain
# В TacticalTablet:
.\gradlew.bat clean build '-PtacticalNpcRepository=C:/path/TacticalNPC/forge-addon/build/repository'
```

По умолчанию локальная Maven-публикация ищется в соседнем
`../[TACZ] Military NPC/forge-addon/build/repository`. На CI передавать путь или URL
через `tacticalNpcRepository`. Удалённый публичный Maven endpoint пока не опубликован.
Compile-only зависимость нужна для компиляции адаптера; классы/JAR TacticalNPC и TaCZ
не вкладываются в TacticalTablet. После выбора другого artifact с тем же номером
версии проверять его происхождение и совместимость заново.

Установить TacticalTablet обычным способом. Для NPC также установить **основной**
TacticalNPC 0.2.0 и **TaCZ 1.1.8-hotfix2** на сервер и клиенты. Legacy NPCs compatibility
addon не требуется. TaCZ этой версии обязателен самому TacticalNPC. Старый smoke-профиль
MDC с TaCZ 1.1.7 не подходит для одновременного запуска TacticalNPC; `-PnpcSmoke`
выбирает 1.1.8-hotfix2, включая совместный запуск с `-PmdcSmokeVersion`. Совместный
NPC+MDC smoke в данной задаче не запускался; проверен профиль NPC без MDC.

NPC выключены по умолчанию. Без TacticalNPC TT продолжает работать. Optional metadata
имеет широкий диапазон версий, а адаптер включается только для точной версии 0.2.0;
неподдерживаемая версия отключает встречи с диагностикой. Собственные обязательные
зависимости установленного TacticalNPC всё равно проверяет Forge.

## Архитектура

Пакет `com.makar.tacticaltablet.game.npc`:

- `NpcEncounterManager`: один серверный менеджер; подготовка только после результата
  `STARTED` существующего `MatchStartCoordinator`, UUID из `getLifecycleSnapshot()`.
- `NpcEncounterSchedule`: чистая логика игровых тиков, отмены и резервирования лимита.
- `NpcEncounterConfig`: world-local JSON, строгая валидация, атомарное создание defaults.
- `NpcLoadoutSelector`: случайная численность и взвешенный выбор подходящего preset.
- `NpcPlacementPlanner`: ограниченный поиск в уже загруженных чанках и preflight площадки.
- `NpcIntegration`: граница без сигнатур optional-классов;
  `TacticalNpcV020Integration` — единственное место типизированных ссылок TacticalNPC.
- `NpcTargetEligibility`: чистое правило допуска игрока, используемое callback политики.

Новых потоков нет. `ServerEvents` вызывает менеджер ровно один раз в END server tick,
после `GameStateManager`, поэтому определение победителя предшествует попытке спавна.
До наступления deadline нет поиска позиций и обхода NPC. Deadline переносится **до**
попытки; отсутствие игроков/места/лимита и отказ API не создают цикл повторов каждый тик.

Для спавна одновременно нужны enabled, совместимый API, тот же UUID, lifecycle RUNNING,
legacy RUNNING и `MatchPhase.RUNNING`, разрешённый режим, доступный игровой ServerLevel,
хотя бы один допустимый живой участник в нём и разрешённая фаза ZoneManager.
В голосовании, STARTING, лобби, ENDING и cleanup спавна нет.

Политика целей каждый раз проверяет актуальные snapshot/participantIds, привязку LivesManager
к текущему UUID, admission/lives/elimination/moderator rules, состояние сущности и подключения,
игровое измерение, `war.playing`, отсутствие Creative/Spectator. Тег сам по себе недостаточен.
Политика закрывается при смене UUID/состояния и при явной деактивации. TacticalNPC применяет
её при выборе/удержании цели и непосредственно перед уроном, включая пули в полёте.

Игровые списки участников, lives, команды игроков, голоса, награды и поиск победителя
не получают UUID NPC. Существующие обработчики смерти игроков продолжают работать;
NPC не начисляется XP и за их убийство не вводятся новые награды.

## Конфигурация

Файл `<world>/tacticaltablet_npcs.json` создаётся при первом успешном старте матча.
Настройки читаются при каждом следующем успешном старте, без hot reload в середине матча.
Ошибочные глобальные параметры отключают подсистему; ошибочные presets исключаются
с диагностикой. Исходный пользовательский файл не исправляется и не перезаписывается.
Ошибка чтения/commit defaults сообщается в серверный лог, NPC остаются выключенными.

Пример (сокращённый список presets, остальные параметры полные):

```json
{
  "enabled": true,
  "maxActiveNpcs": 12,
  "minGroupSize": 1,
  "maxGroupSize": 4,
  "minSpawnIntervalSeconds": 45,
  "maxSpawnIntervalSeconds": 90,
  "minDistanceFromPlayers": 40,
  "maxDistanceFromPlayers": 100,
  "minDistanceBetweenGroups": 32,
  "maxSpawnAttempts": 8,
  "stopSpawningAtZonePhase": 5,
  "dimension": "minecraft:overworld",
  "allowClanWar": false,
  "allowSpecialModes": false,
  "presets": [
    {"id":"lone_survivor","weight":3,"minSize":1,"maxSize":1,
     "weapons":["tacz:ak47"],"behavior":"ATTACK","ammoPolicy":"FINITE","reserve":120,"radius":24},
    {"id":"patrol","weight":2,"minSize":2,"maxSize":2,
     "weapons":["tacz:ak47","tacz:ak47"],"behavior":"PATROL","ammoPolicy":"FINITE","reserve":120,"radius":24},
    {"id":"defense","weight":1,"minSize":2,"maxSize":4,
     "weapons":["tacz:ak47","tacz:ak47","tacz:ak47","tacz:ak47"],
     "behavior":"DEFEND","ammoPolicy":"FINITE","reserve":120,"radius":24}
  ]
}
```

Автоматические defaults также включают `assault` из трёх NPC с индивидуальными слотами.
Оружие каждого слота задаётся независимо; для разных пушек заменить ID реальными ID
**установленного TaCZ gun pack**. Единственный поставляемый fallback — `tacz:ak47`.
Синтаксис ID проверяет TT, реальную поддержку оружия — API TacticalNPC. При `INVALID_WEAPON`
весь preset исключается до следующего матча, предупреждение выводится один раз.
Группа с невалидным оружием не создаётся частично.

Пределы валидации:

| Параметр | Допустимо |
|---|---|
| maxActiveNpcs | 1–256 |
| minGroupSize / maxGroupSize | 1–16, min ≤ max, min ≤ maxActiveNpcs |
| интервалы | 1–86400 секунд, min ≤ max; диапазон включительный |
| дистанции игроков | 1–4096, min ≤ max, только конечные числа |
| minDistanceBetweenGroups | 0–4096 |
| maxSpawnAttempts | 1–64 |
| stopSpawningAtZonePhase | 1–100; новые встречи только строго до этой фазы |
| dimension | ResourceLocation, кроме lobby:lobby; измерение должно существовать |
| presets | до 64; уникальный id `[a-z0-9_.-]{1,64}` |
| weight | 0–1000000; 0 исключает выбор |
| minSize / maxSize | 1–16; weapons содержит не менее maxSize, максимум 16 слотов |
| behavior | ATTACK → ASSAULT, DEFEND → DEFEND_AREA, STAND → HOLD_POSITION, PATROL → PATROL |
| ammoPolicy / reserve | FINITE или INFINITE; reserve 0–1000000, магазин изначально пуст |
| radius | 4–64, для DEFEND 4–128 |

Общие настройки численности пересекаются с диапазоном preset и оставшейся ёмкостью.
Сначала равновероятно выбирается подходящая численность, затем взвешенно preset для неё.
Если целая минимальная группа не помещается в лимит, событие пропускается.
PATROL API v1 принимает anchor/radius; маршрут из waypoint API не предоставляет.

CASUAL SOLO/DUO/TRIO/SQUADS разрешены. Competitive, Chaos и неизвестные специальные
режимы требуют `allowSpecialModes=true`. `allowClanWar` сохранён как отдельный opt-in;
в текущей рабочей копии TT активный ClanWar уже удалён, compatibility-query возвращает false.

## Позиции и лимит

Планировщик выбирает живого участника и случайную точку вокруг него. Проверяет WorldBorder
выбранного измерения и активной карты TT в overworld,
loaded chunks, поверхность, жидкости/опасные блоки, достаточный сплошной грунт из активного
RTP, коллизии и занятость. Он не вызывает загрузку/генерацию чанков и не ставит tickets.

API v1 принимает **origin**, а не произвольный список конечных позиций. Сервис выбирает
места на сетке ±12 блоков для группы (±6 для одного). TT резервирует консервативный
радиус 19 блоков для всей сетки, округления и тела. Расстояния до игроков/других групп
и границы проверяются с этим запасом; проверка расстояний горизонтальная.
Слишком узкий интервал расстояний или маленькая зона могут закономерно не дать площадки.
Для FIXED_Y_BOX площадка также должна помещаться в активный RTP box, и вся сетка должна
быть ровной/безопасной на spawnY, чтобы вертикальная коррекция API не нарушила фиксированный Y.
Это строже обычного RTP и может снижать частоту встреч. Последняя проверка каждой сущности
и атомарность всей группы остаются в `NpcSpawnService`.

Учёт использует API alive+loaded IDs и собственные UUID успешных спавнов. Выгрузка чанка
не освобождает лимит: иначе NPC после возвращения чанка могли бы превысить maxActiveNpcs.
Подтверждённое уничтожение через EntityLeaveLevelEvent освобождает слот; просто
неизвестное/выгруженное состояние сохраняет резерв. Для движущихся групп проверяются
свежие позиции, для выгруженных — последние известные. Это консервативный лимит.

## Остановка, cleanup и рестарт

Перед переходом к ENDING/force stop менеджер отменяет таймер и закрывает callback,
затем вызывает `deactivate(matchId)`. Это блокирует урон до post-game задержки.
Ошибка optional API не открывает callback обратно.

`cleanup.npcs` проходит через `runMatchStage` **до** `cleanup.scoreboard-teams`.
`remove(matchId)` затрагивает только связанный UUID; сторонние/manual NPC сохраняются.
При ошибке ID и адаптер сохраняются, стадия остаётся pending для штатного повторного cleanup.
Повтор успешной очистки безопасен. resetRuntime также очищает NPC до потери lifecycle UUID;
при ошибке lifecycle не сбрасывается в IDLE. ServerStopping сразу закрывает политику.

Менеджер/таймеры не сериализуются и не возобновляются. После рестарта TacticalNPC закрывает
сохранённые match IDs и удаляет старых NPC при admission чанка, включая ранее выгруженные.
Его SavedData также очищает собственные teams. TT не сканирует entity-region файлы и
не загружает чанки ради удаления, не вмешивается в восстановление остальных систем TT.

## Проверки и ручной плейтест

```powershell
.\gradlew.bat npcTest --offline --console=plain
.\gradlew.bat clean build --offline --console=plain
git diff --check
# Изолированные dedicated smoke; копируют только уже принятую EULA, не рабочий мир:
.\tools\testing\run-npc-smoke.ps1 -AcceptedEula run/eula.txt
```

`npcTest` выполняет те же NPC regression sources отдельно от остальной test suite.
Это позволяет диагностировать подсистему при ошибках компиляции несвязанных тестов.
Dedicated fixture включается только `-PnpcProbe=absent|present|restart`, добавление реальных модов —
`-PnpcSmoke`. Smoke-классы исключены из production JAR даже при ошибочном сочетании flags.
Профиль берёт TaCZ JAR из соседнего `forge-addon/libs`; другой путь задаётся
`-PtacticalNpcSmokeLibraries=...`. Smoke вручную подготавливает ограниченную площадку;
эти загрузки чанков/изменения блоков не входят в production planner.
Выходные миры/логи лежат в игнорируемом `dev-runs/`. Успех определяется явным
`TT_NPC_PROBE_*_PASS` и отсутствием `TT_NPC_PROBE_FAIL`, а не только exit code Gradle.

Автоматические результаты текущей реализации записаны в `NPC-VALIDATION.md`.

Ручной плейтест с настоящими клиентами:

1. Включить NPC, войти в CASUAL матч, выбрать класс/RTP; проверить первую встречу после
   заданного числа игровых тиков и разные weapon slots из установленного gun pack.
2. Проверить горы, здания, воду, огонь, границу сужающейся зоны и FIXED_Y_BOX карту;
   при недостатке места не должно быть частичных групп и принудительной загрузки чанков.
3. Проверить одиночек и команды, Creative, модератора, позднего Spectator, игрока лобби,
   выбывшего игрока, reconnect с UUID старого матча и NPC с уже захваченной целью.
4. Завершить матч с пулями в полёте; урон должен закрыться сразу, NPC исчезают при cleanup.
   Повторить force stop и переход к следующему матчу.
5. Выгрузить чанк с NPC, заполнить лимит и вернуть чанк; лимит не должен превышаться.
   Перезапустить сервер, затем естественно загрузить старый чанк: старые NPC не продолжают бой.
6. Проверить обычные PvP смерти/жизни, победу игрока/команды, отсутствие наград за NPC.
7. Competitive/Chaos должны оставаться без NPC при default allowSpecialModes=false.

Клиентский бой TaCZ, пули в полёте в реальной сети, поведение PATROL на сложных картах,
производительность на конкретном gun pack и рестарт с физически выгруженными NPC
нельзя заменить одними unit tests.
