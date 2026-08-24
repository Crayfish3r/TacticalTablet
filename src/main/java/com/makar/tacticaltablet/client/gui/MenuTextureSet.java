package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.resources.ResourceLocation;

public final class MenuTextureSet {

    public static final int BACKGROUND_WIDTH = 1920;
    public static final int BACKGROUND_HEIGHT = 1080;
    public static final int TABLET_WIDTH = 1060;
    public static final int TABLET_HEIGHT = 545;
    public static final int BUTTON_WIDTH = 849;
    public static final int BUTTON_HEIGHT = 71;

    public static final ResourceLocation BACKGROUND = texture("menu.png");
    public static final ResourceLocation TABLET = texture("tablet.png");
    public static final ResourceLocation PAUSE_TABLET = texture("tablet_pause.png");
    public static final ResourceLocation JOIN = texture("button_join.png");
    public static final ResourceLocation CONTINUE = texture("button_continue.png");
    public static final ResourceLocation INFO = texture("button_info.png");
    public static final ResourceLocation SETTINGS = texture("button_settings.png");
    public static final ResourceLocation EXIT = texture("button_exit.png");

    private MenuTextureSet() {
    }

    private static ResourceLocation texture(String fileName) {
        return new ResourceLocation(
                TacticalTabletMod.MODID,
                "textures/gui/main_menu/" + fileName
        );
    }
}
