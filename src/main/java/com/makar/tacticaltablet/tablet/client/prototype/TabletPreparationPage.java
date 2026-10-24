package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.tablet.ClassDefinition;
import com.makar.tacticaltablet.tablet.ClassDefinitions;
import com.makar.tacticaltablet.tablet.client.ClassIconResolver;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletPacket;
import com.makar.tacticaltablet.tablet.preparation.PreparationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Retained page behavior and data. The view/theme own all drawing. */
public final class TabletPreparationPage {
    static final int REFRESH = 1, SELECT = 2, DEPLOY = 3, CONFIRM = 4, CANCEL = 5, ROW = 100;
    private static final long TIMEOUT_MS = 10_000, POLL_MS = 2_000;
    private static long sequence;
    record Rect(int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    static final class Row {
        final ClassDefinition definition;
        final ResourceLocation icon;
        final String name;
        String reason = "Загрузка состояния…";
        String shortReason = reason;
        String badge = "";
        Rect bounds;
        Row(ClassDefinition definition) {
            this.definition = definition;
            var mc = Minecraft.getInstance();
            icon = ClassIconResolver.resolve(definition, id -> mc.getResourceManager().getResource(id).isPresent());
            name = definition.name().getString();
        }
    }
    final Row[] rows;
    final Rect[] buttons = new Rect[6];
    final int height;
    Rect list, modal;
    int offset, capacity, focused, hover = -1, pressed = -1;
    int confirmation = -1;
    String confirmedClass = "", matchStatus = "", selectionStatus = "Класс не выбран", rtpReason = "Загрузка состояния…";
    String feedback = "Запрашиваем актуальное состояние сервера…";
    List<FormattedCharSequence> feedbackLines = List.of();
    boolean ready, awaiting, error, rtpUsed, rtpPending;
    private boolean interactive;
    private boolean refreshNotice;
    private boolean synchronizationFailed;
    private boolean dirty = true;
    private int pendingAction = -1;
    private long requestId, deadline, nextPoll;

    TabletPreparationPage(TabletPrototypeModel.DisplaySurface surface) {
        height = (int) TabletTerminalTheme.logicalHeight(surface);
        var definitions = ClassDefinitions.all();
        rows = new Row[definitions.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = new Row(definitions.get(i));
        layout();
        message(feedback, false);
        request(-1, true);
    }

    public static void acceptSnapshot(PreparationSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof TabletPrototypeScreen screen && screen.shell() != null)
            screen.shell().acceptSnapshot(snapshot);
    }

    boolean accept(PreparationSnapshot snapshot) {
        if (snapshot.requestId() != requestId) return false; // Ignore late replies from previous requests/sessions.
        synchronizationFailed = false;
        boolean firstSnapshot = !ready;
        ready = true;
        confirmedClass = snapshot.selectedClass(); rtpUsed = snapshot.rtpUsed(); rtpPending = snapshot.rtpPending();
        rtpReason = snapshot.rtpReason();
        for (Row row : rows) {
            row.reason = "Сервер не передал состояние класса.";
            for (var availability : snapshot.classes()) if (availability.actionId() == row.definition.actionId()) {
                row.reason = availability.reason(); break;
            }
            row.shortReason = Minecraft.getInstance().font.plainSubstrByWidth(
                    row.reason.isEmpty() ? "Доступен для выбора" : row.reason, 480);
            row.badge = row.definition.classKey().equals(confirmedClass) ? "ПОЛУЧЕН" : "";
        }
        updateMatchStatus();
        if (snapshot.rejected()) {
            awaiting = false;
            message(snapshot.message(), true);
        } else if (awaiting && pendingAction == 7) {
            if (rtpUsed) { awaiting = false; message("Сервер подтвердил вступление в бой.", false); }
            else if (rtpPending) message("Сервер ищет безопасную точку. Ожидайте подтверждения…", false);
            else { awaiting = false; message("RTP не подтверждён. " + rtpReason, true); }
        } else if (awaiting && pendingAction >= 0) {
            awaiting = false;
            ClassDefinition selected = ClassDefinitions.byActionId(pendingAction).orElseThrow();
            message(selected.classKey().equals(confirmedClass)
                    ? "Сервер подтвердил класс: " + selected.name().getString() : "Сервер не подтвердил выбор класса.",
                    !selected.classKey().equals(confirmedClass));
        } else {
            awaiting = false;
            if (firstSnapshot || refreshNotice)
                message("Состояние синхронизировано. Выберите класс и подтвердите получение набора.", false);
        }
        refreshNotice = false;
        pendingAction = awaiting ? pendingAction : -1;
        dirty = true;
        return true;
    }

    void tick() {
        long now = net.minecraft.Util.getMillis();
        updateMatchStatus();
        if (awaiting && now >= deadline) {
            awaiting = false; pendingAction = -1;
            message("Сервер не ответил вовремя. Обновите данные перед повторной попыткой.", true);
            ready = false; nextPoll = now + POLL_MS;
            synchronizationFailed = true;
        }
        if (now >= nextPoll) {
            if (!awaiting) request(-1, true);
            else if (pendingAction == 7 && rtpPending) {
                PacketHandler.sendToServer(new TabletPacket(-1, requestId));
                nextPoll = now + POLL_MS;
            }
        }
    }

    private void request(int action, boolean newRequest) {
        if (Minecraft.getInstance().getConnection() == null) {
            synchronizationFailed = true; awaiting = false; ready = false;
            nextPoll = net.minecraft.Util.getMillis() + POLL_MS;
            message("Нет соединения с сервером.", true); return;
        }
        long now = net.minecraft.Util.getMillis();
        if (newRequest) requestId = ++sequence;
        pendingAction = action; awaiting = true; deadline = now + TIMEOUT_MS; nextPoll = now + POLL_MS;
        PacketHandler.sendToServer(new TabletPacket(action, requestId));
        if (action >= 0) message(action == 7 ? "Запрос вступления отправлен…" : "Ожидание выдачи набора сервером…", false);
        dirty = true;
    }

    private void updateMatchStatus() {
        String value = switch (TabletClientState.getMatchPhase()) {
            case WAITING -> "Ожидание матча";
            case VOTING -> "Выбор режима";
            case TEAM_SELECT -> "Выбор команд";
            case MAP_VOTING -> "Выбор карты";
            case STARTING -> "Подготовка матча";
            case RUNNING -> "Матч идёт";
            case POST_GAME -> "Матч завершён";
            case RESTARTING -> "Перезапуск";
            case SET_REWARDING -> "Подведение итогов";
        };
        if (!value.equals(matchStatus)) { matchStatus = value; dirty = true; }
        selectionStatus = confirmedClass.isEmpty() ? "Класс сервером ещё не подтверждён"
                : ClassDefinitions.byClassKey(confirmedClass).map(d -> "Получен: " + d.name().getString()).orElse("Класс подтверждён");
    }

    private void layout() {
        int m = TabletTerminalTheme.MARGIN, w = TabletTerminalTheme.LOGICAL_WIDTH;
        list = new Rect(m, 64, w - 2 * m, height - 64 - 112);
        capacity = Math.max(1, list.height / TabletTerminalTheme.ROW_HEIGHT);
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.length - capacity)));
        for (int i = 0; i < rows.length; i++) rows[i].bounds = new Rect(list.x, list.y + (i - offset) * TabletTerminalTheme.ROW_HEIGHT,
                list.width, TabletTerminalTheme.ROW_HEIGHT - 4);
        buttons[REFRESH] = new Rect(w - m - 106, m, 106, 26);
        buttons[SELECT] = new Rect(m, height - 94, 236, TabletTerminalTheme.BUTTON_HEIGHT);
        int deployX = m + buttons[SELECT].width + TabletTerminalTheme.GAP;
        buttons[DEPLOY] = new Rect(deployX, height - 94, w - m - deployX, TabletTerminalTheme.BUTTON_HEIGHT);
        modal = new Rect((w - 444) / 2, (height - 156) / 2, 444, 156);
        buttons[CONFIRM] = new Rect(modal.x + 16, modal.y + 104, 242, 32);
        buttons[CANCEL] = new Rect(modal.x + 266, modal.y + 104, 162, 32);
        dirty = true;
    }

    boolean enabled(int id) {
        if (!interactive) return false;
        if (id == CANCEL) return true;
        if (id == REFRESH) return !awaiting;
        if (!ready || awaiting) return false;
        if (id == SELECT) return rows.length > 0 && rows[focused].reason.isEmpty();
        if (id == DEPLOY) return !rtpUsed && !rtpPending && rtpReason.isEmpty();
        if (id == CONFIRM) return enabled(confirmation);
        return id >= ROW;
    }
    int hit(TabletDisplayProjection.Point point) {
        if (point == null) return -1;
        if (confirmation >= 0) {
            if (buttons[CONFIRM].contains(point.x(), point.y())) return CONFIRM;
            return buttons[CANCEL].contains(point.x(), point.y()) ? CANCEL : -1;
        }
        for (int id = REFRESH; id <= DEPLOY; id++) if (buttons[id].contains(point.x(), point.y())) return id;
        if (list.contains(point.x(), point.y()))
            for (int i = offset; i < Math.min(rows.length, offset + capacity); i++)
                if (rows[i].bounds.contains(point.x(), point.y())) return ROW + i;
        return -1;
    }
    void hover(TabletDisplayProjection.Point point, boolean open) {
        int next = open ? hit(point) : -1;
        if (hover != next) { hover = next; dirty = true; }
    }
    void press(TabletDisplayProjection.Point point) { pressed = hit(point); dirty = true; }
    void release(TabletDisplayProjection.Point point) {
        int id = pressed; pressed = -1; dirty = true;
        if (id < 0 || id != hit(point)) return;
        if (id >= ROW) {
            focused = id - ROW;
            message(rows[focused].reason.isEmpty() ? "Набор: " + rows[focused].name : rows[focused].reason, false);
            return;
        }
        if (!enabled(id)) {
            message(!ready ? "Дождитесь актуальных данных сервера."
                    : awaiting ? "Дождитесь ответа сервера на текущий запрос."
                    : id == DEPLOY ? (rtpPending ? "Сервер уже обрабатывает запрос RTP." : rtpReason)
                    : rows[focused].reason, false);
            return;
        }
        if (id == REFRESH) { refreshNotice = true; error = false; message("Обновляем состояние…", false); request(-1, true); }
        else if (id == CANCEL) confirmation = -1;
        else if (id == CONFIRM) {
            int action = confirmation == SELECT ? rows[focused].definition.actionId() : 7;
            confirmation = -1; error = false; request(action, true);
        } else confirmation = id;
    }
    void cancelPress() { if (pressed != -1) { pressed = -1; dirty = true; } }
    void dismissConfirmation() {
        confirmation = -1;
        cancelPress(); hover = -1; dirty = true;
        // Requests already sent keep their nonce, timeout and response processing across phase changes.
    }
    void refreshWhenIdle() { if (!awaiting) nextPoll = 0; }
    void scroll(TabletDisplayProjection.Point point, double delta) {
        if (point == null || confirmation >= 0 || !list.contains(point.x(), point.y())) return;
        int next = Math.max(0, Math.min(Math.max(0, rows.length - capacity), offset - (int) Math.signum(delta)));
        if (next != offset) { offset = next; cancelPress(); layout(); }
    }
    private void message(String text, boolean isError) {
        if (text.equals(feedback) && error == isError && !feedbackLines.isEmpty()) return;
        feedback = text; error = isError;
        feedbackLines = Minecraft.getInstance().font.split(Component.literal(text), 520);
        dirty = true;
    }
    boolean dirty() { return dirty; }
    boolean synchronizationFailed() { return synchronizationFailed; }
    boolean navigationLocked() { return confirmation >= 0 || awaiting && pendingAction >= 0 || rtpPending; }
    void rendered() { dirty = false; }
    void interactive(boolean value) {
        if (interactive != value) { interactive = value; cancelPress(); hover = -1; dirty = true; }
    }
}
