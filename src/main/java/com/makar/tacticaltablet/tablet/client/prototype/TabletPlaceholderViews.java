package com.makar.tacticaltablet.tablet.client.prototype;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Presentation-only placeholders inside the existing tablet target. */
final class TabletPlaceholderViews {
    private TabletPlaceholderViews() { }

    static void profile(GuiGraphics graphics, int height) {
        render(graphics, height, TabletPage.PROFILE.title, "Личная статистика появится на следующем этапе.");
    }

    static void contractTracker(GuiGraphics graphics, int height) {
        render(graphics, height, TabletPage.CONTRACT_TRACKER.title, "Контрактный трекер пока не подключён к 3D-планшету.");
    }

    private static void render(GuiGraphics g, int height, String title, String message) {
        background(g, Kind.SCREEN, State.NORMAL, 0, 0, LOGICAL_WIDTH, height + 1);
        text(g, title, MARGIN, MARGIN, TEXT, TITLE_SCALE);
        background(g, Kind.PANEL, State.NORMAL, MARGIN, 64, LOGICAL_WIDTH - 2 * MARGIN, height - 112);
        text(g, message, MARGIN * 2, 88, SECONDARY, TEXT_SCALE);
        text(g, "Выберите страницу физической кнопкой планшета.", MARGIN * 2, 116, MUTED, DETAIL_SCALE);
        text(g, "ПКМ / ESC — убрать планшет", MARGIN, height - 17, MUTED, DETAIL_SCALE);
    }

    private static void text(GuiGraphics g, String value, int x, int y, int color, float scale) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(Minecraft.getInstance().font, value, 0, 0, color, false);
        g.pose().popPose();
    }
}
