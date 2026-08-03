package com.makar.tacticaltablet.tablet.client.ui.render;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** Lexically owns one GuiGraphics scissor stack entry. */
public final class ScissorScope implements AutoCloseable {
    private final GuiGraphics graphics;
    private boolean closed;

    private ScissorScope(GuiGraphics graphics, int x, int y, int width, int height) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Scissor dimensions must be positive");
        }
        graphics.enableScissor(x, y, x + width, y + height);
    }

    public static ScissorScope open(GuiGraphics graphics, int x, int y, int width, int height) {
        return new ScissorScope(graphics, x, y, width, height);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        graphics.disableScissor();
    }
}
