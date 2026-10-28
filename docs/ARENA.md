# Арена

Режим сета `SetGameMode.ARENA` добавлен в конец enum (ordinal 4). Существующие
ordinal и packet ID сохранены. Канал имеет версию 45: изменение числа set-mode
счётчиков требует обновлённых клиента и сервера. Новый S2C ArenaStatePacket имеет ID 44.

## Правила

- Всегда доступен в обычном голосовании режимов; отсутствуют cooldown, streak и
  минимум участников для появления. Административная фиксация режима сохраняется.
- Только SOLO; обычные голосование за формат и TEAM_SELECT пропускаются.
- Одна игра — 900 серверных секунд после commit RUNNING; один сет — три игры.
  Общая `GAMES_PER_MAP = 5` не менялась.
- Отдельная ArenaZonePolicy с matchId задаёт квадрат 360×360 вокруг центра карты
  без случайного смещения, фаз, сужения и отключения возрождений. Активные настройки
  безопасного RTP сохранены; проверка границы в Арене обязательна.
- Явное LifeRule.UNLIMITED. Ноль в конечном scoreboard-счётчике означает отсутствие
  конечного количества, а не количество жизней; вся игровая доступность использует
  правило жизни. HUD и планшет показывают бесконечность.
- Одна жизнь — один набор. ArenaMatchRuntime.Life хранится по UUID в сессии, переживает
  закрытие планшета, RTP и переподключение. Сбрасывается только при реальной смерти
  или смене матча. Дедуп смерти использует экземпляр ServerPlayer, а не переиспользуемый
  при respawn entity ID. Старые runtime-объекты не передаются фоновой записи профиля.
- Смерть использует обычные corpse/death-screen/индивидуальный возврат в лобби.
  Выбывания из-за количества жизней нет. Независимые spectator/moderator ограничения
  сохранены. При переподключении сохраняются комплект, инвентарь и очки текущей жизни.
- ClassCooldownManager не применяет и не начисляет cooldown Арены. Карта cooldown
  других режимов сохраняется; покупки, class unlock и эксклюзивные классы проверяются
  прежними серверными обработчиками.

## Настройки

`<world>/tacticaltablet_arena.json` создаётся атомарно при preflight. Значения
фиксируются на всю игру:

```json
{"playerKill": 2, "assist": 1, "damagePerPoint": 20.0, "npcKill": 1}
```

ArenaScoringRules считает kills × playerKill + assists × assist +
floor(effective PvP damage / damagePerPoint) + NPC kills × npcKill.
Эффективный урон и ассисты берутся из существующего авторитетного PvP pipeline;
NPC-смерти — после полной проверки и claim в NpcKillRewardHandler. Самоубийства,
административные удаления, чужие NPC, cleanup и повторная смерть очков не дают.
NPC kills не увеличивают PvP kills/KD. Очков за выживание нет. Монеты и XP сохраняют
свои существующие правила независимо от матчевого счёта. Сумма защищена от overflow.

`<world>/tacticaltablet_arena_npcs.json` — отдельный NpcEncounterConfig. Defaults:
enabled=true, maxActiveNpcs=24, группы 2–4, интервалы 20–35 секунд,
`arena_assault` ATTACK/DEFAULT (вес 8), `arena_hard_assault` ATTACK/HARD (вес 2),
`arena_patrol` PATROL/DEFAULT (вес 2), FINITE/120 с `tacz:ak47`;
`arena_rpg_pair` — ровно два `tacz:rpg7`, DEFAULT, FINITE/3, вес 1.
У всех radius=24 — радиус приказа. Ограничения валидатора, loaded chunks,
дистанции и безопасный грунт сохраняются. NPC спавнятся всю игру независимо
от номера фазы зоны. Сложность определяет только точность средствами TacticalNPC.

TacticalNPC 0.2.0 с его совместимым TaCZ обязателен для запуска Арены. Отсутствие,
несовместимость, отключённый/невалидный профиль сообщаются в preflight без зачёта игры.
Регистрация match policy — rollback-capable стадия STARTING. Отклонённый оружейный
пресет или отказ API во время игры безопасно останавливает игру без завершения;
молчаливого перехода в Casual нет. Реальная проверка оружия выполняется поддерживаемым
spawn API при первой встрече, а не выдуманным preflight API.

## Завершение и сохранение

ArenaMatchRuntime вызывается существующим GameStateManager, без второго lifecycle
или отдельного tick subscriber. События закрываются по серверному deadline и matchId
даже до следующего секундного обновления. При 00:00 фиксируется immutable таблица,
NPC policy деактивируется, lifecycle входит в ENDING. Максимальный счёт определяет
победителя; все лидеры с равным максимальным счётом объявляются ничьей, без продления
и выдачи индивидуальной победы за ничью. Offline-участник сохраняет право на результат.

MapSetManager.commitArenaGame одним atomic replace сохраняет receipt matchId,
GamePerformance с явным arenaScore/arenaWinner/npcKills, номер завершённой игры,
участников и итоговую SetLeaderboardSnapshot/награды после третьей игры. При ошибке
commit бой остаётся закрытым, операция повторяется. Финальная публикация и награды
идут после commit. Счёт не пересчитывается формулой Battle Royale в SetPlayerResult
или SetResultService; для сета суммируются три уже зафиксированных результата.

MapSetProgressionPolicy v6 использует прежний backup перед миграцией v5 и сохраняет
старые результаты пяти игр. Старые GamePerformance/SetPlayerResult поля читаются с
нулевыми Arena-полями; прежние конструкторы оставлены. Результаты Арены архивируются
в `<server-root>/tacticaltablet_data/arena_results/<setId>.json` атомарно; существующий
отличающийся архив не перезаписывается. Результаты не исчезают при смене карты.
Карьера completed matches/wins использует существующие idempotent profile receipts
и immutable async save tickets, включая offline-участников; повтор из записанных
игр текущего сета безопасен. Неполная игра не продвигает счётчик и не создаёт receipt
завершения. Незавершённая сессия после перезапуска не возобновляется.

После третьей игры используются существующие rewarding и map-vote стадии.
SetRewardService сохраняет свой идемпотентный путь выплат и не заменяет валюту счётом.
При административной остановке runtime/NPC закрываются через обычный cleanup без
добавления завершённой игры. Номер игры и длина сета проведены через объявления,
нормализацию, результаты, награды, HUD и существующую онлайн-сводку.

## Интерфейс и ограничения

HUD показывает серверный таймер «Арена — MM:SS • N/3», жизни ∞, личный счёт и топ-5.
В бою S2C передаёт топ-5 плюс собственную строку; полный список передаётся для итогов.
На первой странице планшета итоговая таблица прокручивается, включает score, PvP,
NPC, assists и deaths; после третьей игры это сумма сета. Используется существующий
TabletPageShell/TabletDisplayTarget и TabletTerminalTheme, физическая навигация сохранена.

Сетевой лимит — 1024 участника в одной игре и 4096 строк итогов (до 3072 разных
участников трёх игр). Избыточный старт/допуск отклоняется явно. Изменений контрактов,
clan war, production-зависимостей, тестов и инструкций ручной проверки нет.

Подробные Arena-отчёты остаются локальными. Добавление внешней отправки имён,
очков, PvP/NPC kills, assists и deaths в Discord было отклонено автоматической
проверкой из-за отсутствия явного разрешения на payload и destination. Этот путь
отключён, остальные ранее существовавшие онлайн-сводки/режимы сохраняются.

Выполнены `./gradlew.bat clean compileJava processResources --offline --console=plain`
и финальный `./gradlew.bat compileJava processResources --offline --console=plain`:
BUILD SUCCESSFUL, ошибок компиляции нет, присутствуют 30 deprecation warnings.
Финальный лог — `dev-runs/arena-final-compile.log`. `git diff --check` — PASS.
Тесты не добавлялись и не запускались. Игровой запуск, визуальная проверка,
fault injection и multiplayer/dedicated проверки этой задачи не выполнялись.

## Изменённые файлы

- `docs/ARENA.md`.
- `src/main/java/com/makar/tacticaltablet/client/ArenaHudOverlay.java`.
- `src/main/java/com/makar/tacticaltablet/client/LivesHudOverlay.java`.
- `src/main/java/com/makar/tacticaltablet/game/ActivePvpParticipant.java`.
- `src/main/java/com/makar/tacticaltablet/game/arena/ArenaMatchRuntime.java`.
- `src/main/java/com/makar/tacticaltablet/game/arena/ArenaScoringRules.java`.
- `src/main/java/com/makar/tacticaltablet/game/arena/ArenaSnapshot.java`.
- `src/main/java/com/makar/tacticaltablet/game/arena/ArenaZonePolicy.java`.
- `src/main/java/com/makar/tacticaltablet/game/GameStateManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/lives/LivesManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/lobby/LobbyManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/MapSetManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/MapSetProgressionPolicy.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcEncounterConfig.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcEncounterManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcKillRewardHandler.java`.
- `src/main/java/com/makar/tacticaltablet/game/npc/NpcModePolicy.java`.
- `src/main/java/com/makar/tacticaltablet/game/respawn/RespawnControlManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/respawn/RtpTimerManager.java`.
- `src/main/java/com/makar/tacticaltablet/game/ServerEvents.java`.
- `src/main/java/com/makar/tacticaltablet/game/set/GamePerformance.java`.
- `src/main/java/com/makar/tacticaltablet/game/set/SetPlayerResult.java`.
- `src/main/java/com/makar/tacticaltablet/game/set/SetResultService.java`.
- `src/main/java/com/makar/tacticaltablet/game/set/SetScoringRules.java`.
- `src/main/java/com/makar/tacticaltablet/game/SetGameMode.java`.
- `src/main/java/com/makar/tacticaltablet/game/SetModeRotationPolicy.java`.
- `src/main/java/com/makar/tacticaltablet/game/SetModeVotePolicy.java`.
- `src/main/java/com/makar/tacticaltablet/game/zone/ZoneManager.java`.
- `src/main/java/com/makar/tacticaltablet/integration/discord/DiscordLeaderboardService.java`.
- `src/main/java/com/makar/tacticaltablet/integration/moderndamage/client/ModernDamageStaminaOverlay.java`.
- `src/main/java/com/makar/tacticaltablet/integration/online/OnlineWebhookService.java`.
- `src/main/java/com/makar/tacticaltablet/progression/ClassCooldownManager.java`.
- `src/main/java/com/makar/tacticaltablet/progression/PlayerProgressManager.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/ArenaClientState.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/ClientEvents.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/prototype/TabletArenaResultsView.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/prototype/TabletPageShell.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/prototype/TabletPreparationPage.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/prototype/TabletVotingPage.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/client/prototype/TabletVotingView.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/net/ArenaStatePacket.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/net/PacketHandler.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/net/PacketProtocol.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/net/TabletPacket.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/PlayerTabletState.java`.
- `src/main/java/com/makar/tacticaltablet/tablet/preparation/TabletPreparationService.java`.
- `src/main/resources/assets/tacticaltablet/lang/en_us.json`.
- `src/main/resources/assets/tacticaltablet/lang/ru_ru.json`.

## Применение NPC-профиля

Готовый профиль: `ready_map_configs/tacticaltablet_arena_npcs.json`. Для существующего
мира сохраните резервную копию его `tacticaltablet_arena_npcs.json`, затем примените
подготовленный файл по этому world-local пути. Loader не перезаписывает существующие
файлы и не добавляет им новое вооружение. Старые пресеты без `difficulty` остаются HARD.
Обычные встречи используют отдельный `<world>/tacticaltablet_npcs.json`.

Нужен свежий обычный TacticalNPC 0.2.0 с `NpcDifficulty.DEFAULT/HARD` и шестым
аргументом `SpawnMemberSpec`. Старый JAR с тем же номером отклоняется до старта
Арены. Обновлять runtime JAR следует согласованно на сервере и клиентах;
одного обновления compile classpath недостаточно.
