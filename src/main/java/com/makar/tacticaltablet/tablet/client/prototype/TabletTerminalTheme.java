package com.makar.tacticaltablet.tablet.client.prototype;

import com.google.gson.JsonParser;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.EnumMap;
import java.util.Locale;

/** Appearance only. Optional tablet/terminal_skins.json supplies nine-slice skins per kind/state. */
public final class TabletTerminalTheme {
    public static final int LOGICAL_WIDTH = 640;
    public static final int RESOLUTION_SCALE = 2, MAX_TEXTURE_SIZE = 2048, TEXTURE_ALLOCATION_STEP = 256;
    public static final int MARGIN = 16, ROW_HEIGHT = 40, BUTTON_HEIGHT = 32, GAP = 8;
    public static final float DETAIL_SCALE = 1, TEXT_SCALE = 1.12F, TITLE_SCALE = 1.6F;
    public static final int BACKGROUND = 0xFF14191B, PANEL = 0xFF20272A, BORDER = 0xFF444D46;
    public static final int TEXT = 0xFFE7E9DF, SECONDARY = 0xFFB6AF96, MUTED = 0xFF8F9791;
    public static final int OLIVE = 0xFF899768, DISABLED = 0xFF636B65, ERROR = 0xFFD79783;
    public static final int SCRIM = 0xD4101515;
    public enum Kind { SCREEN, PANEL, ROW, BUTTON, PRIMARY }
    public enum State { NORMAL, HOVER, PRESSED, SELECTED, DISABLED }
    private record Skin(ResourceLocation texture, int width, int height, int border) { }
    private static final EnumMap<Kind, EnumMap<State, Skin>> SKINS = new EnumMap<>(Kind.class);
    private TabletTerminalTheme() { }
    public static float logicalHeight(TabletPrototypeModel.DisplaySurface surface) {
        return LOGICAL_WIDTH * surface.height() / surface.width();
    }

    static int tierColor(int tier) {
        return switch (com.makar.tacticaltablet.progression.ClassTier.clamp(tier)) {
            case BASIC -> 0xFF27BE29;
            case RARE -> 0xFF0A15A9;
            case EPIC -> 0xFF9B00F5;
            case LEGEND -> 0xFFFFBB24;
            case MONSTER -> 0xFFD10A00;
        };
    }
    static String tierName(int tier) {
        var value = com.makar.tacticaltablet.progression.ClassTier.clamp(tier);
        return value == com.makar.tacticaltablet.progression.ClassTier.BASIC ? "BASE" : value.displayName();
    }
    static String fit(String value, int width) {
        return net.minecraft.client.Minecraft.getInstance().font.plainSubstrByWidth(value, width);
    }
    static void text(GuiGraphics g, String value, int x, int y, int color, float scale) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font, value, 0, 0, color, false);
        g.pose().popPose();
    }
    static void icon(GuiGraphics g, ResourceLocation icon, int x, int y) {
        g.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        g.blit(icon, x, y, 24, 24, 0, 0, 16, 16, 16, 16);
        g.flush(); RenderSystem.disableBlend();
    }
    static void tierRow(GuiGraphics g, int x, int y, int w, int h, int tier, int next, float progress,
                        boolean hover, boolean pressed, boolean selected, boolean disabled) {
        g.fill(x, y, x + w, y + h, tierColor(tier));
        g.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        if (next >= 0 && progress > 0) {
            int fill = Math.min(w - 2, Math.max(0, Math.round((w - 2) * progress)));
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, 0x80000000 | (tierColor(next) & 0xFFFFFF));
        }
        // A neutral veil preserves tier identity and keeps every text/icon layer readable.
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, pressed ? 0x80000000 : disabled ? 0x78000000 : 0x50000000);
        if (hover) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x22FFFFFF);
        g.flush(); RenderSystem.disableBlend();
        g.renderOutline(x, y, w, h, selected || pressed ? TEXT : hover ? SECONDARY : BORDER);
        if (selected) g.fill(x, y, x + 3, y + h, TEXT);
    }
    static void drawButton(GuiGraphics g, TabletPreparationPage.Rect rect, String label, boolean enabled, boolean hover, boolean pressed) {
        background(g, Kind.BUTTON, !enabled ? State.DISABLED : pressed ? State.PRESSED : hover ? State.HOVER : State.NORMAL,
                rect.x(), rect.y(), rect.width(), rect.height());
        int width = (int) (net.minecraft.client.Minecraft.getInstance().font.width(label) * TEXT_SCALE);
        text(g, label, rect.x() + (rect.width() - width) / 2, rect.y() + 10, enabled ? TEXT : DISABLED, TEXT_SCALE);
    }
    static void scrollbar(GuiGraphics g, TabletPreparationPage.Rect list, int capacity, int count, int offset) {
        int x = list.x() + list.width() - 5;
        g.fill(x, list.y(), x + 3, list.y() + list.height(), BORDER);
        int thumb = Math.max(18, list.height() * capacity / Math.max(capacity, count));
        int y = list.y() + (list.height() - thumb) * offset / Math.max(1, count - capacity);
        g.fill(x, y, x + 3, y + thumb, OLIVE);
    }

    // Resource shape: {"button":{"normal":{"texture":"namespace:path.png","width":32,"height":32,"border":6},...},...}
    static void reload(ResourceManager resources) {
        SKINS.clear();
        var resource = resources.getResource(new ResourceLocation(TacticalTabletMod.MODID, "tablet/terminal_skins.json"));
        if (resource.isEmpty()) return;
        try (var reader = resource.get().openAsReader()) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var component : json.entrySet()) {
                Kind kind = Kind.valueOf(component.getKey().toUpperCase(Locale.ROOT));
                var states = new EnumMap<State, Skin>(State.class);
                for (var entry : component.getValue().getAsJsonObject().entrySet()) {
                    var data = entry.getValue().getAsJsonObject();
                    int w = data.get("width").getAsInt(), h = data.get("height").getAsInt(), b = data.get("border").getAsInt();
                    ResourceLocation texture = new ResourceLocation(data.get("texture").getAsString());
                    if (w < 1 || h < 1 || b < 0 || b * 2 >= Math.min(w, h) || resources.getResource(texture).isEmpty())
                        throw new IllegalArgumentException("Invalid terminal nine-slice " + texture);
                    states.put(State.valueOf(entry.getKey().toUpperCase(Locale.ROOT)), new Skin(texture, w, h, b));
                }
                SKINS.put(kind, states);
            }
        } catch (Exception exception) {
            SKINS.clear();
            TacticalTabletMod.LOGGER.error("Cannot load terminal skins; using programmatic backgrounds", exception);
        }
    }

    static void background(GuiGraphics g, Kind kind, State state, int x, int y, int w, int h) {
        var states = SKINS.get(kind);
        Skin skin = states == null ? null : states.getOrDefault(state, states.get(State.NORMAL));
        if (skin != null) {
            g.flush();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            nineSlice(g, skin, x, y, w, h);
            RenderSystem.disableBlend();
            return;
        }
        if (kind == Kind.SCREEN) { g.fill(x, y, x + w, y + h, BACKGROUND); return; }
        int fill = kind == Kind.PRIMARY ? 0xFF46543A : PANEL;
        int edge = state == State.SELECTED ? OLIVE : BORDER;
        if (state == State.HOVER) { fill = 0xFF323D32; edge = OLIVE; }
        if (state == State.PRESSED) { fill = 0xFF1D281B; edge = TEXT; }
        if (state == State.DISABLED) { fill = 0xFF222728; edge = 0xFF363D38; }
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, edge);
        if (state == State.SELECTED) g.fill(x, y, x + 3, y + h, OLIVE);
    }

    private static void nineSlice(GuiGraphics g, Skin skin, int x, int y, int w, int h) {
        int b = skin.border, dx = Math.min(b, w / 2), dy = Math.min(b, h / 2);
        for (int row = 0; row < 3; row++) {
            int sy = row == 0 ? 0 : row == 1 ? b : skin.height - b;
            int sh = row == 1 ? skin.height - 2 * b : b;
            int ty = row == 0 ? y : row == 1 ? y + dy : y + h - dy;
            int th = row == 1 ? h - 2 * dy : dy;
            for (int column = 0; column < 3; column++) {
                int sx = column == 0 ? 0 : column == 1 ? b : skin.width - b;
                int sw = column == 1 ? skin.width - 2 * b : b;
                int tx = column == 0 ? x : column == 1 ? x + dx : x + w - dx;
                int tw = column == 1 ? w - 2 * dx : dx;
                if (tw > 0 && th > 0 && sw > 0 && sh > 0)
                    g.blit(skin.texture, tx, ty, tw, th, sx, sy, sw, sh, skin.width, skin.height);
            }
        }
    }
}
