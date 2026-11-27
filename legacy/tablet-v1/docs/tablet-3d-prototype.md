# TT01: первый визуальный прототип

Minecraft **1.20.1**, Forge **47.4.18**, Java **17**, Gradle wrapper **8.8** сохранены.
Новых production-зависимостей нет. Новый функциональный UI, RTT и анимации рук не входят в этот этап.

## Переключение и проверка

Клиентский файл `<game directory>/config/tacticaltablet-client.toml`:

```toml
[tablet]
    presentation = "PROTOTYPE"
```

Это значение по умолчанию в ветке. Для существующего конфига **закройте игру**, добавьте
секцию `[tablet]` (если отсутствует) или измените только `presentation`. Для отката задайте
`"LEGACY"` и перезапустите клиент. Серверный конфиг менять не нужно.

Для изолированной проверки, не использующей обычные dev-миры и multiplayer-данные:

```powershell
.\gradlew.bat runClient -PgameRunDirectory=run-tablet-prototype
```

Создайте новый одиночный creative-мир с читами. Никакой матч запускать не нужно:
существующий `ServerRules.enabled()` включает матчевые механики только на dedicated server.
Выдайте предмет:

```mcfunction
/give @s tacticaltablet:tactical_tablet
```

ПКМ (или переназначенное действие «Использовать») запускает `draw` (0,65 с).
Планшет остаётся в положении последнего кадра. Отпустите кнопку, затем снова нажмите ПКМ,
либо Escape: `holster` (0,45 с), затем возвращается управление.
Escape во время доставания ставит убирание в очередь после последнего кадра draw, без скачка позы.
Повторные нажатия в переходах игнорируются; удержание не запускает новый цикл.

Пустой неприостанавливающий Screen владеет вводом до окончания holster, без фона, звуков,
виджетов и пакетов. Во время сеанса обычные действия игры заблокированы. Чтобы вручную
сменить слот или переложить предмет в другую руку, сначала закройте прототип.
Серверная замена/удаление предмета, смена выбранного слота внешним кодом, смерть,
выход из мира, смена мира/экрана и потеря фокуса окна аварийно сбрасывают сеанс.

Проверить вручную:

- NORTH-дисплей, читаемость текста, стороны, UV, повёрнутые детали;
- draw / длительное OPEN / holster, Escape в начале draw, быстрые клики и удержание;
- основной и второй слот руки, обе настройки ведущей руки; два планшета одновременно;
- смену слота/руки между сеансами, удаление предмета сервером во время сеанса;
- инвентарь, выброшенный предмет, рамку, третье лицо (статическая модель);
- смерть, выход/повторный вход, F3+T и потерю фокуса;
- отсутствие старых окон при phase/map-vote/chaos callbacks в PROTOTYPE;
- возврат старых окон и CustomModelData 0/1/2 в LEGACY на сервере;
- загрузку dedicated server без клиентских классов.

Одиночный режим LEGACY сохраняет прежнее поведение: старый интерфейс там не открывался.
PROTOTYPE во всех вариантах CustomModelData использует один TT01; теги и legacy-модели сохранены.
Смена вида камеры не проигрывает first-person-анимацию в третьем лице; время сеанса продолжает идти.

## Граница миграции

`TabletPresentation` — единственный выбор режима. `ClientEvents` отдаёт ПКМ прототипу до
старых проверок singleplayer/chaos, а legacy-автооткрытие и переходы фаз в PROTOTYPE не выполняет.
Callback `MapVoteClientState` открывает экран через тот же шлюз.
`ScreenEvent.Opening` блокирует до init() `TabletScreen`, три `TacticalPhaseScreen`,
внутренние классы TabletScreen и `TacticalDialog`, чей родитель принадлежит планшету.
При смене режима уже открытый legacy-экран закрывается на клиентском тике.

Пакеты продолжают обновлять состояние; прежний `TabletClientState.requestClose()` воздействует
только на старый TabletScreen. Прототип сам не отправляет C2S-пакеты, не выбирает команду,
не голосует, не выдаёт класс и не снимает серверные ограничения.
**Полноценная игра с голосованием/выбором команды/класса пока требует LEGACY.**

Сохранены старые экраны, вложенные окна кланов, текстуры, предметные JSON-модели с
CustomModelData и все серверные обработчики/packet ID. `ContractTrackerScreen` открывает
отдельный предмет `contract_tracker`, поэтому его callback сохранён. HUD боя, смерть,
казино, настройки и разделяемые UI-компоненты продолжают работать.

После появления новых функций можно удалить старый TabletScreen и его вложенные окна,
голосование/выбор команды после их замены, старые tablet-specific GUI-звуки/текстуры,
автооткрытие legacy и затем режим LEGACY. Общие компоненты и серверные пакеты требуют
отдельной проверки потребителей перед удалением.

## Подготовка модели

Оригинал: `model-sources/tablet/TacticalTablet_TT01.bbmodel`, вне runtime-ресурсов.
Фактически: `meta.format_version=4.10`, `meta.model_format=free`, 156 кубов,
8 групп с `tablet_root` и `display_surface`, один встроенный PNG 1024×1024,
по 25 линейных ключей позиции и вращения root на каждый клип.
Плавность уже заложена в частых ключах исходника; между ними применяется linear interpolation.

```powershell
node tools/tablet/prepare-tablet.mjs
node tools/tablet/prepare-tablet.mjs --check
node --test tools/tablet/prepare-tablet.test.mjs
```

Конвертер без npm-зависимостей извлекает PNG, сохраняет иерархию/pivots/rest-вращения,
готовит root-relative вершины с UV каждой грани и нормалями, переносит root-анимации.
Он обновляет **только две записи** общего манифеста ресурсов (размеры/SHA-256/PNG).
Неподдерживаемые возможности (box UV, rescale, expressions, другие типы элементов,
нелинейные или нерутовые треки) завершаются явной ошибкой. Это конвертер TT01, не импортёр общего назначения.
Если модель начнёт использовать такие возможности, сначала расширьте инструмент и его проверки.

Проверенные соглашения Blockbench 4.x:

- Геометрия: исходные координаты, Y вверх; rest Euler **ZYX**, без инверсии знаков.
- Анимация BoneAnimator: позиция **(-x,+y,+z)** / 16, вращение **(-x,-y,+z)**,
  суммируется с rest Euler до построения quaternion ZYX.
- Ориентация NORTH-дисплея к камере: базис Y=180°, без отрицательного масштаба.
  Для левой руки меняется только смещение, текст дисплея не отражается.
- Каждая грань сохраняет свои четыре UV, их направления и четвертьобороты.

Основание: [Blockbench BoneAnimator 4.12.6](https://github.com/JannisX11/blockbench/blob/v4.12.6/js/animations/timeline_animators.js#L332-L359),
[CubeFace.UVToLocal](https://github.com/JannisX11/blockbench/blob/v4.12.6/js/outliner/cube.js#L66-L108).

`TabletPrototypeModel` загружает подготовленный JSON в immutable DTO при resource reload,
не читает исходный bbmodel в игре. Render использует готовые вершины/ключи и штатный
Forge VertexConsumer. PNG обновляется обычным TextureManager. Успешный reload сбрасывает
сеанс. Ошибки подготовки ресурсов не скрываются. Встроенные проверки source/JAR сохранены;
`verifyTabletPrototypeSource` дополнительно проверяет SHA исходника, а bbmodel запрещён в runtime.
Модель предмета обёрнута при baking; при LEGACY используется исходный baked model,
включая его overrides, при PROTOTYPE — BEWLR. Во время активного сеанса RenderHandEvent
подавляет оба штатных прохода рук и рисует один экземпляр без swing/equip/use-преобразований.
В CLOSED и вне первого лица BEWLR рисует статическую модель независимо от контроллера.

## Файлы реализации

- `TacticalTabletClientConfig`, `TacticalTabletItem`: клиентский режим и Forge renderer extension.
- `tablet/client/TabletPresentation`, `ClientEvents`, `MapVoteClientState`: единая граница legacy.
- `tablet/client/ui/widget/TacticalDialog`: доступ к родителю для определения владельца окна.
- `tablet/client/prototype/TabletAnimationController`: независимый от Forge автомат состояний.
- `tablet/client/prototype/TabletPrototypeClient`, `TabletPrototypeScreen`: ввод и жизненный цикл.
- `tablet/client/prototype/TabletPrototypeModel`, `TabletPrototypeRenderer`, `TabletPrototypeResources`:
  reload, отрисовка и совместимость baked models.
- `model-sources/tablet/`, `tools/tablet/`, runtime `tablet/tt01.json` и `textures/item/tablet_tt01.png`.
- `EmbeddedResourceDiagnostics`, общий TSV-манифест, `EmbeddedClientResourcesTest`, `build.gradle`:
  ресурсные проверки; `TabletAnimationControllerTest` и Node-тесты конвертера.
