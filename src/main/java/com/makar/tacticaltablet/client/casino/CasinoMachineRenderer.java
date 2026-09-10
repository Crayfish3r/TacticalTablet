package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoAnimation;
import com.makar.tacticaltablet.casino.CasinoMachineBlock;
import com.makar.tacticaltablet.casino.CasinoMachineBlockEntity;
import com.makar.tacticaltablet.casino.CasinoMachineAnimationState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class CasinoMachineRenderer implements BlockEntityRenderer<CasinoMachineBlockEntity> {
    public CasinoMachineRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(CasinoMachineBlockEntity machine, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        CasinoMachineAnimationState state = machine.animation();
        double elapsed = machine.getLevel() == null ? 80
                : machine.getLevel().getGameTime() - state.startTick() + partialTick;
        renderModel(state, machine.getBlockState().getValue(CasinoMachineBlock.FACING), elapsed,
                pose, buffers, light, overlay);
    }

    static void renderModel(CasinoMachineAnimationState state, net.minecraft.core.Direction facing,
                            double elapsed, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        float angle = switch (facing) {
            case SOUTH -> 180; case EAST -> -90; case WEST -> 90; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(CasinoMachineModel.TEXTURE));
        for (CasinoMachineModel.Part part : CasinoMachineModel.INSTANCE.parts()) {
            pose.pushPose();
            int group = part.movingGroup();
            if (group >= 0 && group < 3) {
                double x = (group - 1) * 5.0 / 16, y = 18.5 / 16, z = -4.4 / 16;
                float degrees = state.spinning() ? (float) CasinoAnimation.reelDegrees(group,
                        state.previous().symbol(group), state.target().symbol(group), elapsed, state.duration(), state.seed())
                        : -state.target().symbol(group) * 45;
                pose.translate(x, y, z);
                pose.mulPose(Axis.XP.rotationDegrees(degrees));
                pose.translate(-x, -y, -z);
            } else if (group == 3 && state.spinning()) {
                pose.translate(9.8 / 16, 18.0 / 16, 0.5 / 16);
                pose.mulPose(Axis.XP.rotationDegrees(CasinoAnimation.leverDegrees(elapsed, state.duration())));
                pose.translate(-9.8 / 16, -18.0 / 16, -0.5 / 16);
            }
            PoseStack.Pose transform = pose.last();
            for (CasinoMachineModel.Vertex v : part.vertices()) {
                consumer.vertex(transform.pose(), v.x(), v.y(), v.z()).color(255, 255, 255, 255)
                        .uv(v.u(), v.v()).overlayCoords(overlay).uv2(light)
                        .normal(transform.normal(), v.nx(), v.ny(), v.nz()).endVertex();
            }
            pose.popPose();
        }
        pose.popPose();
    }
    @Override public int getViewDistance() { return 64; }
}
