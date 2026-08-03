# Tablet core migration

## Accessible shell slice

- `TabletNavigationRail` и `ScrollableActionGrid` создают vanilla-compatible `Button` widgets.
- Виджеты регистрируются через `Screen.addWidget`, но рисуются владельцем слоя, чтобы сохранить clipping внутреннего viewport и прежние ресурспаковые текстуры.
- Tab использует стандартный focus order Minecraft.
- Стрелки перемещают фокус внутри navigation rail или двухколоночной сетки действий.
- При переходе на невидимую строку grid автоматически прокручивается до сфокусированной карточки.
- Enter/Space активируют тот же callback, что и щелчок мыши; серверные проверки и пакеты не изменены.
- Narration карточки содержит название действия и актуальную причину доступности/недоступности.

Следующий срез Tablet Core: отделение page state/controller от монолитного `TabletScreen` и перевод Profile/Clan data views на общие viewport primitives.

## Page state and data viewport slice

- `TabletPageState` теперь владеет текущей страницей, выбранным кланом и независимыми scroll offsets.
- `TabletDataViewport` централизует clamping видимого диапазона и геометрию scrollbar.
- Profile получает immutable `TabletProfileView.Model` и больше не рисуется набором специальных методов внутри `TabletScreen`.
- Clan list, members, pending requests и server info используют общее viewport-состояние; clipping выполняется через `ScissorScope`.

## Завершение complex data slice

- Purchase/unlock/upgrade и clan actions используют общий `TacticalDialog`; доступность подтверждения
  пересчитывается по актуальному балансу клиента.
- `ClanPagePolicy` централизует outsider/member/owner permission matrix, а `ClanCreateInputPolicy` выполняет
  локальную проверку name/tag/color/лимита/стоимости без подмены серверной авторизации.
- Contract state хранит immutable snapshot один раз на packet update. Selection viewport предоставляет все
  разрешённые packet-ом 16 целей, radar mapping ограничен видимой областью, scissor и shader восстанавливаются.
- Resource presence кешируется между reload-событиями; отсутствие необязательного ресурса использует fallback.

HUD и правила финальной ручной приёмки описаны в [ui-hud-rollout.md](ui-hud-rollout.md).
