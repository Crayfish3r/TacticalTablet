package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.SetGameMode;
import com.makar.tacticaltablet.tablet.client.MapVoteClientState;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Drawn exclusively by TabletPageShell into the shared display target, including themed skins. */
final class TabletVotingView {
    private static final String[] FORMAT_NAMES = {"Solo", "Duo", "Trio", "Squads"};
    private TabletVotingView() { }

    static void render(GuiGraphics g, TabletVotingPage page) {
        background(g, Kind.SCREEN, State.NORMAL, 0, 0, LOGICAL_WIDTH, page.height + 1);
        String title = page.phase == MatchPhase.MAP_VOTING ? "ГОЛОСОВАНИЕ ЗА КАРТУ"
                : page.phase == MatchPhase.VOTING ? "ФОРМАТ МАТЧА" : "ВЫБОР КОМАНД";
        text(g, title, MARGIN, MARGIN, LOGICAL_WIDTH - 2 * MARGIN, TEXT, TITLE_SCALE);
        if (page.phase == MatchPhase.TEAM_SELECT) {
            waiting(g, page, "Ожидаем завершение выбора команд.",
                    "Осталось: " + page.secondsLeft() + " с.");
        } else if (!page.ready()) {
            waiting(g, page, "Синхронизация с сервером…", "Загружаем актуальные варианты голосования.");
        } else {
            text(g, page.secondsLeft() > 0 ? "Осталось: " + page.secondsLeft() + " с."
                            : "Приём голосов завершён. Ожидаем решение сервера.",
                    MARGIN, 42, LOGICAL_WIDTH - 2 * MARGIN, SECONDARY, TEXT_SCALE);
            if (page.phase == MatchPhase.MAP_VOTING) maps(g, page);
            else formats(g, page);
        }
        String feedback = page.feedback.isEmpty() ? "Выбор и голоса подтверждаются сервером." : page.feedback;
        text(g, feedback, MARGIN, page.height - 34, LOGICAL_WIDTH - 2 * MARGIN,
                page.error ? ERROR : SECONDARY, DETAIL_SCALE);
        text(g, page.pending() ? "ОЖИДАНИЕ ПОДТВЕРЖДЕНИЯ" : "ПКМ / ESC — убрать планшет",
                MARGIN, page.height - 17, LOGICAL_WIDTH - 2 * MARGIN, MUTED, DETAIL_SCALE);
    }

    private static void waiting(GuiGraphics g, TabletVotingPage page, String label, String detail) {
        background(g, Kind.PANEL, State.NORMAL, MARGIN, 64, LOGICAL_WIDTH - 2 * MARGIN, page.height - 126);
        text(g, label, MARGIN * 2, 88, LOGICAL_WIDTH - 4 * MARGIN, TEXT, TEXT_SCALE);
        text(g, detail, MARGIN * 2, 116, LOGICAL_WIDTH - 4 * MARGIN, SECONDARY, DETAIL_SCALE);
        if (page.phase == MatchPhase.TEAM_SELECT && TabletClientState.getSelectedTeam() >= 0)
            text(g, "Ваша команда: " + (TabletClientState.getSelectedTeam() + 1), MARGIN * 2, 148,
                    LOGICAL_WIDTH - 4 * MARGIN, OLIVE, TEXT_SCALE);
    }

    private static void maps(GuiGraphics g, TabletVotingPage page) {
        var list = page.list;
        background(g, Kind.PANEL, State.NORMAL, list.x() - 3, list.y() - 3, list.width() + 6, list.height() + 6);
        if (page.maps.isEmpty()) text(g, "Сервер пока не предложил карты.", list.x() + 12, list.y() + 18,
                list.width() - 24, MUTED, TEXT_SCALE);
        for (int i = page.offset; i < Math.min(page.maps.size(), page.offset + page.capacity); i++) {
            var row = page.maps.get(i); var rect = page.mapBounds(i);
            boolean selected = row.name().equals(MapVoteClientState.getSelectedMap());
            background(g, Kind.ROW, state(page, TabletVotingPage.MAP + i, selected),
                    rect.x(), rect.y(), rect.width(), rect.height());
            int imageX = rect.x() + 6, imageY = rect.y() + 4;
            if (row.preview() != null) {
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                g.blit(row.preview(), imageX, imageY, 96, 54, 0, 0, 96, 54, 96, 54);
                RenderSystem.disableBlend();
            } else {
                background(g, Kind.PANEL, State.NORMAL, imageX, imageY, 96, 54);
                text(g, "НЕТ ПРЕВЬЮ", imageX + 6, imageY + 22, 84, MUTED, DETAIL_SCALE);
            }
            int textX = rect.x() + 114, textWidth = rect.width() - 126;
            text(g, row.name(), textX, rect.y() + 10, textWidth, TEXT, TEXT_SCALE);
            text(g, "Голосов: " + MapVoteClientState.getVoteCount(row.name()), textX, rect.y() + 36,
                    textWidth / 2, SECONDARY, DETAIL_SCALE);
            if (selected) text(g, "ВАШ ГОЛОС", rect.x() + rect.width() - 132, rect.y() + 36,
                    120, OLIVE, DETAIL_SCALE);
        }
        int trackX = list.x() + list.width() - 5;
        g.fill(trackX, list.y(), trackX + 3, list.y() + list.height(), BORDER);
        int thumb = Math.max(18, list.height() * page.capacity / Math.max(page.capacity, page.maps.size()));
        int thumbY = list.y() + (list.height() - thumb) * page.offset / Math.max(1, page.maps.size() - page.capacity);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumb, OLIVE);
        String modeLabel = MapVoteClientState.areOrdinaryModesEnabled() ? "РЕЖИМ СЛЕДУЮЩЕГО СЕТА"
                : MapVoteClientState.isNextSetCompetitive() ? "РЕЖИМ СЕТА: COMPETITIVE (ЗАДАН СЕРВЕРОМ)"
                : "РЕЖИМ СЕТА ЗАДАН СЕРВЕРОМ";
        text(g, modeLabel, MARGIN, page.height - 106, LOGICAL_WIDTH - 2 * MARGIN, SECONDARY, DETAIL_SCALE);
        for (int i = 0; i < TabletVotingPage.SET_MODES.length; i++) {
            SetGameMode mode = TabletVotingPage.SET_MODES[i];
            var rect = page.setButtons[i];
            boolean selected = mode == MapVoteClientState.getSelectedMode();
            background(g, Kind.BUTTON, state(page, TabletVotingPage.SET + i, selected),
                    rect.x(), rect.y(), rect.width(), rect.height());
            String label = switch (mode) { case CASUAL -> "Casual"; case CHAOS -> "Chaos"; default -> "Competitive"; };
            text(g, label, rect.x() + 9, rect.y() + 7, rect.width() - 18, TEXT, TEXT_SCALE);
            String detail = MapVoteClientState.getModeVoteCount(mode) + " голосов"
                    + (selected ? " • Ваш голос" : !page.setReason(mode).isEmpty() ? " • Недоступен" : "");
            text(g, detail, rect.x() + 9, rect.y() + 26, rect.width() - 18,
                    selected ? OLIVE : page.setReason(mode).isEmpty() ? SECONDARY : DISABLED, DETAIL_SCALE);
        }
    }

    private static void formats(GuiGraphics g, TabletVotingPage page) {
        for (int i = 0; i < TabletVotingPage.FORMATS.length; i++) {
            var mode = TabletVotingPage.FORMATS[i]; var rect = page.formatButtons[i];
            boolean selected = mode == TabletClientState.getSelectedVote();
            boolean available = TabletClientState.isVoteModeAvailable(mode);
            background(g, Kind.BUTTON, state(page, TabletVotingPage.FORMAT + i, selected),
                    rect.x(), rect.y(), rect.width(), rect.height());
            text(g, FORMAT_NAMES[i], rect.x() + 16, rect.y() + 13, rect.width() - 32,
                    available ? TEXT : DISABLED, TITLE_SCALE);
            text(g, "Голосов: " + TabletClientState.getVoteCount(mode), rect.x() + 16, rect.y() + 43,
                    rect.width() - 32, SECONDARY, TEXT_SCALE);
            text(g, selected ? "ВАШ ГОЛОС" : available ? "Выбрать формат" : "Ограничение по участникам",
                    rect.x() + 16, rect.y() + rect.height() - 22, rect.width() - 32,
                    selected ? OLIVE : available ? MUTED : DISABLED, DETAIL_SCALE);
        }
    }

    private static State state(TabletVotingPage page, int id, boolean selected) {
        return !page.enabled(id) ? State.DISABLED : page.pressed == id ? State.PRESSED
                : page.hover == id ? State.HOVER : selected ? State.SELECTED : State.NORMAL;
    }

    private static void text(GuiGraphics g, String value, int x, int y, int width, int color, float scale) {
        var font = Minecraft.getInstance().font;
        int limit = Math.max(0, (int) (width / scale));
        String fitted = font.width(value) <= limit ? value
                : font.plainSubstrByWidth(value, Math.max(0, limit - font.width("…"))) + "…";
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, fitted, 0, 0, color, false);
        g.pose().popPose();
    }
}
