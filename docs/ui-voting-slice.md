# Tactical UI voting vertical slice

Все экраны голосования можно закрыть клавишей Esc. Переход на map vote остаётся автоматическим только при смене фазы с открытого предыдущего экрана голосования; закрытый пользователем UI не переоткрывается каждый tick.

Параметры необязательных изображений карт описаны в [ui-map-previews.md](ui-map-previews.md).

Мигрированные экраны: `VotingScreen`, `TeamSelectScreen`, `MapVotingScreen`.

## Сохранённые контракты

- Переходы фаз по `TabletClientState.getMatchPhase()` и `MapVoteClientState.isActive()` не изменены.
- Отправляются прежние `VoteModePacket`, `JoinTeamPacket`, `VoteMapPacket`, `SetCompetitivePacket` и `SetClanWarPacket`.
- Escape закрывает UI голосования; игра при открытом экране не ставится на паузу.
- Выбранное значение считается подтверждённым только после обновления client state сервером.

## Общий UI pipeline

`TacticalPhaseScreen` открывает один frame/blend scope, рисует tactical backdrop и передаёт экрану phase content. Все варианты являются `TacticalButton`, поэтому сохраняют vanilla focus, narration, Enter/Space и mouse semantics. Стрелки перемещают фокус по двумерной сетке.

## Map viewport

Количество колонок вычисляется из доступной ширины с максимумом три. Все карты зарегистрированы как widgets, но off-screen строки имеют `visible=false`; видимые строки рисуются внутри `ScissorScope`. Колесо меняет `scrollRow`, а стрелочная навигация вызывает `reveal(index)` до переноса фокуса. Это поддерживает текущий packet limit и не требует map preview metadata.

## Pending model

После C2S submit повторное действие того же типа блокируется. Pending снимается при совпадающем server snapshot либо через 60 client ticks с нейтральным timeout-сообщением. Timeout не объявляет серверный отказ и не меняет подтверждённый выбор.

## Manual checklist

- Voting: доступные/недоступные режимы, повторное голосование, Tab и стрелки.
- Team: выбранная и заполненная команда, имена/`+N`, автобаланс и переход фазы.
- Maps: 0/1/3/4/16 кандидатов, wheel и keyboard reveal, длинное имя, operator/non-operator toggles.
- GUI scale 1–4, 1280×720 и узкое окно; RU/EN; задержка ответа более трёх секунд.
