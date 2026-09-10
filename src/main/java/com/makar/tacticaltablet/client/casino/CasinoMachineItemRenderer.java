package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoMachineAnimationState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Reuses the world renderer's unchanged geometry and texture for the inventory item. */
public final class CasinoMachineItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final CasinoMachineAnimationState idle = new CasinoMachineAnimationState();
    public CasinoMachineItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                       MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0, -0.5, 0);
        CasinoMachineRenderer.renderModel(idle, Direction.NORTH, 80, pose, buffers, light, overlay);
        pose.popPose();
    }
}
