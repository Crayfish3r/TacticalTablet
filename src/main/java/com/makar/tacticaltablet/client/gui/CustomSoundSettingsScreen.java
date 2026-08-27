package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.client.gui.component.TacticalSlider;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

public final class CustomSoundSettingsScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {

    private static final int PANEL_WIDTH = 650;
    private static final int PANEL_HEIGHT = 310;
    private static final int PANEL_MARGIN = 10;
    private static final int PANEL_PADDING = 14;
    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 5;

    private final Screen parent;
    private final Options options;
    private final UiFrameClock frameClock = new UiFrameClock();
    private Layout layout;

    public CustomSoundSettingsScreen(Screen parent, Options options) {
        super(Component.translatable("options.sounds.title"));
        this.parent = parent;
        this.options = options;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        int y = layout.firstControlY();

        addVolumeSlider(SoundSource.MASTER, layout.contentX(), y, layout.contentWidth());
        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;

        List<SoundSource> sources = new ArrayList<>(List.of(SoundSource.values()));
        sources.remove(SoundSource.MASTER);
        for (int index = 0; index < sources.size(); index += 2) {
            addVolumeSlider(sources.get(index), layout.contentX(), y, layout.columnWidth());
            if (index + 1 < sources.size()) {
                addVolumeSlider(sources.get(index + 1), layout.rightColumnX(), y, layout.columnWidth());
            }
            y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        }

        addAudioDeviceButton(layout.contentX(), y, layout.contentWidth());
        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        addBooleanButton(options.showSubtitles(), "options.showSubtitles",
                layout.contentX(), y, layout.columnWidth());
        addBooleanButton(options.directionalAudio(), "options.directionalAudio",
                layout.rightColumnX(), y, layout.columnWidth());

        int backWidth = Math.min(180, layout.contentWidth());
        addRenderableWidget(TacticalButton.compact(
                        layout.contentX() + (layout.contentWidth() - backWidth) / 2,
                        layout.backY(),
                        backWidth,
                        Component.translatable("screen.tacticaltablet.common.back"),
                        ignored -> onClose()
                )
                .withAccentBar(true));
    }

    private void addVolumeSlider(SoundSource source, int x, int y, int width) {
        OptionInstance<Double> option = options.getSoundSourceOptionInstance(source);
        addRenderableWidget(new TacticalSlider(
                x,
                y,
                width,
                TacticalTheme.CONTROL_HEIGHT,
                option.get(),
                value -> Component.translatable(
                        "screen.tacticaltablet.settings.sound_value",
                        Component.translatable("soundCategory." + source.getName()),
                        Math.round(value * 100.0D)
                ),
                option::set
        ));
    }

    private void addAudioDeviceButton(int x, int y, int width) {
        List<String> devices = new ArrayList<>();
        devices.add("");
        devices.addAll(Minecraft.getInstance().getSoundManager().getAvailableSoundDevices());

        TacticalButton[] holder = new TacticalButton[1];
        holder[0] = TacticalButton.standard(
                        x,
                        y,
                        width,
                        audioDeviceLabel(options.soundDevice().get()),
                        ignored -> {
                            String current = options.soundDevice().get();
                            int index = devices.indexOf(current);
                            String next = devices.get((Math.max(-1, index) + 1) % devices.size());
                            options.soundDevice().set(next);
                            holder[0].setMessage(audioDeviceLabel(next));
                        }
                )
                .withAccentBar(true);
        addRenderableWidget(holder[0]);
    }

    private static Component audioDeviceLabel(String device) {
        Component value = device.isEmpty()
                ? Component.translatable("options.audioDevice.default")
                : Component.literal(device.replaceFirst("^OpenAL Soft on ", ""));
        return Component.translatable("options.audioDevice").append(": ").append(value);
    }

    private void addBooleanButton(OptionInstance<Boolean> option, String labelKey,
                                  int x, int y, int width) {
        TacticalButton[] holder = new TacticalButton[1];
        holder[0] = TacticalButton.standard(
                        x,
                        y,
                        width,
                        booleanLabel(labelKey, option.get()),
                        ignored -> {
                            boolean next = !option.get();
                            option.set(next);
                            holder[0].setMessage(booleanLabel(labelKey, next));
                        }
                )
                .withAccentBar(true);
        addRenderableWidget(holder[0]);
    }

    private static Component booleanLabel(String labelKey, boolean value) {
        return Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    public void onClose() {
        options.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame,
                com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(),
                    layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.titleY(),
                    TacticalUi.currentPalette().textPrimary());
            TacticalUi.drawDivider(graphics, layout.contentX(), layout.dividerY(),
                    layout.contentWidth(), false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, int columnWidth, int rightColumnX,
                          int titleY, int dividerY, int firstControlY, int backY) {
        private static Layout calculate(int screenWidth, int screenHeight) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, screenWidth - PANEL_MARGIN * 2));
            int panelHeight = Math.min(PANEL_HEIGHT, Math.max(1, screenHeight - PANEL_MARGIN * 2));
            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentX = panelX + PANEL_PADDING;
            int contentWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
            int columnWidth = Math.max(1, (contentWidth - COLUMN_GAP) / 2);
            int rightColumnX = contentX + columnWidth + COLUMN_GAP;
            return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                    columnWidth, rightColumnX, panelY + 9, panelY + 27, panelY + 35,
                    panelY + panelHeight - PANEL_PADDING - TacticalTheme.CONTROL_HEIGHT_COMPACT);
        }
    }
}
