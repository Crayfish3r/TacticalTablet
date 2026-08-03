# Полная UI/UX-переработка TacticalTablet

Статус: проектная спецификация, без реализации production-экранов

Целевая платформа: Minecraft Forge 1.20.1, Java 17

Основание: аудит текущего клиентского UI, его состояний, C2S/S2C-пакетов и существующих GUI-ассетов

## 1. Executive summary

TacticalTablet уже имеет начало общей UI-системы (`TacticalTheme`, `TacticalLayout`, `TacticalUi`, анимации и базовые виджеты), но пользовательский опыт остаётся набором разнородных экранов: часть элементов является настоящими Minecraft-виджетами, часть рисуется и обрабатывает мышь вручную, диалоги дублируются, а текст почти целиком захардкожен. В результате мышь обычно работает, но клавиатура, narration, восстановление фокуса, длинные строки, малые GUI-масштабы и сетевые задержки обрабатываются непоследовательно.

Рекомендуется эволюционная миграция в пять этапов:

1. исправить общие P0-проблемы render state, времени кадра, фокуса, локализации и измеримости;
2. доказать дизайн-систему на вертикальном срезе `VotingScreen` → `TeamSelectScreen` → `MapVotingScreen`;
3. перевести оболочку планшета и страницы Classes/Shop/VIP/Profile;
4. мигрировать Clans/ClanCreate и ContractTracker;
5. унифицировать HUD/уведомления/экран смерти и удалить legacy-пути после паритета.

Целевой стиль: компактный тактический терминал, совместимый с существующими тёмными металлическими рамками, оливковым базовым акцентом и фиолетовым Epic-вариантом. Текстуры планшета остаются внешней «аппаратной» оболочкой, а данные и интерактивные поверхности внутри неё становятся программными и токенизированными. Игровая логика, packet ID и серверная авторизация не меняются в рамках редизайна.

Успех измеряется не только внешним видом: каждый интерактивный элемент достижим клавиатурой, текущий фокус виден независимо от selected-состояния, экран работает при длинных RU/EN-строках и минимальном поддерживаемом GUI scale, сетевое действие имеет понятное pending/timeout-состояние, а UI не меняет и не «угадывает» серверный результат.

## 2. Аудит текущего состояния

### Сильные стороны

- Есть единая точка базовых цветов и примитивов, а также переиспользуемые button/card/text-field классы.
- `TacticalButton` и `TacticalCard` наследуются от ванильного `Button`, поэтому сохраняют базовую обработку мыши, клавиатуры и narration.
- `TacticalTextField` сохраняет ванильную семантику редактирования `EditBox`.
- Scissor в новых помощниках в основном закрывается через `try/finally`.
- Анимация `AnimatedFloat` ограничивает большой delta и покрыта тестами 30/60 FPS.
- Сервер уже остаётся источником истины: C2S-действия валидируются сервером, а клиент получает синхронизированное состояние.
- Существующие планшетные и контрактные текстуры задают узнаваемую визуальную рамку, которую не требуется выбрасывать.

### Главные проблемы

| Приоритет | Проблема | Пользовательское/техническое последствие |
|---|---|---|
| P0 | Глобальные часы `TacticalUi.beginFrame()` зависят от миллисекунды и могут вызываться несколькими слоями | Разные анимации в одном визуальном кадре получают разный или нулевой delta |
| P0 | `GuiTextureRenderer.blitWithAlpha()` не восстанавливает ранее включённый blend во внешнем scope | Утечка render state и ошибки последующей отрисовки |
| P0 | Навигация планшета и `ScrollableActionGrid` рисуются/кликаются вручную | Нет гарантированного focus order, keyboard activation и narration |
| P0 | `TacticalDialog` выбирает confirm по умолчанию даже для destructive action | Enter может немедленно подтвердить опасное действие |
| P0 | Восстановление фокуса диалога хранит ссылку на старый widget, но parent переинициализируется | Возврат фокуса фактически ненадёжен |
| P0 | Ресурсы и часть представлений карточек вычисляются в render-loop | `getResource`, форматирование и временные коллекции создают лишнюю нагрузку на кадр |
| P0 | UI-текст почти не локализован; `ru_ru.json` при UTF-8-чтении выглядит mojibake | Нельзя системно проверить длины, язык и доступность; необходима проверка реальной кодировки файла |
| P1 | selected-состояние перекрывает focus-обводку | Пользователь клавиатуры теряет текущую позицию |
| P1 | Нет общего async-состояния действия и политики timeout | При задержке сети клик выглядит проигнорированным или может дублироваться |
| P1 | Текстовые области часто clip-ятся без tooltip/ellipsis/wrap policy | Длинные имена карт, кланов и локализованные строки теряются |
| P1 | `ContractClientState` возвращает `List.copyOf` при каждом чтении | Аллокации в render-loop; экран читает списки несколько раз за кадр |
| P1 | Карта контракта строит много геометрии каждый кадр и красит цели по чётности индекса | Риск просадки FPS и нестабильная семантика цвета |
| P2 | HUD-компоненты не координируют зоны размещения | Пересечения при узком экране, модифицированном hotbar и нескольких уведомлениях |

### Ограничения доступных данных

Редизайн должен показывать только подтверждённые данные. Без расширения протокола сейчас отсутствуют:

- описания, характеристики и сравнение классов;
- изображения, авторы, размер и рекомендуемое число игроков для карт;
- инициатор голосования и серверная причина отклонения голоса;
- VIP-статус и срок его действия;
- история матчей, достижения и расширенная статистика профиля;
- spectator-choice в выборе команды;
- завершение/провал контракта, точная дистанция до цели и ручная отмена контракта;
- очередь нескольких airdrop-уведомлений.

Такие элементы в макетах помечаются как **optional protocol/data extension** и не входят в паритетную миграцию.

## 3. Оценка UI foundation по классам

### `TacticalTheme`

**Оставить и расширить.** Текущая палитра пригодна как исходная. Добавить семантические токены вместо использования сырых ARGB на экранах: surface, border, text, action, status, team, rarity, contract difficulty, overlay. Добавить шкалы spacing, размеров, типографики и motion. Запретить новые литеральные цвета вне theme/asset-specific renderers.

### `TacticalLayout`

**Расширить без ломки текущих методов.** Нужны `Insets`, `Size`, `Axis`, `Alignment`, row/column/grid/list helpers, safe bounds, viewport, compact mode и вычисление колонок. Все функции остаются чистыми и тестируемыми без Forge.

### `AnimatedFloat` и `Easing`

**Оставить.** Добавить передачу easing на экземпляр, явный `snapTo`, корректный zero-duration и политику reduced motion. Анимация не должна владеть глобальным временем. Текущие easing-функции достаточны для первого релиза.

### `TacticalUi`

**Разделить ответственность.** Статические render-примитивы можно сохранить, но frame delta должен приходить из одного `UiFrameContext`, созданного на верхнем уровне render event/screen и переданного вниз. Добавить явные слои `background/content/floating/tooltip/modal`, семантические стили и debug-overlay bounds/focus/scissor. Не хранить изменяемые часы глобально.

### `GuiTextureRenderer`

**P0-рефакторинг.** Любой метод обязан либо полностью восстановить захваченный GL state, либо документированно требовать уже открытый blend scope. Предпочтение — один `AlphaBlendScope` на группу blit-вызовов, без GL-query и lambda на каждую иконку. Добавить тестовый seam/adapter для проверки порядка enable/restore.

### `TacticalButton`

**Оставить и модифицировать.** Развести оси состояния: enabled, hovered, focused, pressed, selected, pending. Focus-ring рисуется последним и не исчезает при selected. Keyboard activation получает краткий pressed-feedback. Добавить optional leading/trailing icon, spinner, tooltip supplier и стабильную narration-фразу с состоянием.

### `TacticalIconButton`

**Оставить и расширить.** Обязательны accessible name, selected/disabled tint, badge-slot и ковариантные builder-методы. Иконка никогда не является единственным носителем смысла без tooltip+narration.

### `TacticalTextField`

**Модифицировать осторожно, сохраняя `EditBox`.** Убрать временную мутацию `x/width`; вычислять внутренний text rect отдельно. Leading area должна фокусировать поле. Clear-action получает keyboard shortcut/accessible narration или заменяется отдельным focusable icon button при наличии места. Ошибка содержит не только цвет, но и текст/tooltip/narration. Добавить счетчик лимита при приближении к максимуму.

### `TacticalCard`

**Оставить и расширить.** Добавить layout slots: media, title, metadata, status, primary/secondary action. Поддержать ellipsis+tooltip либо максимум две строки. Narration включает subtitle/status. Focus-ring независим от selected. Статические карточки используют отдельный non-button component, чтобы не объявляться кнопками.

### `TacticalDialog`

**Переработать до массового использования.** Хранить не ссылку на widget, а стабильный `focusKey`; после возврата parent восстанавливает фокус по ключу. Для destructive dialog default focus — Cancel, Enter не подтверждает destructive action до явного перемещения фокуса. Body имеет wrap+scroll, responsive width и minimum margins. Callback возвращает результат, а владелец решает навигацию; dialog не должен безусловно перетирать установленный callback-ом screen. На первом этапе разрешён только один modal; вложенные диалоги запрещены.

## 4. Целевая дизайн-система

### Визуальное направление и палитра

Существующие `tablet.png`, `tablet_epic.png`, `vote_panel.png` и `contract_gui.png` используют почти чёрные внутренние поверхности, графитовый металл, оливково-зелёные линии и фиолетовый Epic-акцент. Новые поверхности должны продолжить этот язык, но повысить читаемость.

| Токен | Значение | Назначение |
|---|---:|---|
| `surface.scrim` | `#CC050708` | фон за modal/full-screen UI |
| `surface.base` | `#F012181D` | основное окно |
| `surface.raised` | `#F51A2329` | карточки/toolbar |
| `surface.sunken` | `#E80B1013` | input/list viewport |
| `surface.hover` | `#FF223139` | hover интерактивной поверхности |
| `surface.pressed` | `#FF182B2A` | краткое нажатие |
| `surface.selected` | `#FF1D3533` | выбранная сущность |
| `surface.disabled` | `#FF171D20` | недоступная поверхность |
| `border.default` | `#FF3B474D` | обычная граница |
| `border.hover` | `#FF60737A` | hover-граница |
| `border.focused` | `#FFE5F58A` | внешний focus-ring |
| `border.selected` | `#FF59E0B7` | выбранная сущность |
| `text.primary` | `#FFF1F4F2` | основной текст |
| `text.secondary` | `#FFB3BDB8` | метаданные |
| `text.muted` | `#FF7C8983` | подсказки/disabled text |
| `text.disabled` | `#FF65716C` | disabled text, контраст перепроверить |
| `accent.base` | `#FF59E0B7` | основной функциональный mint accent |
| `accent.muted` | `#FF2E7060` | спокойный selected/background accent |
| `accent.chassis` | `#FF91A638` | только связь с оливковой рамкой корпуса |
| `accent.epic` | `#FF9D45CF` | только rarity/appearance Epic |
| `status.success` | `#FF5FCB7A` | подтверждённый успех |
| `status.warning` | `#FFF0B84B` | ожидание/ограничение |
| `status.danger` | `#FFE05A5A` | ошибка/destructive |
| `status.info` | `#FF59A9D8` | нейтральная информация |
| `shadow` | `#99000000` | мягкая тень 1–2 px |
| `overlay` | `#B8000000` | блокирующий modal overlay |
| `tooltip` | `#FA101619` | фон tooltip |

Mint остаётся главным интерактивным цветом Tactical OS, а оливковый используется как декоративный мост к существующему корпусу; так новая система не спорит с текстурой и не наследует её низкий контраст для focus/action. Rarity: Basic `#AEB7B2`, Rare `#5FA9E6`, Epic `#A95ADB`, Legend `#F2B84B`, Monster `#E45B68`. Team colors берутся из реального `TeamId`, но прогоняются через контрастную text/border-пару. Contract difficulty: easy `success`, medium `warning`, hard `danger`, с обязательным текстом сложности. Требование контраста: обычный текст ≥ 4.5:1, крупный/иконографический ≥ 3:1. Team/rarity/difficulty color всегда дублируется текстом, символом или узором.

### Размеры, сетка и typography

- Spacing: 2 px — hairline/icon nudge; 4 — внутри badge/между icon и label; 8 — стандартный padding/row gap; 12 — card padding/section gap; 16 — крупная секция; 24 — разделение смысловых зон; 32 — только просторный full-screen break, не внутри 204 px tablet viewport.
- Интерактивная высота: compact button 18, standard button 22, primary action 28, icon button 20×20, input 22, tab/navigation item 22 px; минимальный hit target 18×18, предпочтительно 20×20.
- Card padding 8 compact/12 standard. Dialog: 220–320 px шириной, не более `screen - 16`, высота по content до `screen - 16`, затем body scroll. Toast: 180–280 px шириной, минимум 32 px высотой. Минимальная полезная ширина text block — 80 px.
- Радиусы визуально имитируются cut-corners 2/4 px; не смешивать с ванильными скруглениями.
- Text styles: caption 0.75× только для необязательной метаинформации; body/card title/button 1×; section title 1× с цветом/отступом вместо faux bold; screen title 1.25×; numeric indicator 1–1.25×; hero 2× только на death/result.
- Не масштабировать body-текст ниже 1×; сокращать layout, а не читаемость.
- Safe margin: минимум 8 px от края scaled GUI; full-screen overlay учитывает hotbar и bossbar zones.
- Uppercase — только короткие статусы до 12 символов; предложения и динамические имена сохраняют регистр. Body line-height — `font.lineHeight + 2`, tooltip — `+1`. Dynamic names: максимум две строки, затем ellipsis и полный tooltip. Числа выравниваются вправо в таблицах, текст — влево; centered используется только для empty/result.

### Иконки и композиция

- Compact glyph 12×12, standard 16×16, prominent 20×20. Не масштабировать bitmap на нецелый коэффициент.
- Normal — `text.secondary`, hover — `text.primary`, selected — `accent.base`, disabled — `text.disabled`; полноцветные class icons не tint-ить.
- Icon+label: gap 4 px, icon слева; icon-only разрешён только для общеизвестных действий и всегда имеет tooltip, accessible name и narration.
- Внутри panel порядок: header → optional toolbar → scroll viewport → inline feedback/footer. Не смешивать более одной prominent CTA в одной секции.

### Состояния и motion

Обязательные состояния каждого action: default, hover, focused, pressed, selected, disabled, pending. Selected и focused могут существовать одновременно. Pending блокирует повторный submit, но не закрывает экран; через 3 секунды показывается «Сервер не ответил», после следующего state-sync UI сверяется с сервером. Это клиентская обратная связь, не подтверждение успеха.

Motion: hover 100 ms (color/alpha), press 80 ms (visual inset ≤1 px, hitbox неподвижен), selected 120 ms (border/fill), page transition 160 ms (alpha + ≤4 px), modal 140 ms (alpha + ≤2 px), toast enter 140 ms/hold по state/exit 180 ms, progress 160 ms до нового значения, card emphasis 160 ms, error 160 ms (danger border, не shake), success 180 ms (border/check), map selection 120 ms, objective pulse один цикл 600 ms без мигания чаще 3 Hz. Использовать ease-out для появления и ease-in-out для перемещения. При reduced motion всё snap-ится, кроме необходимого fade для читаемости. Анимация никогда не меняет hitbox и не блокирует действие.

### Базовые компоненты

- `UiFrameContext`: delta, scaled bounds, reduced-motion, pointer, debug flags.
- `TacticalPanel`, `TacticalSection`, `TacticalDivider`.
- `TacticalButton`, `TacticalIconButton`, `TacticalTextField`.
- `TacticalCard`, `SelectableCard`, `StatCard`, `TeamCard`, `MapCard`.
- `NavigationRail`, `TabBar`, `Toolbar`, `Breadcrumb`.
- `ScrollableList`, `ResponsiveGrid`, `Scrollbar`.
- `Badge`, `StatusChip`, `ProgressBar`, `Countdown`.
- `Tooltip`, `ToastHost`, `InlineMessage`, `EmptyState`, `Skeleton`.
- `TacticalDialog` (info/confirm/destructive), `ContextMenu` только после keyboard semantics.
- `HudAnchorManager`, `HudCounter`, `NoticeBanner`.

## 5. Информационная архитектура

### Глобальный поток

```text
Предматчевый flow
  Голосование за режим
    → Выбор команды (только командный режим)
      → Голосование за карту
        → Игра

В игре
  Планшет
    ├─ Классы
    ├─ Магазин
    ├─ VIP / Эксклюзивы
    ├─ Профиль
    └─ Кланы
         ├─ Список / мой клан
         └─ Создание клана

  Контрактный трекер
    ├─ Выбор цели
    └─ Активный радар

  HUD
    ├─ Жизни и игроки
    ├─ Airdrop notice
    ├─ Spectator hint
    └─ Death transition
```

### Навигационные правила

- Предматчевые экраны — линейный server-driven flow; Escape не закрывает их, если текущие правила это запрещают.
- В планшете сохраняется выбранный раздел и scroll position на время жизни screen; при полном закрытии допустим reset на Classes.
- Rail доступен Tab/Shift+Tab и стрелками; Enter/Space активирует раздел.
- Modal перехватывает input, фон не интерактивен; после закрытия фокус возвращается по `focusKey`.
- Back в подэкране возвращает на предыдущий стабильный route, а не создаёт новый вложенный `Screen` без истории.
- Состояние экрана — проекция immutable client snapshot; widget не меняет серверные данные напрямую.

### Практическая структура и ownership

Рекомендуемая структура уменьшает монолит постепенно, не требует сразу переименовывать `TabletScreen`:

```text
tablet/client/ui/                 tokens, layout, render scopes, widgets
tablet/client/screen/
  TabletPage.java                lifecycle/render/input contract
  TabletPageContext.java         snapshot, intent sink, navigation, feedback
  page/                           ClassesPage, ShopPage, VipPage, ...
  presenter/                      чистое преобразование snapshot → view model
  dialog/                         dialog specs/controllers, не packets
tablet/client/TabletScreen.java   временный shell; позднее тонкий TabletShellScreen
```

- Shell владеет device frame, viewport, rail, active route, top-level focus, tooltip/toast host, одним modal slot и page transition.
- Page владеет своими widgets, локальным scroll/filter/selection и преобразует user action в semantic intent (`BUY_CLASS`, `JOIN_CLAN`), но не знает packet ID.
- `TabletPageContext` предоставляет immutable `TabletClientSnapshot`, `IntentDispatcher`, navigation, sound/feedback ports и feature flags. Он не предоставляет `MinecraftServer`, `Level`, entity или mutable `ItemStack`.
- Adapter рядом с текущим client networking переводит intent в существующий packet. Packet payload/ID и серверная проверка не меняются.
- Presenter — чистый Java-код: вычисляет visible status, disabled reason и formatted translation arguments; cache инвалидируется по snapshot version/language/resource reload.
- Screen/widget создаются и изменяются только на client thread. UI-анимации обновляются тем же render/tick потоком, без фоновых потоков.
- Один active modal выбран намеренно; tooltip и toast не считаются modal stack.

### Rendering pipeline и z-order

```text
z=0   dim world / renderBackground
z=10  device frame
z=20  inner surface + decoration
z=30  page viewport (scissor owner)
z=40  registered vanilla widgets inside viewport
z=50  floating page elements outside content scissor
z=60  tooltip (edge-clamped)
z=70  modal overlay
z=80  dialog
z=90  critical top notice
```

Top-level `render` создаёт `UiFrameContext` и render scopes один раз. Scissor применяется только к page scroll viewport; rail/header/device frame, tooltip и modal находятся вне него. Nested scroll пересекает свой rect с текущим scissor. Каждый push имеет lexical `close`/`finally`; `removed()` ничего глобально не отключает. `renderBackground` вызывается один раз shell-ом, `super.render` не должен повторно рисовать backdrop после custom layers. Widgets либо рендерятся ванильным проходом в z=40, либо явно как часть контейнера, но не оба раза. Tooltip измеряется до отрисовки и сдвигается внутрь safe bounds; при нехватке места переносится на противоположную сторону anchor.

### Минимальный layout API

```java
Rect safeArea(int screenW, int screenH, Insets insets);
Rect centered(Size preferred, Rect bounds);
Split splitX(Rect bounds, int fixedLeft, int gap);
Rows rows(Rect bounds, int rowHeight, int gap, int count);
Grid grid(Rect viewport, int minCellWidth, int rowHeight, int gap, int itemCount);
Rect scrollViewport(Rect bounds, Insets padding);
```

Это набор чистых вычислений, не layout engine. `Grid` возвращает column count, content height и rect видимых индексов; off-screen rows не render-ятся, но focus navigation умеет прокрутить выбранный item в видимую область. Для 380×220 tablet сначала вычисляется integer scale корпуса, затем точный внутренний viewport; при невозможности 1× используется существующая scaled GUI матрица и safe clipping, а не независимое сжатие отдельных controls.

### Tooltip и toast policy

- Vanilla tooltip API остаётся финальным renderer-ом текста/item tooltip; Tactical wrapper задаёт delay 350 ms, max width 220 px, title/body/shortcut/warning/cost и edge placement. Keyboard focus показывает тот же tooltip без требования hover; anchor key предотвращает мерцание между соседними пикселями.
- `TacticalToastManager` полезен для локальных UI success/error/warning и будущих clan/class/contract events: максимум 3, critical выше, dedupe по semantic key, stack сверху вниз, lifetime 3–6 s, без click action в первой версии, не pause-ится открытым tablet screen. Airdrop сохраняет отдельный server-timed `NoticeBanner`, потому что текущее state заменяемое и не является очередью.
- Success toast создаётся только после подтверждённого snapshot/result. Timeout — warning, а не error. После закрытия screen поздний ответ обновляет общий snapshot, но не воскрешает старый dialog/toast без активного observer.

## 6. Спецификация экранов

### 6.1 VotingScreen

**Цель:** выбрать режим и видеть распределение голосов/оставшееся время. Доступны список `MatchMode`, выбранный голос, counts, availability mask и timer. Инициатор и описание режимов отсутствуют.

```text
┌ Голосование за режим ─────────── 00:23 ┐
│ Выберите режим. Голос можно изменить. │
│ [ SOLO        5 голосов         ✓ ]   │
│ [ DUO         3 голоса            ]   │
│ [ TRIO        недоступно           ]   │
│ [ SQUADS      1 голос              ]   │
└───────────────────────────────────────┘
```

- Список вместо фиксированных координат; disabled mode остаётся видимым с причиной «Недоступно в этом голосовании».
- Клик/Enter отправляет существующий `VoteModePacket`; повторный выбор разрешён и не требует confirm.
- После submit карточка pending до state-sync; таймер не замораживается.
- Empty: «Нет доступных режимов — ожидаем сервер»; stale/timeout — inline warning без объявления результата.
- Default focus: текущий выбор, иначе первый доступный режим.

### 6.2 TeamSelectScreen

**Цель:** выбрать одну из реальных четырёх стандартных команд с видимой вместимостью. Доступны team name/color, slots, selected ordinal и `teamSlotSize`; spectator action отсутствует.

```text
┌ Выбор команды ────────────────────────┐
│ ┌ ALFA ─────────┐ ┌ BETA ─────────┐ │
│ │ 2 / 5  [██░░░]│ │ 5 / 5  ПОЛНА │ │
│ └───────────────┘ └───────────────┘ │
│ ┌ GAMMA ────────┐ ┌ DELTA ───────┐ │
│ │ 4 / 5         │ │ 1 / 5   ✓    │ │
│ └───────────────┘ └───────────────┘ │
│ Автовыбор через 00:12                 │
└──────────────────────────────────────┘
```

- Responsive: 2×2 при ширине, 1 колонка на узком scale; общий grid helper обязан поддерживать 2/3/4 элемента, хотя текущий domain даёт четыре команды.
- Full выводится текстом и disabled-стилем; цвет не единственный сигнал.
- Switch team разрешён сервером; generic rejection после timeout, затем sync возвращает истину.
- Default focus: выбранная команда, иначе первая неполная.
- Не добавлять spectator button без отдельной серверной функции.

### 6.3 MapVotingScreen

**Цель:** сравнить карты и проголосовать. Доступны name, vote count, selected map, timer; оператору — competitive/clan-war toggles. Превью и metadata отсутствуют.

```text
┌ Выбор карты ──────────────────── 00:31 ┐
│ [Соревновательный: ВКЛ] [Война: ВЫКЛ] │
│ ┌ Карта A ┐ ┌ Карта B ┐ ┌ Карта C ┐   │
│ │ preview │ │ preview │ │ preview │   │
│ │ 7  ✓    │ │ 4       │ │ 2       │   │
│ └─────────┘ └─────────┘ └─────────┘   │
│ ┌ Карта с очень длинным… ┐             │
│ │ 1 голос                 │     ▐      │
│ └─────────────────────────┘     ▐      │
└────────────────────────────────────────┘
```

- Рекомендация: responsive scrollable grid (3 колонки стандартно, 2/1 на узком окне), не horizontal carousel: проще сравнение, keyboard navigation и произвольное число карт.
- Пока нет ассета, media area показывает детерминированный pattern/инициал, а не ложное изображение. Реальные preview — optional data/asset extension.
- Длинное имя: две строки или ellipsis+tooltip. Лидер может вычисляться из counts, но не объявляется победителем до смены серверной фазы.
- Operator toggles отделены toolbar-ом и недоступны остальным; сохранить существующие packets/права.

### 6.4 Tablet shell

Сохранить аппаратную рамку 380×220 и inner viewport 286×204. На очень маленьком scaled screen вся оболочка масштабируется единым целым до safe bounds; внутренний body не получает дополнительное дробное масштабирование текста.

```text
┌ device frame ───────────────────────────────────┐
│ ┌ rail 72 ┐ ┌ header: section | coins/status ┐ │
│ │ Classes │ ├────────────────────────────────┤ │
│ │ Shop    │ │ toolbar/filter                 │ │
│ │ VIP     │ │ content viewport              │ │
│ │ Profile │ │                              ▐ │ │
│ │ Clans   │ │                              ▐ │ │
│ │         │ ├────────────────────────────────┤ │
│ │ RTP     │ │ inline status / contextual CTA │ │
│ └─────────┘ └────────────────────────────────┘ │
└────────────────────────────────────────────────┘
```

- `NavigationRail` становится контейнером настоящих widgets; active и focus отображаются одновременно.
- Существующий RTP packet/cooldown/role restriction сохраняются. Tooltip объясняет локально известную причину disabled.
- Ресурсы и fitted text кэшируются при init/resource reload/state change, не в каждом render.
- `removed()` не должен глобально отключать scissor; каждый scope владеет своим состоянием.

### 6.5 Classes

Доступны семь базовых class definitions, имя, category/action/id, icon, unlock/purchase/tier/XP/cooldown/game-state данные. Описаний и характеристик нет.

- Grid карточек: icon, localized name, tier/status, доступное действие.
- Filter пока только «Все/доступные/заблокированные», вычислимый клиентом; не обещать роли или combat stats.
- Выбор class/выдача kit сохраняет текущие ограничения. Pending не считается успехом до sync/system response.
- Недоступная карточка остаётся читаемой и объясняет локально известную причину.

### 6.6 Shop

Доступны восемь shop classes, цена, ownership, coins и ограничения режима.

- Header показывает баланс; grid использует одну карточку с Classes, но purchase CTA и price-slot.
- Confirm обязателен для траты; default focus Cancel. После submit dialog переходит в pending, закрывается только после подтверждённого состояния либо показывает timeout.
- Категории магазина не добавлять: текущая модель содержит только классы.
- Empty: «В этом режиме магазинные классы недоступны» с причиной.

### 6.7 VIP / Exclusive

Доступны восемь exclusive classes и флаги выдачи/покупки. Нет самого VIP-статуса или даты окончания.

- Название раздела «Эксклюзивы» точнее фактических данных; «VIP» можно оставить как route label для совместимости.
- Карточка показывает только «Выдан / Не выдан / Требует открытия», без срока подписки.
- Возможный VIP account banner — только после optional protocol extension.

### 6.8 Profile

Доступны wins, kills, deaths, matches, coins, career progress; локальный skin/head можно получить из клиента без сети.

```text
┌ Профиль ──────────────────────────────┐
│ [head] PlayerName    Прогресс [███░] │
│ Победы 12 │ Убийства 48 │ Смерти 20  │
│ Матчи 31  │ K/D 2.40*   │ Монеты 900 │
└──────────────────────────────────────┘
```

`K/D` допустим как явно вычисляемое значение с защитой от деления на ноль; звёздочка в спецификации означает derived, в UI лучше tooltip «Рассчитано на клиенте». История, achievements и ranking отсутствуют.

### 6.9 Clans

Доступны id/name/tag/color, owner, counts/coins, отношение viewer, marine unlock, requests и members; действия create/join/leave/disband/color/accept/reject/kick.

- Два route-state: discovery list и my-clan dashboard.
- Поиск — client-side по уже полученному списку; не посылать запрос на каждый символ.
- Discovery row: `[TAG] name`, owner, members, статус заявки, Join CTA.
- Dashboard: summary, coins/unlocks, members и requests tabs; owner-only actions видимы/disabled с объяснением.
- Destructive leave/disband/kick — dialog с Cancel default; disband требует повторного подтверждения текстом только если product решит усилить защиту, но это не P0.
- Списки имеют empty/loading/stale states и stable item keys по clan/member UUID.

### 6.10 ClanCreate

Доступны name, tag и color; текущие server limits/validation остаются источником истины.

- Поля показывают label постоянно, placeholder не заменяет label.
- Локальная проверка длины/пустоты/разрешённых символов зеркалит сервер только для ранней обратной связи; серверная проверка остаётся обязательной.
- Color picker доступен клавиатурой и дублирует цвет названием/кодом.
- Submit pending и защищён от двойного клика; server failure без структурированной причины показывается как generic message. Подробные field errors требуют optional response data.

### 6.11 ContractTracker

Доступны до 16 целей с UUID/name/class/kills/wins/career/difficulty/price/reward, affordability; активный tracker содержит zone, player position, signal time и target areas.

```text
┌ Контрактный трекер ─────────────────┐
│ Баланс …              Сигнал 00:42 │
│ ┌ targets ─────────┐ ┌ radar ────┐ │
│ │ Name  HARD  $…   │ │ N   ○     │ │
│ │ Name  MED   $…   │ │   △ player│ │
│ │ …              ▐ │ │ target ◎  │ │
│ └─────────────────┘ └────────────┘ │
│ [Начать отслеживание]               │
└─────────────────────────────────────┘
```

- Убрать лимит отображения первых восьми: scroll list показывает все разрешённые packet-ом 16.
- Цвет цели стабилен по UUID и всегда сопровождается label/legend; difficulty использует текст+цвет.
- Radar сохраняет текущую математику зоны. Геометрию окружностей кэшировать по radius/scale или снизить сегменты после профилирования.
- `ContractClientState` хранит immutable snapshot один раз на update и возвращает без `List.copyOf` в render-loop.
- Не показывать distance/completed/failed/cancel, пока данных и action нет.
- Direct tessellation и shader/blend/scissor должны работать через явный render scope и восстанавливать state.

### 6.12 Lives HUD

Доступны собственные lives; overlay виден только в игре, не spectator, GUI visible, screen закрыт, lives > 0.

- `HudCounter(heart, "×N")` справа от hotbar, с контрастной backing plate только при низкой читаемости фона.
- Сохранить текущие visibility rules. При узкой ширине группа переходит над hotbar через `HudAnchorManager`.
- Изменение lives может дать короткий 160 ms scale/color feedback; без бесконечной анимации.

### 6.13 Players HUD

Доступны alive players и total remaining lives. Текущий формат `×players (remainingLives)` неоднозначен.

- Показать `👥 N` и отдельный compact sublabel `жизней всего M`; tooltip/narration раскрывает оба значения.
- При `alivePlayers == 0` сохранить текущую политику скрытия до подтверждения продуктом; не трактовать ноль как победу.
- Компонент группируется с Lives HUD и не пересекает spectator hint.

### 6.14 Airdrop notice

Доступны строка до packet limit, ARGB color, duration 1–200 ticks, type и локальные fade/sound. Состояние хранит только одно сообщение.

- `NoticeBanner` под bossbar-zone: icon/type label, wrapped message максимум 2 строки, progress не обязателен.
- Цвет packet-а нормализуется по контрасту; неизвестный цвет не должен сделать текст невидимым.
- Новое сообщение заменяет старое — это текущая семантика. Queue/stack только отдельным product+protocol/state решением.
- Уважать hideGui и открытый screen, как сейчас; решить отдельно, должен ли таймер продолжать истекать пока notice скрыт (сейчас продолжает).

### 6.15 Death screen

Доступны title/subtitle (до 128 символов), duration и флаг звука. Экран блокирует input, не паузит игру и сам закрывается.

- Сохранить full-screen transition и input lock.
- Wrap title/subtitle, ограничить 2/3 строки и safe margins; текущий 2× title может выйти за экран.
- Fade-in 350 ms допустим; добавить reduced-motion snap. Не добавлять ручной Skip без изменения игровых правил.
- `Screen` title/narration должен содержать реальный title/subtitle, а не `Component.empty()`.
- При замене существующего screen задокументировать навигационную семантику: сейчас после смерти всегда открывается `null`, предыдущий UI не восстанавливается.

### Общие состояния экранов

Каждый data-driven экран обязан иметь: initial/loading (если snapshot ещё не пришёл), ready, empty, pending action, stale/timeout, recoverable error и closed-by-server. Skeleton применяется только если неизвестна структура данных; при пустом подтверждённом списке показывается EmptyState. Ошибка не очищает последний валидный snapshot.

### Сводка data dependencies и критериев приёмки

| Экран | Data dependency | Keyboard/focus | Минимальный acceptance |
|---|---|---|---|
| Voting | mode list/counts/mask/selection/timer | Tab или ↑↓, Enter/Space | revote меняет тот же server vote; disabled объяснён; phase transition корректен |
| TeamSelect | 4 teams/slots/selection/timer | стрелки 2D, Tab, Enter | full недоступна; switch/auto-balance не сломаны; spectator не придуман |
| MapVoting | maps/counts/selection/timer/operator flags | стрелки по grid, Page/scroll | 0/1/16 карт без overflow; operator controls скрыты/disabled по праву |
| Tablet shell | client snapshot/routes/RTP state | rail arrows, Tab order, Escape по правилам | frame/viewport не клипуют controls scale 1–4; RTP packet прежний |
| Classes | definitions/unlocks/tiers/XP/cooldowns | grid arrows, action Enter | все 7 base actions и причины паритетны legacy |
| Shop | 8 entries/price/ownership/coins/mode | grid + safe confirm | двойной submit невозможен; баланс меняется только после sync |
| VIP | 8 exclusive flags | grid arrows | не отображается несуществующий срок VIP; выдача/выбор паритетны |
| Profile | local skin/name + six stats | Tab только для actions | derived K/D защищён от нуля; длинное имя не ломает header |
| Clans | clan/member/request snapshots + permissions | tabs/list arrows/actions | outsider/member/owner matrix; большие списки scroll; destructive safe |
| ClanCreate | name/tag/color + server limits | predictable field→color→actions | clipboard/editing vanilla; local и server errors различимы |
| Contract | selection/tracker snapshots до 16 | target list arrows, Enter | все 16 доступны; radar mapping паритетен; no per-frame list copy |
| Lives/Players HUD | match/lives counts | не интерактивен, narration summary | visibility parity; hotbar collision matrix; значения однозначны |
| Airdrop | message/color/type/duration | не интерактивен | packet limit wraps; hideGui/screen policy documented; no false direction/distance |
| Death | title/subtitle/duration/sound | input blocked как сейчас | 128-char wrap; closes on time; no vanilla conflict; reduced motion |

Для Airdrop направление/расстояние/progress-to-drop недоступны и не показываются; для Death оставшиеся жизни, respawn timer/action также не входят в packet. Их добавление возможно только отдельным optional protocol proposal, поэтому визуальные placeholders для них запрещены.

## 7. Gap-анализ компонентов

| Компонент | Есть сейчас | Требуемое действие |
|---|---|---|
| Theme tokens | Частично | Расширить семантическими токенами и lint-правилом |
| Layout primitives | Минимально | Добавить чистые row/grid/viewport/safe-area helpers |
| Buttons/cards/field | Есть | Исправить focus, pending, narration, hit zones |
| Dialog | Есть, небезопасен | P0 redesign focus/default action/lifecycle |
| Navigation rail | Ручной | Заменить widget-контейнером |
| Scrollable list/grid | Частично ручной | Общий focusable container + scrollbar |
| Tooltip | Vanilla/ручной | Единая фабрика, delayed hover, keyboard focus |
| Toast/inline feedback | Нет общего | Добавить host и семантические варианты |
| Async action state | Нет | Local request tracker, timeout, deduplication |
| Segmented/toggle control | Vanilla buttons | Нужен для operator mode flags; не использовать как скрытый checkbox |
| Dropdown | Нет | Пока не добавлять: ни один audited flow не требует длинного single-choice menu |
| RadarWidget | Встроен в screen | Выделить после parity tests, сохранив чистую математику mapping |
| Progress/countdown | Разрозненно | Общие normalized ProgressBar и tick-based Countdown |
| HUD layout manager | Нет | Добавить anchor/reservation API |
| Localization QA | Почти нет | Translation keys, псевдолокаль, overflow tests |
| Render scope | Частично | Явные blend/scissor/shader scopes и state tests |
| Accessibility harness | Нет | Focus/narration snapshot tests + manual matrix |

Общая модель действия:

```text
IDLE ──submit──> SUBMITTING ──matching snapshot──> ACKNOWLEDGED ──> IDLE
                       └────3 s────> TIMED_OUT ──late snapshot──> IDLE
                       └─screen close─> DETACHED (snapshot всё равно обновляется глобально)
```

`SUCCESS` — краткий presentation event после подтверждения, а не долговечное mutable состояние widget. `ERROR` используется только при явном результате; без result code показывается `TIMED_OUT/STALE`, а не выдуманная причина. `offline` означает отсутствие соединения/client level и блокирует C2S; `permission denied` выводится только из известного permission flag или явного ответа. `completed` применим лишь там, где snapshot действительно содержит завершение.

## 8. Варианты решений и рекомендации

### A. Полная замена текстур vs гибрид

- **A1 — всё программное:** проще токенизация, но теряется идентичность устройства и требуются новые рамки.
- **A2 — всё текстурное:** визуально совместимо, но плохо масштабируется и множит state-assets.
- **Рекомендация A3 — гибрид:** оставить device frames/icon art, программно рисовать внутренние поверхности, border/focus/state. Это минимальный asset-риск и лучший путь к accessibility.

### B. Один `TabletScreen` vs отдельные screens на страницы

- **B1 — монолит:** минимум навигационных изменений, но усложняет lifecycle и тестирование.
- **B2 — отдельный Minecraft `Screen` на route:** чище классы, но переинициализация и focus/history сложнее.
- **Рекомендация B3 — один shell + page controllers/views:** shell владеет viewport/navigation/toast/modal, страницы получают immutable snapshot и emit intents. Clan dialogs не должны быть вложенными legacy `Screen` внутри монолита.

### C. Map carousel vs grid

- Carousel экономит место, но скрывает варианты и ухудшает keyboard comparison.
- **Рекомендация:** adaptive scroll grid 3/2/1 columns. Carousel допустим только если позже появятся большие художественные preview и число кандидатов будет жёстко ограничено.

### D. Сетевой feedback

- Optimistic UI даёт мгновенную реакцию, но может соврать при серверном отказе.
- Полное ожидание sync честно, но кажется зависанием.
- **Рекомендация:** visual selection/pending сразу, но confirmed data меняется только от S2C snapshot. Повторный submit блокируется; timeout не равен failure. Packet ID не меняются.

### E. Modal stack

- Стек удобен для сложных flows, но повышает риск focus/state ошибок.
- **Рекомендация:** один modal slot в первом релизе. Nested flow преобразуется в последовательные шаги одного dialog controller.

### F. Локализация серверных сообщений

- Пересылка готовых русских строк проста, но клиент не может локализовать.
- Translation key + args лучше, но меняет packet/contracts.
- **Рекомендация:** UI chrome и локально известные причины перевести сейчас; серверные строки сохранить ради совместимости. Структурированные result codes — отдельное versioned protocol proposal.

## 9. Миграционный roadmap

### Этап 0 — Baseline и guardrails (1 PR)

- Снимки текущих экранов на 2–3 GUI scales и frame-time baseline.
- Исправление кодировки/ключей локализации UI foundation.
- Focus map и render-state contract document.
- Feature flag `tacticaltablet.new_ui` только для разработки, default off.

**Exit:** воспроизводимые golden screenshots, список interaction paths и отсутствие изменений gameplay.

### Этап 1 — Foundation hardening (2–3 PR)

- `UiFrameContext`, render scopes, semantic tokens/layout.
- Button/card/text field/dialog accessibility fixes.
- ResponsiveGrid/ScrollableList, tooltip/toast/inline status.

**Exit:** unit/component tests, render state восстанавливается, destructive dialog безопасен, keyboard-only demo проходит.

### Этап 2 — Вертикальный предматчевый срез (2 PR)

- Voting, TeamSelect, MapVoting на новой системе.
- Используются существующие packets/state; старые экраны остаются fallback под flag.

**Exit:** parity для mouse/keyboard, 1–16 карт без overflow, сетевой latency simulation, RU/EN scale matrix.

### Этап 3 — Tablet core (3–5 PR)

- Shell/navigation/resource cache.
- Classes/Shop/VIP/Profile.
- Dialog purchase/upgrade flows.

**Exit:** parity всех class actions/RTP, нет resource lookup и временных коллекций в steady-state render path.

### Этап 4 — Complex data screens (3–4 PR)

- Clans/ClanCreate.
- Contract tracker selection/radar optimization.

**Exit:** 16 targets, большие clan/member/request списки, focus restoration и owner permission matrix.

### Этап 5 — HUD и rollout (2 PR)

- HUD anchor manager, Lives/Players/Airdrop/Spectator/Death.
- Default-on flag, telemetry/logging только технических ошибок, удаление legacy после одного стабильного релиза.

**Exit:** overlay collision matrix, no visual regressions, legacy removal отдельным PR.

### Контроль объёма этапов

| Этап | Основные файлы/зоны | Зависимости | Ручная проверка | Допустимый diff |
|---|---|---|---|---|
| 0 | docs, lang JSON, dev config/test fixtures | нет | текущий tablet/RTP/fields/scissor, scale 1–4 | S/M, без production layout rewrite |
| 1 | `tablet/client/ui/**`, `GuiTextureRenderer`, tests | baseline | focus, narrator, nested modal/scissor, resize | M на PR; один primitive concern |
| 2 | `VotingScreen`, `TeamSelectScreen`, `MapVotingScreen`, shared voting views | этап 1 | полный предматчевый flow, operator/non-operator, latency | M/L; максимум один общий shell + 1–2 screens |
| 3 | `TabletScreen`, новые `screen/page/**`, client presenters | этап 1–2 | rail, RTP, reopen, resource reload | M/L; shell отдельно от page migration |
| 4 | page classes, clan dialogs/presenters, contract screen/state/radar | shell/list/dialog | permission matrix, 16 targets, FPS | M/L; clans и contract в разных PR |
| 5 | HUD overlays, toast/anchor manager, death screen | theme/render scopes | hotbar/bossbar/spectator/hideGui | M на overlay group; legacy removal отдельно |

Каждый PR должен оставлять сборку зелёной и иметь feature-flag/fallback там, где экран ещё не достиг полного паритета. XL diff не допускается: его необходимо разделить по foundation/shell/page или renderer/state.

## 10. Backlog P0–P3

Оценка относительная: S — локальное изменение; M — один компонент/экран с тестами; L — вертикальный срез нескольких тесно связанных классов; XL следует декомпозировать. Каждая задача — отдельный reviewable PR либо явно указанный вертикальный срез.

| Pri | Title | Outcome | Dependencies | Definition of Done | Risks | Size |
|---|---|---|---|---|---|---|
| P0 | Render state scopes | Blend/scissor/shader всегда восстановлены | нет | state tests + визуальный smoke | GL adapter testability | M |
| P0 | Single frame context | Один delta на визуальный кадр | render scopes | 30/60/lag tests, no global clock | Forge event boundary | M |
| P0 | Safe dialog lifecycle | Cancel default, stable focus restore | focus keys | destructive Enter test, wrap/scroll | parent re-init | M |
| P0 | Foundation localization | UI chrome в keys, UTF-8 verified | key naming convention | RU/EN load, no mojibake | большой объём literals | M |
| P0 | Resource/render cache | Нет resource manager lookup в card loop | reload listener | profiler/assertion + reload test | invalidation | M |
| P0 | Focusable navigation/grid | Keyboard/narration parity | layout helpers | complete focus map tests | custom container complexity | L |
| P0 | Voting vertical slice | Три предматчевых экрана на DS | все выше | gameplay parity + latency/scale matrix | phase races | L |
| P1 | Semantic tokens/layout | Нет magic UI colors/geometry в новых views | theme audit | token docs/tests | over-abstraction | M |
| P1 | Async action tracker | pending/dedupe/timeout без optimistic truth | snapshot identity | simulated 0/500/3000 ms | false timeout | M |
| P1 | Tooltip/toast/inline | Единая feedback grammar | localization | mouse+focus triggers, queue bounds | overlay collision | M |
| P1 | Tablet shell | Accessible rail и viewport | components | route/scroll/focus persistence | legacy coupling | L |
| P1 | Classes/Shop/VIP/Profile | Паритет tablet pages | shell | action matrix + long text | absent metadata | L |
| P1 | Clans/ClanCreate | Accessible large lists/forms | dialogs/list | permissions + validation matrix | stale memberships | L |
| P1 | Contract snapshot/perf | 16 целей без per-frame copies | profiler harness | allocation/FPS budget | radar shader state | M |
| P1 | HUD anchor manager | Overlay zones не пересекаются | viewport model | resolution/scale matrix | third-party HUD unknown | M |
| P2 | Airdrop/death modernization | Wrap/contrast/narration/reduced motion | HUD/theme | packet limit and sound tests | screen replacement semantics | M |
| P2 | Pseudolocalization harness | Overflow обнаруживается в CI/manual | localization | +35% strings screenshot pass | font differences | S |
| P2 | UI debug overlay | Bounds/focus/scissor видимы dev-only | frame context | hotkey/dev config; no prod cost | accidental enable | S |
| P2 | Optional map metadata RFC | Контракт previews/authors описан | product/content | RFC only, versioning specified | asset weight | S |
| P2 | Structured action results RFC | Локализуемые точные ошибки | networking review | backward-compatible version plan | packet compatibility | M |
| P3 | Rich class metadata | Descriptions/compare model | content + protocol/static registry | separate approved scope | balance misinformation | L |
| P3 | VIP/account metadata | Честный status banner | backend/protocol | explicit data owner | privacy/staleness | L |

## 11. Матрица тестирования

### Автоматические тесты

| Область | Проверки |
|---|---|
| Layout | 320×180, 640×360, ultrawide; 1/2/3 columns; safe margins; empty/1/16/100 items |
| Animation | 30/60/144 FPS, 500 ms stall clamp, reduced motion, zero duration |
| Interaction state | selected+focused, disabled+hover, pending, keyboard pressed feedback |
| Focus | Tab/Shift+Tab, arrows in grid, modal trap, focusKey restore, removed item fallback |
| Text | ellipsis, 2-line wrap, surrogate/unicode, RU/EN, pseudo +35%, empty string |
| Dialog | destructive Enter does not confirm, Escape/cancel, long body scroll, callback navigation |
| Async | ack before timeout, timeout then late ack, rejection snapshot, double-click dedupe |
| Render state | nested blend/scissor, exception path, shader restore, parent+modal rendering |
| Contract radar | coordinate mapping boundaries, zero radius guard, 16 targets, stable UUID color |
| Snapshot | immutable list identity between updates, replacement only on packet handle |

Существующие `AnimatedFloatTest`, `TacticalLayoutTest`, `ScrollableGridLayoutTest`, `ContractTrackerScreenTest` и texture inventory tests расширяются; архитектурные string-check tests не заменяют поведенческие проверки.

### Ручная матрица

- GUI scale: Auto и все доступные значения, минимум 320×180 scaled viewport.
- Язык: RU, EN, pseudo-long; проверить имена игроков/кланов/карт на packet limits.
- Input: mouse only, keyboard only, mixed; screen reader/narrator modes Minecraft.
- Сеть: normal, 500 ms, 3 s, lost/late state sync; многократный быстрый клик.
- Состояния: operator/non-operator, clan owner/member/outsider/pending, enough/not enough currency, locked/unlocked/max tier, game/not running, spectator.
- Данные: 0/1/max maps, clans, members, requests, contract targets.
- HUD: hotbar, bossbar, chat, scoreboard, spectator lock hint, multiple notices, hideGui, open screen.
- FPS: 30/60/144, resource reload, resize while screen open.
- Window: 1280×720, 1920×1080, 2560×1440, нестандартное узкое окно, fullscreen/windowed, Alt+Tab и resize в открытом UI.
- Lifecycle: reconnect, повторное открытие, закрытие во время pending, поздний/дублирующий ответ, clipboard/editing в clan fields.

### Architecture tests

- Common/server packages не импортируют `net.minecraft.client` и `tablet.client.ui`.
- Универсальные widgets не импортируют `PacketHandler` и packet classes; intent adapter находится на screen/client boundary.
- В мигрированном screen запрещены новые raw `Button`/ручной hit-test, кроме явно allowlisted radar geometry.
- Production dependency set не изменён.
- Render methods не вызывают resource manager/file I/O и не создают `List.copyOf`/`String.format` в allowlisted hot paths.
- Packet registration order/IDs и encode/decode payload не изменены UI-only PR.

### Visual regression checklist

Зафиксировать deterministic сцены: Voting 4 modes/one disabled; Team 2 full/one selected; Map 1 и 16 карт; Tablet для каждой route; Shop недостаточно монет и pending dialog; Clan outsider/owner + long names; Contract 0/16 targets и active radar; HUD normal/danger/narrow; Airdrop max text; Death max title/subtitle. Для каждой сцены — RU/EN, GUI scale 1 и 4, focused/selected/disabled/pending. Скриншоты сравниваются с утверждённым baseline с допуском только на намеренно изменённые области; shader/render-state ошибки дополнительно проверяются последовательным открытием разных overlays.

### Performance budgets

- Steady-state render: 0 resource-manager lookups, 0 `List.copyOf`, 0 `String.format` в hot path.
- Не создавать widgets/components каждый кадр; rebuild только при init, resize, resource reload или snapshot version change.
- UI CPU budget: ориентир ≤ 1 ms/frame на типичном экране и ≤ 2 ms для radar при 16 целях на целевой машине; подтвердить profiler-ом, не обещать до измерения.
- Scissor/blend scopes группируют операции; не выполнять GL state query на каждую иконку.

## 12. Risk register

| Риск | Вероятность/влияние | Митигация | Сигнал |
|---|---|---|---|
| Изменение gameplay при «косметическом» переносе | M/H | intent adapters вызывают те же packets; parity tests | различие packet trace |
| Packet backward compatibility | L/H | не менять IDs; extensions отдельной версией | disconnect/decode error |
| Render state leak | M/H | scope API + exception/nesting tests | артефакты следующих overlays |
| Focus regression после rebuild | H/M | stable focusKey, deterministic fallback | focus уходит в null/первый элемент |
| Длинный перевод ломает layout | H/M | pseudo locale, wrap/tooltip, scale matrix | clip/overlap screenshot |
| Цветовая недоступность | M/M | text/icon secondary signal, contrast checks | различие только оттенком |
| Server latency вызывает дубли | M/H | pending dedupe, request cooldown, server remains authority | повторные C2S traces |
| Stale snapshot выглядит успехом | M/H | confirmed vs pending distinction | toast success до S2C |
| Radar просаживает FPS | M/M | cache geometry, allocation profiling | >2 ms UI time |
| Asset scope разрастается | M/M | hybrid strategy, placeholder policy | блокировка PR на art |
| Кодировка RU локали | H/M | проверить bytes/UTF-8, CI JSON decode | mojibake в игре |
| Конфликт со сторонним HUD | M/M | anchor reservations/config offsets | overlap reports |
| Legacy и new UI расходятся | M/M | короткий dual-run, shared presenters, removal deadline | fixes только в одном пути |

## 13. План ассетов

### Не нужны для начала миграции

- Новые полноразмерные фоны экранов и отдельные PNG для hover/pressed/selected/disabled.
- Внешний шрифт, новый logo, scanline/noise mask и декоративные накладки корпуса.
- Map preview, role icons и новые class illustrations: отсутствие этих ресурсов не блокирует foundation/voting/shell.

### Переиспользовать

- `tablet.png`, `tablet_epic.png`, `tablet_legend.png` как device frames.
- `contract_gui.png` как контрактную внешнюю рамку.
- class icons и `class_fallback.png`.
- `heart.png`, `players_count.png` после проверки контраста/масштабирования.
- Nav/button textures временно как fallback во время миграции, затем оценить удаление.

### Нужны позднее

- Монохромный 16×16 atlas: back, close, search, clear, info, warning, lock, check, coins, members, crown/owner, radar target, timer, refresh.
- Map placeholder patterns можно рисовать программно; реальные preview понадобятся только после контентного pipeline и metadata RFC.
- Focus/selected не требуют отдельных bitmap: рисуются программно для всех тем.

### Optional polish

- Логотип TacticalTablet для splash/empty state.
- Слабая tileable noise/scanline mask с настройкой интенсивности и возможностью отключения.
- Миниатюры карт, декоративные map emblems, дополнительные role/class glyphs.
- Новые варианты внешнего корпуса только как appearance tier, не как состояние кнопки.
- 9-slice декоративные панели допустимы лишь если программный cut-corner не даёт нужной pixel-art формы.

### Технические правила

- Pixel-perfect размеры и nearest filtering; UV-region constants централизованы.
- Альфа без цветных fringe, 1×/2× визуальная проверка.
- Resource location проверяется inventory test; missing asset всегда имеет fallback.
- Asset reload инвалидирует кэши, но не пересоздаёт серверные/игровые состояния.
- Название файла lower_snake_case; атлас документирует координаты и semantic name.

## 14. Definition of Done полной переработки

Переработка считается завершённой, когда одновременно выполнено следующее:

1. Все перечисленные экраны используют общие theme/layout/component/render-scope APIs; ручной hit-testing отсутствует, кроме математической области radar.
2. Все действия вызывают существующую авторизованную серверную логику; packet trace и игровые результаты совпадают с baseline.
3. Mouse, keyboard и narration имеют полный паритет; focus всегда видим, modal безопасно восстанавливает его.
4. RU/EN/pseudo-long проходят поддерживаемую resolution/GUI-scale матрицу без непредусмотренного clip/overlap.
5. Все состояния ready/empty/pending/timeout/error определены; клиент не показывает успех до серверного подтверждения.
6. Render state восстанавливается после каждого screen/overlay, включая exception path; нет бесхозного scissor/blend.
7. Steady-state rendering удовлетворяет бюджету и не делает resource lookup, list copy, widget creation или тяжёлое форматирование каждый кадр.
8. Цвет не является единственным сигналом; контраст и reduced motion проверены.
9. Legacy UI удалён только после стабильного релиза новой реализации и отдельного review.
10. Пройдены unit/component tests, ручная матрица, `git diff --check` и полный `gradlew clean build`; результаты зафиксированы в PR.
11. Документация translation keys, tokens, focus maps, asset atlas и optional protocol RFC актуальна.
12. Нет новых production-зависимостей.

## 15. Точный следующий PR

**Название:** `UI foundation: deterministic frame context and safe render scopes`

**Почему он первый:** любая визуальная миграция сейчас будет наследовать глобальную ошибку delta-time и неявную семантику blend/scissor. Этот PR мал, не меняет gameplay, создаёт проверяемый фундамент и не зависит от новых ассетов.

**Перед началом PR:** выполнить и приложить к issue/PR текущий in-game smoke baseline: открыть tablet на scale 1–4, пройти rail/RTP, ввести/очистить clan name/tag, проверить Tab/Enter/Escape, обрезание viewport/scissor, resize/fullscreen и последовательное открытие tablet → contract → voting. Найденные блокирующие дефекты foundation входят в этот PR только если относятся к frame/render state; остальные фиксируются отдельными задачами.

**Планируемые файлы:**

- изменить `src/main/java/com/makar/tacticaltablet/tablet/client/ui/TacticalUi.java`;
- добавить `src/main/java/com/makar/tacticaltablet/tablet/client/ui/UiFrameContext.java`;
- изменить `src/main/java/com/makar/tacticaltablet/tablet/client/GuiTextureRenderer.java`;
- добавить `src/main/java/com/makar/tacticaltablet/tablet/client/ui/render/AlphaBlendScope.java` и `ScissorScope.java` либо один минимальный `RenderScopes.java` после spike;
- изменить `TacticalButton.java`, `TacticalIconButton.java`, `TacticalCard.java` только для context/state integration;
- добавить/расширить соответствующие `src/test/java/.../client/ui/**` tests;
- добавить `docs/ui-render-contract.md` и baseline checklist.

**Scope:**

1. Добавить immutable `UiFrameContext` с одним `deltaSeconds` на top-level screen/overlay render invocation и reduced-motion flag; убрать обновление глобального времени из вложенных компонентов, сохранив временный deprecated adapter для legacy callers.
2. Добавить `AlphaBlendScope`/`ScissorScope` (или эквивалентный `AutoCloseable` API) с гарантированным восстановлением захваченного состояния и поддержкой вложенности.
3. Перевести только `TacticalButton`, `TacticalIconButton`, `TacticalCard` и минимальный showcase/test screen path на новый context/scope. Не мигрировать production screens целиком.
4. Исправить приоритет visual state: focus-ring рисуется поверх selected; добавить keyboard pressed feedback.
5. Добавить тесты: один delta для siblings, 30/60 FPS, stall clamp, nested/exception render scope, selected+focused rendering policy.
6. Добавить короткий `docs/ui-render-contract.md`: владелец frame context, порядок слоёв, владение blend/scissor, запрет GL query/per-icon lambda в loop.

**Вне scope:** redesign диалога, локализация всех строк, новые packets, изменение экранных layout, новые ассеты, перенос Voting/Tablet/Contract/HUD.

**Acceptance criteria:**

- два компонента в одном кадре получают идентичный delta;
- вложенный alpha/scissor scope восстанавливает внешний state и при исключении;
- focused+selected визуально различимы;
- mouse/keyboard activation вызывает прежний callback ровно один раз;
- существующие UI tests и полный build проходят;
- `git diff --check` чист;
- packet classes, server managers и gameplay code не изменены.

**Обязательный ручной сценарий после изменения:** повторить baseline на scale 1–4; удержать мышь на RTP/class card и проверить плавность; выбрать карточку клавиатурой и убедиться, что focus виден поверх selected; открыть/закрыть tablet, contract и voting в разном порядке; вызвать resource reload; проверить, что последующий vanilla HUD/tooltip не теряет alpha/blend. В PR приложить результаты, даже если визуальное сравнение выявило известный legacy-дефект.

Следующий после него PR: безопасный `TacticalDialog` + stable focus keys. Только затем — vertical slice предматчевых экранов.
