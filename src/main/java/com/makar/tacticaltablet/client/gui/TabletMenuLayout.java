package com.makar.tacticaltablet.client.gui;

import net.minecraft.util.Mth;

public record TabletMenuLayout(
        int frameX,
        int frameY,
        int frameWidth,
        int frameHeight,
        int buttonX,
        int buttonWidth,
        int buttonHeight,
        int firstButtonY,
        int buttonStep
) {

    public static final int BUTTON_COUNT = 4;

    private static final float MAIN_WIDTH_RATIO = 0.55F;
    private static final float MAIN_HEIGHT_RATIO = 0.52F;
    private static final float MAIN_TOP_RATIO = 0.395F;
    private static final float PAUSE_WIDTH_RATIO = 0.62F;
    private static final float PAUSE_HEIGHT_RATIO = 0.72F;
    private static final int SCREEN_MARGIN = 8;
    private static final int FIRST_BUTTON_TEXTURE_Y = 70;
    private static final int BUTTON_TEXTURE_STEP = 95;

    static TabletMenuLayout calculateMain(int screenWidth, int screenHeight) {
        TabletSize tablet = TabletSize.fit(
                screenWidth,
                screenHeight,
                MAIN_WIDTH_RATIO,
                MAIN_HEIGHT_RATIO
        );
        int frameY = Math.round(screenHeight * MAIN_TOP_RATIO);
        frameY = Mth.clamp(frameY, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - tablet.height - SCREEN_MARGIN));
        return create(screenWidth, tablet, frameY);
    }

    static TabletMenuLayout calculatePause(int screenWidth, int screenHeight) {
        TabletSize tablet = TabletSize.fit(
                screenWidth,
                screenHeight,
                PAUSE_WIDTH_RATIO,
                PAUSE_HEIGHT_RATIO
        );
        return create(screenWidth, tablet, (screenHeight - tablet.height) / 2);
    }

    private static TabletMenuLayout create(int screenWidth, TabletSize tablet, int frameY) {
        int frameX = (screenWidth - tablet.width) / 2;
        int buttonWidth = Math.max(1, Math.round(MenuTextureSet.BUTTON_WIDTH * tablet.scale));
        int buttonHeight = Math.max(1, Math.round(MenuTextureSet.BUTTON_HEIGHT * tablet.scale));
        int buttonX = frameX + Math.round(
                (MenuTextureSet.TABLET_WIDTH - MenuTextureSet.BUTTON_WIDTH) * 0.5F * tablet.scale
        );
        int firstButtonY = frameY + Math.round(FIRST_BUTTON_TEXTURE_Y * tablet.scale);
        int buttonStep = Math.max(buttonHeight + 1, Math.round(BUTTON_TEXTURE_STEP * tablet.scale));

        return new TabletMenuLayout(
                frameX,
                frameY,
                tablet.width,
                tablet.height,
                buttonX,
                buttonWidth,
                buttonHeight,
                firstButtonY,
                buttonStep
        );
    }

    public int buttonY(int index) {
        return firstButtonY + Mth.clamp(index, 0, BUTTON_COUNT - 1) * buttonStep;
    }

    private record TabletSize(int width, int height, float scale) {
        private static TabletSize fit(
                int screenWidth,
                int screenHeight,
                float widthRatio,
                float heightRatio
        ) {
            int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
            int availableHeight = Math.max(1, screenHeight - SCREEN_MARGIN * 2);
            float scale = Math.min(
                    screenWidth * widthRatio / MenuTextureSet.TABLET_WIDTH,
                    screenHeight * heightRatio / MenuTextureSet.TABLET_HEIGHT
            );
            scale = Math.min(scale, Math.min(
                    availableWidth / (float) MenuTextureSet.TABLET_WIDTH,
                    availableHeight / (float) MenuTextureSet.TABLET_HEIGHT
            ));
            scale = Math.max(1.0F / MenuTextureSet.TABLET_WIDTH, scale);
            return new TabletSize(
                    Math.max(1, Math.round(MenuTextureSet.TABLET_WIDTH * scale)),
                    Math.max(1, Math.round(MenuTextureSet.TABLET_HEIGHT * scale)),
                    scale
            );
        }
    }
}
