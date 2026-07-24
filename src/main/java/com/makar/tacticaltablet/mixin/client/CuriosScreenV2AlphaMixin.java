package com.makar.tacticaltablet.mixin.client;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;

@Pseudo
@Mixin(CuriosScreenV2.class)
abstract class CuriosScreenV2AlphaMixin {
    private static final String RENDER_BG =
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V";
    private static final String PLAYER_MODEL_RENDER =
            "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;"
                    + "renderEntityInInventoryFollowsMouse"
                    + "(Lnet/minecraft/client/gui/GuiGraphics;IIIFF"
                    + "Lnet/minecraft/world/entity/LivingEntity;)V";

    @Inject(method = RENDER_BG, at = @At("HEAD"), require = 1)
    private void tacticaltablet$beginBackgroundBlend(
            GuiGraphics guiGraphics,
            float partialTicks,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        GuiTextureRenderer.beginAlphaBlend(guiGraphics);
    }

    @Inject(
            method = RENDER_BG,
            at = @At(
                    value = "INVOKE",
                    target = PLAYER_MODEL_RENDER,
                    shift = At.Shift.BEFORE
            ),
            require = 1
    )
    private void tacticaltablet$pauseBlendForPlayerModel(
            GuiGraphics guiGraphics,
            float partialTicks,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        GuiTextureRenderer.endAlphaBlend(guiGraphics);
    }

    @Inject(
            method = RENDER_BG,
            at = @At(
                    value = "INVOKE",
                    target = PLAYER_MODEL_RENDER,
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void tacticaltablet$resumeBackgroundBlend(
            GuiGraphics guiGraphics,
            float partialTicks,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        GuiTextureRenderer.beginAlphaBlend(guiGraphics);
    }

    @Inject(method = RENDER_BG, at = @At("RETURN"), require = 1)
    private void tacticaltablet$endBackgroundBlend(
            GuiGraphics guiGraphics,
            float partialTicks,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        GuiTextureRenderer.endAlphaBlend(guiGraphics);
    }
}
