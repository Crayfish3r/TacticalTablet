package com.makar.tacticaltablet.mixin.client;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
abstract class InventoryScreenAlphaMixin {
    private static final String RENDER_BG =
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V";
    private static final String BACKGROUND_BLIT =
            "Lnet/minecraft/client/gui/GuiGraphics;blit"
                    + "(Lnet/minecraft/resources/ResourceLocation;IIIIII)V";

    @Inject(
            method = RENDER_BG,
            at = @At(
                    value = "INVOKE",
                    target = BACKGROUND_BLIT,
                    shift = At.Shift.BEFORE
            ),
            require = 1
    )
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
                    target = BACKGROUND_BLIT,
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
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
