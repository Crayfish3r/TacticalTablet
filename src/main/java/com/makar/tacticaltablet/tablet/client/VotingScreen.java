package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.VoteModePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

public class VotingScreen extends Screen {

    public static final int PANEL_W = 220;
    public static final int PANEL_H = 118;
    public static final String PANEL_TEXTURE_PATH = "assets/tacticaltablet/textures/gui/vote_panel.png";

    private static final ResourceLocation PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/vote_panel.png");
    private static final ResourceLocation CLICK =
            new ResourceLocation("tacticaltablet", "click");
    private static final ResourceLocation HOVER =
            new ResourceLocation("tacticaltablet", "hover");
    private static final float GUI_SOUND_VOLUME = 0.0625F;

    private static final int BUTTON_H = 26;

    public VotingScreen() {
        super(Component.literal("Голосование"));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        List<MatchMode> modes = availableModes();
        if (modes.isEmpty()) return;

        int buttonW = buttonWidth(modes.size());
        int gap = modes.size() >= 4 ? 5 : 8;
        int totalW = buttonW * modes.size() + gap * (modes.size() - 1);
        int x = (this.width - totalW) / 2;
        int y = (this.height - PANEL_H) / 2 + 58;

        for (int i = 0; i < modes.size(); i++) {
            MatchMode mode = modes.get(i);
            this.addRenderableWidget(new VoteButton(x + i * (buttonW + gap), y, buttonW, mode));
        }
    }

    @Override
    public void tick() {
        MatchPhase phase = TabletClientState.getMatchPhase();
        if (phase == MatchPhase.TEAM_SELECT) {
            Minecraft.getInstance().setScreen(new TeamSelectScreen());
            return;
        }
        if (phase != MatchPhase.VOTING) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        GuiTextureRenderer.blitWithAlpha(g, PANEL, x, y, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        Minecraft minecraft = Minecraft.getInstance();
        g.drawCenteredString(minecraft.font, "ГОЛОСОВАНИЕ", x + PANEL_W / 2, y + 14, 0xFF66FF66);
        g.drawCenteredString(
                minecraft.font,
                "Осталось: " + TabletClientState.getVoteTimeLeft() + " сек.",
                x + PANEL_W / 2,
                y + 30,
                0xFFFFFFFF
        );
        g.drawCenteredString(
                minecraft.font,
                "Голос засчитывается после нажатия",
                x + PANEL_W / 2,
                y + 93,
                0xFFAAAAAA
        );

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static List<MatchMode> availableModes() {
        List<MatchMode> modes = new ArrayList<>();
        for (MatchMode mode : MatchMode.values()) {
            if (TabletClientState.isVoteModeAvailable(mode)) {
                modes.add(mode);
            }
        }
        return modes;
    }

    private static int buttonWidth(int count) {
        if (count <= 2) return 78;
        if (count == 3) return 58;
        return 48;
    }

    private static String shortLabel(MatchMode mode) {
        return switch (mode) {
            case SOLO -> "СОЛО";
            case DUO -> "ДУО";
            case TRIO -> "ТРИО";
            case SQUADS -> "ОТРЯДЫ";
        };
    }

    private static void playSound(ResourceLocation sound) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(sound),
                        1.0F,
                        GUI_SOUND_VOLUME
                )
        );
    }

    private static class VoteButton extends Button {

        private final MatchMode mode;
        private boolean wasHovered;

        private VoteButton(int x, int y, int w, MatchMode mode) {
            super(Button.builder(Component.literal(shortLabel(mode)), button -> {}).bounds(x, y, w, BUTTON_H));
            this.mode = mode;
        }

        @Override
        public void onPress() {
            playSound(CLICK);
            PacketHandler.sendToServer(new VoteModePacket(mode));
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);
            boolean selected = TabletClientState.getSelectedVote() == mode;

            if (hover && !wasHovered) {
                playSound(HOVER);
            }
            wasHovered = hover;

            int fill = selected ? 0xCC2E7D32 : hover ? 0xCC243824 : 0xCC101810;
            int border = selected ? 0xFFFFFFFF : hover ? 0xFFE6FFE6 : 0xFF66FF66;
            int titleColor = selected ? 0xFFFFFFFF : hover ? 0xFFFFFFFF : 0xFF66FF66;

            g.fill(getX(), getY(), getX() + width, getY() + height, fill);
            g.fill(getX(), getY(), getX() + width, getY() + 1, border);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            g.fill(getX(), getY(), getX() + 1, getY() + height, border);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);

            Minecraft minecraft = Minecraft.getInstance();
            g.drawCenteredString(
                    minecraft.font,
                    shortLabel(mode),
                    getX() + width / 2,
                    getY() + 4,
                    titleColor
            );
            g.drawCenteredString(
                    minecraft.font,
                    String.valueOf(TabletClientState.getVoteCount(mode)),
                    getX() + width / 2,
                    getY() + 15,
                    selected ? 0xFFFFFFFF : 0xFFAAAAAA
            );
        }
    }
}
