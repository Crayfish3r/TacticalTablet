package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.client.ui.TacticalLayout;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Shared render and lifecycle foundation for server-driven pre-match phase screens. */
abstract class TacticalPhaseScreen extends Screen {
    private static final float GUI_SOUND_VOLUME = 0.0625F;
    private static final ResourceLocation CLICK = new ResourceLocation("tacticaltablet", "click");
    private static final ResourceLocation HOVER = new ResourceLocation("tacticaltablet", "hover");

    private final UiFrameClock frameClock = new UiFrameClock();

    protected TacticalPhaseScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (TacticalUi.FrameScope ignoredFrame = TacticalUi.openFrame(
                frameClock.nextFrame(Util.getMillis(), false));
             GuiTextureRenderer.AlphaBlendScope ignoredBlend = GuiTextureRenderer.openAlphaBlend(graphics)) {
            renderBackground(graphics);
            TacticalUi.drawBackdrop(graphics, width, height);
            renderPhase(graphics, mouseX, mouseY, partialTick);
        }
    }

    protected abstract void renderPhase(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    protected final TacticalLayout.Rect panelBounds(int preferredWidth, int preferredHeight) {
        return TacticalLayout.centeredPanel(width, height, preferredWidth, preferredHeight);
    }

    protected final void renderDefaultWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    protected static void playClick() {
        playSound(CLICK);
    }

    protected static void playHover() {
        playSound(HOVER);
    }

    private static void playSound(ResourceLocation sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(sound), 1.0F, GUI_SOUND_VOLUME));
    }

    @Override
    public final boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }
}
