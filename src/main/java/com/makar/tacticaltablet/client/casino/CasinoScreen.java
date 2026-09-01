package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoRewardKind;
import com.makar.tacticaltablet.casino.CasinoSpinTable;
import com.makar.tacticaltablet.casino.net.CasinoClosePacket;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/** Texture-free first casino presentation. The server has already committed the result before animation starts. */
public final class CasinoScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MAX_HEIGHT = 330;
    private static final int PANEL_MARGIN = 18;
    private static final int SLOT_GAP = 10;
    private static final List<String> SPIN_SYMBOLS = List.of("COINS", "КЛАСС", "VIP", "♪", "—");

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
    private TacticalButton exitButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean closeSent;

    public CasinoScreen(UUID sessionId, int balance, boolean spectatorSource) {
        super(Component.translatable("screen.tacticaltablet.casino.title"));
        this.sessionId = sessionId;
        this.balance = Math.max(0, balance);
        this.spectatorSource = spectatorSource;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(300, width - PANEL_MARGIN * 2));
        panelHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(250, height - PANEL_MARGIN * 2));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int gap = 10;
        int available = panelWidth - 40;
        int buttonWidth = Math.max(78, (available - gap * 2) / 3);
        int buttonY = panelY + panelHeight - TacticalTheme.CONTROL_HEIGHT - 18;
        int buttonX = panelX + 20;
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
        exitButton = addRenderableWidget(TacticalButton.standard(
                buttonX + (buttonWidth + gap) * 2,
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

    private Component stakeLabel() {
        return Component.translatable("screen.tacticaltablet.casino.stake", currentStake());
    }

    private int currentStake() {
        return CasinoSpinTable.STAKES.get(Mth.clamp(stakeIndex, 0, CasinoSpinTable.STAKES.size() - 1));
    }

    private void play() {
        if (awaitingServer || animationTicksRemaining > 0 || balance < currentStake()) return;
        pendingRequestId = UUID.randomUUID();
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
        awaitingServer = false;
        balance = Math.max(0, newBalance);
        if (status == CasinoSpinResultPacket.Status.SUCCESS) {
            rewardKind = newRewardKind;
            awardedCoins = Math.max(0, newAwardedCoins);
            classId = newClassId == null ? "" : newClassId;
            duplicate = wasDuplicate;
            animationSeed = seed;
            animationDurationTicks = Math.max(1, animationTicks);
            animationTicksRemaining = animationDurationTicks;
            statusText = Component.translatable("screen.tacticaltablet.casino.spinning");
        } else {
            pendingRequestId = null;
            statusText = Component.translatable("screen.tacticaltablet.casino.error." + status.name().toLowerCase());
        }
        updateButtons();
    }

    @Override
    public void tick() {
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
        if (exitButton != null) exitButton.active = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame, ExternalUiTheme.PALETTE)) {
            renderBackground(graphics);
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
        int slotHeight = Math.min(92, Math.max(58, panelHeight / 3));
        int startX = panelX + (panelWidth - (slotWidth * 3 + SLOT_GAP * 2)) / 2;
        int slotY = panelY + 70;
        for (int index = 0; index < 3; index++) {
            int x = startX + index * (slotWidth + SLOT_GAP);
            TacticalUi.drawCutCornerBorder(graphics, x, slotY, slotWidth, slotHeight,
                    TacticalTheme.CORNER_CUT, 1, ExternalUiTheme.BORDER,
                    TacticalUi.withAlpha(ExternalUiTheme.SURFACE_RAISED, 0xF0));
            Component symbol = animationTicksRemaining > 0
                    ? Component.literal(spinningSymbol(index))
                    : finalSymbol();
            graphics.drawCenteredString(font, symbol, x + slotWidth / 2,
                    slotY + (slotHeight - font.lineHeight) / 2, ExternalUiTheme.TEXT_PRIMARY);
        }
    }

    private String spinningSymbol(int reel) {
        long elapsed = Math.max(0, animationDurationTicks - animationTicksRemaining);
        long value = animationSeed + elapsed * (7L + reel * 4L) + reel * 31L;
        return SPIN_SYMBOLS.get(Math.floorMod(value, SPIN_SYMBOLS.size()));
    }

    private Component finalSymbol() {
        return switch (rewardKind) {
            case COINS -> Component.literal(awardedCoins > 0 ? "+" + awardedCoins : "—");
            case SHOP_CLASS -> Component.literal("КЛАСС");
            case VIP_CLASS -> Component.literal("VIP");
            case SAD_TROMBONE -> Component.literal("♪");
        };
    }

    private Component rewardDescription() {
        if (duplicate) {
            return Component.translatable("screen.tacticaltablet.casino.reward.duplicate", awardedCoins);
        }
        return switch (rewardKind) {
            case COINS -> awardedCoins > 0
                    ? Component.translatable("screen.tacticaltablet.casino.reward.coins", awardedCoins)
                    : Component.translatable("screen.tacticaltablet.casino.reward.none");
            case SHOP_CLASS -> Component.translatable("screen.tacticaltablet.casino.reward.shop_class",
                    classDisplayName(classId));
            case VIP_CLASS -> Component.translatable("screen.tacticaltablet.casino.reward.vip_class",
                    classDisplayName(classId));
            case SAD_TROMBONE -> Component.translatable("screen.tacticaltablet.casino.reward.sad_trombone");
        };
    }

    private static String classDisplayName(String id) {
        return switch (id) {
            case "solider" -> "Солдат";
            case "blackops" -> "Black Ops";
            case "rebel" -> "Повстанец";
            case "saboteur" -> "Саботёр";
            case "dream" -> "Dream";
            case "shahed" -> "Шахед оператор";
            case "miniboss" -> "Мини-босс";
            case "cowboy" -> "Ковбой";
            case "boomguy" -> "Подрывник";
            case "tagilla" -> "Тагилла";
            case "killer" -> "Киллер";
            case "crossbowman" -> "Арбалетчик";
            case "krot" -> "Крот";
            case "medic" -> "Медик";
            case "microwave" -> "Микровэйв";
            case "railgunner" -> "Рэйл-ганнер";
            case "smartstormtrooper" -> "Smart-штурмовик";
            default -> id == null || id.isBlank() ? "Неизвестный класс" : id;
        };
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public void onClose() {
        sendCloseOnce();
        super.onClose();
    }

    @Override
    public void removed() {
        sendCloseOnce();
        super.removed();
    }

    private void sendCloseOnce() {
        if (closeSent) return;
        closeSent = true;
        PacketHandler.sendToServer(new CasinoClosePacket(sessionId));
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
