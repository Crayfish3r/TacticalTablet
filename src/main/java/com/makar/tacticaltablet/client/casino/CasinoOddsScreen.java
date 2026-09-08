package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoSpinTable;
import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Read-only odds presentation backed by the authoritative CasinoSpinTable snapshot. */
final class CasinoOddsScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {
    private static final int PANEL_MARGIN = 10;
    private static final int PANEL_MAX_WIDTH = 780;
    private static final int PANEL_MAX_HEIGHT = 330;
    private static final int WIDE_TABLE_THRESHOLD = 620;

    private final CasinoScreen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean returningToParent;

    CasinoOddsScreen(CasinoScreen parent) {
        super(Component.translatable("screen.tacticaltablet.casino.odds_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(Math.max(1, width - 8),
                Math.min(PANEL_MAX_WIDTH, Math.max(300, width - PANEL_MARGIN * 2)));
        panelHeight = Math.min(Math.max(1, height - 8),
                Math.min(PANEL_MAX_HEIGHT, Math.max(220, height - PANEL_MARGIN * 2)));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int buttonWidth = Math.min(180, panelWidth - 32);
        addRenderableWidget(TacticalButton.standard(
                panelX + (panelWidth - buttonWidth) / 2,
                panelY + panelHeight - TacticalTheme.CONTROL_HEIGHT - 12,
                buttonWidth,
                Component.translatable("screen.tacticaltablet.casino.back"),
                ignored -> onClose()
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame, ExternalUiTheme.PALETTE)) {
            renderBackground(graphics);
            graphics.fill(0, 0, width, height, 0x88000000);
            TacticalUi.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
            graphics.drawCenteredString(font, title, width / 2, panelY + 16, ExternalUiTheme.ACCENT);

            int contentX = panelX + 14;
            int contentY = panelY + 38;
            int contentWidth = panelWidth - 28;
            int contentBottom = panelY + panelHeight - TacticalTheme.CONTROL_HEIGHT - 20;
            if (contentWidth >= WIDE_TABLE_THRESHOLD) {
                renderWideTable(graphics, contentX, contentY, contentWidth, contentBottom - contentY);
            } else {
                renderCompactTable(graphics, contentX, contentY, contentWidth, contentBottom - contentY);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderWideTable(GuiGraphics graphics, int x, int y, int width, int height) {
        String[][] headers = {
                {"Ставка", ""},
                {"Без", "выигрыша"},
                {"Малый", "приз"},
                {"Средний", "приз"},
                {"Крупный", "приз"},
                {"Магазинный", "класс"},
                {"VIP", "класс"},
                {"Дудка", ""}
        };
        int[] proportions = {52, 76, 108, 104, 104, 112, 64, 58};
        int total = 0;
        for (int proportion : proportions) total += proportion;

        int headerHeight = 30;
        int rowHeight = Math.max(24, Math.min(31, (height - headerHeight) / CasinoSpinTable.oddsRows().size()));
        int cursorX = x;
        for (int column = 0; column < proportions.length; column++) {
            int columnWidth = column == proportions.length - 1
                    ? x + width - cursorX
                    : width * proportions[column] / total;
            graphics.fill(cursorX, y, cursorX + columnWidth - 1, y + headerHeight,
                    TacticalUi.withAlpha(ExternalUiTheme.SURFACE_RAISED, 0xD8));
            drawCentered(graphics, headers[column][0], cursorX, y + 6, columnWidth, ExternalUiTheme.TEXT_PRIMARY);
            if (!headers[column][1].isEmpty()) {
                drawCentered(graphics, headers[column][1], cursorX, y + 16, columnWidth,
                        ExternalUiTheme.TEXT_SECONDARY);
            }
            cursorX += columnWidth;
        }

        List<CasinoSpinTable.OddsRow> rows = CasinoSpinTable.oddsRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            CasinoSpinTable.OddsRow row = rows.get(rowIndex);
            String[] values = {
                    Integer.toString(row.stake()),
                    row.noPrizeChance() + "%",
                    reward(row.smallChance(), row.smallCoins()),
                    reward(row.mediumChance(), row.mediumCoins()),
                    reward(row.largeChance(), row.largeCoins()),
                    row.shopClassChance() + "%",
                    row.vipClassChance() + "%",
                    row.sadTromboneChance() + "%"
            };
            int rowY = y + headerHeight + rowIndex * rowHeight;
            int rowColor = rowIndex % 2 == 0
                    ? TacticalUi.withAlpha(ExternalUiTheme.SURFACE, 0xB8)
                    : TacticalUi.withAlpha(ExternalUiTheme.SURFACE_RAISED, 0xA8);
            cursorX = x;
            for (int column = 0; column < proportions.length; column++) {
                int columnWidth = column == proportions.length - 1
                        ? x + width - cursorX
                        : width * proportions[column] / total;
                graphics.fill(cursorX, rowY, cursorX + columnWidth - 1, rowY + rowHeight - 1, rowColor);
                drawFittedCentered(graphics, values[column], cursorX, rowY + (rowHeight - font.lineHeight) / 2,
                        columnWidth, column == 0 ? ExternalUiTheme.ACCENT : ExternalUiTheme.TEXT_PRIMARY);
                cursorX += columnWidth;
            }
        }
        TacticalUi.drawCutCornerBorder(graphics, x, y, width,
                headerHeight + rowHeight * rows.size(), 2, 1,
                ExternalUiTheme.BORDER, 0x00000000);
    }

    private void renderCompactTable(GuiGraphics graphics, int x, int y, int width, int height) {
        List<CasinoSpinTable.OddsRow> rows = CasinoSpinTable.oddsRows();
        int gap = 3;
        int rowHeight = Math.max(23, Math.min(39, (height - gap * (rows.size() - 1)) / rows.size()));
        for (int index = 0; index < rows.size(); index++) {
            CasinoSpinTable.OddsRow row = rows.get(index);
            int rowY = y + index * (rowHeight + gap);
            TacticalUi.drawCutCornerBorder(graphics, x, rowY, width, rowHeight,
                    2, 1, ExternalUiTheme.BORDER,
                    TacticalUi.withAlpha(index % 2 == 0
                            ? ExternalUiTheme.SURFACE
                            : ExternalUiTheme.SURFACE_RAISED, 0xC0));

            String top = "Ставка " + row.stake()
                    + "   |   Без выигрыша " + row.noPrizeChance() + "%"
                    + "   |   Класс " + row.shopClassChance() + "%"
                    + "   |   VIP " + row.vipClassChance() + "%"
                    + "   |   Дудка " + row.sadTromboneChance() + "%";
            String bottom = "Coins: "
                    + reward(row.smallChance(), row.smallCoins()) + "   •   "
                    + reward(row.mediumChance(), row.mediumCoins()) + "   •   "
                    + reward(row.largeChance(), row.largeCoins());
            drawFittedCentered(graphics, top, x + 6, rowY + 6, width - 12, ExternalUiTheme.TEXT_PRIMARY);
            drawFittedCentered(graphics, bottom, x + 6, rowY + rowHeight - 6 - font.lineHeight,
                    width - 12, ExternalUiTheme.TEXT_SECONDARY);
        }
    }

    private void drawCentered(GuiGraphics graphics, String value, int x, int y, int width, int color) {
        graphics.drawCenteredString(font, value, x + width / 2, y, color);
    }

    private void drawFittedCentered(
            GuiGraphics graphics,
            String value,
            int x,
            int y,
            int width,
            int color
    ) {
        int textWidth = font.width(value);
        if (textWidth <= width) {
            graphics.drawCenteredString(font, value, x + width / 2, y, color);
            return;
        }
        float scale = Math.max(0.62F, (float) width / textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, value, -textWidth / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private static String reward(int chance, int coins) {
        return chance + "% → " + coins;
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        returningToParent = true;
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (!returningToParent && (minecraft == null || minecraft.screen != parent)) {
            parent.closeSessionFromChild();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return ExternalUiTheme.PALETTE;
    }
}
