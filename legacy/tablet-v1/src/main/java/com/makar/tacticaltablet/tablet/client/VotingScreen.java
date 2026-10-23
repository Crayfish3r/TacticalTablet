package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.client.ui.TacticalLayout;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.VoteModePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VotingScreen extends TacticalPhaseScreen {
    public static final int PANEL_W = 284;
    public static final int PANEL_H = 160;
    public static final String PANEL_TEXTURE_PATH = "assets/tacticaltablet/textures/gui/vote_panel.png";

    private static final ResourceLocation PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/vote_panel.png");
    private static final int PANEL_TEXTURE_W = 220;
    private static final int PANEL_TEXTURE_H = 118;
    private static final int GAP = 6;
    private static final int CARD_H = 38;
    private static final int PENDING_TIMEOUT_TICKS = 60;

    private final List<VoteButton> voteButtons = new ArrayList<>();
    private MatchMode pendingMode;
    private int pendingTicks;
    private int timeoutMessageTicks;

    public VotingScreen() {
        super(Component.translatable("screen.tacticaltablet.voting.title"));
    }

    @Override
    protected void init() {
        clearWidgets();
        voteButtons.clear();
        TacticalLayout.Rect panel = panelBounds(PANEL_W, PANEL_H);
        int contentX = panel.x() + TacticalTheme.SPACING_LARGE;
        int contentWidth = Math.max(1, panel.width() - TacticalTheme.SPACING_LARGE * 2);
        int columns = VotingGridLayout.columnsFor(contentWidth, 112, GAP, MatchMode.values().length, 2);
        int cardWidth = Math.max(1, (contentWidth - GAP * (columns - 1)) / columns);
        int startY = panel.y() + 47;

        for (int index = 0; index < MatchMode.values().length; index++) {
            MatchMode mode = MatchMode.values()[index];
            VoteButton button = new VoteButton(
                    contentX + index % columns * (cardWidth + GAP),
                    startY + index / columns * (CARD_H + GAP),
                    cardWidth,
                    mode);
            voteButtons.add(addRenderableWidget(button));
        }

        VoteButton initial = voteButtons.stream()
                .filter(button -> button.mode == TabletClientState.getSelectedVote())
                .findFirst()
                .orElseGet(() -> voteButtons.stream().filter(button -> button.active)
                        .findFirst().orElse(voteButtons.get(0)));
        setInitialFocus(initial);
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
            return;
        }
        if (pendingMode != null) {
            pendingTicks++;
            if (TabletClientState.getSelectedVote() == pendingMode) clearPending();
            else if (pendingTicks >= PENDING_TIMEOUT_TICKS) {
                clearPending();
                timeoutMessageTicks = PENDING_TIMEOUT_TICKS;
            }
        }
        if (timeoutMessageTicks > 0) timeoutMessageTicks--;
    }

    @Override
    protected void renderPhase(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalLayout.Rect panel = panelBounds(PANEL_W, PANEL_H);
        GuiTextureRenderer.blitRegionWithAlpha(graphics, PANEL, panel.x(), panel.y(), panel.width(), panel.height(),
                0.0F, 0.0F, PANEL_TEXTURE_W, PANEL_TEXTURE_H, PANEL_TEXTURE_W, PANEL_TEXTURE_H,
                1.0F, 1.0F, 1.0F, 1.0F);
        TacticalUi.drawCutCornerBorder(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                TacticalTheme.CORNER_CUT, TacticalTheme.BORDER_WIDTH, TacticalTheme.ACCENT_MUTED, 0x7A12181D);

        graphics.drawString(font, Component.translatable("screen.tacticaltablet.voting.heading"),
                panel.x() + TacticalTheme.SPACING_LARGE, panel.y() + 12, TacticalTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, Component.translatable("screen.tacticaltablet.voting.time",
                        TabletClientState.getVoteTimeLeft()),
                panel.x() + TacticalTheme.SPACING_LARGE, panel.y() + 27, TacticalTheme.TEXT_SECONDARY, false);

        Component footer = pendingMode != null
                ? Component.translatable("screen.tacticaltablet.common.awaiting_server")
                : timeoutMessageTicks > 0
                ? Component.translatable("screen.tacticaltablet.common.server_timeout")
                : Component.translatable("screen.tacticaltablet.voting.hint");
        int footerColor = pendingMode != null ? TacticalTheme.WARNING
                : timeoutMessageTicks > 0 ? TacticalTheme.DANGER : TacticalTheme.TEXT_SECONDARY;
        graphics.drawCenteredString(font, footer, panel.x() + panel.width() / 2,
                panel.bottom() - 16, footerColor);
        renderDefaultWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> VotingGridLayout.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> VotingGridLayout.RIGHT;
            case GLFW.GLFW_KEY_UP -> VotingGridLayout.UP;
            case GLFW.GLFW_KEY_DOWN -> VotingGridLayout.DOWN;
            default -> -1;
        };
        if (direction < 0 || voteButtons.isEmpty()) return false;
        int current = voteButtons.indexOf(getFocused());
        int next = VotingGridLayout.moveIndex(current, voteButtons.size(), 2, direction);
        setFocused(voteButtons.get(next));
        return true;
    }

    private void submit(MatchMode mode) {
        if (pendingMode != null || !TabletClientState.isVoteModeAvailable(mode)) return;
        playClick();
        pendingMode = mode;
        pendingTicks = 0;
        timeoutMessageTicks = 0;
        PacketHandler.sendToServer(new VoteModePacket(mode));
    }

    private void clearPending() {
        pendingMode = null;
        pendingTicks = 0;
    }

    private static Component modeName(MatchMode mode) {
        return Component.translatable("screen.tacticaltablet.voting.mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private final class VoteButton extends TacticalButton {
        private final MatchMode mode;

        private VoteButton(int x, int y, int width, MatchMode mode) {
            super(x, y, width, CARD_H, modeName(mode), ignored -> submit(mode));
            this.mode = mode;
            selectedWhen(() -> TabletClientState.getSelectedVote() == mode);
            withFocusKey("voting.mode." + mode.name().toLowerCase(Locale.ROOT));
            onHover(TacticalPhaseScreen::playHover);
            active = TabletClientState.isVoteModeAvailable(mode);
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            active = pendingMode == null && TabletClientState.isVoteModeAvailable(mode);
            setMessage(Component.translatable("screen.tacticaltablet.voting.option_narration",
                    modeName(mode), TabletClientState.getVoteCount(mode)));
            int textColor = active ? TacticalTheme.TEXT_PRIMARY : TacticalTheme.TEXT_DISABLED;
            graphics.drawString(font, modeName(mode), getX() + 8, getY() + 7, textColor, false);
            Component count = Component.translatable("screen.tacticaltablet.voting.votes",
                    TabletClientState.getVoteCount(mode));
            graphics.drawString(font, count, getX() + 8, getY() + 21,
                    active ? TacticalTheme.TEXT_SECONDARY : TacticalTheme.TEXT_DISABLED, false);
            if (TabletClientState.getSelectedVote() == mode) {
                graphics.drawString(font, Component.literal("✓"), getX() + width - 15, getY() + 14,
                        TacticalTheme.ACCENT, false);
            }
        }
    }
}
