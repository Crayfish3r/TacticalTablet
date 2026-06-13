package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletPacket;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TabletScreen extends Screen {

    private static final ResourceLocation PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/tablet.png");
    private static final ResourceLocation PANEL_EPIC =
            new ResourceLocation("tacticaltablet", "textures/gui/tablet_epic.png");
    private static final ResourceLocation PANEL_LEGEND =
            new ResourceLocation("tacticaltablet", "textures/gui/tablet_legend.png");

    private static final ResourceLocation BTN =
            new ResourceLocation("tacticaltablet", "textures/gui/button.png");
    private static final ResourceLocation BTN_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_hover.png");
    private static final ResourceLocation BTN_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/button_disabled.png");

    private static final ResourceLocation BTN_EPIC =
            new ResourceLocation("tacticaltablet", "textures/gui/button_epic.png");
    private static final ResourceLocation BTN_EPIC_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_hover_epic.png");
    private static final ResourceLocation BTN_EPIC_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/button_disabled_epic.png");

    private static final ResourceLocation BTN_LEGEND =
            new ResourceLocation("tacticaltablet", "textures/gui/button_legend.png");
    private static final ResourceLocation BTN_LEGEND_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_hover_legend.png");
    private static final ResourceLocation BTN_LEGEND_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/button_disabled_legend.png");

    private static final ResourceLocation TP_BTN =
            new ResourceLocation("tacticaltablet", "textures/gui/tp_button.png");
    private static final ResourceLocation TP_BTN_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/tp_button_hover.png");
    private static final ResourceLocation TP_BTN_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/tp_button_disabled.png");

    private static final ResourceLocation TAB_BTN =
            new ResourceLocation("tacticaltablet", "textures/gui/tab_button.png");
    private static final ResourceLocation TAB_BTN_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/tab_button_hover.png");
    private static final ResourceLocation TAB_BTN_ACTIVE =
            new ResourceLocation("tacticaltablet", "textures/gui/tab_button_active.png");

    private static final ResourceLocation CONFIRM_PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/confirm_panel.png");
    private static final ResourceLocation CONFIRM_BUTTON =
            new ResourceLocation("tacticaltablet", "textures/gui/confirm_button.png");
    private static final ResourceLocation CONFIRM_BUTTON_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/confirm_button_hover.png");
    private static final ResourceLocation CONFIRM_BUTTON_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/confirm_button_disabled.png");

    private static final ResourceLocation CLICK =
            new ResourceLocation("tacticaltablet", "click");
    private static final ResourceLocation TELEPORT =
            new ResourceLocation("tacticaltablet", "teleport");
    private static final ResourceLocation HOVER =
            new ResourceLocation("tacticaltablet", "hover");

    private static final int UI_WIDTH = 380;
    private static final int UI_HEIGHT = 220;

    private static final int BTN_W = 160;
    private static final int BTN_H = 30;
    private static final int TAB_W = 60;
    private static final int TAB_H = 20;

    private static final int CONFIRM_W = 240;
    private static final int CONFIRM_H = 132;
    private static final int CONFIRM_BUTTON_W = 96;
    private static final int CONFIRM_BUTTON_H = 24;

    private static final int OFFSET_X = 23;
    private static final int OFFSET_Y = 46;
    private static final int BUTTON_GAP_X = 174;
    private static final int BUTTON_GAP_Y = 40;

    private static final int INFO_LEFT = 38;
    private static final int INFO_TOP = 72;
    private static final int INFO_WIDTH = 292;
    private static final int INFO_HEIGHT = 118;
    private static final int INFO_LINE_HEIGHT = 10;
    private static final float GUI_SOUND_VOLUME = 0.0625F;

    private static final String SERVER_INFO_TEXT = """
            Добро пожаловать на сервер DeluxeWarfare!

            Я ZumaDeluxe - создатель сервера. Возможно, ты видел меня в TikTok или на YouTube — рад видеть тебя здесь. Сервер ещё находится в разработке, но ты уже можешь играть, тестировать режимы и помогать нам делать проект лучше.

            Главная цель режима — остаться последним выжившим игроком на карте. Это похоже на Battle Royale, но без системы лута. На карте есть безопасная зона, которая постепенно сужается и каждый новый матч центр оказывается в случайном месте.

            У тебя есть планшет, который ты сейчас держишь в руке. Это твой личный помощник в игре. Через него ты сможешь смотреть свой прогресс, информацию о наборах и использовать основные игровые кнопки.

            Как начать игру?

            Игра начинается автоматически, если на сервере больше одного человека. Тебе выдадут планшет, в котором есть все необходимые кнопки. Для игры есть два типа кнопок: наборы (или классы) и телепорт (rtp). При старте у тебя есть 30 секунд на подготовку (ты находишься в безопасном лобби). Нажав на кнопку с названием класса, ты можешь его выбрать, после чего тебе выдадут соответствующее снаряжение. Затем кнопки классов станут недоступны и тебе придется телепортироваться в рандомное место на карте (кнопка rtp). Если ты этого не сделаешь, то тебя телепортирует автоматически. Главное что ты должен знать - после телепортации ты находишься в игре и тебя могут убить! У каждого игрока есть по три жизни. Потратив все жизни игрок выбывает.

            Удачи!
            """;

    private static final TabletAction STORMTROOPER =
            TabletAction.classKit("ШТУРМОВИК", "stormtrooper", 0);
    private static final TabletAction SNIPER =
            TabletAction.classKit("СНАЙПЕР", "sniper", 1);
    private static final TabletAction SCOUT =
            TabletAction.classKit("РАЗВЕДЧИК", "scout", 2);
    private static final TabletAction DRONE_OPERATOR =
            TabletAction.classKit("ДРОН ОП.", "droneoperator", 3);
    private static final TabletAction MORTARMAN =
            TabletAction.classKit("МИНОМЁТЧИК", "mortarman", 5);
    private static final TabletAction TELEPORT_RTP =
            TabletAction.rtp("ТЕЛЕПОРТ (RTP)", 7);
    private static final TabletAction MACHINE_GUNNER =
            TabletAction.classKit("ПУЛЕМЁТЧИК", "machinegunner", 8);
    private static final TabletAction RPG_TROOPER =
            TabletAction.classKit("РПГ-БОЕЦ", "rpgtrooper", 9);

    private static final TabletAction BOOMGUY_SHOP =
            TabletAction.shopClass("ПОДРЫВНИК", "boomguy", 4, 500, 2);
    private static final TabletAction DREAM_SHOP =
            TabletAction.shopClass("ДРИМ", "dream", 6, 500, 2);
    private static final TabletAction TAGILLA_SHOP =
            TabletAction.shopClass("ТАГИЛЛА", "tagilla", 10, 750, 2);
    private static final TabletAction BLACK_OPS_SHOP =
            TabletAction.shopClass("СПЕЦНАЗ", "blackops", 11, 1000, 2);
    private static final TabletAction COWBOY_SHOP =
            TabletAction.shopClass("КОВБОЙ", "cowboy", 12, 100, 1);
    private static final TabletAction SOLIDER_SHOP =
            TabletAction.shopClass("СОЛДАТ", "solider", 13, 50, 0);
    private static final TabletAction REBEL_SHOP =
            TabletAction.shopClass("ПОВСТАНЕЦ", "rebel", 14, 1000, 2);

    private static final TabletAction LOCKED =
            TabletAction.locked("???");

    private static final TabletPage[] PAGES = new TabletPage[]{
            new TabletPage("КЛАССЫ", PageType.ACTIONS, new TabletAction[]{
                    STORMTROOPER,
                    SNIPER,
                    SCOUT,
                    DRONE_OPERATOR,
                    MACHINE_GUNNER,
                    MORTARMAN,
                    RPG_TROOPER,
                    TELEPORT_RTP
            }),
            new TabletPage("ИНФО", PageType.SERVER_INFO, new TabletAction[0]),
            new TabletPage("ПРОФИЛЬ", PageType.PROFILE, new TabletAction[0]),
            new TabletPage("МАГАЗИН", PageType.ACTIONS, new TabletAction[]{
                    BOOMGUY_SHOP,
                    DREAM_SHOP,
                    TAGILLA_SHOP,
                    BLACK_OPS_SHOP,
                    COWBOY_SHOP,
                    SOLIDER_SHOP,
                    REBEL_SHOP,
                    LOCKED
            })
    };

    private int currentPage;
    private int infoScroll;
    private final Set<String> dismissedUpgradePrompts = new HashSet<>();

    public TabletScreen() {
        super(Component.literal("ТАКТИЧЕСКИЙ ПЛАНШЕТ"));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int x0 = (this.width - UI_WIDTH) / 2;
        int y0 = (this.height - UI_HEIGHT) / 2;

        addPageTabs(x0, y0);

        TabletPage page = PAGES[currentPage];
        if (page.type() == PageType.ACTIONS) {
            addActionButtons(x0, y0, page);
        }
    }

    private void addPageTabs(int x0, int y0) {
        int totalWidth = PAGES.length * TAB_W + (PAGES.length - 1) * 6;
        int startX = x0 + (UI_WIDTH - totalWidth) / 2;
        int y = y0 + 17;

        for (int i = 0; i < PAGES.length; i++) {
            this.addRenderableWidget(new PageTabButton(
                    startX + i * (TAB_W + 6),
                    y,
                    i
            ));
        }
    }

    private void addActionButtons(int x0, int y0, TabletPage page) {
        TabletAction[] actions = Arrays.copyOf(page.actions(), 8);

        for (int i = 0; i < actions.length; i++) {
            TabletAction action = actions[i] == null ? LOCKED : actions[i];

            int x = x0 + OFFSET_X + (i % 2 * BUTTON_GAP_X);
            int y = y0 + OFFSET_Y + (i / 2 * BUTTON_GAP_Y);

            this.addRenderableWidget(new TabletActionButton(
                    x,
                    y,
                    BTN_W,
                    BTN_H,
                    action
            ));
        }
    }

    @Override
    public void tick() {
        super.tick();

        for (var widget : this.renderables) {
            if (widget instanceof TabletActionButton button) {
                button.updateState();
            }
        }

        if (TabletClientState.shouldClose()) {
            Minecraft.getInstance().setScreen(null);
            TabletClientState.resetCloseFlag();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int x = (this.width - UI_WIDTH) / 2;
        int y = (this.height - UI_HEIGHT) / 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        ResourceLocation panel = getPanelTexture();
        RenderSystem.setShaderTexture(0, panel);
        g.blit(panel, x, y, 0, 0, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);

        renderPageContent(g, x, y);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPageContent(GuiGraphics g, int x, int y) {
        TabletPage page = PAGES[currentPage];

        if (page.type() == PageType.SERVER_INFO) {
            drawServerInfo(g, x, y);
            return;
        }

        if (page.type() == PageType.PROFILE) {
            drawHeader(g, x, y, "ПРОФИЛЬ ИГРОКА");
            drawInfoLine(g, x, y, 0, "Баланс", TabletClientState.getCoins() + " монет", 0xFFFFD966);
            drawInfoLine(g, x, y, 1, "Победы", String.valueOf(TabletClientState.getWins()));
            drawInfoLine(g, x, y, 2, "Матчи", String.valueOf(TabletClientState.getMatchesPlayed()));
            drawInfoLine(g, x, y, 3, "Убийства", String.valueOf(TabletClientState.getKills()));
            drawInfoLine(g, x, y, 4, "Смерти", String.valueOf(TabletClientState.getDeaths()));
            drawInfoLine(g, x, y, 5, "У/С", TabletClientState.getKdaText());
            drawInfoLine(g, x, y, 6, "Карьера", TabletClientState.getCareerProgressPercent() + "%");
        }
    }

    private ResourceLocation getPanelTexture() {
        int tier = TabletClientState.getTabletAppearanceTier();
        if (tier >= 2) return PANEL_LEGEND;
        if (tier == 1) return PANEL_EPIC;
        return PANEL;
    }

    private void drawServerInfo(GuiGraphics g, int x, int y) {
        drawHeader(g, x, y, "ИНФОРМАЦИЯ О СЕРВЕРЕ");

        List<FormattedCharSequence> lines = getServerInfoLines(INFO_WIDTH);
        int maxScroll = getMaxInfoScroll(lines);
        infoScroll = clamp(infoScroll, 0, maxScroll);

        int left = x + INFO_LEFT;
        int top = y + INFO_TOP;
        int right = left + INFO_WIDTH;
        int bottom = top + INFO_HEIGHT;

        g.enableScissor(left, top, right, bottom);

        int lineY = top - infoScroll;
        for (FormattedCharSequence line : lines) {
            if (lineY > top - INFO_LINE_HEIGHT && lineY < bottom) {
                g.drawString(
                        Minecraft.getInstance().font,
                        line,
                        left,
                        lineY,
                        0xFFE6E6E6,
                        false
                );
            }
            lineY += INFO_LINE_HEIGHT;
        }

        g.disableScissor();
        drawInfoScrollbar(g, right + 5, top, INFO_HEIGHT, infoScroll, maxScroll);
    }

    private List<FormattedCharSequence> getServerInfoLines(int width) {
        List<FormattedCharSequence> result = new ArrayList<>();

        for (String paragraph : SERVER_INFO_TEXT.split("\\R", -1)) {
            if (paragraph.isBlank()) {
                result.add(FormattedCharSequence.EMPTY);
            } else {
                result.addAll(Minecraft.getInstance().font.split(Component.literal(paragraph), width));
            }
        }

        return result;
    }

    private int getMaxInfoScroll(List<FormattedCharSequence> lines) {
        int contentHeight = lines.size() * INFO_LINE_HEIGHT;
        return Math.max(0, contentHeight - INFO_HEIGHT);
    }

    private void drawInfoScrollbar(GuiGraphics g, int x, int y, int height, int scroll, int maxScroll) {
        g.fill(x, y, x + 3, y + height, 0x66000000);

        if (maxScroll <= 0) {
            g.fill(x, y, x + 3, y + height, 0xFF66FF66);
            return;
        }

        int thumbHeight = Math.max(16, height * height / (height + maxScroll));
        int thumbY = y + (height - thumbHeight) * scroll / maxScroll;
        g.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF66FF66);
    }

    private boolean isMouseOverInfoArea(double mouseX, double mouseY) {
        int x = (this.width - UI_WIDTH) / 2 + INFO_LEFT;
        int y = (this.height - UI_HEIGHT) / 2 + INFO_TOP;

        return mouseX >= x && mouseX <= x + INFO_WIDTH + 10
                && mouseY >= y && mouseY <= y + INFO_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (PAGES[currentPage].type() == PageType.SERVER_INFO && isMouseOverInfoArea(mouseX, mouseY)) {
            int maxScroll = getMaxInfoScroll(getServerInfoLines(INFO_WIDTH));
            infoScroll = clamp(infoScroll - (int) Math.round(delta * INFO_LINE_HEIGHT * 3.0D), 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawHeader(GuiGraphics g, int x, int y, String text) {
        g.drawCenteredString(
                Minecraft.getInstance().font,
                text,
                x + UI_WIDTH / 2,
                y + 52,
                0xFF66FF66
        );
    }

    private void drawInfoLine(GuiGraphics g, int x, int y, int row, String label, String value) {
        drawInfoLine(g, x, y, row, label, value, 0xFFFFFFFF);
    }

    private void drawInfoLine(GuiGraphics g, int x, int y, int row, String label, String value, int valueColor) {
        int left = x + 52;
        int top = y + 72 + row * 17;

        g.drawString(
                Minecraft.getInstance().font,
                label + ":",
                left,
                top,
                0xFFAAAAAA,
                false
        );

        g.drawString(
                Minecraft.getInstance().font,
                value,
                left + 112,
                top,
                valueColor,
                false
        );
    }

    private void playSound(ResourceLocation sound) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(sound),
                        1.0F,
                        GUI_SOUND_VOLUME
                )
        );
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }

    private enum PageType {
        ACTIONS,
        SERVER_INFO,
        PROFILE
    }

    private record TabletPage(String title, PageType type, TabletAction[] actions) {
    }

    private record TabletAction(
            String label,
            String classKey,
            int actionId,
            boolean rtp,
            boolean locked,
            boolean shop,
            int price,
            int fixedLevel
    ) {
        private static TabletAction classKit(String label, String classKey, int actionId) {
            return new TabletAction(label, classKey, actionId, false, false, false, 0, -1);
        }

        private static TabletAction shopClass(String label, String classKey, int actionId, int price, int fixedLevel) {
            return new TabletAction(label, classKey, actionId, false, false, true, price, fixedLevel);
        }

        private static TabletAction rtp(String label, int actionId) {
            return new TabletAction(label, "", actionId, true, false, false, 0, -1);
        }

        private static TabletAction locked(String label) {
            return new TabletAction(label, "", -1, false, true, false, 0, -1);
        }
    }

    private class PageTabButton extends Button {

        private final int pageIndex;
        private boolean wasHovered;

        private PageTabButton(int x, int y, int pageIndex) {
            super(Button.builder(Component.literal(PAGES[pageIndex].title()), button -> {}).bounds(x, y, TAB_W, TAB_H));
            this.pageIndex = pageIndex;
        }

        @Override
        public void onPress() {
            if (currentPage == pageIndex) return;

            playSound(CLICK);
            currentPage = pageIndex;
            TabletScreen.this.init();
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);

            if (hover && !wasHovered && currentPage != pageIndex) {
                playSound(HOVER);
            }
            wasHovered = hover;

            ResourceLocation tex = currentPage == pageIndex
                    ? TAB_BTN_ACTIVE
                    : hover ? TAB_BTN_HOVER : TAB_BTN;

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, tex);
            g.blit(tex, getX(), getY(), 0, 0, width, height, width, height);

            int color = currentPage == pageIndex
                    ? 0xFFFFFFFF
                    : hover ? 0xFFE6FFE6 : 0xFF66FF66;

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    PAGES[pageIndex].title(),
                    getX() + width / 2,
                    getY() + (height - 8) / 2,
                    color
            );
        }
    }

    private void showPurchaseConfirmation(TabletAction action) {
        Minecraft.getInstance().setScreen(new TabletConfirmScreen(action, ConfirmAction.SHOP_PURCHASE, 0));
    }

    private void showBaseUnlockConfirmation(TabletAction action) {
        Minecraft.getInstance().setScreen(new TabletConfirmScreen(action, ConfirmAction.BASE_UNLOCK, 0));
    }

    private void showTierUpgradeConfirmation(TabletAction action, int targetTier) {
        Minecraft.getInstance().setScreen(new TabletConfirmScreen(action, ConfirmAction.TIER_UPGRADE, targetTier));
    }

    private enum ConfirmAction {
        SHOP_PURCHASE,
        BASE_UNLOCK,
        TIER_UPGRADE
    }

    private class TabletConfirmScreen extends Screen {

        private final TabletAction action;
        private final ConfirmAction confirmAction;
        private final int targetTier;

        private TabletConfirmScreen(TabletAction action, ConfirmAction confirmAction, int targetTier) {
            super(Component.literal("Подтверждение покупки"));
            this.action = action;
            this.confirmAction = confirmAction;
            this.targetTier = targetTier;
        }

        @Override
        protected void init() {
            this.clearWidgets();

            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            int buttonY = y + 94;

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + 18,
                    buttonY,
                    Component.literal("ОТМЕНА"),
                    this::cancel
            ));

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal("КУПИТЬ"),
                    this::confirm
            ));

            if (confirmAction != ConfirmAction.SHOP_PURCHASE) {
                this.clearWidgets();
                this.addRenderableWidget(new ConfirmTextureButton(
                        x + 18,
                        buttonY,
                        Component.literal("НЕТ"),
                        this::cancel
                ));
                this.addRenderableWidget(new ConfirmTextureButton(
                        x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                        buttonY,
                        Component.literal("ДА"),
                        this::confirm
                ));
            }
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);
            g.fill(0, 0, this.width, this.height, 0xAA000000);

            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, CONFIRM_PANEL);
            g.blit(CONFIRM_PANEL, x, y, 0, 0, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    getConfirmTitle(),
                    x + CONFIRM_W / 2,
                    y + 17,
                    0xFF66FF66
            );

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    action.label(),
                    x + CONFIRM_W / 2,
                    y + 44,
                    getShopTitleColor(action)
            );

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    getPrice() + " монет",
                    x + CONFIRM_W / 2,
                    y + 58,
                    0xFFFFFFFF
            );

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    "Баланс: " + TabletClientState.getCoins() + " монет",
                    x + CONFIRM_W / 2,
                    y + 72,
                    0xFFAAAAAA
            );

            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void confirm() {
            if (confirmAction == ConfirmAction.SHOP_PURCHASE) {
                PacketHandler.sendToServer(new TabletPacket(action.actionId()));
            } else if (confirmAction == ConfirmAction.BASE_UNLOCK) {
                PacketHandler.sendToServer(new TabletPacket(TabletPacket.unlockBaseActionId(action.actionId())));
            } else if (targetTier == PlayerProgressManager.EPIC_TIER) {
                PacketHandler.sendToServer(new TabletPacket(TabletPacket.upgradeEpicActionId(action.actionId())));
            } else if (targetTier == PlayerProgressManager.LEGEND_TIER) {
                PacketHandler.sendToServer(new TabletPacket(TabletPacket.upgradeLegendActionId(action.actionId())));
            }

            returnToTablet();
        }

        private void cancel() {
            if (confirmAction == ConfirmAction.TIER_UPGRADE) {
                dismissedUpgradePrompts.add(action.classKey());
            }

            returnToTablet();
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }

        private int getPrice() {
            if (confirmAction == ConfirmAction.SHOP_PURCHASE) {
                return action.price();
            }

            if (confirmAction == ConfirmAction.BASE_UNLOCK) {
                return PlayerProgressManager.BASE_UNLOCK_COST;
            }

            return PlayerProgressManager.getUpgradeCost(targetTier);
        }

        private String getConfirmTitle() {
            if (confirmAction == ConfirmAction.BASE_UNLOCK) {
                return "ОТКРЫТЬ КЛАСС?";
            }

            if (confirmAction == ConfirmAction.TIER_UPGRADE) {
                return "УЛУЧШИТЬ КЛАСС?";
            }

            return "ПОДТВЕРДИТЬ ПОКУПКУ";
        }

        private int getShopTitleColor(TabletAction action) {
            if (action.fixedLevel() >= 2) {
                return 0xFFFFD966;
            }

            if (action.fixedLevel() == 1) {
                return 0xFFB266FF;
            }

            return 0xFF66FF66;
        }
    }

    private class ConfirmTextureButton extends Button {

        private boolean wasHovered;
        private final Runnable action;

        private ConfirmTextureButton(int x, int y, Component label, Runnable action) {
            super(Button.builder(label, button -> {}).bounds(x, y, CONFIRM_BUTTON_W, CONFIRM_BUTTON_H));
            this.action = action;
        }

        @Override
        public void onPress() {
            playSound(CLICK);
            action.run();
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);

            if (hover && !wasHovered) {
                playSound(HOVER);
            }
            wasHovered = hover;

            ResourceLocation texture = !this.active
                    ? CONFIRM_BUTTON_DISABLED
                    : hover ? CONFIRM_BUTTON_HOVER : CONFIRM_BUTTON;

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            g.blit(texture, getX(), getY(), 0, 0, width, height, width, height);

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    getMessage(),
                    getX() + width / 2,
                    getY() + (height - 8) / 2,
                    this.active ? hover ? 0xFFFFFFFF : 0xFF66FF66 : 0xFF555555
            );
        }
    }

    private class TabletActionButton extends Button {

        private final TabletAction action;
        private boolean wasHovered;

        private TabletActionButton(int x, int y, int w, int h, TabletAction action) {
            super(Button.builder(Component.literal(action.label()), button -> {}).bounds(x, y, w, h));
            this.action = action;
            this.active = !action.locked();
        }

        public void updateState() {
            if (action.locked()) {
                this.active = false;
                return;
            }

            boolean purchased = action.shop() && TabletClientState.isClassPurchased(action.classKey());
            if (action.shop() && !purchased) {
                this.active = TabletClientState.getCoins() >= action.price();
                return;
            }

            boolean running = TabletClientState.isGameRunning();
            if (isBaseClassAction() && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
                this.active = TabletClientState.getCoins() >= PlayerProgressManager.BASE_UNLOCK_COST;
                return;
            }

            if (isBaseClassAction()
                    && TabletClientState.isBaseClassUnlocked(action.classKey())
                    && !dismissedUpgradePrompts.contains(action.classKey())) {
                int targetTier = getAvailableUpgradeTier();
                if (targetTier > PlayerProgressManager.STANDARD_TIER
                        && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                    this.active = true;
                    return;
                }
            }

            boolean kitUsed = TabletClientState.isKitUsed();
            boolean rtpUsed = TabletClientState.isRtpUsed();
            long cooldown = action.rtp() ? 0L : TabletClientState.getCooldown(action.actionId());

            if (!running) {
                this.active = false;
                return;
            }

            if (action.rtp()) {
                this.active = !rtpUsed;
            } else {
                this.active = !(kitUsed || cooldown > 0L);
            }
        }

        @Override
        public void onPress() {
            if (!this.active || action.locked()) return;

            if (action.shop() && !TabletClientState.isClassPurchased(action.classKey())) {
                showPurchaseConfirmation(action);
                return;
            }

            boolean running = TabletClientState.isGameRunning();
            if (isBaseClassAction() && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
                showBaseUnlockConfirmation(action);
                return;
            }

            if (isBaseClassAction() && !dismissedUpgradePrompts.contains(action.classKey())) {
                int targetTier = getAvailableUpgradeTier();
                if (targetTier == PlayerProgressManager.EPIC_TIER
                        && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                    showTierUpgradeConfirmation(action, targetTier);
                    return;
                }
                if (targetTier == PlayerProgressManager.LEGEND_TIER
                        && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                    showTierUpgradeConfirmation(action, targetTier);
                    return;
                }
            }

            playSound(action.rtp() ? TELEPORT : CLICK);
            PacketHandler.sendToServer(new TabletPacket(action.actionId()));
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);

            if (hover && !wasHovered && !action.locked()) {
                playSound(HOVER);
            }
            wasHovered = hover;

            String label = getRenderedLabel();
            ResourceLocation texture = getTexture(hover);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            g.blit(texture, getX(), getY(), 0, 0, width, height, width, height);

            int color = this.active
                    ? (hover ? 0xFFFFFFFF : 0xFF66FF66)
                    : 0xFF555555;

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    label,
                    getX() + width / 2,
                    getY() + (height - 8) / 2 - 1,
                    color
            );
        }

        private String getRenderedLabel() {
            if (action.locked()) {
                return action.label();
            }

            boolean running = TabletClientState.isGameRunning();

            if (action.rtp()) {
                long timeLeft = TabletClientState.getRtpTimeLeft();

                if (!running) {
                    return action.label() + " [ОЖИД.]";
                }

                if (!this.active) {
                    return action.label() + " [ИСП.]";
                }

                if (timeLeft > 0L) {
                    return action.label() + " [" + formatTime(timeLeft) + "]";
                }

                return action.label();
            }

            if (action.shop() && !TabletClientState.isClassPurchased(action.classKey())) {
                return action.label() + " [" + action.price() + " МОНЕТ]";
            }

            if (isBaseClassAction() && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
                return action.label() + " [ОТКР. " + PlayerProgressManager.BASE_UNLOCK_COST + "]";
            }

            if (isBaseClassAction() && !dismissedUpgradePrompts.contains(action.classKey())) {
                int targetTier = getAvailableUpgradeTier();
                if (targetTier == PlayerProgressManager.EPIC_TIER
                        && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                    return action.label() + " [УЛУЧШИТЬ EPIC]";
                }
                if (targetTier == PlayerProgressManager.LEGEND_TIER
                        && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                    return action.label() + " [УЛУЧШИТЬ LEGEND]";
                }
            }

            String classLabel = getProgressPrefix() + action.label();
            long cooldown = TabletClientState.getCooldown(action.actionId());

            if (!running) {
                return classLabel + " [ОЖИД.]";
            }

            if (cooldown > 0L) {
                return classLabel + " [КД " + formatTime(cooldown) + "]";
            }

            if (!this.active) {
                return classLabel + " [ИСП.]";
            }

            return classLabel;
        }

        private String getProgressPrefix() {
            if (action.shop()) {
                return "КУПЛЕНО ";
            }

            int level = TabletClientState.getClassTier(action.classKey());
            int xp = TabletClientState.getXP(action.classKey());

            if (level >= 2) {
                return "МАКС. ";
            }

            if (level == 1) {
                return xp + " / 800 ";
            }

            return xp + " / 300 ";
        }

        private ResourceLocation getTexture(boolean hover) {
            if (action.locked()) {
                return BTN_DISABLED;
            }

            if (action.rtp()) {
                return !this.active ? TP_BTN_DISABLED : hover ? TP_BTN_HOVER : TP_BTN;
            }

            int level = action.fixedLevel() >= 0
                    ? action.fixedLevel()
                    : TabletClientState.getClassTier(action.classKey());

            if (!this.active) {
                if (level >= 2) {
                    return BTN_LEGEND_DISABLED;
                }

                if (level == 1) {
                    return BTN_EPIC_DISABLED;
                }

                return BTN_DISABLED;
            }

            if (level >= 2) {
                return hover ? BTN_LEGEND_HOVER : BTN_LEGEND;
            }

            if (level == 1) {
                return hover ? BTN_EPIC_HOVER : BTN_EPIC;
            }

            return hover ? BTN_HOVER : BTN;
        }

        private boolean isBaseClassAction() {
            return !action.shop() && !action.rtp() && !action.locked();
        }

        private int getAvailableUpgradeTier() {
            int tier = TabletClientState.getClassTier(action.classKey());
            int xp = TabletClientState.getXP(action.classKey());

            if (tier == PlayerProgressManager.STANDARD_TIER && xp >= PlayerProgressManager.EPIC_XP) {
                return PlayerProgressManager.EPIC_TIER;
            }

            if (tier == PlayerProgressManager.EPIC_TIER && xp >= PlayerProgressManager.LEGEND_XP) {
                return PlayerProgressManager.LEGEND_TIER;
            }

            return PlayerProgressManager.STANDARD_TIER;
        }
    }

}
