package com.makar.tacticaltablet.tablet.client;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TabletButtonTextures {
    private static final String ROOT = "textures/gui/buttons/";

    public static final ButtonTextureSpec CLASS_BUTTON = texture("class_button.png", 130, 34);

    public static final ButtonTextureSet NAV_CLASSES = states("nav_classes", 72, 28, true, false);
    public static final ButtonTextureSet NAV_SHOP = states("nav_shop", 72, 28, true, false);
    public static final ButtonTextureSet NAV_VIP = states("nav_vip", 72, 28, true, false);
    public static final ButtonTextureSet NAV_CLANS = states("nav_clans", 72, 28, true, false);
    public static final ButtonTextureSet NAV_PROFILE = states("nav_profile", 72, 28, true, false);

    public static final ButtonTextureSet RTP = states("rtp_button", 78, 20, false, true);
    public static final ButtonTextureSet CLAN_ROW = states("clan_row", 264, 22, true, false);
    public static final ButtonTextureSet CLAN_CREATE = states("clan_create_button", 120, 24, false, true);
    public static final ButtonTextureSet CLAN_BACK = states("clan_back_button", 76, 22, false, true);
    public static final ButtonTextureSet CLAN_ACTION = states("clan_action_button", 120, 24, false, true);
    public static final ButtonTextureSet CLAN_DANGER = states("clan_danger_button", 120, 24, false, true);
    public static final ButtonTextureSet CLAN_SMALL = states("clan_small_button", 58, 18, false, true);
    public static final ButtonTextureSet CLAN_REQUEST_ACCEPT =
            states("clan_request_accept", 40, 14, false, true);
    public static final ButtonTextureSet CLAN_REQUEST_REJECT =
            states("clan_request_reject", 44, 14, false, true);
    public static final ButtonTextureSet CLAN_KICK = states("clan_kick", 42, 14, false, true);
    public static final ButtonTextureSet CLAN_COLOR = colorStates();
    public static final ButtonTextureSet CONFIRM_CANCEL = states("confirm_cancel", 96, 24, false, true);
    public static final ButtonTextureSet CONFIRM_PRIMARY = states("confirm_primary", 96, 24, false, true);

    private static final Map<String, ButtonTextureSet> NAVIGATION = Map.of(
            "classes", NAV_CLASSES,
            "shop", NAV_SHOP,
            "vip", NAV_VIP,
            "clans", NAV_CLANS,
            "profile", NAV_PROFILE
    );

    private TabletButtonTextures() {
    }

    public static ButtonTextureSet navigation(String pageKey) {
        ButtonTextureSet textures = NAVIGATION.get(pageKey);
        if (textures == null) throw new IllegalArgumentException("Unknown tablet navigation page " + pageKey);
        return textures;
    }

    public static List<ButtonTextureSpec> all() {
        LinkedHashMap<ResourceLocation, ButtonTextureSpec> textures = new LinkedHashMap<>();
        add(textures, CLASS_BUTTON);
        for (ButtonTextureSet set : List.of(
                NAV_CLASSES, NAV_SHOP, NAV_VIP, NAV_CLANS, NAV_PROFILE,
                RTP, CLAN_ROW, CLAN_CREATE, CLAN_BACK, CLAN_ACTION, CLAN_DANGER, CLAN_SMALL,
                CLAN_REQUEST_ACCEPT, CLAN_REQUEST_REJECT, CLAN_KICK, CLAN_COLOR,
                CONFIRM_CANCEL, CONFIRM_PRIMARY
        )) {
            add(textures, set.normal());
            add(textures, set.hover());
            add(textures, set.active());
            add(textures, set.disabled());
        }
        return List.copyOf(textures.values());
    }

    private static ButtonTextureSet states(
            String baseName,
            int width,
            int height,
            boolean supportsActive,
            boolean supportsDisabled
    ) {
        ButtonTextureSpec normal = texture(baseName + ".png", width, height);
        ButtonTextureSpec hover = texture(baseName + "_hover.png", width, height);
        ButtonTextureSpec active = supportsActive
                ? texture(baseName + "_active.png", width, height)
                : normal;
        ButtonTextureSpec disabled = supportsDisabled
                ? texture(baseName + "_disabled.png", width, height)
                : normal;
        return new ButtonTextureSet(normal, hover, active, disabled);
    }

    private static ButtonTextureSet colorStates() {
        return new ButtonTextureSet(
                texture("clan_color.png", 16, 16),
                texture("clan_color_hover.png", 16, 16),
                texture("clan_color_selected.png", 16, 16),
                texture("clan_color_disabled.png", 16, 16)
        );
    }

    private static ButtonTextureSpec texture(String fileName, int width, int height) {
        return new ButtonTextureSpec(
                ResourceLocation.fromNamespaceAndPath("tacticaltablet", ROOT + fileName),
                width,
                height
        );
    }

    private static void add(
            Map<ResourceLocation, ButtonTextureSpec> textures,
            ButtonTextureSpec texture
    ) {
        textures.put(texture.location(), texture);
    }
}
