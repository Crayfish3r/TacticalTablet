package com.makar.tacticaltablet.tablet.client.prototype;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Profile presentation using the same themed panels and optional nine-slice skins as preparation. */
final class TabletProfileView {
    private static final String[] LABELS = {"Матчей сыграно", "Победы", "Убийства", "Смерти", "K/D", "Монеты"};
    private TabletProfileView() { }

    static void render(GuiGraphics g, TabletProfilePage page, int height) {
        background(g, Kind.SCREEN, State.NORMAL, 0, 0, LOGICAL_WIDTH, height + 1);
        text(g, TabletPage.PROFILE.title, MARGIN, MARGIN, TEXT, TITLE_SCALE);
        text(g, page.playerName, MARGIN, 42, SECONDARY, TEXT_SCALE);
        int width = (LOGICAL_WIDTH - 2 * MARGIN - 2 * GAP) / 3;
        int cardHeight = (height - 200 - GAP) / 2;
        for (int i = 0; i < LABELS.length; i++) {
            int x = MARGIN + i % 3 * (width + GAP), y = 64 + i / 3 * (cardHeight + GAP);
            background(g, Kind.PANEL, State.NORMAL, x, y, width, cardHeight);
            text(g, LABELS[i], x + MARGIN, y + 12, SECONDARY, TEXT_SCALE);
            text(g, page.values[i], x + MARGIN, y + cardHeight / 2, TEXT, TITLE_SCALE);
        }
        int careerY = height - 128, innerWidth = LOGICAL_WIDTH - 4 * MARGIN;
        background(g, Kind.PANEL, State.NORMAL, MARGIN, careerY, LOGICAL_WIDTH - 2 * MARGIN, 80);
        text(g, "Карьерный прогресс", MARGIN * 2, careerY + 12, TEXT, TEXT_SCALE);
        text(g, page.loaded ? page.careerPercent + "%" : "…", LOGICAL_WIDTH - MARGIN * 2 - 62,
                careerY + 12, OLIVE, TITLE_SCALE);
        int barY = careerY + 47;
        g.fill(MARGIN * 2, barY, MARGIN * 2 + innerWidth, barY + 12, BORDER);
        if (page.loaded) g.fill(MARGIN * 2, barY, MARGIN * 2 + innerWidth * page.careerPercent / 100, barY + 12, OLIVE);
        text(g, page.status, MARGIN, height - 34, page.error ? ERROR : SECONDARY, DETAIL_SCALE);
        text(g, "ПКМ / ESC — убрать планшет", MARGIN, height - 17, MUTED, DETAIL_SCALE);
    }

    private static void text(GuiGraphics g, String value, int x, int y, int color, float scale) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(Minecraft.getInstance().font, value, 0, 0, color, false);
        g.pose().popPose();
    }
}
