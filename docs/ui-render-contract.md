# Tactical UI render contract

Этот документ фиксирует минимальные правила render foundation. Он дополняет `tactical-ui-ux-redesign-plan.md` и применяется ко всем новым или мигрируемым tactical screens.

## Frame ownership

- Каждый top-level `Screen.render` создаёт собственный `UiFrameClock` один раз на экземпляр экрана.
- В начале render открывается `TacticalUi.openFrame(frameClock.nextFrame(Util.getMillis(), reducedMotion))`.
- Scope охватывает фон, дочерние widgets, tooltip и modal content и всегда закрывается через try-with-resources.
- Вложенный renderer, например parent под dialog, может открыть свой scope, но `TacticalUi` сохранит context внешнего top-level кадра. Все siblings получают один и тот же delta.
- Widget читает только `TacticalUi.currentFrame()`. Новый код не вызывает deprecated `beginFrame()` или `frameDeltaSeconds()`.
- Delta ограничен 100 ms. При reduced motion компонент snap-ит анимацию к target; hitbox не анимируется.

## Render state ownership

- Код, который меняет blend или scissor, обязан владеть lexical scope и закрыть его при normal return и exception.
- Группа текстурных blit-вызовов открывает один `GuiTextureRenderer.openAlphaBlend`. Внутренние blit-вызовы переиспользуют scope и не выполняют GL query на каждую иконку.
- Одиночный legacy blit без внешнего scope безопасен: он временно захватывает и восстанавливает blend state. Это fallback, а не рекомендуемый hot path.
- Scissor открывается через `ScissorScope.open`. Вложенность использует стек `GuiGraphics`; прямой глобальный `RenderSystem.disableScissor()` в `removed()` запрещён.
- Цвет `GuiGraphics` после texture blit возвращается к `(1, 1, 1, 1)`.

## Layer order

1. `renderBackground`/world dim;
2. device frame;
3. inner surface and decoration;
4. page viewport внутри scissor;
5. registered widgets;
6. floating feedback;
7. tooltip;
8. modal overlay/dialog;
9. critical notice.

`super.render` вызывается ровно в том слое, где должны появиться зарегистрированные widgets. Tooltip и modal не помещаются внутрь page scissor. Parent screen под modal получает неинтерактивные mouse coordinates.

## Visual control state

`ControlVisualState` хранит независимые флаги enabled, hovered, focused, pressed и selected. Selected не заменяет focused: focus ring рисуется последним. Mouse и keyboard activation используют один callback; pressed feedback не меняет bounds.

## Modal focus and navigation

- Интерактивный компонент может реализовать `FocusKeyProvider`; key должен быть стабильным в пределах route и не зависеть от экранных координат.
- Dialog захватывает focus key и индекс fallback до открытия, а восстанавливает уже после повторного `parent.init`.
- Danger dialog всегда начинает с Cancel. Enter/Space подтверждает danger action только после явного перевода фокуса на confirm.
- Callback может сам открыть другой screen; dialog возвращает parent только если после callback активным всё ещё является сам dialog.
- Длинный body ограничен собственным scissor viewport и прокручивается, не сдвигая actions за safe bounds.

## Review checklist

- Нет resource manager/file I/O/widget creation в steady-state render loop.
- Нет несбалансированного blend/scissor/shader state.
- Нет нового глобального animation clock.
- Нет raw ARGB вне theme или asset-specific renderer.
- Нет UI-компонента, импортирующего packet ID/handler.
- `git diff --check`, релевантные tests и `gradlew clean build` успешны.
