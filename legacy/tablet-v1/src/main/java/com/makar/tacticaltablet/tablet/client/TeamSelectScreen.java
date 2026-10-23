package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.tablet.client.ui.TacticalLayout;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.net.JoinTeamPacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeamSelectScreen extends TacticalPhaseScreen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 196;
    private static final int GAP = 8;
    private static final int PENDING_TIMEOUT_TICKS = 60;

    private final List<TeamButton> teamButtons = new ArrayList<>();
    private TeamId pendingTeam;
    private int pendingTicks;
    private int timeoutMessageTicks;

    public TeamSelectScreen() {
        super(Component.translatable("screen.tacticaltablet.team_select.title"));
    }

    @Override
    protected void init() {
        clearWidgets();
        teamButtons.clear();
        TacticalLayout.Rect panel = panelBounds(PANEL_W, PANEL_H);
        int contentX = panel.x() + 18;
        int contentWidth = Math.max(1, panel.width() - 36);
        int cardWidth = Math.max(1, (contentWidth - GAP) / 2);
        int gridTop = panel.y() + 43;
        int gridBottom = Math.max(gridTop + 2, panel.bottom() - 25);
        int cardHeight = Math.max(1, (gridBottom - gridTop - GAP) / 2);

        TeamId[] teams = TeamId.standardValues();
        for (int index = 0; index < teams.length; index++) {
            TeamButton button = new TeamButton(
                    contentX + index % 2 * (cardWidth + GAP),
                    gridTop + index / 2 * (cardHeight + GAP),
                    cardWidth, cardHeight, teams[index]);
            teamButtons.add(addRenderableWidget(button));
        }

        TeamButton initial = teamButtons.stream()
                .filter(button -> button.team.ordinal() == TabletClientState.getSelectedTeam())
                .findFirst()
                .orElseGet(() -> teamButtons.stream().filter(button -> button.active)
                        .findFirst().orElse(teamButtons.get(0)));
        setInitialFocus(initial);
    }

    @Override
    public void tick() {
        MatchPhase phase = TabletClientState.getMatchPhase();
        if (phase == MatchPhase.VOTING) {
            Minecraft.getInstance().setScreen(new VotingScreen());
            return;
        }
        if (phase != MatchPhase.TEAM_SELECT) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        if (pendingTeam != null) {
            pendingTicks++;
            if (TabletClientState.getSelectedTeam() == pendingTeam.ordinal()) clearPending();
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
        TacticalUi.drawPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        TacticalUi.drawAccentBar(graphics, panel.x() + TacticalTheme.SPACING_LARGE,
                panel.y() + 11, 18, TacticalTheme.ACCENT);
        graphics.drawString(font, Component.translatable("screen.tacticaltablet.team_select.heading"),
                panel.x() + 21, panel.y() + 12, TacticalTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, Component.translatable("screen.tacticaltablet.team_select.time",
                        TabletClientState.getTeamSelectTimeLeft()),
                panel.x() + 21, panel.y() + 27, TacticalTheme.TEXT_SECONDARY, false);

        Component footer = pendingTeam != null
                ? Component.translatable("screen.tacticaltablet.common.awaiting_server")
                : timeoutMessageTicks > 0
                ? Component.translatable("screen.tacticaltablet.common.server_timeout")
                : Component.translatable("screen.tacticaltablet.team_select.hint");
        int color = pendingTeam != null ? TacticalTheme.WARNING
                : timeoutMessageTicks > 0 ? TacticalTheme.DANGER : TacticalTheme.TEXT_SECONDARY;
        graphics.drawCenteredString(font, footer, panel.x() + panel.width() / 2, panel.bottom() - 17, color);
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
        if (direction < 0 || teamButtons.isEmpty()) return false;
        int current = teamButtons.indexOf(getFocused());
        int next = VotingGridLayout.moveIndex(current, teamButtons.size(), 2, direction);
        setFocused(teamButtons.get(next));
        return true;
    }

    private void submit(TeamId team) {
        if (pendingTeam != null || isFull(team) && TabletClientState.getSelectedTeam() != team.ordinal()) return;
        playClick();
        pendingTeam = team;
        pendingTicks = 0;
        timeoutMessageTicks = 0;
        PacketHandler.sendToServer(new JoinTeamPacket(team));
    }

    private void clearPending() {
        pendingTeam = null;
        pendingTicks = 0;
    }

    private static int occupiedSlots(TeamId team) {
        int occupied = 0;
        for (int slot = 0; slot < Math.max(1, TabletClientState.getTeamSlotSize()); slot++) {
            if (!TabletClientState.getTeamSlotName(team.ordinal(), slot).isBlank()) occupied++;
        }
        return occupied;
    }

    private static boolean isFull(TeamId team) {
        return occupiedSlots(team) >= Math.max(1, TabletClientState.getTeamSlotSize());
    }

    private static Component teamName(TeamId team) {
        return Component.translatable("screen.tacticaltablet.team_select.team."
                + team.name().toLowerCase(Locale.ROOT));
    }

    private final class TeamButton extends TacticalButton {
        private final TeamId team;

        private TeamButton(int x, int y, int width, int height, TeamId team) {
            super(x, y, width, height, teamName(team), ignored -> submit(team));
            this.team = team;
            selectedWhen(() -> TabletClientState.getSelectedTeam() == team.ordinal());
            withAccentColor(team.textColor());
            withFocusKey("team_select." + team.name().toLowerCase(Locale.ROOT));
            onHover(TacticalPhaseScreen::playHover);
            active = !isFull(team) || TabletClientState.getSelectedTeam() == team.ordinal();
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int slots = Math.max(1, TabletClientState.getTeamSlotSize());
            int occupied = occupiedSlots(team);
            boolean selected = TabletClientState.getSelectedTeam() == team.ordinal();
            active = pendingTeam == null && (!isFull(team) || selected);
            setMessage(Component.translatable("screen.tacticaltablet.team_select.option_narration",
                    teamName(team), occupied, slots));

            int titleColor = active ? team.textColor() : TacticalTheme.TEXT_DISABLED;
            graphics.drawString(font, teamName(team), getX() + 8, getY() + 6, titleColor, false);
            Component occupancy = isFull(team) && !selected
                    ? Component.translatable("screen.tacticaltablet.team_select.full")
                    : Component.translatable("screen.tacticaltablet.team_select.occupancy", occupied, slots);
            graphics.drawString(font, occupancy, getX() + 8, getY() + 18,
                    active ? TacticalTheme.TEXT_SECONDARY : TacticalTheme.TEXT_DISABLED, false);

            int availableLines = Math.max(0, (height - 31) / 9);
            int rendered = 0;
            for (int slot = 0; slot < slots && rendered < availableLines; slot++) {
                String name = TabletClientState.getTeamSlotName(team.ordinal(), slot);
                if (name.isBlank()) continue;
                int remainingOccupied = occupied - rendered;
                if (rendered == availableLines - 1 && remainingOccupied > 1) {
                    graphics.drawString(font, Component.translatable("screen.tacticaltablet.team_select.more",
                                    remainingOccupied),
                            getX() + 8, getY() + 30 + rendered * 9, TacticalTheme.TEXT_DISABLED, false);
                    break;
                }
                String fitted = font.plainSubstrByWidth(name, Math.max(1, width - 16));
                graphics.drawString(font, fitted, getX() + 8, getY() + 30 + rendered * 9,
                        TacticalTheme.TEXT_SECONDARY, false);
                rendered++;
            }
        }
    }
}
