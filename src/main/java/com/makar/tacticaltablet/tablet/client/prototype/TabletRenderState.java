package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Exact state scope for offscreen GUI and immediate emissive display drawing. */
final class TabletRenderState implements AutoCloseable {
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final VertexSorting sorting = RenderSystem.getVertexSorting();
    private final ShaderInstance shader = RenderSystem.getShader();
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final int[] shaderTextures = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2)};
    private final int[] textures = new int[3], viewport = new int[4], scissor = new int[4];
    private final float[] clear = new float[4];
    private final boolean[] writeMask = new boolean[4];
    private final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

    TabletRenderState() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var ints = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, ints); ints.get(viewport);
            ints.clear(); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, ints); ints.get(scissor);
            var floats = stack.mallocFloat(4);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, floats); floats.get(clear);
            var bytes = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, bytes);
            for (int i = 0; i < 4; i++) writeMask[i] = bytes.get(i) != 0;
        }
        for (int i = 0; i < textures.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GlStateManager._activeTexture(activeTexture);
        RenderSystem.getModelViewStack().pushPose();
    }
    @Override public void close() {
        // Invalidate ShaderInstance's program cache before restoring the actual GL program.
        // Applying the old shader here would overwrite the saved blend mode and sampler bindings.
        ShaderInstance current = RenderSystem.getShader();
        if (current != null) current.clear();
        RenderSystem.setShader(() -> shader);
        ProgramManager.glUseProgram(program);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.getModelViewStack().popPose(); RenderSystem.applyModelViewMatrix();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(depthWrite);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        if (!clipped) RenderSystem.disableScissor();
        RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]); RenderSystem.clearDepth(clearDepth);
        RenderSystem.colorMask(writeMask[0], writeMask[1], writeMask[2], writeMask[3]);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.setShaderTexture(i, shaderTextures[i]);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i); GlStateManager._bindTexture(textures[i]);
        }
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._glBindVertexArray(vao); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        BufferUploader.invalidate();
    }
}
