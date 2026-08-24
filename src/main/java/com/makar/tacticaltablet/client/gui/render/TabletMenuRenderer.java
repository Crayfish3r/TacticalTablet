package com.makar.tacticaltablet.client.gui.render;

import com.makar.tacticaltablet.client.gui.MenuTextureSet;
import com.makar.tacticaltablet.client.gui.TabletMenuLayout;
import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class TabletMenuRenderer {

    private static final float START_SCALE = 0.92F;

    private TabletMenuRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            TabletMenuLayout layout,
            ResourceLocation texture,
            float revealProgress
    ) {
        if (layout == null) return;

        float progress = Mth.clamp(revealProgress, 0.0F, 1.0F);
        float scale = Mth.lerp(progress, START_SCALE, 1.0F);
        int renderWidth = Math.max(1, Math.round(layout.frameWidth() * scale));
        int renderHeight = Math.max(1, Math.round(layout.frameHeight() * scale));
        int renderX = layout.frameX() + (layout.frameWidth() - renderWidth) / 2;
        int renderY = layout.frameY() + (layout.frameHeight() - renderHeight) / 2;

        GuiTextureRenderer.blitRegionWithAlpha(
                graphics,
                texture,
                renderX,
                renderY,
                renderWidth,
                renderHeight,
                0.0F,
                0.0F,
                MenuTextureSet.TABLET_WIDTH,
                MenuTextureSet.TABLET_HEIGHT,
                MenuTextureSet.TABLET_WIDTH,
                MenuTextureSet.TABLET_HEIGHT,
                1.0F,
                1.0F,
                1.0F,
                progress
        );
    }
}
