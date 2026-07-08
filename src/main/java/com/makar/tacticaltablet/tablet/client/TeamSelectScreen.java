package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.tablet.net.JoinTeamPacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class TeamSelectScreen extends Screen {

    private static final ResourceLocation CLICK =
            new ResourceLocation("tacticaltablet", "click");
    private static final ResourceLocation HOVER =
            new ResourceLocation("tacticaltablet", "hover");
    private static final float GUI_SOUND_VOLUME = 0.0625F;

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 196;
    private static final int TEAM_CARD_W = 138;
    private static final int TEAM_CARD_H = 62;
    private static final int TEAM_CARD_GAP_X = 8;
    private static final int TEAM_CARD_GAP_Y = 8;

    public TeamSelectScreen() {
        super(Component.literal("Выбор команды"));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int x0 = (this.width - PANEL_W) / 2;
        int y0 = (this.height - PANEL_H) / 2;
        int startX = x0 + 18;
        int startY = y0 + 48;

        TeamId[] teams = TeamId.standardValues();
        for (int i = 0; i < teams.length; i++) {
            int x = startX + (i % 2) * (TEAM_CARD_W + TEAM_CARD_GAP_X);
            int y = startY + (i / 2) * (TEAM_CARD_H + TEAM_CARD_GAP_Y);
            this.addRenderableWidget(new TeamButton(x, y, teams[i]));
        }
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
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.fill(0, 0, this.width, this.height, 0x99000000);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        drawPanel(g, x, y);

        Minecraft minecraft = Minecraft.getInstance();
        g.drawCenteredString(minecraft.font, "ВЫБОР КОМАНДЫ", x + PANEL_W / 2, y + 14, 0xFF66FF66);
        g.drawCenteredString(
                minecraft.font,
                "Осталось: " + TabletClientState.getTeamSelectTimeLeft() + " сек.",
                x + PANEL_W / 2,
                y + 30,
                0xFFFFFFFF
        );
        g.drawCenteredString(
                minecraft.font,
                "Выбери слот. Пустые места заполнит автобаланс.",
                x + PANEL_W / 2,
                y + PANEL_H - 16,
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

    private static void drawPanel(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 4, x + PANEL_W + 4, y + PANEL_H + 4, 0x66000000);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xEB080E0A);
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + 3, 0xFF5CFF70);
        g.fill(x + 1, y + PANEL_H - 3, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF5CFF70);
        g.fill(x + 1, y + 1, x + 3, y + PANEL_H - 1, 0xFF5CFF70);
        g.fill(x + PANEL_W - 3, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF5CFF70);
        g.fill(x + 6, y + 6, x + PANEL_W - 6, y + PANEL_H - 6, 0x33122014);
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

    private static String fitName(Minecraft minecraft, String name, int maxWidth) {
        if (minecraft.font.width(name) <= maxWidth) return name;

        String suffix = "...";
        int suffixWidth = minecraft.font.width(suffix);
        String result = name;
        while (!result.isEmpty() && minecraft.font.width(result) + suffixWidth > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? suffix : result + suffix;
    }

    private static class TeamButton extends Button {

        private final TeamId team;
        private boolean wasHovered;

        private TeamButton(int x, int y, TeamId team) {
            super(Button.builder(Component.literal(team.displayName()), button -> {}).bounds(x, y, TEAM_CARD_W, TEAM_CARD_H));
            this.team = team;
        }

        @Override
        public void onPress() {
            playSound(CLICK);
            PacketHandler.sendToServer(new JoinTeamPacket(team));
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);
            boolean selected = TabletClientState.getSelectedTeam() == team.ordinal();

            if (hover && !wasHovered) {
                playSound(HOVER);
            }
            wasHovered = hover;

            int border = selected ? 0xFFFFFFFF : team.textColor();
            int bg = selected ? 0xCC1A2E1A : hover ? 0xCC162316 : 0xCC101810;

            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY(), getX() + width, getY() + 1, border);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            g.fill(getX(), getY(), getX() + 1, getY() + height, border);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);

            Minecraft minecraft = Minecraft.getInstance();
            g.drawCenteredString(
                    minecraft.font,
                    team.displayName(),
                    getX() + width / 2,
                    getY() + 5,
                    team.textColor()
            );

            int slots = Math.max(1, TabletClientState.getTeamSlotSize());
            for (int i = 0; i < slots; i++) {
                String name = TabletClientState.getTeamSlotName(team.ordinal(), i);
                boolean empty = name == null || name.isBlank();
                String label = empty ? "[ пусто ]" : fitName(minecraft, name, width - 20);
                int color = empty ? 0xFF777777 : 0xFFE6E6E6;
                g.drawString(
                        minecraft.font,
                        label,
                        getX() + 10,
                        getY() + 18 + i * 8,
                        color,
                        false
                );
            }
        }
    }
}
