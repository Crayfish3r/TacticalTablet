package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.SetClanWarPacket;
import com.makar.tacticaltablet.tablet.net.SetCompetitivePacket;
import com.makar.tacticaltablet.tablet.net.VoteMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MapVotingScreen extends Screen {

    private static final int PANEL_W = 366;
    private static final int PANEL_H = 268;
    private static final int CARD_W = 110;
    private static final int CARD_H = 68;
    private static final int IMAGE_SIZE = 22;
    private static final int GAP_X = 6;
    private static final int GAP_Y = 4;
    private Button competitiveToggleButton;
    private Button clanWarToggleButton;

    public MapVotingScreen() {
        super(Component.literal("Выбор карты"));
    }

    @Override
    protected void init() {
        clearWidgets();
        List<String> maps = MapVoteClientState.getMaps();
        if (maps.isEmpty()) return;

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;
        int columns = Math.min(3, maps.size());
        int gridWidth = columns * CARD_W + Math.max(0, columns - 1) * GAP_X;
        int gridX = panelX + (PANEL_W - gridWidth) / 2;
        int gridY = panelY + 30;

        for (int i = 0; i < maps.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            addRenderableWidget(new MapCardButton(
                    gridX + column * (CARD_W + GAP_X),
                    gridY + row * (CARD_H + GAP_Y),
                    maps.get(i)
            ));
        }

        if (MapVoteClientState.isOperator()) {
            int toggleW = 172;
            int toggleGap = 8;
            int totalToggleW = toggleW * 2 + toggleGap;
            int toggleX = panelX + (PANEL_W - totalToggleW) / 2;
            int toggleY = panelY + PANEL_H - 22;
            competitiveToggleButton = addRenderableWidget(Button.builder(
                    competitiveToggleLabel(),
                    button -> PacketHandler.sendToServer(new SetCompetitivePacket(
                            !MapVoteClientState.isNextSetCompetitive()
                    ))
            ).bounds(toggleX, toggleY, toggleW, 18).build());
            clanWarToggleButton = addRenderableWidget(Button.builder(
                    clanWarToggleLabel(),
                    button -> PacketHandler.sendToServer(new SetClanWarPacket(
                            !MapVoteClientState.isNextSetClanWar()
                    ))
            ).bounds(toggleX + toggleW + toggleGap, toggleY, toggleW, 18).build());
        }
    }

    @Override
    public void tick() {
        if (competitiveToggleButton != null) {
            competitiveToggleButton.setMessage(competitiveToggleLabel());
        }
        if (clanWarToggleButton != null) {
            clanWarToggleButton.setMessage(clanWarToggleLabel());
        }
        if (TabletClientState.getMatchPhase() != MatchPhase.MAP_VOTING || !MapVoteClientState.isActive()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xAA000000);

        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xF0121712);
        drawBorder(graphics, x, y, PANEL_W, PANEL_H, 0xFF66FF66);
        graphics.drawCenteredString(font, "ВЫБЕРИТЕ СЛЕДУЮЩУЮ КАРТУ", width / 2, y + 3, 0xFF66FF66);
        graphics.drawCenteredString(
                font,
                "До завершения голосования: " + MapVoteClientState.getSecondsLeft() + " сек.",
                width / 2,
                y + 15,
                0xFFFFFFFF
        );

        if (MapVoteClientState.isNextSetCompetitive() && !MapVoteClientState.isOperator()) {
            graphics.drawString(font, "Следующий сет: СОРЕВНОВАТЕЛЬНЫЙ", x + 8, y + PANEL_H - 20, 0xFFFFAA00);
        }

        if (MapVoteClientState.isNextSetClanWar() && !MapVoteClientState.isOperator()) {
            graphics.drawString(font, "Следующий сет: ВОЙНА КЛАНОВ", x + 8, y + PANEL_H - 20, 0xFFFFAA00);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static Component competitiveToggleLabel() {
        return Component.literal("Соревновательный режим ["
                + (MapVoteClientState.isNextSetCompetitive() ? "X" : " ") + "]");
    }

    private static Component clanWarToggleLabel() {
        return Component.literal("Война кланов ["
                + (MapVoteClientState.isNextSetClanWar() ? "X" : " ") + "]");
    }

    private static final class MapCardButton extends Button {

        private final String mapName;

        private MapCardButton(int x, int y, String mapName) {
            super(Button.builder(Component.literal(mapName), ignored -> {}).bounds(x, y, CARD_W, CARD_H));
            this.mapName = mapName;
        }

        @Override
        public void onPress() {
            PacketHandler.sendToServer(new VoteMapPacket(mapName));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = mapName.equals(MapVoteClientState.getSelectedMap());
            boolean hovered = isMouseOver(mouseX, mouseY);
            int background = selected ? 0xDD2E7D32 : hovered ? 0xDD263626 : 0xDD171D17;
            int border = selected ? 0xFFFFFFFF : hovered ? 0xFFAAFFAA : 0xFF66FF66;

            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            drawBorder(graphics, getX(), getY(), width, height, border);

            int imageX = getX() + (width - IMAGE_SIZE) / 2;
            int imageY = getY() + 2;
            graphics.fill(imageX, imageY, imageX + IMAGE_SIZE, imageY + IMAGE_SIZE, 0xFFFFFFFF);
            drawBorder(graphics, imageX, imageY, IMAGE_SIZE, IMAGE_SIZE, 0xFFCCCCCC);

            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    mapName,
                    getX() + width / 2,
                    getY() + 27,
                    selected ? 0xFFFFFFFF : 0xFFE6FFE6
            );
            String[] builders = builderLines(mapName);
            if (builders.length > 0) {
                graphics.drawCenteredString(
                        Minecraft.getInstance().font,
                        builders[0],
                        getX() + width / 2,
                        getY() + 38,
                        0xFFBBBBBB
                );
            }
            if (builders.length > 1) {
                graphics.drawCenteredString(
                        Minecraft.getInstance().font,
                        builders[1],
                        getX() + width / 2,
                        getY() + 47,
                        0xFFBBBBBB
                );
            }
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    "Голосов: " + MapVoteClientState.getVoteCount(mapName),
                    getX() + width / 2,
                    getY() + (builders.length > 0 ? 58 : 46),
                    0xFFBBBBBB
            );
        }

        private static String[] builderLines(String mapName) {
            if ("Дикий Запад".equals(mapName)) {
                return new String[]{"Строители Илюха", "и ZumaDeluxe"};
            }
            if ("Глубокая пещера".equals(mapName)) {
                return new String[]{"Строители Xeno", "и SADER"};
            }
            return new String[0];
        }
    }
}
