package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.SetGameMode;
import com.makar.tacticaltablet.tablet.client.MapVoteClientState;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.VoteMapPacket;
import com.makar.tacticaltablet.tablet.net.VoteModePacket;
import com.makar.tacticaltablet.tablet.net.VoteSetModePacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Retained voting states of PREPARATION, not additional physical pages or a separate poller. */
final class TabletVotingPage {
    static final int SET = 10, FORMAT = 20, MAP = 100;
    static final SetGameMode[] SET_MODES = {SetGameMode.CASUAL, SetGameMode.CHAOS, SetGameMode.COMPETITIVE};
    static final MatchMode[] FORMATS = {MatchMode.SOLO, MatchMode.DUO, MatchMode.TRIO, MatchMode.SQUADS};
    private static final int MAP_ROW_HEIGHT = 66;
    private static final long TIMEOUT_MS = 8_000, CLICK_INTERVAL_MS = 250;
    record MapRow(String name, ResourceLocation preview) { }

    final int height;
    final TabletPreparationPage.Rect list;
    final TabletPreparationPage.Rect[] setButtons = new TabletPreparationPage.Rect[SET_MODES.length];
    final TabletPreparationPage.Rect[] formatButtons = new TabletPreparationPage.Rect[FORMATS.length];
    List<MapRow> maps = List.of();
    int offset, capacity, hover = -1, pressed = -1;
    MatchPhase phase = MatchPhase.WAITING;
    String feedback = "";
    boolean error;
    private boolean interactive, dirty = true, sessionConfirmed, mapReady, refreshRequested;
    private long expectedMapSnapshot, expectedPhaseRevision;
    private long mapRevision = -1, matchRevision = -1, mapRound = -1;
    private long deadline, nextClick, pendingRevision;
    private String pendingMap;
    private SetGameMode pendingSet;
    private MatchMode pendingFormat;

    TabletVotingPage(int height) {
        this.height = height;
        list = new TabletPreparationPage.Rect(MARGIN, 64, LOGICAL_WIDTH - 2 * MARGIN, height - 176);
        capacity = Math.max(1, list.height() / MAP_ROW_HEIGHT);
        int setWidth = (LOGICAL_WIDTH - 2 * MARGIN - 2 * GAP) / 3;
        for (int i = 0; i < setButtons.length; i++)
            setButtons[i] = new TabletPreparationPage.Rect(MARGIN + i * (setWidth + GAP), height - 87, setWidth, 44);
        int formatWidth = (LOGICAL_WIDTH - 2 * MARGIN - GAP) / 2;
        int formatHeight = (height - 134 - GAP) / 2;
        for (int i = 0; i < formatButtons.length; i++)
            formatButtons[i] = new TabletPreparationPage.Rect(MARGIN + i % 2 * (formatWidth + GAP),
                    64 + i / 2 * (formatHeight + GAP), formatWidth, formatHeight);
    }

    void phaseChanged(MatchPhase next) {
        phase = next;
        sessionConfirmed = false; mapReady = false; expectedMapSnapshot = 0;
        maps = List.of(); offset = 0; mapRound = -1;
        refreshRequested = false; nextClick = 0;
        clearPending(); feedback = ""; error = false;
        cancelInput(); dirty = true;
    }

    void acceptPreparation(long requestId) {
        sessionConfirmed = true;
        expectedMapSnapshot = requestId;
        expectedPhaseRevision = TabletClientState.getPhaseRevision();
        if (phase == MatchPhase.VOTING) recovered();
        dirty = true;
    }

    void acceptMapSnapshot(long requestId) {
        if (requestId <= 0 || requestId != expectedMapSnapshot || !sessionConfirmed
                || phase != MatchPhase.MAP_VOTING || expectedPhaseRevision != TabletClientState.getPhaseRevision()
                || !MapVoteClientState.isActive()) return;
        mapReady = true;
        mapRound = MapVoteClientState.getRoundRevision();
        recovered();
        updateMaps(); dirty = true;
    }

    void tick() {
        if (nextClick > 0 && Util.getMillis() >= nextClick) { nextClick = 0; dirty = true; }
        long currentMapRevision = MapVoteClientState.getRevision();
        long currentMatchRevision = TabletClientState.getMatchSetupRevision();
        if (mapRevision != currentMapRevision || matchRevision != currentMatchRevision) {
            mapRevision = currentMapRevision; matchRevision = currentMatchRevision; dirty = true;
            if (phase == MatchPhase.MAP_VOTING) {
                if (!MapVoteClientState.isActive() || mapRound != MapVoteClientState.getRoundRevision()) {
                    mapReady = false; cancelInput();
                }
                if (mapReady) updateMaps();
            }
        }
        if (pending()) {
            boolean confirmed = pendingMap != null && mapReady && mapRevision > pendingRevision
                    && pendingMap.equals(MapVoteClientState.getSelectedMap())
                    || pendingSet != null && mapReady && mapRevision > pendingRevision
                    && pendingSet == MapVoteClientState.getSelectedMode()
                    || pendingFormat != null && sessionConfirmed && matchRevision > pendingRevision
                    && pendingFormat == TabletClientState.getSelectedVote();
            if (confirmed) {
                clearPending(); feedback = "Сервер подтвердил ваш выбор."; error = false; dirty = true;
            } else if (Util.getMillis() >= deadline) {
                clearPending(); mapReady = false; sessionConfirmed = false; refreshRequested = true;
                feedback = "Выбор не подтверждён. Обновляем данные; затем можно повторить.";
                error = true; dirty = true;
            }
        }
    }

    boolean takeRefreshRequest() {
        boolean value = refreshRequested; refreshRequested = false; return value;
    }

    private void recovered() {
        if (error) {
            error = false;
            feedback = "Данные обновлены. Проверьте подтверждённый выбор; можно голосовать снова.";
        }
    }

    private void updateMaps() {
        List<String> names = MapVoteClientState.getMaps();
        if (maps.stream().map(MapRow::name).toList().equals(names)) return;
        maps = names.stream().map(name -> {
            var candidate = new ResourceLocation("tacticaltablet", "textures/gui/maps/"
                    + String.format(Locale.ROOT, "map_%08x.png", name.hashCode()));
            return new MapRow(name, Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()
                    ? candidate : null);
        }).toList();
        offset = Math.min(offset, Math.max(0, maps.size() - capacity));
        cancelInput(); dirty = true;
    }

    TabletPreparationPage.Rect mapBounds(int index) {
        return new TabletPreparationPage.Rect(list.x(), list.y() + (index - offset) * MAP_ROW_HEIGHT,
                list.width() - 9, MAP_ROW_HEIGHT - 4);
    }

    boolean ready() {
        return sessionConfirmed && (phase != MatchPhase.MAP_VOTING || mapReady && MapVoteClientState.isActive());
    }

    int secondsLeft() {
        return phase == MatchPhase.MAP_VOTING ? MapVoteClientState.getSecondsLeft()
                : phase == MatchPhase.TEAM_SELECT ? TabletClientState.getTeamSelectTimeLeft()
                : TabletClientState.getVoteTimeLeft();
    }

    boolean enabled(int id) {
        if (!interactive || !ready() || secondsLeft() <= 0 || pending()
                || Minecraft.getInstance().getConnection() == null || Util.getMillis() < nextClick) return false;
        if (phase == MatchPhase.MAP_VOTING) {
            if (id >= MAP) return id - MAP < maps.size();
            if (id >= SET && id < SET + SET_MODES.length) {
                SetGameMode mode = SET_MODES[id - SET];
                return MapVoteClientState.areOrdinaryModesEnabled() && MapVoteClientState.isModeAvailable(mode);
            }
        }
        return phase == MatchPhase.VOTING && id >= FORMAT && id < FORMAT + FORMATS.length
                && TabletClientState.isVoteModeAvailable(FORMATS[id - FORMAT]);
    }

    String setReason(SetGameMode mode) {
        if (!MapVoteClientState.areOrdinaryModesEnabled()) return "Режим зафиксирован сервером";
        return MapVoteClientState.isModeAvailable(mode) ? "" : "Ограничен правилами сервера";
    }

    int hit(TabletDisplayProjection.Point point) {
        if (point == null || !ready()) return -1;
        if (phase == MatchPhase.MAP_VOTING) {
            if (list.contains(point.x(), point.y())) {
                for (int i = offset; i < Math.min(maps.size(), offset + capacity); i++)
                    if (mapBounds(i).contains(point.x(), point.y())) return MAP + i;
            }
            for (int i = 0; i < setButtons.length; i++)
                if (setButtons[i].contains(point.x(), point.y())) return SET + i;
        } else if (phase == MatchPhase.VOTING) {
            for (int i = 0; i < formatButtons.length; i++)
                if (formatButtons[i].contains(point.x(), point.y())) return FORMAT + i;
        }
        return -1;
    }

    void interactive(boolean value) {
        if (interactive != value) { interactive = value; cancelInput(); dirty = true; }
    }
    void hover(TabletDisplayProjection.Point point) {
        int next = interactive ? hit(point) : -1;
        if (hover != next) { hover = next; dirty = true; }
    }
    void press(TabletDisplayProjection.Point point) { pressed = interactive ? hit(point) : -1; dirty = true; }
    void release(TabletDisplayProjection.Point point) {
        int id = pressed; pressed = -1; dirty = true;
        if (id < 0 || id != hit(point) || !interactive) return;
        if (!enabled(id)) {
            if (!pending()) {
                feedback = secondsLeft() <= 0 ? "Приём голосов завершён. Ожидаем решение сервера."
                        : Util.getMillis() < nextClick ? "Подождите перед следующим голосом."
                        : phase == MatchPhase.MAP_VOTING && id < MAP ? setReason(SET_MODES[id - SET])
                        : "Этот формат сейчас недоступен для участников матча.";
                error = false;
            }
            return;
        }
        if (id >= MAP) {
            String name = maps.get(id - MAP).name();
            if (name.equals(MapVoteClientState.getSelectedMap())) return;
            pendingMap = name; pendingRevision = mapRevision;
            PacketHandler.sendToServer(new VoteMapPacket(name));
        } else if (phase == MatchPhase.MAP_VOTING) {
            SetGameMode mode = SET_MODES[id - SET];
            if (mode == MapVoteClientState.getSelectedMode()) return;
            pendingSet = mode; pendingRevision = mapRevision;
            PacketHandler.sendToServer(new VoteSetModePacket(mode));
        } else {
            MatchMode mode = FORMATS[id - FORMAT];
            if (mode == TabletClientState.getSelectedVote()) return;
            pendingFormat = mode; pendingRevision = matchRevision;
            PacketHandler.sendToServer(new VoteModePacket(mode));
        }
        deadline = Util.getMillis() + TIMEOUT_MS; nextClick = Util.getMillis() + CLICK_INTERVAL_MS;
        feedback = "Ожидаем подтверждение выбора сервером…"; error = false;
    }

    void scroll(TabletDisplayProjection.Point point, double delta) {
        if (!interactive || !ready() || phase != MatchPhase.MAP_VOTING
                || point == null || !list.contains(point.x(), point.y())) return;
        int next = Math.max(0, Math.min(Math.max(0, maps.size() - capacity), offset - (int) Math.signum(delta)));
        if (offset != next) { offset = next; cancelInput(); dirty = true; }
    }
    boolean pending() { return pendingMap != null || pendingSet != null || pendingFormat != null; }
    private void clearPending() { pendingMap = null; pendingSet = null; pendingFormat = null; }
    void cancelPress() { if (pressed != -1) { pressed = -1; dirty = true; } }
    void cancelInput() { cancelPress(); hover = -1; dirty = true; }
    boolean dirty() { return dirty; }
    void rendered() { dirty = false; }
}
