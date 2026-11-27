package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Stateless profile page renderer consuming an immutable client snapshot. */
final class TabletProfileView {
    private static final int ROW_HEIGHT = 18;
    private static final int VALUE_X = 126;

    private TabletProfileView() {
    }

    static void render(GuiGraphics graphics, Font font, int x, int y, Model model) {
        drawRow(graphics, font, x, y, 0, "\u041c\u043e\u043d\u0435\u0442\u044b", model.coins(), 0xFFE7C76A);
        drawRow(graphics, font, x, y, 1, "\u041f\u043e\u0431\u0435\u0434\u044b", model.wins(), 0xFFFFFFFF);
        drawRow(graphics, font, x, y, 2, "\u041c\u0430\u0442\u0447\u0438", model.matches(), 0xFFFFFFFF);
        drawRow(graphics, font, x, y, 3, "\u0423\u0431\u0438\u0439\u0441\u0442\u0432\u0430", model.kills(), 0xFFFFFFFF);
        drawRow(graphics, font, x, y, 4, "\u0421\u043c\u0435\u0440\u0442\u0438", model.deaths(), 0xFFFFFFFF);
        drawRow(graphics, font, x, y, 5, "KDA", model.kda(), 0xFFFFFFFF);
        drawRow(graphics, font, x, y, 6, "\u041f\u0440\u043e\u0433\u0440\u0435\u0441\u0441", model.progress(), 0xFFFFFFFF);
    }

    private static void drawRow(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int row,
            String label,
            String value,
            int valueColor
    ) {
        int rowY = y + 10 + row * ROW_HEIGHT;
        graphics.drawString(font, label + ":", x + 10, rowY, 0xFF9FB2A4, false);
        graphics.drawString(font, value, x + 10 + VALUE_X, rowY, valueColor, false);
    }

    record Model(String coins, String wins, String matches, String kills, String deaths, String kda, String progress) {
    }
}
