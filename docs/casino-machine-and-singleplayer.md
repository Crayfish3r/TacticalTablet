# Казино-блок и одиночная игра

Дата: 9 сентября 2026. Работа продолжена с текущего дерева Astra; откатов и замены архитектуры не было.

## 1. Результат сборки

- Финальная команда: `.\gradlew.bat clean build` (из корня проекта).
- **BUILD SUCCESSFUL in 45s**, exit code 0; 22 задачи выполнены.
- **764 теста**, 0 failures, 0 errors, 0 skipped.
- `git diff --check`: успешно.
- Production JAR: [build/libs/tacticaltablet-1.0.0.jar](<C:/MCDev/Tactical Tablet/build/libs/tacticaltablet-1.0.0.jar>) (7 717 711 байт).
- Проверки ресурсов, метаданных и reobfuscation прошли. Предупреждения deprecated API не рефакторились.

## 2. Одиночная игра

В меню «Моды → Tactical Tablet → Настройки», где находится адрес сервера, добавлена кнопка ванильной одиночной игры. Она открывает SelectWorldScreen: существующие миры, создание новых, ванильный выбор режима и настроек. Кнопка доступна вне загруженного мира.

На интегрированном сервере не запускаются автоматический lobby bootstrap, матчевые gamerules, матчевый tick, принудительный перенос в лобби и матчевое возрождение. Отключены ограничения строительства/урона в lobby, подбора/выбрасывания предметов и контейнеров, автоматическая выдача матчевого компаса, серверная корректировка Py132. Сохранение casino-профилей остаётся активным. Одиночная пауза остаётся ванильной; F3/F5 не блокируются. Временное снятие серверных привязок клавиш восстанавливается после выхода с сервера.

ServerRules.enabled проверяет MinecraftServer.isDedicatedServer. Интегрированный сервер, включая хозяина мира при открытии LAN, остаётся локальным режимом. У подключившихся по LAN удалённых клиентов отдельного признака локального режима по сети не добавлялось: их клиентские ограничения требуют отдельной проверки.

Для проверки в творческом мире с командами:

```mcfunction
/give @s tacticaltablet:casino_machine
/ttcoins give ИМЯ_ИГРОКА 1000
```

Coins выдаются существующей административной командой, без изменения экономики. Автомат можно открывать и проверять в локальном мире в любом измерении; проверки позиции, расстояния, Menu и busy сохраняются. Закрытие локального казино не запускает возврат в матч.

## 3. Казино

`Player → CasinoMachineBlock → CasinoMenu → CasinoSessionManager → проверка machine/position/dimension/distance/session/stake/cooldown → резерв BE → CasinoSpinTable.roll → PlayerProgressManager.applyCasinoSpin → подтверждённый результат → CasinoReelResult → BE animation + CasinoSpinResultPacket/CasinoAnimationPacket → GUI`.

Сохранено имя Menu из реализации Astra: CasinoMenu. GUI работает через AbstractContainerScreen; odds переключается внутри того же экрана. Menu.removed завершает серверную сессию; старый close packet остаётся совместимым и не отбрасывается общим rate limiter. Spectator entry использует тот же Menu без machinePos.

Один автомат выполняет один spin; независимые BlockEntity работают параллельно. После подтверждённого spin закрытие GUI или disconnect не отменяет визуальную анимацию и выплату. BE сохраняет только spinning, время/длительность, seed и предыдущие/целевые символы. После истечения времени при загрузке chunk busy снимается; future tick из шаблона другого мира не блокирует автомат навсегда.

GUI ожидает серверную временную шкалу, включая receipt replay с нулевой длительностью. RNG/платёж/награда остаются на сервере. Протокол **42**, добавлен только S2C **packet ID 42 CasinoAnimationPacket**. ID 37–41 сохранены.

CasinoSpinTable, CasinoReward, CasinoRewardKind, PlayerProgressManager.applyCasinoSpin, профили/receipts, CasinoReturnPolicy и игровая экономика не переписаны. Git подтвердил отсутствие diff для CasinoSpinTable, PlayerProgressManager и CasinoReturnPolicy.

OBJ renderer использует подготовленные группы трёх барабанов и рычага, общий 80-tick timeline и прежние pivot points. Предмет в инвентаре использует тот же renderer. Проверено совпадение SHA-256 исходных OBJ и seamless 2048 PNG с включёнными ресурсами: геометрия, UV и текстура не изменялись.

## 4. Lobby и legacy NPC

Новый шаблон ожидается в `src/main/resources/data/lobby/structures/lobby.nbt` (ID lobby:lobby), с резервным старым lobby:spawn. Пользователь добавит новый файл самостоятельно. Координаты автоматов не задаются кодом.

Существующее содержимое лобби автоматически не перезаписывается. Для уже построенного/отмеченного bootstrap мира подмена ресурса сама по себе не заменяет постройку.

Удалены действующее восстановление casino NPC, spawn, NoAI, invulnerability и UUID sanitation казино. CasinoNpcTemplateMigration оставлен как узкий фильтр копии шаблона: локальная legacy-константа распознаёт старых casino-жителей только в NBT шаблона. Остальные блоки, BlockEntity и декорации сохраняются. Исходные байты старого spawn.nbt не изменены. Уже существующие жители в пользовательских мирах массово не удаляются и больше не восстанавливаются.

CasinoOddsScreen удалён; представление odds перенесено в CasinoOddsPresentation. Старый файл и удалённый код можно восстановить из Git.

## 5. Проверки и оставшиеся риски

Добавлены CasinoMachineTest (13 тестов), CasinoMachineArchitectureTest (3), SingleplayerArchitectureTest (4); обновлены legacy/lobby, packet registry, версия протокола, временные ограничения клавиш и ресурсный manifest. Существующие тесты таблицы выплат, admission, return policy и receipts прошли вместе с полной сборкой.

Проверены unit-тестами NBT round-trip, некорректные visual data, chunk-time expiry, future clock, busy двух машин, отказ резерва/платежа, все конечные позиции барабанов, рычаг, deceleration, mapping reward→reels, packet round-trip, сравнение UUID/позиции/измерения и граница расстояния. Lifecycle и серверный порядок операций дополнительно проверяются архитектурными тестами по исходному коду; это не полноценные сетевые GameTests.

**В игре ещё нужно проверить:** создание/повторное открытие мира, F3/F5 и строительство; фактический вид/освещение/ориентацию/область взаимодействия OBJ; одновременную игру двух клиентов, spectator menu, disconnect и удаление блока в работающем сервере; сохранение при реальной выгрузке chunk и перезапуске Minecraft. Клиент и выделенный сервер в этой работе не запускались.

**Persistence:** исходные проблемы economy persistence остаются отдельной задачей: ожидание disk IO на server thread, неоднозначный timeout и поздняя ошибка после replace. Никакого исправления этих проблем или гарантии rollback после timeout данная миграция не заявляет. Нового filesystem IO на render/client thread не добавлено.

**Старые миры:** уже сохранённые gamerules из прежней версии автоматически не сбрасываются: невозможно отличить настройки пользователя от прежних принудительных значений. При необходимости их следует поправить ванильными командами. Сторонние моды также могут вводить свои ограничения.

## 6. Полный список файлов относительно HEAD

Список включает ранее сделанную часть Astra и завершение этой задачи.

### Изменены

- [src/main/java/com/makar/tacticaltablet/airdrop/AirdropEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/airdrop/AirdropEvents.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoAdmissionPolicy.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoAdmissionPolicy.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoEvents.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoSessionManager.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoSessionManager.java>)
- [src/main/java/com/makar/tacticaltablet/casino/net/CasinoClosePacket.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/net/CasinoClosePacket.java>)
- [src/main/java/com/makar/tacticaltablet/casino/net/CasinoSpinResultPacket.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/net/CasinoSpinResultPacket.java>)
- [src/main/java/com/makar/tacticaltablet/client/ClientAntiCheatEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/ClientAntiCheatEvents.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoClientAccess.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoClientAccess.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoScreen.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoScreen.java>)
- [src/main/java/com/makar/tacticaltablet/client/event/ClientKeyBindingSanitizer.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/event/ClientKeyBindingSanitizer.java>)
- [src/main/java/com/makar/tacticaltablet/client/event/ClientScreenEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/event/ClientScreenEvents.java>)
- [src/main/java/com/makar/tacticaltablet/client/gui/JoinServerConfigScreen.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/gui/JoinServerConfigScreen.java>)
- [src/main/java/com/makar/tacticaltablet/core/ModBlockEntities.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/core/ModBlockEntities.java>)
- [src/main/java/com/makar/tacticaltablet/core/ModBlocks.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/core/ModBlocks.java>)
- [src/main/java/com/makar/tacticaltablet/core/ModItems.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/core/ModItems.java>)
- [src/main/java/com/makar/tacticaltablet/core/TacticalTabletMod.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/core/TacticalTabletMod.java>)
- [src/main/java/com/makar/tacticaltablet/game/GameStateManager.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/GameStateManager.java>)
- [src/main/java/com/makar/tacticaltablet/game/MatchGameRules.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/MatchGameRules.java>)
- [src/main/java/com/makar/tacticaltablet/game/ServerEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/ServerEvents.java>)
- [src/main/java/com/makar/tacticaltablet/game/balance/Py132BalanceHandler.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/balance/Py132BalanceHandler.java>)
- [src/main/java/com/makar/tacticaltablet/game/lobby/CasinoNpcTemplateMigration.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/lobby/CasinoNpcTemplateMigration.java>)
- [src/main/java/com/makar/tacticaltablet/game/lobby/LobbyBootstrapManager.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/lobby/LobbyBootstrapManager.java>)
- [src/main/java/com/makar/tacticaltablet/inventory/InventoryLockEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/inventory/InventoryLockEvents.java>)
- [src/main/java/com/makar/tacticaltablet/tablet/client/ClientEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/tablet/client/ClientEvents.java>)
- [src/main/java/com/makar/tacticaltablet/tablet/net/PacketHandler.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/tablet/net/PacketHandler.java>)
- [src/main/java/com/makar/tacticaltablet/tablet/net/PacketProtocol.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/tablet/net/PacketProtocol.java>)
- [src/main/resources/assets/tacticaltablet/lang/en_us.json](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/lang/en_us.json>)
- [src/main/resources/assets/tacticaltablet/lang/ru_ru.json](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/lang/ru_ru.json>)
- [src/test/java/com/makar/tacticaltablet/client/KeyBindingSanitizerArchitectureTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/client/KeyBindingSanitizerArchitectureTest.java>)
- [src/test/java/com/makar/tacticaltablet/game/LobbyLifecycleRegressionArchitectureTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/game/LobbyLifecycleRegressionArchitectureTest.java>)
- [src/test/java/com/makar/tacticaltablet/game/lobby/CasinoNpcTemplateMigrationTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/game/lobby/CasinoNpcTemplateMigrationTest.java>)
- [src/test/java/com/makar/tacticaltablet/game/respawn/PostRtpProtectionArchitectureTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/game/respawn/PostRtpProtectionArchitectureTest.java>)
- [src/test/java/com/makar/tacticaltablet/tablet/net/PacketRegistryTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/tablet/net/PacketRegistryTest.java>)
- [src/test/resources/deluxewarfare-runtime-assets.tsv](<C:/MCDev/Tactical Tablet/src/test/resources/deluxewarfare-runtime-assets.tsv>)

### Добавлены

- [src/main/java/com/makar/tacticaltablet/casino/CasinoAnimation.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoAnimation.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoMachineAccess.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoMachineAccess.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoMachineAnimationState.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoMachineAnimationState.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoMachineBlock.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoMachineBlock.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoMachineBlockEntity.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoMachineBlockEntity.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoMachineItem.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoMachineItem.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoMenu.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoMenu.java>)
- [src/main/java/com/makar/tacticaltablet/casino/CasinoReelResult.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/CasinoReelResult.java>)
- [src/main/java/com/makar/tacticaltablet/casino/net/CasinoAnimationPacket.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/casino/net/CasinoAnimationPacket.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoClientEvents.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoClientEvents.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoMachineItemRenderer.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoMachineItemRenderer.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoMachineModel.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoMachineModel.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoMachineRenderer.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoMachineRenderer.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoOddsPresentation.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoOddsPresentation.java>)
- [src/main/java/com/makar/tacticaltablet/client/casino/CasinoPresentation.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/client/casino/CasinoPresentation.java>)
- [src/main/java/com/makar/tacticaltablet/core/ModMenuTypes.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/core/ModMenuTypes.java>)
- [src/main/java/com/makar/tacticaltablet/game/ServerRules.java](<C:/MCDev/Tactical Tablet/src/main/java/com/makar/tacticaltablet/game/ServerRules.java>)
- [src/main/resources/assets/tacticaltablet/blockstates/casino_machine.json](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/blockstates/casino_machine.json>)
- [src/main/resources/assets/tacticaltablet/models/block/casino_machine.json](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/models/block/casino_machine.json>)
- [src/main/resources/assets/tacticaltablet/models/block/slot_machine_casino.mtl](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/models/block/slot_machine_casino.mtl>)
- [src/main/resources/assets/tacticaltablet/models/block/slot_machine_casino.obj](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/models/block/slot_machine_casino.obj>)
- [src/main/resources/assets/tacticaltablet/models/item/casino_machine.json](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/models/item/casino_machine.json>)
- [src/main/resources/assets/tacticaltablet/textures/block/slot_machine_casino_seamless_texture_2048.png](<C:/MCDev/Tactical Tablet/src/main/resources/assets/tacticaltablet/textures/block/slot_machine_casino_seamless_texture_2048.png>)
- [src/test/java/com/makar/tacticaltablet/casino/CasinoMachineArchitectureTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/casino/CasinoMachineArchitectureTest.java>)
- [src/test/java/com/makar/tacticaltablet/casino/CasinoMachineTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/casino/CasinoMachineTest.java>)
- [src/test/java/com/makar/tacticaltablet/game/SingleplayerArchitectureTest.java](<C:/MCDev/Tactical Tablet/src/test/java/com/makar/tacticaltablet/game/SingleplayerArchitectureTest.java>)
- [docs/casino-machine-and-singleplayer.md](<C:/MCDev/Tactical Tablet/docs/casino-machine-and-singleplayer.md>)

### Удалены

- src/main/java/com/makar/tacticaltablet/client/casino/CasinoOddsScreen.java
