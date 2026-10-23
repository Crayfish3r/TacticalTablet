package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** One reusable target and isolated GUI buffers. No texture registration or per-frame allocation. */
final class TabletDisplayTarget {
    private static TextureTarget target;
    private static GuiGraphics graphics;
    private static TabletPageShell renderedPage;
    private TabletDisplayTarget() { }

    static void update(TabletPageShell page, TabletPrototypeModel.DisplaySurface display, MultiBufferSource worldBuffers) {
        if (page == null) return;
        int limit = Math.min(TabletTerminalTheme.MAX_TEXTURE_SIZE, RenderSystem.maxSupportedTextureSize());
        float aspect = display.height() / display.width();
        int requestedWidth = Math.max(TabletTerminalTheme.LOGICAL_WIDTH * TabletTerminalTheme.RESOLUTION_SCALE,
                Minecraft.getInstance().getWindow().getWidth());
        int step = TabletTerminalTheme.TEXTURE_ALLOCATION_STEP;
        requestedWidth = ((requestedWidth + step - 1) / step) * step;
        int width = Math.max(1, Math.min((int) (limit / Math.max(1, aspect)),
                requestedWidth));
        int height = Math.max(1, Math.round(width * aspect));
        boolean recreate = target == null || target.width != width || target.height != height;
        if (!recreate && renderedPage == page && !page.dirty()) return;
        // End pending world/item draws under their original framebuffer and matrices.
        flush(worldBuffers);
        try (var ignored = new TabletRenderState()) {
            if (recreate) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_LINEAR);
                target.setClearColor(0, 0, 0, 1);
            }
            if (graphics == null) graphics = new GuiGraphics(Minecraft.getInstance(),
                    MultiBufferSource.immediate(new BufferBuilder(256 * 1024)));
            RenderSystem.disableScissor(); RenderSystem.colorMask(true, true, true, true);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, TabletTerminalTheme.LOGICAL_WIDTH,
                    TabletTerminalTheme.logicalHeight(display), 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().setIdentity(); RenderSystem.applyModelViewMatrix();
            graphics.pose().setIdentity(); RenderSystem.setShaderColor(1, 1, 1, 1);
            try { page.render(graphics); }
            finally { graphics.flush(); } // Never let GUI vertices escape into the world framebuffer.
            renderedPage = page; page.rendered();
        }
    }

    static void draw(Matrix4f matrix, TabletPrototypeModel.DisplaySurface display, MultiBufferSource buffers) {
        if (target == null) return;
        flush(buffers); // Body geometry must finish before the opaque display quad.
        try (var ignored = new TabletRenderState()) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(true);
            RenderSystem.disableCull(); RenderSystem.disableBlend();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < 4; i++) {
                var p = display.corners().get(i);
                // FBO images have bottom-left UV origin; logical UI and picking have top-left origin.
                builder.vertex(matrix, p.x(), p.y(), p.z()).uv(i >= 2 ? 1 : 0, i == 0 || i == 3 ? 1 : 0).endVertex();
            }
            BufferUploader.drawWithShader(builder.end()); // Unlit shader: body alone uses world light.
        }
    }
    private static void flush(MultiBufferSource source) {
        if (source instanceof MultiBufferSource.BufferSource buffer) buffer.endBatch();
        else throw new IllegalStateException("Tablet display requires flushable first-person buffers");
    }
    static void release() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(TabletDisplayTarget::release); return; }
        if (target != null) {
            try (var ignored = new TabletRenderState()) { target.destroyBuffers(); }
            target = null;
        }
        renderedPage = null;
        TabletDisplayProjection.clear();
    }
}
