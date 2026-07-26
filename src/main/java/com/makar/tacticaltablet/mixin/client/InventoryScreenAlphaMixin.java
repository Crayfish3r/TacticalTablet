package com.makar.tacticaltablet.mixin.client;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InventoryScreen.class)
abstract class InventoryScreenAlphaMixin {
    private static final String RENDER_BG =
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V";
    private static final String BACKGROUND_BLIT =
            "Lnet/minecraft/client/gui/GuiGraphics;blit"
                    + "(Lnet/minecraft/resources/ResourceLocation;IIIIII)V";

    @Redirect(
            method = RENDER_BG,
            at = @At(
                    value = "INVOKE",
                    target = BACKGROUND_BLIT
            ),
            require = 0
    )
    private void tacticaltablet$renderBackgroundWithAlpha(
            GuiGraphics guiGraphics,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height
    ) {
        GuiTextureRenderer.withAlphaBlend(
                guiGraphics,
                () -> guiGraphics.blit(texture, x, y, u, v, width, height)
        );
    }
}
