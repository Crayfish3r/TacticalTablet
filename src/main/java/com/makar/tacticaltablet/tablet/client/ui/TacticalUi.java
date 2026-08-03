package com.makar.tacticaltablet.tablet.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Stateless primitives for drawing the texture-free tactical UI. */
public final class TacticalUi {
    private static final ThreadLocal<Deque<UiFrameContext>> FRAME_CONTEXTS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<UiFrameClock> LEGACY_CLOCK =
            ThreadLocal.withInitial(UiFrameClock::new);
    private static final ThreadLocal<UiFrameContext> LEGACY_CONTEXT = new ThreadLocal<>();

    private TacticalUi() {
    }

    /**
     * Legacy bridge for screens not yet migrated to an explicit frame scope.
     * New top-level renderers must use {@link #openFrame(UiFrameContext)}.
     */
    @Deprecated(forRemoval = false)
    public static void beginFrame() {
        LEGACY_CONTEXT.set(LEGACY_CLOCK.get().nextFrame(Util.getMillis(), false));
    }

    @Deprecated(forRemoval = false)
    public static float frameDeltaSeconds() {
        return currentFrame().animationDeltaSeconds();
    }

    @Deprecated(forRemoval = false)
    public static void resetFrameClock() {
        LEGACY_CLOCK.remove();
        LEGACY_CONTEXT.remove();
    }

    /** Opens a frame scope; nested tactical renderers reuse the outer context. */
    public static FrameScope openFrame(UiFrameContext requestedContext) {
        Objects.requireNonNull(requestedContext, "requestedContext");
        Deque<UiFrameContext> contexts = FRAME_CONTEXTS.get();
        UiFrameContext effectiveContext = contexts.isEmpty() ? requestedContext : contexts.peek();
        contexts.push(effectiveContext);
        return new FrameScope(contexts);
    }

    public static UiFrameContext currentFrame() {
        Deque<UiFrameContext> contexts = FRAME_CONTEXTS.get();
        if (!contexts.isEmpty()) return contexts.peek();
        UiFrameContext legacy = LEGACY_CONTEXT.get();
        return legacy == null ? UiFrameContext.INITIAL : legacy;
    }

    public static void drawBackdrop(GuiGraphics graphics, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fillGradient(0, 0, width, height, 0xE3070C12, TacticalTheme.BACKDROP);
    }

    public static void fillCutCornerRect(GuiGraphics graphics, int x, int y, int width, int height,
                                         int cut, int color) {
        if (width <= 0 || height <= 0) return;
        int safeCut = Math.max(0, Math.min(cut, Math.min(width / 2, height / 2)));
        if (safeCut == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + safeCut, y, x + width - safeCut, y + height, color);
        graphics.fill(x, y + safeCut, x + width, y + height - safeCut, color);
    }

    public static void drawCutCornerBorder(GuiGraphics graphics, int x, int y, int width, int height,
                                            int cut, int borderWidth, int borderColor, int fillColor) {
        if (width <= 0 || height <= 0) return;
        int border = Math.max(1, Math.min(borderWidth, Math.min(width, height) / 2));
        fillCutCornerRect(graphics, x, y, width, height, cut, borderColor);
        if (width > border * 2 && height > border * 2) {
            fillCutCornerRect(graphics, x + border, y + border, width - border * 2, height - border * 2,
                    Math.max(0, cut - border), fillColor);
        }
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        drawSoftShadow(graphics, x, y, width, height);
        drawCutCornerBorder(graphics, x, y, width, height, TacticalTheme.CORNER_CUT,
                TacticalTheme.BORDER_WIDTH, TacticalTheme.BORDER, TacticalTheme.SURFACE);
    }

    public static void drawCard(GuiGraphics graphics, int x, int y, int width, int height,
                                ControlVisualState state, int accent, float transition) {
        if (width <= 0 || height <= 0) return;
        float amount = clamp01(transition);
        int base = !state.enabled() ? TacticalTheme.SURFACE_DISABLED : TacticalTheme.SURFACE_RAISED;
        int target = state.selected() ? TacticalTheme.SURFACE_SELECTED : TacticalTheme.SURFACE_HOVER;
        int fill = !state.emphasized() ? base : lerpArgb(base, target, amount);
        int border = !state.enabled() ? TacticalTheme.BORDER_DISABLED
                : lerpArgb(TacticalTheme.BORDER, accent, amount);
        drawCutCornerBorder(graphics, x, y, width, height, TacticalTheme.CORNER_CUT,
                TacticalTheme.BORDER_WIDTH, border, fill);
        if (state.focused() && state.enabled()) drawFocusRing(graphics, x, y, width, height, accent);
    }

    @Deprecated(forRemoval = false)
    public static void drawCard(GuiGraphics graphics, int x, int y, int width, int height,
                                ControlState state, int accent, float transition) {
        drawCard(graphics, x, y, width, height, ControlVisualState.fromLegacy(state), accent, transition);
    }

    public static void drawButton(GuiGraphics graphics, int x, int y, int width, int height,
                                  ControlVisualState state, float hover, float selected, int accent) {
        if (width <= 0 || height <= 0) return;
        float emphasis = Math.max(clamp01(hover), clamp01(selected));
        int fill;
        int border;
        if (!state.enabled()) {
            fill = TacticalTheme.SURFACE_DISABLED;
            border = TacticalTheme.BORDER_DISABLED;
        } else {
            int target = state.pressed() ? TacticalTheme.ACCENT_DARK
                    : state.selected() ? TacticalTheme.SURFACE_SELECTED : TacticalTheme.SURFACE_HOVER;
            fill = lerpArgb(TacticalTheme.SURFACE_RAISED, target, emphasis);
            border = lerpArgb(TacticalTheme.BORDER, accent, emphasis);
        }
        drawCutCornerBorder(graphics, x, y, width, height, TacticalTheme.CORNER_CUT,
                TacticalTheme.BORDER_WIDTH, border, fill);
        if (state.focused() && state.enabled()) drawFocusRing(graphics, x, y, width, height, accent);
    }

    @Deprecated(forRemoval = false)
    public static void drawButton(GuiGraphics graphics, int x, int y, int width, int height,
                                  ControlState state, float hover, float selected, int accent) {
        drawButton(graphics, x, y, width, height, ControlVisualState.fromLegacy(state), hover, selected, accent);
    }

    public static void drawDivider(GuiGraphics graphics, int x, int y, int length, boolean vertical) {
        if (length <= 0) return;
        if (vertical) graphics.fill(x, y, x + 1, y + length, TacticalTheme.BORDER);
        else graphics.fill(x, y, x + length, y + 1, TacticalTheme.BORDER);
    }

    public static void drawBadge(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        drawCutCornerBorder(graphics, x, y, width, height, Math.min(2, height / 2), 1, color,
                withAlpha(color, 0x42));
    }

    public static void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height,
                                       float progress, int color) {
        if (width <= 0 || height <= 0) return;
        fillCutCornerRect(graphics, x, y, width, height, Math.min(2, height / 2), TacticalTheme.SURFACE_DISABLED);
        int filled = Math.round(width * clamp01(progress));
        if (filled > 0) fillCutCornerRect(graphics, x, y, filled, height,
                Math.min(2, Math.min(filled, height) / 2), color);
    }

    public static void drawAccentBar(GuiGraphics graphics, int x, int y, int height, int color) {
        if (height > 0) graphics.fill(x, y, x + 2, y + height, color);
    }

    public static void drawSoftShadow(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        fillCutCornerRect(graphics, x + 3, y + 4, width, height, TacticalTheme.CORNER_CUT,
                withAlpha(TacticalTheme.SHADOW, 0x28));
        fillCutCornerRect(graphics, x + 2, y + 2, width, height, TacticalTheme.CORNER_CUT,
                withAlpha(TacticalTheme.SHADOW, 0x38));
    }

    public static void drawFocusRing(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        int cut = Math.min(TacticalTheme.CORNER_CUT + 1, Math.min(width / 2, height / 2));
        int ring = withAlpha(color, 0xE8);
        graphics.fill(x + cut, y - 1, x + width - cut, y, ring);
        graphics.fill(x + cut, y + height, x + width - cut, y + height + 1, ring);
        graphics.fill(x - 1, y + cut, x, y + height - cut, ring);
        graphics.fill(x + width, y + cut, x + width + 1, y + height - cut, ring);
    }

    public static int lerpArgb(int start, int end, float amount) {
        float t = clamp01(amount);
        int a = Math.round(channel(start, 24) + (channel(end, 24) - channel(start, 24)) * t);
        int r = Math.round(channel(start, 16) + (channel(end, 16) - channel(start, 16)) * t);
        int g = Math.round(channel(start, 8) + (channel(end, 8) - channel(start, 8)) * t);
        int b = Math.round(channel(start, 0) + (channel(end, 0) - channel(start, 0)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 0x00FFFFFF;
    }

    public static void withScissor(GuiGraphics graphics, int x, int y, int width, int height, Runnable render) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(render, "render");
        if (width <= 0 || height <= 0) return;
        try (ScissorScope ignored = ScissorScope.open(graphics, x, y, width, height)) {
            render.run();
        }
    }

    private static int channel(int color, int shift) {
        return color >> shift & 0xFF;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record ControlVisualState(
            boolean enabled,
            boolean hovered,
            boolean focused,
            boolean pressed,
            boolean selected
    ) {
        public boolean emphasized() {
            return enabled && (hovered || focused || pressed || selected);
        }

        private static ControlVisualState fromLegacy(ControlState state) {
            Objects.requireNonNull(state, "state");
            return new ControlVisualState(
                    state != ControlState.DISABLED,
                    state == ControlState.HOVERED,
                    state == ControlState.FOCUSED,
                    state == ControlState.PRESSED,
                    state == ControlState.SELECTED
            );
        }
    }

    /** Legacy single-axis state retained while existing screens migrate to {@link ControlVisualState}. */
    @Deprecated(forRemoval = false)
    public enum ControlState {
        NORMAL, HOVERED, FOCUSED, PRESSED, SELECTED, DISABLED
    }

    public static final class FrameScope implements AutoCloseable {
        private final Deque<UiFrameContext> contexts;
        private boolean closed;

        private FrameScope(Deque<UiFrameContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (contexts.isEmpty()) throw new IllegalStateException("Unbalanced UI frame scope");
            contexts.pop();
            if (contexts.isEmpty()) FRAME_CONTEXTS.remove();
        }
    }
}
