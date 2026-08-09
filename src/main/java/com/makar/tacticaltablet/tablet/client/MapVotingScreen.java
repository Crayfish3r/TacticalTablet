package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.SetGameMode;
import com.makar.tacticaltablet.tablet.client.ui.TacticalLayout;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.SetClanWarPacket;
import com.makar.tacticaltablet.tablet.net.SetCompetitivePacket;
import com.makar.tacticaltablet.tablet.net.VoteMapPacket;
import com.makar.tacticaltablet.tablet.net.VoteSetModePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class MapVotingScreen extends TacticalPhaseScreen {
    private static final int PANEL_W = 366;
    private static final int PANEL_H = 320;
    private static final int CARD_H = 86;
    private static final int GAP = 6;
    private static final int PENDING_TIMEOUT_TICKS = 60;

    private final List<MapCardButton> mapButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private TacticalLayout.Rect viewport = new TacticalLayout.Rect(0, 0, 0, 0);
    private int columns = 1;
    private int visibleRows = 1;
    private int scrollRow;
    private String pendingMap;
    private int pendingMapTicks;
    private Boolean pendingCompetitive;
    private int pendingCompetitiveTicks;
    private Boolean pendingClanWar;
    private int pendingClanWarTicks;
    private int timeoutMessageTicks;
    private TacticalButton competitiveToggleButton;
    private TacticalButton clanWarToggleButton;
    private SetGameMode pendingMode;
    private int pendingModeTicks;

    public MapVotingScreen() {
        super(Component.translatable("screen.tacticaltablet.map_voting.title"));
    }

    @Override
    protected void init() {
        clearWidgets();
        mapButtons.clear();
        modeButtons.clear();
        TacticalLayout.Rect panel = panelBounds(PANEL_W, PANEL_H);
        int footerHeight = MapVoteClientState.isOperator() ? 112 : 70;
        viewport = new TacticalLayout.Rect(panel.x() + 12, panel.y() + 39,
                Math.max(1, panel.width() - 24), Math.max(1, panel.height() - 47 - footerHeight));
        List<String> maps = MapVoteClientState.getMaps();
        columns = VotingGridLayout.columnsFor(viewport.width(), 100, GAP, maps.size(), 3);
        visibleRows = Math.max(1, (viewport.height() + GAP) / (CARD_H + GAP));
        scrollRow = VotingGridLayout.clampScrollRow(scrollRow, maps.size(), columns, visibleRows);
        int cardWidth = Math.max(1, (viewport.width() - GAP * (columns - 1)) / columns);

        for (String map : maps) {
            MapCardButton button = new MapCardButton(0, 0, cardWidth, map);
            mapButtons.add(addRenderableWidget(button));
        }
        layoutCards();
        createModeButtons(panel);
        createOperatorToggles(panel);

        MapCardButton initial = mapButtons.stream()
                .filter(button -> button.mapName.equals(MapVoteClientState.getSelectedMap()))
                .findFirst().orElse(mapButtons.isEmpty() ? null : mapButtons.get(0));
        if (initial != null) {
            reveal(mapButtons.indexOf(initial));
            setInitialFocus(initial);
        } else if (competitiveToggleButton != null) {
            setInitialFocus(competitiveToggleButton);
        }
    }

    private void createModeButtons(TacticalLayout.Rect panel) {
        int gap = 6;
        int width = Math.max(1, (panel.width() - 24 - gap * 2) / 3);
        int y = panel.bottom() - (MapVoteClientState.isOperator() ? 86 : 48);
        int index = 0;
        for (SetGameMode mode : SetGameMode.values()) {
            ModeButton button = new ModeButton(panel.x() + 12 + index * (width + gap), y, width, mode);
            modeButtons.add(addRenderableWidget(button));
            index++;
        }
    }

    private void createOperatorToggles(TacticalLayout.Rect panel) {
        if (!MapVoteClientState.isOperator()) {
            competitiveToggleButton = null;
            clanWarToggleButton = null;
            return;
        }
        int toggleGap = 8;
        int toggleWidth = Math.max(1, (panel.width() - 24 - toggleGap) / 2);
        int toggleY = panel.bottom() - 27;
        competitiveToggleButton = addRenderableWidget(TacticalButton.compact(
                panel.x() + 12, toggleY, toggleWidth, competitiveToggleLabel(),
                ignored -> submitCompetitive()).selectedWhen(MapVoteClientState::isNextSetCompetitive)
                .withFocusKey("map_voting.competitive").onHover(TacticalPhaseScreen::playHover));
        clanWarToggleButton = addRenderableWidget(TacticalButton.compact(
                panel.x() + 12 + toggleWidth + toggleGap, toggleY, toggleWidth, clanWarToggleLabel(),
                ignored -> submitClanWar()).selectedWhen(MapVoteClientState::isNextSetClanWar)
                .withFocusKey("map_voting.clan_war").onHover(TacticalPhaseScreen::playHover));
    }

    @Override
    public void tick() {
        if (TabletClientState.getMatchPhase() != MatchPhase.MAP_VOTING || !MapVoteClientState.isActive()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        if (competitiveToggleButton != null) competitiveToggleButton.setMessage(competitiveToggleLabel());
        if (clanWarToggleButton != null) clanWarToggleButton.setMessage(clanWarToggleLabel());
        if (pendingMap != null && tickPendingMap()) pendingMap = null;
        if (pendingCompetitive != null && tickPendingCompetitive()) pendingCompetitive = null;
        if (pendingClanWar != null && tickPendingClanWar()) pendingClanWar = null;
        if (pendingMode != null && tickPendingMode()) pendingMode = null;
        if (timeoutMessageTicks > 0) timeoutMessageTicks--;
    }

    @Override
    protected void renderPhase(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalLayout.Rect panel = panelBounds(PANEL_W, PANEL_H);
        TacticalUi.drawPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        TacticalUi.drawAccentBar(graphics, panel.x() + 12, panel.y() + 10, 19, TacticalTheme.ACCENT);
        graphics.drawString(font, Component.translatable("screen.tacticaltablet.map_voting.heading"),
                panel.x() + 21, panel.y() + 11, TacticalTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, Component.translatable("screen.tacticaltablet.map_voting.time",
                        MapVoteClientState.getSecondsLeft()),
                panel.x() + 21, panel.y() + 26, TacticalTheme.TEXT_SECONDARY, false);

        if (mapButtons.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.tacticaltablet.map_voting.empty"),
                    panel.x() + panel.width() / 2, viewport.y() + viewport.height() / 2,
                    TacticalTheme.TEXT_SECONDARY);
        } else {
            try (ScissorScope ignored = ScissorScope.open(
                    graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height())) {
                for (MapCardButton button : mapButtons) if (button.visible) {
                    button.render(graphics, mouseX, mouseY, partialTick);
                }
            }
            renderScrollbar(graphics);
        }
        if (competitiveToggleButton != null) competitiveToggleButton.render(graphics, mouseX, mouseY, partialTick);
        if (clanWarToggleButton != null) clanWarToggleButton.render(graphics, mouseX, mouseY, partialTick);
        for (ModeButton button : modeButtons) button.render(graphics, mouseX, mouseY, partialTick);
        for (ModeButton button : modeButtons) if (button.isHovered() && button.mode == SetGameMode.RACE)
            graphics.renderTooltip(font, Component.literal("Режим появится позже"), mouseX, mouseY);
        renderFooterStatus(graphics, panel);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= viewport.x() && mouseX < viewport.right()
                && mouseY >= viewport.y() && mouseY < viewport.bottom() && !mapButtons.isEmpty()) {
            int next = VotingGridLayout.clampScrollRow(scrollRow - (int) Math.signum(delta),
                    mapButtons.size(), columns, visibleRows);
            if (next != scrollRow) {
                scrollRow = next;
                layoutCards();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        int current = mapButtons.indexOf(getFocused());
        if (direction < 0 || current < 0) return false;
        int next = VotingGridLayout.moveIndex(current, mapButtons.size(), columns, direction);
        reveal(next);
        setFocused(mapButtons.get(next));
        return true;
    }

    private void layoutCards() {
        int cardWidth = Math.max(1, (viewport.width() - GAP * (columns - 1)) / columns);
        for (int index = 0; index < mapButtons.size(); index++) {
            int visibleRow = index / columns - scrollRow;
            MapCardButton button = mapButtons.get(index);
            button.setX(viewport.x() + index % columns * (cardWidth + GAP));
            button.setY(viewport.y() + visibleRow * (CARD_H + GAP));
            button.setWidth(cardWidth);
            button.visible = visibleRow >= 0 && visibleRow < visibleRows;
        }
    }

    private void reveal(int index) {
        if (index < 0 || index >= mapButtons.size()) return;
        int row = index / columns;
        if (row < scrollRow) scrollRow = row;
        else if (row >= scrollRow + visibleRows) scrollRow = row - visibleRows + 1;
        scrollRow = VotingGridLayout.clampScrollRow(scrollRow, mapButtons.size(), columns, visibleRows);
        layoutCards();
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int rows = VotingGridLayout.rowCount(mapButtons.size(), columns);
        if (rows <= visibleRows) return;
        int trackX = viewport.right() - 2;
        graphics.fill(trackX, viewport.y(), trackX + 2, viewport.bottom(), TacticalTheme.SURFACE_DISABLED);
        int thumbHeight = Math.max(8, viewport.height() * visibleRows / rows);
        int maximumScroll = rows - visibleRows;
        int thumbY = viewport.y() + (viewport.height() - thumbHeight) * scrollRow / Math.max(1, maximumScroll);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, TacticalTheme.ACCENT_MUTED);
    }

    private void renderFooterStatus(GuiGraphics graphics, TacticalLayout.Rect panel) {
        Component status;
        int color;
        if (pendingMap != null || pendingCompetitive != null || pendingClanWar != null || pendingMode != null) {
            status = Component.translatable("screen.tacticaltablet.common.awaiting_server");
            color = TacticalTheme.WARNING;
        } else if (timeoutMessageTicks > 0) {
            status = Component.translatable("screen.tacticaltablet.common.server_timeout");
            color = TacticalTheme.DANGER;
        } else if (MapVoteClientState.isNextSetClanWar()) {
            status = Component.translatable("screen.tacticaltablet.map_voting.clan_war_enabled");
            color = TacticalTheme.WARNING;
        } else if (MapVoteClientState.isNextSetCompetitive()) {
            status = Component.translatable("screen.tacticaltablet.map_voting.competitive_enabled");
            color = TacticalTheme.WARNING;
        } else if (!MapVoteClientState.areOrdinaryModesEnabled()) {
            status = Component.literal("Режим сета задан оператором");
            color = TacticalTheme.WARNING;
        } else {
            status = Component.translatable("screen.tacticaltablet.map_voting.hint");
            color = TacticalTheme.TEXT_SECONDARY;
        }
        int y = MapVoteClientState.isOperator() ? panel.bottom() - 54 : panel.bottom() - 17;
        graphics.drawCenteredString(font, status, panel.x() + panel.width() / 2, y, color);
    }

    private void submitMap(String mapName) {
        if (pendingMap != null) return;
        playClick();
        pendingMap = mapName;
        pendingMapTicks = 0;
        timeoutMessageTicks = 0;
        PacketHandler.sendToServer(new VoteMapPacket(mapName));
    }

    private void submitCompetitive() {
        if (pendingCompetitive != null) return;
        playClick();
        pendingCompetitive = !MapVoteClientState.isNextSetCompetitive();
        pendingCompetitiveTicks = 0;
        competitiveToggleButton.active = false;
        PacketHandler.sendToServer(new SetCompetitivePacket(pendingCompetitive));
    }

    private void submitClanWar() {
        if (pendingClanWar != null) return;
        playClick();
        pendingClanWar = !MapVoteClientState.isNextSetClanWar();
        pendingClanWarTicks = 0;
        clanWarToggleButton.active = false;
        PacketHandler.sendToServer(new SetClanWarPacket(pendingClanWar));
    }

    private void submitMode(SetGameMode mode) {
        if (pendingMode != null || mode == null || !mode.selectable()
                || !MapVoteClientState.areOrdinaryModesEnabled()) return;
        playClick();
        pendingMode = mode;
        pendingModeTicks = 0;
        PacketHandler.sendToServer(new VoteSetModePacket(mode));
    }

    private boolean tickPendingMode() {
        pendingModeTicks++;
        if (pendingMode == MapVoteClientState.getSelectedMode()) return true;
        if (pendingModeTicks < PENDING_TIMEOUT_TICKS) return false;
        timeoutMessageTicks = PENDING_TIMEOUT_TICKS;
        return true;
    }

    private boolean tickPendingMap() {
        pendingMapTicks++;
        if (pendingMap.equals(MapVoteClientState.getSelectedMap())) return true;
        if (pendingMapTicks < PENDING_TIMEOUT_TICKS) return false;
        timeoutMessageTicks = PENDING_TIMEOUT_TICKS;
        return true;
    }

    private boolean tickPendingCompetitive() {
        pendingCompetitiveTicks++;
        if (pendingCompetitive == MapVoteClientState.isNextSetCompetitive()) {
            competitiveToggleButton.active = true;
            return true;
        }
        if (pendingCompetitiveTicks < PENDING_TIMEOUT_TICKS) return false;
        timeoutMessageTicks = PENDING_TIMEOUT_TICKS;
        competitiveToggleButton.active = true;
        return true;
    }

    private boolean tickPendingClanWar() {
        pendingClanWarTicks++;
        if (pendingClanWar == MapVoteClientState.isNextSetClanWar()) {
            clanWarToggleButton.active = true;
            return true;
        }
        if (pendingClanWarTicks < PENDING_TIMEOUT_TICKS) return false;
        timeoutMessageTicks = PENDING_TIMEOUT_TICKS;
        clanWarToggleButton.active = true;
        return true;
    }

    private static Component competitiveToggleLabel() {
        return Component.translatable("screen.tacticaltablet.map_voting.competitive_toggle");
    }

    private static Component clanWarToggleLabel() {
        return Component.translatable("screen.tacticaltablet.map_voting.clan_war_toggle");
    }

    private final class MapCardButton extends TacticalButton {
        private final String mapName;
        private final ResourceLocation previewTexture;

        private MapCardButton(int x, int y, int width, String mapName) {
            super(x, y, width, CARD_H, Component.literal(mapName), ignored -> submitMap(mapName));
            this.mapName = mapName;
            ResourceLocation candidate = MapPreviewAssets.textureFor(mapName);
            this.previewTexture = ClientResourcePresenceCache.exists(candidate)
                    ? candidate
                    : null;
            selectedWhen(() -> mapName.equals(MapVoteClientState.getSelectedMap()));
            withFocusKey("map_voting.map." + mapName);
            onHover(TacticalPhaseScreen::playHover);
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            active = pendingMap == null;
            int imageWidth = Math.min(MapPreviewAssets.WIDTH, Math.max(1, width - 14));
            int imageHeight = imageWidth * MapPreviewAssets.HEIGHT / MapPreviewAssets.WIDTH;
            int imageX = getX() + (width - imageWidth) / 2;
            int imageY = getY() + 7;
            graphics.fill(imageX, imageY, imageX + imageWidth, imageY + imageHeight, 0xFF0B1114);
            if (previewTexture != null) {
                GuiTextureRenderer.blitRegionWithAlpha(graphics, previewTexture,
                        imageX, imageY, imageWidth, imageHeight,
                        0, 0, MapPreviewAssets.WIDTH, MapPreviewAssets.HEIGHT,
                        MapPreviewAssets.WIDTH, MapPreviewAssets.HEIGHT,
                        1.0F, 1.0F, 1.0F, active ? 1.0F : 0.55F);
            } else {
                int patternColor = 0xFF30443E | mapName.hashCode() & 0x001F1F1F;
                for (int stripe = 0; stripe < imageWidth; stripe += 8) {
                    graphics.fill(imageX + stripe, imageY, imageX + Math.min(imageWidth, stripe + 4),
                            imageY + imageHeight, patternColor);
                }
            }
            String fitted = font.plainSubstrByWidth(mapName, Math.max(1, width - 14));
            graphics.drawCenteredString(font, fitted, getX() + width / 2, getY() + 64,
                    active ? TacticalTheme.TEXT_PRIMARY : TacticalTheme.TEXT_DISABLED);
            graphics.drawCenteredString(font, Component.translatable("screen.tacticaltablet.map_voting.votes",
                            MapVoteClientState.getVoteCount(mapName)),
                    getX() + width / 2, getY() + 75, TacticalTheme.TEXT_SECONDARY);
        }
    }

    private final class ModeButton extends TacticalButton {
        private final SetGameMode mode;

        private ModeButton(int x, int y, int width, SetGameMode mode) {
            super(x, y, width, 24, Component.literal(mode.displayName()), ignored -> submitMode(mode));
            this.mode = mode;
            selectedWhen(() -> MapVoteClientState.getSelectedMode() == mode);
            withFocusKey("map_voting.set_mode." + mode.name().toLowerCase());
            onHover(TacticalPhaseScreen::playHover);
            active = mode.selectable() && MapVoteClientState.areOrdinaryModesEnabled();
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            active = pendingMode == null && mode.selectable() && MapVoteClientState.areOrdinaryModesEnabled();
            int color = active ? TacticalTheme.TEXT_PRIMARY : TacticalTheme.TEXT_DISABLED;
            graphics.drawCenteredString(font, mode.displayName(), getX() + width / 2, getY() + 5, color);
            if (mode.selectable()) graphics.drawCenteredString(font,
                    Component.literal(String.valueOf(MapVoteClientState.getModeVoteCount(mode))),
                    getX() + width / 2, getY() + 14, color);
        }
    }
}
