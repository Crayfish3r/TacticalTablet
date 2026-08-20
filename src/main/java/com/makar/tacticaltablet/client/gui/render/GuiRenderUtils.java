package com.makar.tacticaltablet.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;

public final class GuiRenderUtils {

    private GuiRenderUtils() {
    }

    public static void fillRoundedRect(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int color
    ) {
        if (width <= 0 || height <= 0) return;

        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (safeRadius == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }

        graphics.fill(x + safeRadius, y, x + width - safeRadius, y + height, color);
        graphics.fill(x, y + safeRadius, x + width, y + height - safeRadius, color);

        for (int row = 0; row < safeRadius; row++) {
            int inset = cornerInset(safeRadius, row);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(
                    x + inset,
                    y + height - row - 1,
                    x + width - inset,
                    y + height - row,
                    color
            );
        }
    }

    public static void drawRoundedBorder(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int borderColor,
            int fillColor
    ) {
        fillRoundedRect(graphics, x, y, width, height, radius, borderColor);
        if (width <= 2 || height <= 2) return;
        fillRoundedRect(
                graphics,
                x + 1,
                y + 1,
                width - 2,
                height - 2,
                Math.max(0, radius - 1),
                fillColor
        );
    }

    private static int cornerInset(int radius, int row) {
        double y = radius - row - 0.5D;
        double horizontalExtent = Math.sqrt(Math.max(0.0D, radius * radius - y * y));
        return Math.max(0, (int) Math.ceil(radius - 0.5D - horizontalExtent));
    }
}
