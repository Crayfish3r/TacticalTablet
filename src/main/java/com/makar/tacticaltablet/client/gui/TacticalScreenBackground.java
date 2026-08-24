package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class TacticalScreenBackground {

    private TacticalScreenBackground() {
    }

    static void render(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        if (minecraft.level == null) {
            float coverScale = Math.max(
                    width / (float) MenuTextureSet.BACKGROUND_WIDTH,
                    height / (float) MenuTextureSet.BACKGROUND_HEIGHT
            );
            int drawWidth = Math.max(1, (int) Math.ceil(MenuTextureSet.BACKGROUND_WIDTH * coverScale));
            int drawHeight = Math.max(1, (int) Math.ceil(MenuTextureSet.BACKGROUND_HEIGHT * coverScale));
            graphics.blit(
                    MenuTextureSet.BACKGROUND,
                    (width - drawWidth) / 2,
                    (height - drawHeight) / 2,
                    drawWidth,
                    drawHeight,
                    0.0F,
                    0.0F,
                    MenuTextureSet.BACKGROUND_WIDTH,
                    MenuTextureSet.BACKGROUND_HEIGHT,
                    MenuTextureSet.BACKGROUND_WIDTH,
                    MenuTextureSet.BACKGROUND_HEIGHT
            );
        }
        TacticalUi.drawBackdrop(graphics, width, height);
    }
}
