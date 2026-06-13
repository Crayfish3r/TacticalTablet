package com.makar.tacticaltablet.corpse.client;

import com.makar.tacticaltablet.corpse.CorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public class CorpseRenderer extends LivingEntityRenderer<CorpseEntity, PlayerModel<CorpseEntity>> {

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.25F);
        addLayer(new DarkRedCorpseLayer(this));
    }

    @Override
    public void render(CorpseEntity corpse, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        model.setAllVisible(true);
        model.crouching = false;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(corpse, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        if (corpse.createGameProfile().getProperties().containsKey("textures")) {
            return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(corpse.createGameProfile());
        }
        return DefaultPlayerSkin.getDefaultSkin(corpse.getUUID());
    }

    @Override
    protected boolean shouldShowName(CorpseEntity corpse) {
        return false;
    }

    private static class DarkRedCorpseLayer extends RenderLayer<CorpseEntity, PlayerModel<CorpseEntity>> {

        private DarkRedCorpseLayer(RenderLayerParent<CorpseEntity, PlayerModel<CorpseEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(
                PoseStack poseStack,
                MultiBufferSource buffer,
                int packedLight,
                CorpseEntity corpse,
                float limbSwing,
                float limbSwingAmount,
                float partialTick,
                float ageInTicks,
                float netHeadYaw,
                float headPitch
        ) {
            VertexConsumer vertex = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(corpse)));
            getParentModel().renderToBuffer(
                    poseStack,
                    vertex,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0.38F,
                    0.0F,
                    0.0F,
                    0.55F
            );
        }
    }
}
