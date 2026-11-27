package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Retained page rendering. Text/data are separate from optional textured backgrounds. */
final class TabletPreparationView {
    private TabletPreparationView() { }
    static void render(GuiGraphics g, TabletPreparationPage page) {
        background(g, Kind.SCREEN, State.NORMAL, 0, 0, LOGICAL_WIDTH, page.height + 1);
        text(g, "ПОДГОТОВКА К БОЮ", MARGIN, MARGIN, TEXT, TITLE_SCALE);
        text(g, page.ready ? page.matchStatus : "Синхронизация с сервером", MARGIN, 42, SECONDARY, TEXT_SCALE);
        button(g, page, TabletPreparationPage.REFRESH, "Обновить", false);
        background(g, Kind.PANEL, State.NORMAL, page.list.x() - 3, page.list.y() - 3, page.list.width() + 6, page.list.height() + 6);
        int end = Math.min(page.rows.length, page.offset + page.capacity);
        for (int i = page.offset; i < end; i++) {
            var row = page.rows[i]; var rect = row.bounds;
            boolean arrow = row.state != null && row.state.nextTier() >= 0;
            int bodyWidth = rect.width() - (arrow ? 55 : 9);
            tierRow(g, rect.x(), rect.y(), bodyWidth, rect.height(), row.state == null ? 0 : row.state.tier(),
                    row.state == null ? -1 : row.state.nextTier(), row.state == null ? 0 : row.state.progress(),
                    page.hover == TabletPreparationPage.ROW + i, page.pressed == TabletPreparationPage.ROW + i,
                    page.focused == i || !row.badge.isEmpty(), !row.reason.isEmpty());
            if (arrow) {
                var target = new TabletPreparationPage.Rect(rect.x() + rect.width() - 51, rect.y(), 42, rect.height());
                drawButton(g, target, "↑", row.state.canUpgrade() && !TabletClassesClient.busy(),
                        page.hover == TabletPreparationPage.UPGRADE + i, page.pressed == TabletPreparationPage.UPGRADE + i);
            }
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            g.blit(row.icon, rect.x() + 9, rect.y() + 6, 24, 24, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
            text(g, row.name, rect.x() + 43, rect.y() + 5, TEXT, TEXT_SCALE);
            String reason = row.reason.isEmpty() && row.state != null && row.state.nextTier() >= 0
                    ? row.state.upgradeReason().isEmpty() ? "Улучшение: " + row.state.upgradePrice() + " монет" : row.state.upgradeReason()
                    : row.shortReason;
            if (row.reason.isEmpty() && row.state != null && row.state.nextTier() < 0
                    && row.definition.category() == com.makar.tacticaltablet.tablet.ClassCategory.BASE)
                reason = row.state.upgradeReason();
            text(g, fit(reason, bodyWidth - 56), rect.x() + 43, rect.y() + 23, SECONDARY, DETAIL_SCALE);
            if (row.state != null) {
                String tier = tierName(row.state.tier());
                if (row.state.tier() == 4 && row.definition.category() == com.makar.tacticaltablet.tablet.ClassCategory.BASE) tier += " MAX";
                text(g, tier, rect.x() + bodyWidth - 106, rect.y() + 6, TEXT, DETAIL_SCALE);
            }
        }
        if (page.rows.length == 0) {
            text(g, TabletClassesClient.snapshot() == null ? "Загрузка классов с сервера…" : "Нет классов для подготовки.",
                    page.list.x() + 18, page.list.y() + 20, SECONDARY, TEXT_SCALE);
            if (TabletClassesClient.snapshot() != null) button(g, page, TabletPreparationPage.OPEN_SHOP, "Перейти в магазин", true);
        }
        int trackX = page.list.x() + page.list.width() - 5;
        g.fill(trackX, page.list.y(), trackX + 3, page.list.y() + page.list.height(), BORDER);
        int thumb = Math.max(18, page.list.height() * page.capacity / Math.max(page.capacity, page.rows.length));
        int thumbY = page.list.y() + (page.list.height() - thumb) * page.offset / Math.max(1, page.rows.length - page.capacity);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumb, OLIVE);
        text(g, page.selectionStatus, MARGIN, page.height - 112, SECONDARY, DETAIL_SCALE);
        button(g, page, TabletPreparationPage.SELECT, "Выбрать класс", false);
        button(g, page, TabletPreparationPage.DEPLOY, page.rtpUsed ? "Вы в бою" : "Вступить в бой", true);
        int lineY = page.height - 50;
        for (int i = 0; i < Math.min(2, page.feedbackLines.size()); i++) {
            g.drawString(Minecraft.getInstance().font, page.feedbackLines.get(i), MARGIN, lineY + i * 12,
                    page.error ? ERROR : SECONDARY, false);
        }
        text(g, TabletClassesClient.busy() ? "СОХРАНЕНИЕ ИЗМЕНЕНИЙ…"
                : page.awaiting ? "ОЖИДАНИЕ ОТВЕТА СЕРВЕРА" : "ПКМ / ESC — убрать планшет", MARGIN, page.height - 17, MUTED, DETAIL_SCALE);
        if (page.confirmation >= 0) {
            g.fill(0, 0, LOGICAL_WIDTH, page.height + 1, SCRIM);
            var m = page.modal;
            background(g, Kind.PANEL, State.SELECTED, m.x(), m.y(), m.width(), m.height());
            text(g, "ПОДТВЕРЖДЕНИЕ", m.x() + 16, m.y() + 18, TEXT, TITLE_SCALE);
            text(g, page.confirmation == TabletPreparationPage.SELECT ? "Получить набор выбранного класса?" : "Запросить вступление в бой через RTP?",
                    m.x() + 16, m.y() + 52, SECONDARY, DETAIL_SCALE);
            if (page.confirmation == TabletPreparationPage.SELECT)
                text(g, page.confirmedRow() == null ? "Класс больше недоступен" : page.confirmedRow().name,
                        m.x() + 16, m.y() + 75, OLIVE, TEXT_SCALE);
            button(g, page, TabletPreparationPage.CONFIRM, "Подтвердить", true);
            button(g, page, TabletPreparationPage.CANCEL, "Отмена", false);
        }
    }
    private static void button(GuiGraphics g, TabletPreparationPage page, int id, String label, boolean primary) {
        var rect = page.buttons[id];
        State state = !page.enabled(id) ? State.DISABLED : page.pressed == id ? State.PRESSED : page.hover == id ? State.HOVER : State.NORMAL;
        background(g, primary ? Kind.PRIMARY : Kind.BUTTON, state, rect.x(), rect.y(), rect.width(), rect.height());
        float scale = TEXT_SCALE;
        int width = (int) (Minecraft.getInstance().font.width(label) * scale);
        text(g, label, rect.x() + (rect.width() - width) / 2, rect.y() + 10, state == State.DISABLED ? DISABLED : TEXT, scale);
    }
    private static void text(GuiGraphics g, String value, int x, int y, int color, float scale) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(Minecraft.getInstance().font, value, 0, 0, color, false);
        g.pose().popPose();
    }
}
