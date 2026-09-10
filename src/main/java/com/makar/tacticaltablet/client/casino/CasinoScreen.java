package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoRewardKind;
import com.makar.tacticaltablet.casino.CasinoSpinTable;
import com.makar.tacticaltablet.casino.net.CasinoSpinRequestPacket;
import com.makar.tacticaltablet.casino.net.CasinoSpinResultPacket;
import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import com.makar.tacticaltablet.casino.CasinoMenu;
import com.makar.tacticaltablet.casino.CasinoReelResult;
import com.makar.tacticaltablet.casino.CasinoAnimation;
import com.makar.tacticaltablet.casino.net.CasinoAnimationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Texture-free first casino presentation. The server has already committed the result before animation starts. */
public final class CasinoScreen extends AbstractContainerScreen<CasinoMenu> implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {
    private static final int PANEL_MAX_WIDTH = 430;
    private static final int PANEL_MAX_HEIGHT = 270;
    private static final int PANEL_MARGIN = 18;
    private static final int SLOT_GAP = 10;

    private final UUID sessionId;
    private final boolean spectatorSource;
    private final UiFrameClock frameClock = new UiFrameClock();
    private int balance;
    private int stakeIndex;
    private UUID pendingRequestId;
    private boolean awaitingServer;
    private int animationTicksRemaining;
    private int animationDurationTicks;
    private long animationSeed;
    private CasinoRewardKind rewardKind = CasinoRewardKind.COINS;
    private int awardedCoins;
    private String classId = "";
    private boolean duplicate;
    private Component statusText = Component.translatable("screen.tacticaltablet.casino.ready");
    private TacticalButton stakeButton;
    private TacticalButton playButton;
    private TacticalButton oddsButton;
    private TacticalButton exitButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    public enum View { MAIN, ODDS }
    private View view = View.MAIN;
    private CasinoReelResult previousReels = CasinoReelResult.IDLE;
    private CasinoReelResult targetReels = CasinoReelResult.IDLE;
    private long animationStart;
    private boolean timelineReceived;

    public CasinoScreen(CasinoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.sessionId = menu.sessionId();
        this.balance = menu.openingBalance();
        this.spectatorSource = menu.spectatorSource();
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        if (view == View.ODDS) {
            panelWidth = Math.min(width - 8, 780);
            panelHeight = Math.min(height - 8, 330);
            panelX = (width - panelWidth) / 2;
            panelY = (height - panelHeight) / 2;
            addRenderableWidget(TacticalButton.standard(width / 2 - 70,
                    panelY + panelHeight - TacticalTheme.CONTROL_HEIGHT - 12, 140,
                    Component.translatable("screen.tacticaltablet.casino.back"), ignored -> switchView(View.MAIN)));
            return;
        }
        panelWidth = Math.min(Math.max(1, width - 8),
                Math.min(PANEL_MAX_WIDTH, Math.max(280, width - PANEL_MARGIN * 2)));
        panelHeight = Math.min(Math.max(1, height - 8),
                Math.min(PANEL_MAX_HEIGHT, Math.max(200, height - PANEL_MARGIN * 2)));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int gap = 6;
        int available = panelWidth - 32;
        int buttonWidth = Math.max(62, (available - gap * 3) / 4);
        int buttonY = panelY + panelHeight - TacticalTheme.CONTROL_HEIGHT - 14;
        int buttonX = panelX + 16;
        stakeButton = addRenderableWidget(TacticalButton.standard(
                buttonX,
                buttonY,
                buttonWidth,
                stakeLabel(),
                ignored -> cycleStake()
        ).withAccentBar(true));
        playButton = addRenderableWidget(TacticalButton.standard(
                buttonX + buttonWidth + gap,
                buttonY,
                buttonWidth,
                Component.translatable("screen.tacticaltablet.casino.play"),
                ignored -> play()
        ).withAccentBar(true).withAccentColor(ExternalUiTheme.SUCCESS));
        oddsButton = addRenderableWidget(TacticalButton.standard(
                buttonX + (buttonWidth + gap) * 2,
                buttonY,
                buttonWidth,
                Component.translatable("screen.tacticaltablet.casino.odds"),
                ignored -> openOdds()
        ));
        exitButton = addRenderableWidget(TacticalButton.standard(
                buttonX + (buttonWidth + gap) * 3,
                buttonY,
                buttonWidth,
                Component.translatable("screen.tacticaltablet.casino.exit"),
                ignored -> onClose()
        ).withAccentColor(ExternalUiTheme.DANGER));
        updateButtons();
    }

    private void cycleStake() {
        stakeIndex = (stakeIndex + 1) % CasinoSpinTable.STAKES.size();
        stakeButton.setMessage(stakeLabel());
        statusText = Component.translatable("screen.tacticaltablet.casino.ready");
        updateButtons();
    }

    private void openOdds() {
        if (minecraft == null || awaitingServer || animationTicksRemaining > 0) return;
        switchView(View.ODDS);
    }

    private Component stakeLabel() {
        return Component.translatable("screen.tacticaltablet.casino.stake", currentStake());
    }

    private int currentStake() {
        return CasinoSpinTable.STAKES.get(Mth.clamp(stakeIndex, 0, CasinoSpinTable.STAKES.size() - 1));
    }

    private void play() {
        if (awaitingServer || animationTicksRemaining > 0 || balance < currentStake()) return;
        pendingRequestId = UUID.randomUUID();
        timelineReceived = false;
        awaitingServer = true;
        statusText = Component.translatable("screen.tacticaltablet.casino.awaiting");
        updateButtons();
        PacketHandler.sendToServer(new CasinoSpinRequestPacket(sessionId, pendingRequestId, currentStake()));
    }

    void acceptResult(
            UUID receivedSessionId,
            UUID requestId,
            CasinoSpinResultPacket.Status status,
            int newBalance,
            CasinoRewardKind newRewardKind,
            int newAwardedCoins,
            String newClassId,
            boolean wasDuplicate,
            int animationTicks,
            long seed
    ) {
        if (!sessionId.equals(receivedSessionId) || pendingRequestId == null
                || !pendingRequestId.equals(requestId)) return;
        balance = Math.max(0, newBalance);
        if (status == CasinoSpinResultPacket.Status.SUCCESS) {
            rewardKind = newRewardKind;
            awardedCoins = Math.max(0, newAwardedCoins);
            classId = newClassId == null ? "" : newClassId;
            duplicate = wasDuplicate;
            animationSeed = seed;
            // Wait for packet 42, including a receipt replay with zero duration.
            awaitingServer = true;
            targetReels = CasinoReelResult.fromReward(newRewardKind, newAwardedCoins, seed);
            animationDurationTicks = Math.max(1, animationTicks);
            animationTicksRemaining = animationDurationTicks;
            statusText = Component.translatable("screen.tacticaltablet.casino.spinning");
        } else {
            awaitingServer = false;
            timelineReceived = false;
            animationTicksRemaining = 0;
            pendingRequestId = null;
            statusText = Component.translatable("screen.tacticaltablet.casino.error." + status.name().toLowerCase());
        }
        updateButtons();
    }

    @Override
    protected void containerTick() {
        if (awaitingServer) return;
        if (timelineReceived && minecraft != null && minecraft.level != null) {
            animationTicksRemaining = (int) Math.max(0, animationStart + animationDurationTicks - minecraft.level.getGameTime());
            if (animationTicksRemaining == 0) {
                statusText = rewardDescription();
                pendingRequestId = null;
                timelineReceived = false;
                updateButtons();
            }
            return;
        }
        if (animationTicksRemaining <= 0) return;
        animationTicksRemaining--;
        if (animationTicksRemaining == 0) {
            statusText = rewardDescription();
            pendingRequestId = null;
            updateButtons();
        }
    }

    private void updateButtons() {
        boolean ready = !awaitingServer && animationTicksRemaining <= 0;
        if (stakeButton != null) stakeButton.active = ready;
        if (playButton != null) playButton.active = ready && balance >= currentStake();
        if (oddsButton != null) oddsButton.active = ready;
        if (exitButton != null) exitButton.active = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame, ExternalUiTheme.PALETTE)) {
            renderBackground(graphics);
            if (view == View.ODDS) {
                graphics.fill(0, 0, width, height, 0x88000000);
                TacticalUi.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
                graphics.drawCenteredString(font, Component.translatable("screen.tacticaltablet.casino.odds_title"),
                        width / 2, panelY + 16, ExternalUiTheme.ACCENT);
                new CasinoOddsPresentation(font).render(graphics, panelX + 14, panelY + 38,
                        panelWidth - 28, panelHeight - TacticalTheme.CONTROL_HEIGHT - 58);
                super.render(graphics, mouseX, mouseY, partialTick);
                return;
            }
            graphics.fill(0, 0, width, height, 0x78000000);
            TacticalUi.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
            graphics.drawCenteredString(font, title, width / 2, panelY + 18, ExternalUiTheme.ACCENT);
            graphics.drawString(font,
                    Component.translatable("screen.tacticaltablet.casino.balance", balance),
                    panelX + 20,
                    panelY + 40,
                    ExternalUiTheme.TEXT_PRIMARY,
                    false);
            if (spectatorSource) {
                Component spectator = Component.translatable("screen.tacticaltablet.casino.spectator_mode");
                graphics.drawString(font, spectator,
                        panelX + panelWidth - 20 - font.width(spectator), panelY + 40,
                        ExternalUiTheme.TEXT_SECONDARY, false);
            }
            renderSlots(graphics);
            graphics.drawCenteredString(font, statusText, width / 2,
                    panelY + panelHeight - TacticalTheme.CONTROL_HEIGHT - 38,
                    ExternalUiTheme.TEXT_PRIMARY);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderSlots(GuiGraphics graphics) {
        int totalWidth = panelWidth - 80;
        int slotWidth = (totalWidth - SLOT_GAP * 2) / 3;
        int slotHeight = Math.min(72, Math.max(52, panelHeight / 3));
        int startX = panelX + (panelWidth - (slotWidth * 3 + SLOT_GAP * 2)) / 2;
        int slotY = panelY + 70;
        for (int index = 0; index < 3; index++) {
            int x = startX + index * (slotWidth + SLOT_GAP);
            TacticalUi.drawCutCornerBorder(graphics, x, slotY, slotWidth, slotHeight,
                    TacticalTheme.CORNER_CUT, 1, ExternalUiTheme.BORDER,
                    TacticalUi.withAlpha(ExternalUiTheme.SURFACE_RAISED, 0xF0));
            int position = targetReels.symbol(index);
            if (animationTicksRemaining > 0 && minecraft != null && minecraft.level != null) {
                double elapsed = timelineReceived ? minecraft.level.getGameTime() - animationStart
                        : animationDurationTicks - animationTicksRemaining;
                double degrees = CasinoAnimation.reelDegrees(index, previousReels.symbol(index), position,
                        elapsed, animationDurationTicks, animationSeed);
                position = Math.floorMod((int) Math.round(-degrees / 45), 8);
            }
            Component symbol = CasinoPresentation.symbol(position);
            graphics.drawCenteredString(font, symbol, x + slotWidth / 2,
                    slotY + (slotHeight - font.lineHeight) / 2, ExternalUiTheme.TEXT_PRIMARY);
        }
    }

    private Component rewardDescription() {
        return CasinoPresentation.rewardDescription(rewardKind, awardedCoins, classId, duplicate);
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    private void switchView(View next) {
        view = next;
        init();
    }

    void acceptAnimation(CasinoAnimationPacket packet) {
        if (!sessionId.equals(packet.sessionId()) || !packet.requestId().equals(pendingRequestId)) return;
        previousReels = packet.previous();
        targetReels = packet.target();
        animationStart = packet.startTick();
        animationDurationTicks = packet.duration();
        animationSeed = packet.seed();
        awaitingServer = false;
        timelineReceived = true;
        containerTick();
    }

    void refreshBalance(UUID id, int coins) {
        if (sessionId.equals(id)) { balance = Math.max(0, coins); updateButtons(); }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
    @Override public void onClose() {
        if (view == View.ODDS) { switchView(View.MAIN); return; }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return ExternalUiTheme.PALETTE;
    }
}
