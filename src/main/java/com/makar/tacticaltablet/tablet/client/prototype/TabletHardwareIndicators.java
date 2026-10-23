package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Opaque per-part material replaces (never overlays) the authored indicator faces in a session. */
final class TabletHardwareIndicators {
    static final int SELECTED_COLOR = 0xFF789D65;
    static final int OFF_COLOR = 0xFF1A211D;

    private TabletHardwareIndicators() { }

    // Called after TabletDisplayTarget.draw has flushed the body and finished its immediate display draw.
    static void draw(Matrix4f matrix, TabletPrototypeModel.Model model, TabletPage selected, boolean open) {
        try (var ignored = new TabletRenderState()) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(true);
            RenderSystem.disableCull(); RenderSystem.disableBlend();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (var entry : model.indicators().entrySet()) {
                int color = open && entry.getKey().equals(selected.indicator) ? SELECTED_COLOR : OFF_COLOR;
                int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                for (var vertex : entry.getValue())
                    builder.vertex(matrix, vertex.x(), vertex.y(), vertex.z()).color(r, g, b, 255).endVertex();
            }
            BufferUploader.drawWithShader(builder.end());
        }
    }
}
