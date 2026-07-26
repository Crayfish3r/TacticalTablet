package com.makar.tacticaltablet.mixin.client;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;

@Pseudo
@Mixin(CuriosScreenV2.class)
abstract class CuriosScreenV2AlphaMixin {
    private static final String RENDER_BG =
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V";
    private static final String BACKGROUND_BLIT =
            "Lnet/minecraft/client/gui/GuiGraphics;blit"
                    + "(Lnet/minecraft/resources/ResourceLocation;IIIIII)V";
    private static final String RENDER_EXTRA_SLOTS =
            "Lnet/minecraftforge/common/util/LazyOptional;ifPresent"
                    + "(Lnet/minecraftforge/common/util/NonNullConsumer;)V";

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

    @Redirect(
            method = RENDER_BG,
            at = @At(value = "INVOKE", target = RENDER_EXTRA_SLOTS, remap = false),
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void tacticaltablet$renderExtraSlotsWithAlpha(
            LazyOptional optional,
            NonNullConsumer renderer,
            GuiGraphics guiGraphics,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        optional.ifPresent(value -> GuiTextureRenderer.withAlphaBlend(
                guiGraphics,
                () -> renderer.accept(value)
        ));
    }
}
