package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.clan.ClanAcceptJoinPacket;
import com.makar.tacticaltablet.clan.ClanChangeColorPacket;
import com.makar.tacticaltablet.clan.ClanConstants;
import com.makar.tacticaltablet.clan.ClanCreatePacket;
import com.makar.tacticaltablet.clan.ClanDisbandPacket;
import com.makar.tacticaltablet.clan.ClanJoinRequestPacket;
import com.makar.tacticaltablet.clan.ClanKickMemberPacket;
import com.makar.tacticaltablet.clan.ClanLeavePacket;
import com.makar.tacticaltablet.clan.ClanListPacket;
import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.clan.ClanRejectJoinPacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletPacket;
import com.makar.tacticaltablet.tablet.ClassCategory;
import com.makar.tacticaltablet.tablet.ClassDefinition;
import com.makar.tacticaltablet.tablet.ClassDefinitions;
import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalDialog;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalTextField;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class TabletScreen extends Screen {

    private static final ResourceLocation PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/tablet.png");
    private static final ResourceLocation PANEL_EPIC =
            new ResourceLocation("tacticaltablet", "textures/gui/tablet_epic.png");
    private static final ResourceLocation PANEL_LEGEND =
            new ResourceLocation("tacticaltablet", "textures/gui/tablet_legend.png");

    private static final ResourceLocation CONFIRM_PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/confirm_panel.png");
    private static final ResourceLocation CLICK =
            new ResourceLocation("tacticaltablet", "click");
    private static final ResourceLocation TELEPORT =
            new ResourceLocation("tacticaltablet", "teleport");
    private static final ResourceLocation HOVER =
            new ResourceLocation("tacticaltablet", "hover");

    private static final int UI_WIDTH = 380;
    private static final int UI_HEIGHT = 220;

    private static final int NAV_X = 12;
    private static final int NAV_Y = 14;
    private static final int NAV_W = 72;
    private static final int NAV_H = 192;
    private static final int HEADER_X = 94;
    private static final int HEADER_Y = 14;
    private static final int HEADER_W = 184;
    private static final int HEADER_H = 26;
    private static final int RTP_X = 286;
    private static final int RTP_Y = 16;
    private static final int RTP_W = 78;
    private static final int RTP_H = 20;
    private static final int CONTENT_X = 94;
    private static final int CONTENT_Y = 46;
    private static final int CONTENT_W = 274;
    private static final int CONTENT_H = 150;
    private static final int SCREEN_X = 88;
    private static final int SCREEN_Y = 8;
    private static final int SCREEN_W = 286;
    private static final int SCREEN_H = 204;

    private static final int CONFIRM_W = 240;
    private static final int CONFIRM_H = 132;
    private static final int CONFIRM_BUTTON_W = 96;
    private static final int CONFIRM_BUTTON_H = 24;

    private static final int INFO_LEFT = CONTENT_X;
    private static final int INFO_TOP = CONTENT_Y;
    private static final int INFO_WIDTH = 264;
    private static final int INFO_HEIGHT = 118;
    private static final int INFO_LINE_HEIGHT = 10;
    private static final int CLAN_LIST_LEFT = CONTENT_X;
    private static final int CLAN_LIST_TOP = CONTENT_Y;
    private static final int CLAN_ROW_W = 264;
    private static final int CLAN_ROW_H = 22;
    private static final int CLAN_ROW_GAP = 24;
    private static final int CLAN_CREATE_W = 120;
    private static final int CLAN_CREATE_H = 24;
    private static final int CLAN_BACK_W = 76;
    private static final int CLAN_BACK_H = 22;
    private static final int CLAN_ACTION_W = 120;
    private static final int CLAN_ACTION_H = 24;
    private static final int CLAN_SMALL_W = 58;
    private static final int CLAN_SMALL_H = 18;
    private static final int CLAN_INFO_LEFT = CONTENT_X;
    private static final int CLAN_INFO_TOP = 70;
    private static final int CLAN_MEMBERS_LEFT = CONTENT_X;
    private static final int CLAN_MEMBERS_TOP = 120;
    private static final int CLAN_MEMBERS_WIDTH = 126;
    private static final int CLAN_MEMBERS_HEIGHT = 58;
    private static final int CLAN_MEMBER_ROW_H = 13;
    private static final int CLAN_VISIBLE_MEMBERS = 3;
    private static final int CLAN_PENDING_LEFT = 232;
    private static final int CLAN_PENDING_TOP = 120;
    private static final int CLAN_PENDING_WIDTH = 132;
    private static final int CLAN_PENDING_HEIGHT = 58;
    private static final int CLAN_PENDING_ROW_H = 18;
    private static final int CLAN_VISIBLE_PENDING = 3;
    private static final int CLAN_BOTTOM_BUTTON_Y = 178;
    private static final String MARINE_CLASS = "marine";
    private static final float GUI_SOUND_VOLUME = 0.0625F;

    private static final String SERVER_INFO_TEXT = "";

    private static final TabletAction TELEPORT_RTP =
            TabletAction.rtp("RTP", 7);

    private static final TabletPage[] PAGES = new TabletPage[]{
            new TabletPage("КЛАССЫ", "classes", PageType.ACTIONS, actionsFor(ClassCategory.BASE)),
            new TabletPage("МАГАЗИН", "shop", PageType.ACTIONS, actionsFor(ClassCategory.SHOP)),
            new TabletPage("VIP", "vip", PageType.ACTIONS, actionsFor(ClassCategory.EXCLUSIVE)),
            new TabletPage("КЛАНЫ", "clans", PageType.CLAN, List.of()),
            new TabletPage("ПРОФИЛЬ", "profile", PageType.PROFILE, List.of())
    };

    private final Set<String> dismissedUpgradePrompts = new HashSet<>();
    private final TabletPageState pageState = new TabletPageState();
    private int lastHoveredActionId = -1;
    private final ScrollableActionGrid<TabletAction> actionGrid =
            new ScrollableActionGrid<>(this::renderActionCard, this::pressAction, this::actionNarration);
    private final UiFrameClock frameClock = new UiFrameClock();
    private final TabletNavigationRail navigationRail = new TabletNavigationRail(
            Arrays.stream(PAGES)
                    .map(page -> new TabletNavigationRail.Item(
                            page.title(),
                            TabletButtonTextures.navigation(page.key())))
                    .toList(),
            this::selectPage,
            () -> playSound(HOVER));


    public TabletScreen() {
        super(Component.literal("\u0422\u0430\u043a\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u043f\u043b\u0430\u043d\u0448\u0435\u0442"));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int x0 = panelX();
        int y0 = panelY();

        navigationRail.initialize(x0 + NAV_X, y0 + NAV_Y, button -> this.addWidget(button));
        navigationRail.setSelectedIndex(pageState.currentPage());
        Button selectedNavigation = navigationRail.selectedButton();
        if (selectedNavigation != null) setInitialFocus(selectedNavigation);
        this.addRenderableWidget(new TabletRtpButton(x0 + RTP_X, y0 + RTP_Y));

        TabletPage page = PAGES[pageState.currentPage()];
        if (page.type() == PageType.ACTIONS) {
            actionGrid.initialize(x0 + CONTENT_X, y0 + CONTENT_Y, page.key(), page.actions(),
                    button -> this.addWidget(button));
        } else if (page.type() == PageType.CLAN) {
            addClanButtons(x0, y0);
        }
    }

    private void addClanButtons(int x0, int y0) {
        List<ClanListPacket.ClanEntry> clans = TabletClientState.getClans();
        boolean selectedIndexInvalid = pageState.clampSelectedClan(clans.size());
        if (selectedIndexInvalid) {
            pageState.reset(TabletPageState.CLAN_PENDING, TabletPageState.CLAN_MEMBERS);
        }
        int selectedClanIndex = pageState.selectedClanIndex();
        if (selectedClanIndex < 0) {
            pageState.reset(TabletPageState.CLAN_MEMBERS);
        }
        int clanScrollOffset = pageState.clampOffset(TabletPageState.CLAN_LIST, clans.size() - 4);

        if (selectedClanIndex < 0) {
            int rowCount = Math.min(4, clans.size() - clanScrollOffset);
            for (int i = 0; i < rowCount; i++) {
                int index = clanScrollOffset + i;
                ClanListPacket.ClanEntry clan = clans.get(index);
                this.addRenderableWidget(new ClanTextureButton(
                        x0 + CLAN_LIST_LEFT,
                        y0 + CLAN_LIST_TOP + i * CLAN_ROW_GAP,
                        CLAN_ROW_W,
                        CLAN_ROW_H,
                        TabletButtonTextures.CLAN_ROW,
                        Component.literal("[" + clan.tag() + "] " + clan.name()),
                        () -> clan.color(),
                        () -> selectedClanIndex == index,
                        () -> {
                            pageState.selectClan(index);
                            TabletScreen.this.init();
                        }
                ));
            }

            ClanTextureButton createClanButton = new ClanTextureButton(
                    x0 + CONTENT_X + (CONTENT_W - CLAN_CREATE_W) / 2,
                    y0 + 172,
                    CLAN_CREATE_W,
                    CLAN_CREATE_H,
                    TabletButtonTextures.CLAN_CREATE,
                    Component.literal("\u0421\u043e\u0437\u0434\u0430\u0442\u044c"),
                    this::openClanCreateConfirmation
            );
            createClanButton.active = clans.size() < ClanConstants.MAX_CLANS && hasFreeClanColor("");
            this.addRenderableWidget(createClanButton);
            return;
        }

        ClanListPacket.ClanEntry clan = clans.get(selectedClanIndex);
        ClanPagePolicy.Permissions permissions = ClanPagePolicy.permissions(
                clan.owner(), clan.member(), clan.pending());
        this.addRenderableWidget(new ClanTextureButton(
                x0 + CONTENT_X,
                y0 + 46,
                CLAN_BACK_W,
                CLAN_BACK_H,
                TabletButtonTextures.CLAN_BACK,
                Component.literal("\u041d\u0430\u0437\u0430\u0434"),
                () -> {
                    pageState.selectClan(-1);
                    TabletScreen.this.init();
                }
        ));

        if (permissions.canRequestJoin()) {
            this.addRenderableWidget(newClanActionButton(x0 + 130, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0417\u0430\u044f\u0432\u043a\u0430",
                    () -> openClanJoinConfirmation(clan)));
        } else if (permissions.canLeave()) {
            this.addRenderableWidget(newClanDangerButton(x0 + 130, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0412\u044b\u0439\u0442\u0438",
                    () -> openClanConfirmation(
                            "\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 \u043a\u043b\u0430\u043d\u0430?",
                            clan.name(),
                            () -> PacketHandler.sendToServer(new ClanLeavePacket())
                    )));
        } else if (permissions.canChangeColor()) {
            this.addRenderableWidget(newClanActionButton(x0 + 130, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0426\u0432\u0435\u0442 10\u041a\u041a",
                    () -> Minecraft.getInstance().setScreen(new ClanChangeColorScreen(clan))));
            this.addRenderableWidget(newClanDangerButton(x0 + 254, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0420\u0430\u0441\u043f\u0443\u0441\u0442\u0438\u0442\u044c",
                    () -> openClanConfirmation(
                            "\u0420\u0430\u0441\u043f\u0443\u0441\u0442\u0438\u0442\u044c \u043a\u043b\u0430\u043d?",
                            clan.name(),
                            () -> PacketHandler.sendToServer(new ClanDisbandPacket(clan.id()))
                    )));

            addOwnerRequestButtons(x0, y0, clan);
            addOwnerKickButtons(x0, y0, clan);
        }
    }

    private ClanTextureButton newClanActionButton(int x, int y, String label, Runnable action) {
        return new ClanTextureButton(x, y, CLAN_ACTION_W, CLAN_ACTION_H,
                TabletButtonTextures.CLAN_ACTION,
                Component.literal(label), action);
    }

    private ClanTextureButton newClanDangerButton(int x, int y, String label, Runnable action) {
        return new ClanTextureButton(x, y, CLAN_ACTION_W, CLAN_ACTION_H,
                TabletButtonTextures.CLAN_DANGER,
                Component.literal(label), action);
    }

    private ClanTextureButton newClanSmallButton(int x, int y, String label, Runnable action) {
        return new ClanTextureButton(x, y, CLAN_SMALL_W, CLAN_SMALL_H,
                TabletButtonTextures.CLAN_SMALL,
                Component.literal(label), action);
    }

    private ClanTextureButton newClanPlainButton(
            int x,
            int y,
            int width,
            int height,
            ButtonTextureSet textures,
            String label,
            Runnable action
    ) {
        return new ClanTextureButton(x, y, width, height, textures, Component.literal(label), action);
    }

    private void addOwnerRequestButtons(int x0, int y0, ClanListPacket.ClanEntry clan) {
        if (!clan.owner()) return;
        if (clan.pendingEntries() == null || clan.pendingEntries().isEmpty()) return;

        int pendingScrollOffset = pageState.clampOffset(
                TabletPageState.CLAN_PENDING, clan.pendingEntries().size() - CLAN_VISIBLE_PENDING);
        int count = Math.min(CLAN_VISIBLE_PENDING, clan.pendingEntries().size() - pendingScrollOffset);
        int listTop = y0 + CLAN_PENDING_TOP + 18;
        int okX = x0 + CLAN_PENDING_LEFT + 60;
        int rejectX = x0 + CLAN_PENDING_LEFT + 104;

        for (int i = 0; i < count; i++) {
            ClanListPacket.PendingEntry pending = clan.pendingEntries().get(pendingScrollOffset + i);
            int buttonY = listTop + i * CLAN_PENDING_ROW_H - 3;
            this.addRenderableWidget(newClanPlainButton(okX, buttonY, 40, 14,
                    TabletButtonTextures.CLAN_REQUEST_ACCEPT, "\u041e\u041a",
                    () -> PacketHandler.sendToServer(new ClanAcceptJoinPacket(clan.id(), pending.uuid()))));
            this.addRenderableWidget(newClanPlainButton(rejectX, buttonY, 44, 14,
                    TabletButtonTextures.CLAN_REQUEST_REJECT, "\u041e\u0442\u043a\u043b.",
                    () -> PacketHandler.sendToServer(new ClanRejectJoinPacket(clan.id(), pending.uuid()))));
        }
    }

    private void addOwnerKickButtons(int x0, int y0, ClanListPacket.ClanEntry clan) {
        if (!clan.owner()) return;

        List<ClanListPacket.MemberEntry> members = clan.memberEntries() == null ? List.of() : clan.memberEntries();
        if (members.isEmpty()) return;

        int memberScrollOffset = pageState.clampOffset(
                TabletPageState.CLAN_MEMBERS, members.size() - CLAN_VISIBLE_MEMBERS);
        int count = Math.min(CLAN_VISIBLE_MEMBERS, members.size() - memberScrollOffset);
        int buttonX = x0 + CLAN_MEMBERS_LEFT + 112;
        int listTop = y0 + CLAN_MEMBERS_TOP + 14;

        for (int i = 0; i < count; i++) {
            ClanListPacket.MemberEntry member = members.get(memberScrollOffset + i);
            if (member.uuid().equals(clan.ownerUuid())) continue;

            int buttonY = listTop + i * CLAN_MEMBER_ROW_H - 3;
            this.addRenderableWidget(newClanPlainButton(buttonX, buttonY, 42, 14,
                    TabletButtonTextures.CLAN_KICK, "\u041a\u0438\u043a",
                    () -> openClanConfirmation(
                            "\u0418\u0441\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0438\u0433\u0440\u043e\u043a\u0430?",
                            member.name(),
                            () -> PacketHandler.sendToServer(new ClanKickMemberPacket(clan.id(), member.uuid()))
                    )));
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (TabletClientState.shouldClose()) {
            Minecraft.getInstance().setScreen(null);
            TabletClientState.resetCloseFlag();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try (TacticalUi.FrameScope ignoredFrame = TacticalUi.openFrame(
                frameClock.nextFrame(Util.getMillis(), false));
             GuiTextureRenderer.AlphaBlendScope ignoredBlend = GuiTextureRenderer.openAlphaBlend(g)) {
            renderFrame(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderFrame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int x = panelX();
        int y = panelY();

        ResourceLocation panel = getPanelTexture();
        GuiTextureRenderer.blitWithAlpha(g, panel, x, y, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);

        try (ScissorScope ignored = ScissorScope.open(
                g, x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H)) {
            TacticalUi.drawPanel(g, x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        }
        navigationRail.render(g, mouseX, mouseY, partialTick);
        try (ScissorScope ignored = ScissorScope.open(
                g, x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H)) {
            renderShell(g, x, y);
            renderPageContent(g, x, y);
            if (PAGES[pageState.currentPage()].type() == PageType.ACTIONS) {
                actionGrid.render(g, mouseX, mouseY, partialTick);
            }
            renderFooter(g, x, y);
            super.render(g, mouseX, mouseY, partialTick);
        }

        renderHoverFeedback(g, mouseX, mouseY);
    }

    private void renderPageContent(GuiGraphics g, int x, int y) {
        TabletPage page = PAGES[pageState.currentPage()];

        if (page.type() == PageType.CLAN) {
            drawClans(g, x, y);
            return;
        }

        if (page.type() == PageType.SERVER_INFO) {
            drawServerInfo(g, x, y);
            return;
        }

        if (page.type() == PageType.PROFILE) {
            TabletProfileView.render(g, font, x + CONTENT_X, y + CONTENT_Y, new TabletProfileView.Model(
                    TabletClientState.getCoins() + " \u043c",
                    String.valueOf(TabletClientState.getWins()),
                    String.valueOf(TabletClientState.getMatchesPlayed()),
                    String.valueOf(TabletClientState.getKills()),
                    String.valueOf(TabletClientState.getDeaths()),
                    TabletClientState.getKdaText(),
                    TabletClientState.getCareerProgressPercent() + "%"
            ));
        }
    }

    private void renderShell(GuiGraphics g, int x, int y) {
        if (ChaosClientState.isActive() && PAGES[pageState.currentPage()].type() == PageType.ACTIONS) {
            drawHeader(g, x, y, "ХАОС · Игра " + ChaosClientState.gameNumber());
            g.drawString(font, "Выбери класс для следующей жизни", x + HEADER_X, y + 36, 0xFFFFA6CE, false);
        } else {
            drawHeader(g, x, y, PAGES[pageState.currentPage()].title());
        }
    }

    private void renderFooter(GuiGraphics g, int x, int y) {
        TabletPage page = PAGES[pageState.currentPage()];
        String footer;
        if (page.type() == PageType.ACTIONS) {
            if (ChaosClientState.isActive()) {
                footer = "Доступно: " + ChaosClientState.availableCount() + " из 3";
                g.drawString(Minecraft.getInstance().font, fitText(footer, 266), x + 96, y + 201, 0xFFFFA6CE, false);
                return;
            }
            int rows = ScrollableGridLayout.rowCount(page.actions().size());
            footer = page.actions().size() + " классов";
            if (rows > ScrollableGridLayout.VISIBLE_ROWS) {
                footer += " • колесо: ряд " + (actionGrid.scrollRows() + 1) + "/"
                        + (ScrollableGridLayout.maxScrollRows(page.actions().size()) + 1);
            }
        } else if (page.type() == PageType.CLAN) {
            footer = "Список, участники и заявки прокручиваются отдельно";
        } else {
            footer = "Данные обновляются с сервера";
        }
        g.drawString(Minecraft.getInstance().font, fitText(footer, 266), x + 96, y + 201, 0xFF9FB2A4, false);
    }

    private ResourceLocation getPanelTexture() {
        int tier = TabletClientState.getTabletAppearanceTier();
        if (tier >= 2) return PANEL_LEGEND;
        if (tier == 1) return PANEL_EPIC;
        return PANEL;
    }

    private void drawClans(GuiGraphics g, int x, int y) {
        List<ClanListPacket.ClanEntry> clans = TabletClientState.getClans();
        int selectedClanIndex = pageState.selectedClanIndex();
        if (selectedClanIndex >= 0 && selectedClanIndex < clans.size()) {
            drawClanDetails(g, x, y, clans.get(selectedClanIndex));
            return;
        }

        int listLeft = x + CLAN_LIST_LEFT;
        int listTop = y + CLAN_LIST_TOP;

        if (clans.isEmpty()) {
            drawWrappedText(g, "\u041a\u043b\u0430\u043d\u043e\u0432 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442.", listLeft, listTop + 12, CLAN_ROW_W, 0xFFE6F0E8);
            drawWrappedText(g, "\u0421\u043e\u0437\u0434\u0430\u0439 \u043f\u0435\u0440\u0432\u044b\u0439 \u043a\u043b\u0430\u043d \u0437\u0430 " + ClanConstants.CREATE_COST + " \u043c\u043e\u043d\u0435\u0442.", listLeft, listTop + 38, CLAN_ROW_W, 0xFF9FB2A4);
            return;
        }

        TabletDataViewport.VisibleRange visible = TabletDataViewport.visibleRange(
                clans.size(), pageState.offset(TabletPageState.CLAN_LIST), 4);
        int clanScrollOffset = pageState.clampOffset(TabletPageState.CLAN_LIST, visible.maximumOffset());
        int rowCount = visible.size();
        for (int i = 0; i < rowCount; i++) {
            int index = clanScrollOffset + i;
            ClanListPacket.ClanEntry clan = clans.get(index);
            int rowTop = listTop + i * CLAN_ROW_GAP;
            if (isBlackClanColor(clan.color())) {
                g.fill(listLeft - 1, rowTop + 1, listLeft + 9, rowTop + 11, 0xFFFFFFFF);
            }
            g.fill(listLeft, rowTop + 2, listLeft + 8, rowTop + 10, clan.color());
        }

        if (clans.size() > 4) {
            drawInfoScrollbar(g, listLeft + CLAN_ROW_W + 7, listTop, 92, clanScrollOffset, Math.max(1, clans.size() - 4));
        }
    }

    private void drawClanDetails(GuiGraphics g, int x, int y, ClanListPacket.ClanEntry clan) {
        if (clan == null) return;
        drawClanDetailsSimple(g, x, y, clan);
    }

    private void drawClanDetailsSimple(GuiGraphics g, int x, int y, ClanListPacket.ClanEntry clan) {
        drawCenteredClanText(g, "[" + clan.tag() + "] " + clan.name(),
                x + CONTENT_X + CONTENT_W / 2, y + 56, clan.color());

        String status = clan.owner()
                ? "\u0421\u0442\u0430\u0442\u0443\u0441: \u0433\u043b\u0430\u0432\u0430"
                : clan.member() ? "\u0421\u0442\u0430\u0442\u0443\u0441: \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a"
                : clan.pending() ? "\u0421\u0442\u0430\u0442\u0443\u0441: \u0437\u0430\u044f\u0432\u043a\u0430"
                : "\u0421\u0442\u0430\u0442\u0443\u0441: \u043d\u0435 \u0432 \u043a\u043b\u0430\u043d\u0435";
        int statusColor = clan.owner() || clan.pending() ? 0xFFE7C76A : clan.member() ? 0xFF72D68A : 0xFF9FB2A4;

        int left = x + CLAN_INFO_LEFT;
        int top = y + CLAN_INFO_TOP;

        g.drawString(Minecraft.getInstance().font, status, left, top, statusColor, false);
        g.drawString(Minecraft.getInstance().font, "\u0413\u043b\u0430\u0432\u0430: " + fitText(clan.ownerName(), 112), left, top + 14, 0xFFE6F0E8, false);
        g.drawString(Minecraft.getInstance().font, "\u0423\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432: " + clan.memberCount() + "/" + ClanConstants.MAX_MEMBERS, left, top + 28, 0xFFE6F0E8, false);
        g.drawString(Minecraft.getInstance().font, "\u041a\u041a: " + clan.clanCoins(), left, top + 42, 0xFF72D68A, false);

        drawClanMembers(g, clan, x + CLAN_MEMBERS_LEFT, y + CLAN_MEMBERS_TOP);
        drawClanPending(g, clan, x + CLAN_PENDING_LEFT, y + CLAN_PENDING_TOP);
    }

    private void drawClanMembers(GuiGraphics g, ClanListPacket.ClanEntry clan, int x, int y) {
        List<ClanListPacket.MemberEntry> members = clan.memberEntries() == null ? List.of() : clan.memberEntries();
        g.drawString(Minecraft.getInstance().font, "\u0423\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u0438", x, y, 0xFFE6F0E8, false);

        if (members.isEmpty()) {
            g.drawString(Minecraft.getInstance().font, "\u041d\u0435\u0442 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432", x, y + 14, 0xFF777777, false);
            return;
        }

        TabletDataViewport.VisibleRange visible = TabletDataViewport.visibleRange(
                members.size(), pageState.offset(TabletPageState.CLAN_MEMBERS), CLAN_VISIBLE_MEMBERS);
        int memberScrollOffset = pageState.clampOffset(TabletPageState.CLAN_MEMBERS, visible.maximumOffset());
        int listTop = y + 14;
        int count = visible.size();

        try (ScissorScope ignored = ScissorScope.open(
                g, x, listTop, CLAN_MEMBERS_WIDTH, y + CLAN_MEMBERS_HEIGHT - listTop)) {
            for (int i = 0; i < count; i++) {
                ClanListPacket.MemberEntry member = members.get(memberScrollOffset + i);
                boolean owner = member.uuid().equals(clan.ownerUuid());
                String suffix = owner ? " *" : "";
                int rowY = listTop + i * CLAN_MEMBER_ROW_H;
                int nameWidth = clan.owner() && !owner ? 70 : 114;
                int color = owner ? 0xFFE7C76A : 0xFFE6F0E8;
                g.drawString(Minecraft.getInstance().font, fitText(member.name() + suffix, nameWidth), x, rowY, color, false);
            }
        }

        if (members.size() > CLAN_VISIBLE_MEMBERS) {
            drawInfoScrollbar(
                    g,
                    x + CLAN_MEMBERS_WIDTH - 5,
                    listTop,
                    CLAN_MEMBERS_HEIGHT - 14,
                    memberScrollOffset,
                    Math.max(1, members.size() - CLAN_VISIBLE_MEMBERS)
            );
        }
    }

    private void drawClanPending(GuiGraphics g, ClanListPacket.ClanEntry clan, int x, int y) {
        int pending = clan.pendingEntries() == null ? 0 : clan.pendingEntries().size();
        g.drawString(Minecraft.getInstance().font, "\u0417\u0430\u044f\u0432\u043a\u0438: " + pending, x, y, clan.owner() ? 0xFFE7C76A : 0xFF9FB2A4, false);
        if (!clan.owner()) return;

        if (pending <= 0) {
            g.drawString(Minecraft.getInstance().font, "\u041d\u0435\u0442 \u0437\u0430\u044f\u0432\u043e\u043a", x, y + 18, 0xFF777777, false);
            return;
        }

        TabletDataViewport.VisibleRange visible = TabletDataViewport.visibleRange(
                pending, pageState.offset(TabletPageState.CLAN_PENDING), CLAN_VISIBLE_PENDING);
        int pendingScrollOffset = pageState.clampOffset(TabletPageState.CLAN_PENDING, visible.maximumOffset());
        int count = visible.size();
        int listTop = y + 18;

        try (ScissorScope ignored = ScissorScope.open(
                g, x, listTop, CLAN_PENDING_WIDTH, y + CLAN_PENDING_HEIGHT - listTop)) {
            for (int i = 0; i < count; i++) {
                ClanListPacket.PendingEntry entry = clan.pendingEntries().get(pendingScrollOffset + i);
                g.drawString(Minecraft.getInstance().font, fitText(entry.name(), 48), x,
                        listTop + i * CLAN_PENDING_ROW_H, 0xFFE6F0E8, false);
            }
        }

        if (pending > CLAN_VISIBLE_PENDING) {
            drawInfoScrollbar(g, x + CLAN_PENDING_WIDTH - 4, listTop, CLAN_PENDING_HEIGHT - 18, pendingScrollOffset, Math.max(1, pending - CLAN_VISIBLE_PENDING));
        }
    }

    private String fitText(String text, int width) {
        if (text == null) return "";
        if (Minecraft.getInstance().font.width(text) <= width) return text;
        return Minecraft.getInstance().font.plainSubstrByWidth(text, Math.max(0, width - 12)) + "...";
    }

    private void drawCenteredClanText(GuiGraphics g, String text, int centerX, int y, int color) {
        if (isBlackClanColor(color)) {
            g.drawCenteredString(Minecraft.getInstance().font, text, centerX - 1, y, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, text, centerX + 1, y, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, text, centerX, y - 1, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, text, centerX, y + 1, 0xFFFFFFFF);
        }
        g.drawCenteredString(Minecraft.getInstance().font, text, centerX, y, color);
    }

    private boolean isBlackClanColor(int color) {
        return (color & 0x00FFFFFF) == 0x00111111;
    }

    private boolean isClanColorTaken(int color, String ignoredClanId) {
        for (ClanListPacket.ClanEntry clan : TabletClientState.getClans()) {
            if (ignoredClanId != null && ignoredClanId.equals(clan.id())) continue;
            if (clan.color() == color) return true;
        }
        return false;
    }

    private boolean hasFreeClanColor(String ignoredClanId) {
        for (int color : ClanConstants.ALLOWED_COLORS) {
            if (!isClanColorTaken(color, ignoredClanId)) {
                return true;
            }
        }
        return false;
    }

    private int firstFreeClanColor(String ignoredClanId, int fallbackColor) {
        for (int color : ClanConstants.ALLOWED_COLORS) {
            if (!isClanColorTaken(color, ignoredClanId)) {
                return color;
            }
        }
        return fallbackColor;
    }

    private void drawWrappedText(GuiGraphics g, String text, int x, int y, int width, int color) {
        int lineY = y;
        for (FormattedCharSequence line : Minecraft.getInstance().font.split(Component.literal(text), width)) {
            g.drawString(Minecraft.getInstance().font, line, x, lineY, color, false);
            lineY += INFO_LINE_HEIGHT;
        }
    }

    private void drawServerInfo(GuiGraphics g, int x, int y) {
        List<FormattedCharSequence> lines = getServerInfoLines(INFO_WIDTH);
        int maxScroll = getMaxInfoScroll(lines);
        int infoScroll = pageState.clampOffset(TabletPageState.SERVER_INFO, maxScroll);

        int left = x + INFO_LEFT;
        int top = y + INFO_TOP;
        int right = left + INFO_WIDTH;
        int bottom = top + INFO_HEIGHT;

        try (ScissorScope ignored = ScissorScope.open(g, left, top, right - left, bottom - top)) {
            int lineY = top - infoScroll;
            for (FormattedCharSequence line : lines) {
                if (lineY > top - INFO_LINE_HEIGHT && lineY < bottom) {
                    g.drawString(
                            Minecraft.getInstance().font,
                            line,
                            left,
                            lineY,
                            0xFFE6F0E8,
                            false
                    );
                }
                lineY += INFO_LINE_HEIGHT;
            }
        }
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
        TabletDataViewport.Scrollbar scrollbar = TabletDataViewport.scrollbar(height, scroll, maxScroll, 16);
        int thumbY = y + scrollbar.yOffset();
        g.fill(x, thumbY, x + 3, thumbY + scrollbar.height(), 0xFF72D68A);
    }

    private boolean isMouseOverInfoArea(double mouseX, double mouseY) {
        int x = (this.width - UI_WIDTH) / 2 + INFO_LEFT;
        int y = (this.height - UI_HEIGHT) / 2 + INFO_TOP;

        return mouseX >= x && mouseX <= x + INFO_WIDTH + 10
                && mouseY >= y && mouseY <= y + INFO_HEIGHT;
    }

    private boolean isMouseOverClanList(double mouseX, double mouseY) {
        int x = (this.width - UI_WIDTH) / 2 + CLAN_LIST_LEFT;
        int y = (this.height - UI_HEIGHT) / 2 + CLAN_LIST_TOP;

        return mouseX >= x && mouseX <= x + CLAN_ROW_W + 14
                && mouseY >= y && mouseY <= y + 98;
    }

    private boolean isMouseOverPendingList(double mouseX, double mouseY) {
        int x = (this.width - UI_WIDTH) / 2 + CLAN_PENDING_LEFT;
        int y = (this.height - UI_HEIGHT) / 2 + CLAN_PENDING_TOP;

        return mouseX >= x && mouseX <= x + CLAN_PENDING_WIDTH
                && mouseY >= y && mouseY <= y + CLAN_PENDING_HEIGHT;
    }

    private boolean isMouseOverMemberList(double mouseX, double mouseY) {
        int x = (this.width - UI_WIDTH) / 2 + CLAN_MEMBERS_LEFT;
        int y = (this.height - UI_HEIGHT) / 2 + CLAN_MEMBERS_TOP;

        return mouseX >= x && mouseX <= x + CLAN_MEMBERS_WIDTH
                && mouseY >= y && mouseY <= y + CLAN_MEMBERS_HEIGHT;
    }

    private boolean isClanWarSetupOnly() {
        return TabletClientState.isClanWarSet() && !TabletClientState.isGameRunning();
    }

    private boolean isCurrentPlayerInClan() {
        return getCurrentClan() != null;
    }

    private ClanListPacket.ClanEntry getCurrentClan() {
        for (ClanListPacket.ClanEntry clan : TabletClientState.getClans()) {
            if (clan.member()) return clan;
        }
        return null;
    }

    private boolean isMarineAction(TabletAction action) {
        return action != null && MARINE_CLASS.equals(action.classKey());
    }

    private boolean isMarineUnlockedForCurrentClan() {
        ClanListPacket.ClanEntry clan = getCurrentClan();
        return clan != null && clan.marineUnlocked();
    }

    private boolean canBuyMarineForCurrentClan() {
        ClanListPacket.ClanEntry clan = getCurrentClan();
        return clan != null && clan.owner() && !clan.marineUnlocked() && clan.clanCoins() >= ClanManager.MARINE_CLASS_COST;
    }

    private boolean isClanWarSoloShopRestricted(TabletAction action) {
        return TabletClientState.isClanWarSet()
                && action.shop()
                && !isCurrentPlayerInClan();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (navigationRail.mouseScrolled(mouseX, mouseY, delta)) return true;

        if (PAGES[pageState.currentPage()].type() == PageType.ACTIONS && actionGrid.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }

        if (PAGES[pageState.currentPage()].type() == PageType.CLAN) {
            List<ClanListPacket.ClanEntry> clans = TabletClientState.getClans();
            int selectedClanIndex = pageState.selectedClanIndex();
            if (selectedClanIndex < 0 && isMouseOverClanList(mouseX, mouseY)) {
                pageState.scroll(TabletPageState.CLAN_LIST, -(int) Math.signum(delta), clans.size() - 4);
                TabletScreen.this.init();
                return true;
            }

            if (selectedClanIndex >= 0 && selectedClanIndex < clans.size() && isMouseOverMemberList(mouseX, mouseY)) {
                ClanListPacket.ClanEntry clan = clans.get(selectedClanIndex);
                int members = clan.memberEntries() == null ? 0 : clan.memberEntries().size();
                pageState.scroll(TabletPageState.CLAN_MEMBERS,
                        -(int) Math.signum(delta), members - CLAN_VISIBLE_MEMBERS);
                TabletScreen.this.init();
                return true;
            }

            if (selectedClanIndex >= 0 && selectedClanIndex < clans.size() && isMouseOverPendingList(mouseX, mouseY)) {
                ClanListPacket.ClanEntry clan = clans.get(selectedClanIndex);
                int pending = clan.pendingEntries() == null ? 0 : clan.pendingEntries().size();
                pageState.scroll(TabletPageState.CLAN_PENDING,
                        -(int) Math.signum(delta), pending - CLAN_VISIBLE_PENDING);
                TabletScreen.this.init();
                return true;
            }
        }

        if (PAGES[pageState.currentPage()].type() == PageType.SERVER_INFO && isMouseOverInfoArea(mouseX, mouseY)) {
            int maxScroll = getMaxInfoScroll(getServerInfoLines(INFO_WIDTH));
            pageState.scroll(TabletPageState.SERVER_INFO,
                    -(int) Math.round(delta * INFO_LINE_HEIGHT * 3.0D), maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            GuiEventListener nextNavigation = navigationRail.moveFocus(keyCode, getFocused());
            if (nextNavigation != null) {
                setFocused(nextNavigation);
                return true;
            }
            if (PAGES[pageState.currentPage()].type() == PageType.ACTIONS) {
                GuiEventListener nextAction = actionGrid.moveFocus(keyCode, getFocused());
                if (nextAction != null) {
                    setFocused(nextAction);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int panelX() {
        return Math.max(0, (this.width - UI_WIDTH) / 2);
    }

    private int panelY() {
        return Math.max(0, (this.height - UI_HEIGHT) / 2);
    }

    private void selectPage(int pageIndex) {
        if (!pageState.selectPage(pageIndex, PAGES.length)) return;
        playSound(CLICK);
        init();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawHeader(GuiGraphics g, int x, int y, String text) {
        g.drawString(
                Minecraft.getInstance().font,
                text,
                x + HEADER_X + 8,
                y + HEADER_Y + 9,
                0xFFE6F0E8,
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
        PROFILE,
        CLAN
    }

    private record TabletPage(String title, String key, PageType type, List<TabletAction> actions) {
    }

    private record TabletAction(
            String label,
            String classKey,
            int actionId,
            boolean rtp,
            boolean locked,
            boolean shop,
            boolean exclusive,
            int price,
            int fixedLevel,
            ResourceLocation icon
    ) {
        private static TabletAction fromDefinition(ClassDefinition definition) {
            return new TabletAction(
                    definition.name().getString().toUpperCase(Locale.ROOT),
                    definition.classKey(),
                    definition.actionId(),
                    false,
                    false,
                    definition.category() == ClassCategory.SHOP,
                    definition.category() == ClassCategory.EXCLUSIVE,
                    definition.price(),
                    definition.fixedTier(),
                    definition.icon()
            );
        }

        private static TabletAction rtp(String label, int actionId) {
            return new TabletAction(label, "", actionId, true, false, false, false, 0, -1,
                    ClassDefinitions.FALLBACK_ICON);
        }
    }

    private static List<TabletAction> actionsFor(ClassCategory category) {
        return ClassDefinitions.byCategory(category).stream().map(TabletAction::fromDefinition).toList();
    }

    private void renderActionCard(GuiGraphics g, TabletAction action, int x, int y, int width, int height,
                                  int mouseX, int mouseY, float partialTick, boolean focused) {
        boolean hover = focused || mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        ActionPresentation presentation = describeAction(action);
        ClassTier actualTier = ChaosClientState.isActive() && ChaosClientState.isOffered(action.classKey())
                ? ClassTier.clamp(ChaosClientState.tier(action.classKey()))
                : ClassButtonStyle.actualTier(action.fixedLevel(), TabletClientState.getClassTier(action.classKey()));
        ResourceLocation resolvedIcon = ClassDefinitions.byClassKey(action.classKey())
                .map(definition -> ClassIconResolver.resolve(definition,
                        ClientResourcePresenceCache::exists))
                .orElse(ClassDefinitions.FALLBACK_ICON);
        String title = fitText(action.label(), 88);
        String status = fitText(presentation.status(), 92);
        TabletActionCard.render(g, x, y, width, height, hover, resolvedIcon,
                ClientResourcePresenceCache.exists(resolvedIcon), title,
                new TabletActionCard.Presentation(presentation.active(), actualTier, status,
                        presentation.statusColor(), presentation.marker()));
    }

    private Component actionNarration(TabletAction action) {
        ActionPresentation presentation = describeAction(action);
        return Component.literal(action.label() + ". " + presentation.detail());
    }

    private void pressAction(TabletAction action) {
        if (!isActionActive(action) || action.locked()) return;
        if (ChaosClientState.isActive() && !action.rtp()) {
            playSound(CLICK);
            PacketHandler.sendToServer(new TabletPacket(action.actionId()));
            return;
        }
        if (isClanWarSetupOnly()) return;
        if (TabletClientState.isCompetitiveSet() && action.shop()) return;
        if (isClanWarSoloShopRestricted(action)) return;

        if (isMarineAction(action) && !isMarineUnlockedForCurrentClan()) {
            PacketHandler.sendToServer(new TabletPacket(action.actionId()));
            return;
        }

        if (action.shop() && !TabletClientState.isClassPurchased(action.classKey())) {
            showPurchaseConfirmation(action);
            return;
        }

        if (isBaseClassAction(action) && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
            showBaseUnlockConfirmation(action);
            return;
        }

        if (isBaseClassAction(action) && !dismissedUpgradePrompts.contains(action.classKey())) {
            int targetTier = getAvailableUpgradeTier(action);
            if (targetTier > PlayerProgressManager.BASIC_TIER
                    && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                showTierUpgradeConfirmation(action, targetTier);
                return;
            }
        }

        playSound(CLICK);
        PacketHandler.sendToServer(new TabletPacket(action.actionId()));
    }

    private boolean isActionActive(TabletAction action) {
        if (ChaosClientState.isActive() && !action.rtp()) {
            return TabletClientState.isGameRunning()
                    && ChaosClientState.requiresSelection()
                    && ChaosClientState.isOffered(action.classKey())
                    && !ChaosClientState.isSpent(action.classKey())
                    && ChaosClientState.selected().isBlank();
        }
        if (action.locked() || isClanWarSetupOnly()) return false;
        if (isMarineAction(action) && !isMarineUnlockedForCurrentClan()) return canBuyMarineForCurrentClan();
        if (action.exclusive() && !isMarineAction(action)
                && !TabletClientState.isClassPurchased(action.classKey())) return false;
        if (TabletClientState.isCompetitiveSet() && action.shop()) return false;
        if (isClanWarSoloShopRestricted(action)) return false;

        if (action.shop() && !TabletClientState.isClassPurchased(action.classKey())) {
            return TabletClientState.getCoins() >= action.price();
        }
        if (isBaseClassAction(action) && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
            return TabletClientState.getCoins() >= PlayerProgressManager.BASE_UNLOCK_COST;
        }
        if (isBaseClassAction(action) && !dismissedUpgradePrompts.contains(action.classKey())) {
            int targetTier = getAvailableUpgradeTier(action);
            if (targetTier > PlayerProgressManager.BASIC_TIER
                    && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) return true;
        }
        if (!TabletClientState.isGameRunning()) return false;
        return !TabletClientState.isKitUsed() && TabletClientState.getCooldown(action.actionId()) <= 0L;
    }

    private ActionPresentation describeAction(TabletAction action) {
        boolean active = isActionActive(action);
        if (ChaosClientState.isActive() && !action.rtp()) {
            if (action.classKey().equals(ChaosClientState.selected()))
                return unavailable("Используется сейчас", "✓");
            if (ChaosClientState.isSpent(action.classKey()))
                return unavailable("Класс уже потрачен", "×");
            if (!ChaosClientState.isOffered(action.classKey()))
                return unavailable("В режиме Хаос доступны только 3 класса этой игры");
            return new ActionPresentation("Доступен в Хаосе", "Выбрать для следующей жизни",
                    active, 0xFFFF4FA3, "!");
        }
        if (isClanWarSetupOnly()) return unavailable("Матч ещё не начался");
        if (isMarineAction(action)) {
            long cooldown = TabletClientState.getCooldown(action.actionId());
            MarineActionPresentationPolicy.Presentation marine =
                    MarineActionPresentationPolicy.describe(
                            isCurrentPlayerInClan(),
                            isMarineUnlockedForCurrentClan(),
                            canBuyMarineForCurrentClan(),
                            active,
                            TabletClientState.isKitUsed(),
                            cooldown > 0L ? formatTime(cooldown) : "",
                            TabletClientState.isGameRunning(),
                            ClanManager.MARINE_CLASS_COST
                    );
            return new ActionPresentation(
                    marine.status(),
                    marine.hint(),
                    marine.active(),
                    marine.color(),
                    marine.marker()
            );
        }
        if (action.exclusive()
                && !isMarineAction(action)
                && !TabletClientState.isClassPurchased(action.classKey())) {
            return unavailable("Эксклюзивный класс не выдан");
        }
        if (TabletClientState.isCompetitiveSet() && action.shop()) {
            return unavailable("Недоступен в соревновательном режиме");
        }
        if (isClanWarSoloShopRestricted(action)) return unavailable("Требуется клан", "C");

        if (action.shop() && !TabletClientState.isClassPurchased(action.classKey())) {
            int color = TabletClientState.getCoins() >= action.price() ? 0xFFE7C76A : 0xFFD87575;
            return new ActionPresentation(TabletStatusFormatter.purchase(action.price()),
                    TabletClientState.getCoins() >= action.price() ? "Нажмите для покупки" : "Недостаточно монет",
                    active, color, "¤");
        }
        if (isBaseClassAction(action) && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
            boolean enough = TabletClientState.getCoins() >= PlayerProgressManager.BASE_UNLOCK_COST;
            return new ActionPresentation("Открытие • " + PlayerProgressManager.BASE_UNLOCK_COST + " монет",
                    enough ? "Нажмите для разблокировки" : "Недостаточно монет", active,
                    enough ? 0xFFE7C76A : 0xFFD87575, "▣");
        }

        if (isBaseClassAction(action) && !dismissedUpgradePrompts.contains(action.classKey())) {
            int targetTier = getAvailableUpgradeTier(action);
            if (targetTier > PlayerProgressManager.BASIC_TIER
                    && TabletClientState.getCoins() >= PlayerProgressManager.getUpgradeCost(targetTier)) {
                return new ActionPresentation(TabletStatusFormatter.upgrade(PlayerProgressManager.getUpgradeCost(targetTier)),
                        "Доступно повышение тира", true, 0xFF72D68A, "↑");
            }
        }

        long cooldown = TabletClientState.getCooldown(action.actionId());
        if (cooldown > 0L) {
            return new ActionPresentation(TabletStatusFormatter.cooldown(formatTime(cooldown)),
                    "Класс на перезарядке", false, 0xFFE7C76A, "◷");
        }
        if (TabletClientState.isKitUsed()) return unavailable("Уже использован", "✓");
        if (!TabletClientState.isGameRunning()) return unavailable("Нет активной игры");
        if (isBaseClassAction(action)) {
            int tier = TabletClientState.getClassTier(action.classKey());
            int xp = TabletClientState.getXP(action.classKey());
            ClassTier current = ClassTier.clamp(tier);
            Optional<ClassTier> next = current.next();
            if (next.isPresent() && xp >= next.get().requiredXp()
                    && !dismissedUpgradePrompts.contains(action.classKey())) {
                return new ActionPresentation(TabletStatusFormatter.upgrade(next.get().upgradeCost()),
                        "Доступно повышение тира", active, 0xFF72D68A, "↑");
            }
            int cap = next.map(ClassTier::requiredXp).orElse(current.xpCap());
            return new ActionPresentation(TabletStatusFormatter.progress(tierName(current), xp, cap),
                    "Класс доступен", active, 0xFF9FB2A4, "✓");
        }
        if (action.shop()) return new ActionPresentation("Куплено", "Класс доступен", active, 0xFF72D68A, "✓");
        return new ActionPresentation("Доступен", "Класс доступен", active, 0xFF72D68A, "✓");
    }

    private ActionPresentation unavailable(String detail) {
        return unavailable(detail, "▣");
    }

    private ActionPresentation unavailable(String detail, String marker) {
        return new ActionPresentation(detail, detail, false, 0xFFD87575, marker);
    }

    private String tierName(ClassTier tier) {
        return switch (tier) {
            case BASIC -> "Базовый";
            case RARE -> "Редкий";
            case EPIC -> "Эпический";
            case LEGEND -> "Легендарный";
            case MONSTER -> "Монстр";
        };
    }

    private boolean isBaseClassAction(TabletAction action) {
        return !action.shop() && !action.exclusive() && !action.rtp() && !action.locked();
    }

    private int getAvailableUpgradeTier(TabletAction action) {
        int tier = TabletClientState.getClassTier(action.classKey());
        int xp = TabletClientState.getXP(action.classKey());
        ClassTier current = ClassTier.clamp(tier);
        return current.next().filter(next -> xp >= next.requiredXp()).map(ClassTier::id)
                .orElse(PlayerProgressManager.BASIC_TIER);
    }

    private void renderHoverFeedback(GuiGraphics g, int mouseX, int mouseY) {
        if (PAGES[pageState.currentPage()].type() == PageType.ACTIONS) {
            Optional<TabletAction> hovered = actionGrid.itemAt(mouseX, mouseY);
            int hoveredId = hovered.map(TabletAction::actionId).orElse(-1);
            if (hoveredId >= 0 && hoveredId != lastHoveredActionId) playSound(HOVER);
            lastHoveredActionId = hoveredId;
            if (hovered.isPresent()) {
                TabletAction action = hovered.get();
                ActionPresentation presentation = describeAction(action);
                g.renderComponentTooltip(Minecraft.getInstance().font,
                        List.of(Component.literal(action.label()), Component.literal(presentation.detail())),
                        mouseX, mouseY);
                return;
            }
        } else {
            lastHoveredActionId = -1;
        }

        if (isMouseOverRtp(mouseX, mouseY)) {
            g.renderComponentTooltip(Minecraft.getInstance().font,
                    List.of(Component.literal("RTP"), Component.literal(rtpUnavailableReason())), mouseX, mouseY);
        }
    }

    private boolean isMouseOverRtp(double mouseX, double mouseY) {
        int x = panelX() + RTP_X;
        int y = panelY() + RTP_Y;
        return mouseX >= x && mouseX < x + RTP_W && mouseY >= y && mouseY < y + RTP_H;
    }

    private boolean isRtpActive() {
        return TabletClientState.isGameRunning()
                && !TabletClientState.isRtpUsed()
                && (!ChaosClientState.isActive() || !ChaosClientState.requiresSelection());
    }

    private String rtpUnavailableReason() {
        if (ChaosClientState.isActive() && ChaosClientState.requiresSelection()) {
            return "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u043a\u043b\u0430\u0441\u0441 \u0434\u043b\u044f \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0439 \u0436\u0438\u0437\u043d\u0438";
        }
        if (TabletClientState.isRtpUsed()) return "RTP уже использован";
        if (!TabletClientState.isGameRunning()) return "Недоступно: нет активной игры";
        return "Случайная телепортация";
    }

    private record ActionPresentation(String status, String detail, boolean active, int statusColor, String marker) {
    }

    private class TabletRtpButton extends TacticalButton {
        private TabletRtpButton(int x, int y) {
            super(x, y, RTP_W, RTP_H, Component.literal("RTP"), ignored -> { });
            withAccentBar(true);
            onHover(() -> playSound(HOVER));
        }

        @Override
        public void onPress() {
            if (!isRtpActive()) return;
            playSound(TELEPORT);
            PacketHandler.sendToServer(new TabletPacket(TELEPORT_RTP.actionId()));
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.active = isRtpActive();
            super.renderWidget(g, mouseX, mouseY, partialTick);
            int color = active ? 0xFF72D68A : 0xFF77867B;
            g.drawCenteredString(Minecraft.getInstance().font,
                    TabletClientState.isRtpUsed() ? "RTP ✓" : "RTP", getX() + width / 2, getY() + 6, color);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        }
    }

    private void showPurchaseConfirmation(TabletAction action) {
        showActionConfirmation(action, ConfirmAction.SHOP_PURCHASE, 0);
    }

    private void showBaseUnlockConfirmation(TabletAction action) {
        showActionConfirmation(action, ConfirmAction.BASE_UNLOCK, 0);
    }

    private void showTierUpgradeConfirmation(TabletAction action, int targetTier) {
        showActionConfirmation(action, ConfirmAction.TIER_UPGRADE, targetTier);
    }

    private void showActionConfirmation(TabletAction action, ConfirmAction confirmAction, int targetTier) {
        int price = confirmationPrice(action, confirmAction, targetTier);
        Component body = Component.literal(action.label() + "\n" + price + " \u043c\u043e\u043d\u0435\u0442\n"
                + "\u0411\u0430\u043b\u0430\u043d\u0441: " + TabletClientState.getCoins());
        Component confirmLabel = Component.literal(
                confirmAction == ConfirmAction.SHOP_PURCHASE ? "\u041a\u0443\u043f\u0438\u0442\u044c" : "\u041e\u041a");
        Minecraft.getInstance().setScreen(new TacticalDialog(
                this,
                Component.literal(confirmationTitle(confirmAction)),
                body,
                confirmLabel,
                Component.literal("\u041e\u0442\u043c\u0435\u043d\u0430"),
                () -> submitConfirmation(action, confirmAction, targetTier, price),
                () -> {
                    if (confirmAction == ConfirmAction.TIER_UPGRADE) {
                        dismissedUpgradePrompts.add(action.classKey());
                    }
                },
                true,
                false,
                () -> TabletClientState.getCoins() >= price
        ));
    }

    private void openClanCreateConfirmation() {
        Minecraft.getInstance().setScreen(new TacticalDialog(
                this,
                Component.literal("\u0421\u043e\u0437\u0434\u0430\u0442\u044c \u043a\u043b\u0430\u043d?"),
                Component.literal("\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanConstants.CREATE_COST
                        + " \u043c\u043e\u043d\u0435\u0442\n\u0411\u0430\u043b\u0430\u043d\u0441: " + TabletClientState.getCoins()),
                Component.literal("\u0414\u0430\u043b\u0435\u0435"),
                Component.literal("\u041e\u0442\u043c\u0435\u043d\u0430"),
                () -> Minecraft.getInstance().setScreen(new ClanCreateScreen()),
                () -> { },
                true,
                false,
                () -> TabletClientState.getCoins() >= ClanConstants.CREATE_COST
                        && TabletClientState.getClans().size() < ClanConstants.MAX_CLANS
                        && hasFreeClanColor("")
        ));
    }

    private void openClanJoinConfirmation(ClanListPacket.ClanEntry clan) {
        Minecraft.getInstance().setScreen(new TacticalDialog(
                this,
                Component.literal("\u0412\u0441\u0442\u0443\u043f\u0438\u0442\u044c \u0432 \u043a\u043b\u0430\u043d?"),
                Component.literal("[" + clan.tag() + "] " + clan.name()
                        + "\n\u0413\u043b\u0430\u0432\u0430 \u0440\u0430\u0441\u0441\u043c\u043e\u0442\u0440\u0438\u0442 \u0437\u0430\u044f\u0432\u043a\u0443"),
                Component.literal("\u0417\u0430\u044f\u0432\u043a\u0430"),
                Component.literal("\u041e\u0442\u043c\u0435\u043d\u0430"),
                () -> PacketHandler.sendToServer(new ClanJoinRequestPacket(clan.id())),
                () -> { },
                true,
                false
        ));
    }

    private void openClanConfirmation(String title, String detail, Runnable confirmAction) {
        Minecraft.getInstance().setScreen(new TacticalDialog(
                this,
                Component.literal(title),
                Component.literal(detail == null ? "" : detail),
                Component.literal("\u041e\u041a"),
                Component.literal("\u041e\u0442\u043c\u0435\u043d\u0430"),
                confirmAction,
                () -> { },
                true,
                true
        ));
    }

    private void submitConfirmation(TabletAction action, ConfirmAction confirmAction, int targetTier, int price) {
        if (TabletClientState.getCoins() < price) return;
        int actionId = switch (confirmAction) {
            case SHOP_PURCHASE -> action.actionId();
            case BASE_UNLOCK -> TabletPacket.unlockBaseActionId(action.actionId());
            case TIER_UPGRADE -> TabletPacket.upgradeActionId(action.actionId(), targetTier);
        };
        if (actionId >= 0) PacketHandler.sendToServer(new TabletPacket(actionId));
    }

    private int confirmationPrice(TabletAction action, ConfirmAction confirmAction, int targetTier) {
        return switch (confirmAction) {
            case SHOP_PURCHASE -> action.price();
            case BASE_UNLOCK -> PlayerProgressManager.BASE_UNLOCK_COST;
            case TIER_UPGRADE -> PlayerProgressManager.getUpgradeCost(targetTier);
        };
    }

    private static String confirmationTitle(ConfirmAction confirmAction) {
        return switch (confirmAction) {
            case SHOP_PURCHASE -> "\u041a\u0443\u043f\u0438\u0442\u044c \u043a\u043b\u0430\u0441\u0441?";
            case BASE_UNLOCK -> "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043a\u043b\u0430\u0441\u0441?";
            case TIER_UPGRADE -> "\u0423\u043b\u0443\u0447\u0448\u0438\u0442\u044c \u043a\u043b\u0430\u0441\u0441?";
        };
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
        private boolean submitting;

        private TabletConfirmScreen(TabletAction action, ConfirmAction confirmAction, int targetTier) {
            super(Component.literal("\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u0435"));
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
                    Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"),
                    ConfirmButtonKind.CANCEL,
                    this::cancel
            ));
            ConfirmTextureButton confirmButton = new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal(confirmAction == ConfirmAction.SHOP_PURCHASE ? "\u041a\u0423\u041f\u0418\u0422\u042c" : "\u041e\u041a"),
                    this::confirm
            );
            confirmButton.active = TabletClientState.getCoins() >= getPrice();
            this.addRenderableWidget(confirmButton);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);

            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;

            GuiTextureRenderer.blitWithAlpha(
                    g, CONFIRM_PANEL, x, y, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);

            g.drawCenteredString(Minecraft.getInstance().font, getConfirmTitle(), x + CONFIRM_W / 2, y + 17, 0xFF72D68A);
            g.drawCenteredString(Minecraft.getInstance().font, fitText(action.label(), CONFIRM_W - 30), x + CONFIRM_W / 2, y + 44, getShopTitleColor(action));
            g.drawCenteredString(Minecraft.getInstance().font, getPrice() + " \u043c\u043e\u043d\u0435\u0442", x + CONFIRM_W / 2, y + 58, 0xFFFFFFFF);
            boolean enoughCoins = TabletClientState.getCoins() >= getPrice();
            String balance = enoughCoins
                    ? "У тебя: " + TabletClientState.getCoins() + " монет"
                    : "Недостаточно монет: " + TabletClientState.getCoins() + "/" + getPrice();
            g.drawCenteredString(Minecraft.getInstance().font, balance, x + CONFIRM_W / 2, y + 72,
                    enoughCoins ? 0xFF9FB2A4 : 0xFFD87575);

            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void confirm() {
            if (submitting || TabletClientState.getCoins() < getPrice()) return;
            submitting = true;
            if (confirmAction == ConfirmAction.SHOP_PURCHASE) {
                PacketHandler.sendToServer(new TabletPacket(action.actionId()));
            } else if (confirmAction == ConfirmAction.BASE_UNLOCK) {
                PacketHandler.sendToServer(new TabletPacket(TabletPacket.unlockBaseActionId(action.actionId())));
            } else {
                int actionId = TabletPacket.upgradeActionId(action.actionId(), targetTier);
                if (actionId >= 0) {
                    PacketHandler.sendToServer(new TabletPacket(actionId));
                }
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
                return "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043a\u043b\u0430\u0441\u0441?";
            }

            if (confirmAction == ConfirmAction.TIER_UPGRADE) {
                return "\u0423\u043b\u0443\u0447\u0448\u0438\u0442\u044c \u043a\u043b\u0430\u0441\u0441?";
            }

            return "\u041a\u0443\u043f\u0438\u0442\u044c \u043a\u043b\u0430\u0441\u0441?";
        }

        private int getShopTitleColor(TabletAction action) {
            ClassTier tier = ClassButtonStyle.actualTier(
                    action.fixedLevel(),
                    TabletClientState.getClassTier(action.classKey()));
            return ClassButtonStyle.color(tier);
        }
    }

    private class ClanCreateConfirmScreen extends Screen {

        private ClanCreateConfirmScreen() {
            super(Component.literal("\u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0435 \u043a\u043b\u0430\u043d\u0430"));
        }

        @Override
        protected void init() {
            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            int buttonY = y + 94;

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + 18,
                    buttonY,
                    Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"),
                    ConfirmButtonKind.CANCEL,
                    this::returnToTablet
            ));
            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal("\u0414\u0410\u041b\u0415\u0415"),
                    () -> Minecraft.getInstance().setScreen(new ClanCreateScreen())
            ));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);

            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            GuiTextureRenderer.blitWithAlpha(
                    g, CONFIRM_PANEL, x, y, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);
            g.drawCenteredString(Minecraft.getInstance().font, "\u0421\u043e\u0437\u0434\u0430\u0442\u044c \u043a\u043b\u0430\u043d?", x + CONFIRM_W / 2, y + 18, 0xFF72D68A);
            g.drawCenteredString(Minecraft.getInstance().font, "\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanConstants.CREATE_COST + " \u043c\u043e\u043d\u0435\u0442", x + CONFIRM_W / 2, y + 48, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, "\u041c\u043e\u043d\u0435\u0442\u044b: " + TabletClientState.getCoins(), x + CONFIRM_W / 2, y + 66, 0xFF9FB2A4);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }
    }

    private class ClanJoinConfirmScreen extends Screen {

        private final ClanListPacket.ClanEntry clan;
        private boolean submitting;

        private ClanJoinConfirmScreen(ClanListPacket.ClanEntry clan) {
            super(Component.literal("\u0412\u0441\u0442\u0443\u043f\u043b\u0435\u043d\u0438\u0435 \u0432 \u043a\u043b\u0430\u043d"));
            this.clan = clan;
        }

        @Override
        protected void init() {
            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            int buttonY = y + 94;

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + 18,
                    buttonY,
                    Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"),
                    ConfirmButtonKind.CANCEL,
                    this::returnToTablet
            ));
            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal("\u0417\u0410\u042f\u0412\u041a\u0410"),
                    this::confirm
            ));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);
            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            GuiTextureRenderer.blitWithAlpha(
                    g, CONFIRM_PANEL, x, y, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);
            g.drawCenteredString(Minecraft.getInstance().font, "\u0412\u0441\u0442\u0443\u043f\u0438\u0442\u044c \u0432 \u043a\u043b\u0430\u043d?", x + CONFIRM_W / 2, y + 18, 0xFF72D68A);
            drawCenteredClanText(g, fitText("[" + clan.tag() + "] " + clan.name(), CONFIRM_W - 24),
                    x + CONFIRM_W / 2, y + 48, clan.color());
            g.drawCenteredString(Minecraft.getInstance().font, "\u0413\u043b\u0430\u0432\u0430 \u0440\u0430\u0441\u0441\u043c\u043e\u0442\u0440\u0438\u0442 \u0437\u0430\u044f\u0432\u043a\u0443", x + CONFIRM_W / 2, y + 66, 0xFF9FB2A4);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }

        private void confirm() {
            if (submitting) return;
            submitting = true;
            PacketHandler.sendToServer(new ClanJoinRequestPacket(clan.id()));
            returnToTablet();
        }
    }

    private class ClanSimpleConfirmScreen extends Screen {

        private final String title;
        private final String detail;
        private final Runnable confirmAction;
        private boolean submitting;

        private ClanSimpleConfirmScreen(String title, String detail, Runnable confirmAction) {
            super(Component.literal(title));
            this.title = title;
            this.detail = detail == null ? "" : detail;
            this.confirmAction = confirmAction;
        }

        @Override
        protected void init() {
            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            int buttonY = y + 94;

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + 18,
                    buttonY,
                    Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"),
                    ConfirmButtonKind.CANCEL,
                    this::returnToTablet
            ));
            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal("\u041e\u041a"),
                    this::confirm
            ));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);
            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            GuiTextureRenderer.blitWithAlpha(
                    g, CONFIRM_PANEL, x, y, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);
            g.drawCenteredString(Minecraft.getInstance().font, fitText(title, CONFIRM_W - 24),
                    x + CONFIRM_W / 2, y + 18, 0xFF72D68A);
            g.drawCenteredString(Minecraft.getInstance().font, fitText(detail, CONFIRM_W - 24),
                    x + CONFIRM_W / 2, y + 52, 0xFFE6F0E8);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }

        private void confirm() {
            if (submitting) return;
            submitting = true;
            confirmAction.run();
            returnToTablet();
        }
    }

    private class ClanChangeColorScreen extends Screen {

        private final ClanListPacket.ClanEntry clan;
        private int selectedColor;
        private ConfirmTextureButton changeButton;

        private ClanChangeColorScreen(ClanListPacket.ClanEntry clan) {
            super(Component.literal("\u0426\u0432\u0435\u0442 \u043a\u043b\u0430\u043d\u0430"));
            this.clan = clan;
            this.selectedColor = firstFreeClanColor(clan.id(), clan.color());
        }

        @Override
        protected void init() {
            int x = (this.width - UI_WIDTH) / 2;
            int y = (this.height - UI_HEIGHT) / 2;
            selectedColor = firstFreeClanColor(clan.id(), selectedColor);
            ClanColorButton initialColorButton = null;

            for (int i = 0; i < ClanConstants.ALLOWED_COLORS.length; i++) {
                int color = ClanConstants.ALLOWED_COLORS[i];
                ClanColorButton colorButton = new ClanColorButton(
                        x + 106 + i * 24,
                        y + 108,
                        color,
                        () -> selectedColor == color,
                        () -> selectedColor = color
                );
                colorButton.active = color != clan.color() && !isClanColorTaken(color, clan.id());
                this.addRenderableWidget(colorButton);
                if (initialColorButton == null && colorButton.active) initialColorButton = colorButton;
            }

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + 70,
                    y + 170,
                    Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"),
                    ConfirmButtonKind.CANCEL,
                    this::returnToTablet
            ));
            changeButton = new ConfirmTextureButton(
                    x + UI_WIDTH - CONFIRM_BUTTON_W - 70,
                    y + 170,
                    Component.literal("\u0418\u0417\u041c\u0415\u041d\u0418\u0422\u042c"),
                    () -> {
                        PacketHandler.sendToServer(new ClanChangeColorPacket(clan.id(), selectedColor));
                        returnToTablet();
                    }
            );
            this.addRenderableWidget(changeButton);
            if (initialColorButton != null) setInitialFocus(initialColorButton);
        }

        @Override
        public void tick() {
            super.tick();
            if (changeButton != null) {
                changeButton.active = clan.clanCoins() >= ClanManager.CHANGE_COLOR_COST
                        && selectedColor != clan.color()
                        && !isClanColorTaken(selectedColor, clan.id());
            }
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);
            int x = (this.width - UI_WIDTH) / 2;
            int y = (this.height - UI_HEIGHT) / 2;
            ResourceLocation panel = getPanelTexture();
            GuiTextureRenderer.blitWithAlpha(g, panel, x, y, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);

            drawHeader(g, x, y, "\u0426\u0432\u0435\u0442 \u043a\u043b\u0430\u043d\u0430");
            drawCenteredClanText(g, "[" + clan.tag() + "] " + clan.name(), x + UI_WIDTH / 2, y + 74, selectedColor);
            g.drawString(Minecraft.getInstance().font, "\u0421\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0435 \u0446\u0432\u0435\u0442\u0430", x + 106, y + 94, 0xFF9FB2A4, false);
            g.drawString(Minecraft.getInstance().font, "\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanManager.CHANGE_COLOR_COST + " \u041a\u041a", x + 106, y + 136, 0xFFE7C76A, false);
            g.drawString(Minecraft.getInstance().font, "\u041a\u041a \u043a\u043b\u0430\u043d\u0430: " + clan.clanCoins(), x + 106, y + 150, clan.clanCoins() >= ClanManager.CHANGE_COLOR_COST ? 0xFF72D68A : 0xFFD87575, false);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }
    }

    private class ClanCreateScreen extends Screen {

        private TacticalTextField nameBox;
        private TacticalTextField tagBox;
        private int selectedColor = ClanConstants.ALLOWED_COLORS[0];
        private ConfirmTextureButton createButton;
        private String errorMessage = "";

        private ClanCreateScreen() {
            super(Component.literal("\u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0435 \u043a\u043b\u0430\u043d\u0430"));
        }

        @Override
        protected void init() {
            int x = (this.width - UI_WIDTH) / 2;
            int y = (this.height - UI_HEIGHT) / 2;
            selectedColor = firstFreeClanColor("", ClanConstants.ALLOWED_COLORS[0]);

            nameBox = new TacticalTextField(Minecraft.getInstance().font, x + 82, y + 78, 190, 18,
                    Component.literal("\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435"))
                    .withPlaceholder(Component.literal("\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435"));
            nameBox.setMaxLength(ClanConstants.MAX_NAME_LENGTH);
            this.addRenderableWidget(nameBox);

            tagBox = new TacticalTextField(Minecraft.getInstance().font, x + 82, y + 112, 68, 18,
                    Component.literal("\u0422\u0435\u0433"))
                    .withPlaceholder(Component.literal("\u0422\u0435\u0433"));
            tagBox.setMaxLength(ClanConstants.MAX_TAG_LENGTH);
            this.addRenderableWidget(tagBox);

            for (int i = 0; i < ClanConstants.ALLOWED_COLORS.length; i++) {
                int color = ClanConstants.ALLOWED_COLORS[i];
                int index = i;
                ClanColorButton colorButton = new ClanColorButton(
                        x + 170 + i * 22,
                        y + 111,
                        color,
                        () -> selectedColor == color,
                        () -> selectedColor = ClanConstants.ALLOWED_COLORS[index]
                );
                colorButton.active = !isClanColorTaken(color, "");
                this.addRenderableWidget(colorButton);
            }

            this.addRenderableWidget(new ConfirmTextureButton(
                    x + 70,
                    y + 170,
                    Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"),
                    ConfirmButtonKind.CANCEL,
                    this::returnToTablet
            ));
            createButton = new ConfirmTextureButton(
                    x + UI_WIDTH - CONFIRM_BUTTON_W - 70,
                    y + 170,
                    Component.literal("\u0421\u041e\u0417\u0414\u0410\u0422\u042c"),
                    this::createClan
            );
            createButton.active = false;
            this.addRenderableWidget(createButton);
            setInitialFocus(nameBox);
        }

        @Override
        public void tick() {
            super.tick();
            if (createButton != null) {
                createButton.active = isValidInput();
            }
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);
            int x = (this.width - UI_WIDTH) / 2;
            int y = (this.height - UI_HEIGHT) / 2;
            ResourceLocation panel = getPanelTexture();
            GuiTextureRenderer.blitWithAlpha(g, panel, x, y, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);
            drawHeader(g, x, y, "\u041d\u043e\u0432\u044b\u0439 \u043a\u043b\u0430\u043d");
            g.drawString(Minecraft.getInstance().font, "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435", x + 82, y + 66, 0xFF9FB2A4, false);
            g.drawString(Minecraft.getInstance().font, "\u0422\u0435\u0433", x + 82, y + 100, 0xFF9FB2A4, false);
            g.drawString(Minecraft.getInstance().font, "\u0426\u0432\u0435\u0442", x + 170, y + 100, 0xFF9FB2A4, false);
            g.drawString(Minecraft.getInstance().font, "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435: 3-" + ClanConstants.MAX_NAME_LENGTH + " \u0441\u0438\u043c\u0432.", x + 82, y + 134, 0xFF777777, false);
            g.drawString(Minecraft.getInstance().font, "\u0422\u0435\u0433: 1-" + ClanConstants.MAX_TAG_LENGTH + " \u0441\u0438\u043c\u0432.", x + 82, y + 146, 0xFF777777, false);
            g.drawString(Minecraft.getInstance().font, "\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanConstants.CREATE_COST + " \u043c\u043e\u043d\u0435\u0442", x + 82, y + 158, 0xFFE7C76A, false);
            if (!errorMessage.isBlank()) {
                g.drawString(Minecraft.getInstance().font, errorMessage, x + 82, y + 184, 0xFFD87575, false);
            }
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            returnToTablet();
        }

        private void createClan() {
            String name = nameBox.getValue().trim();
            String tag = tagBox.getValue().trim();
            if (!isValidInput()) {
                errorMessage = "\u0417\u0430\u043f\u043e\u043b\u043d\u0438\u0442\u0435 \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0438 \u0442\u0435\u0433";
                return;
            }

            PacketHandler.sendToServer(new ClanCreatePacket(name, selectedColor, tag));
            returnToTablet();
        }

        private boolean isValidInput() {
            String name = nameBox == null ? "" : nameBox.getValue().trim();
            String tag = tagBox == null ? "" : tagBox.getValue().trim();
            return ClanCreateInputPolicy.isValid(
                    name,
                    tag,
                    TabletClientState.getClans().size(),
                    !isClanColorTaken(selectedColor, ""),
                    TabletClientState.getCoins()
            );
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }
    }

    private class ClanTextureButton extends Button {

        private final Runnable action;
        private final ButtonTextureSet textures;
        private final BooleanSupplier selected;
        private IntSupplier textColorSupplier;
        private boolean wasHovered;

        private ClanTextureButton(
                int x,
                int y,
                int width,
                int height,
                ButtonTextureSet textures,
                Component label,
                IntSupplier textColorSupplier,
                BooleanSupplier selected,
                Runnable action
        ) {
            this(x, y, width, height, textures, label, selected, action);
            this.textColorSupplier = textColorSupplier;
        }

        private ClanTextureButton(
                int x,
                int y,
                int width,
                int height,
                ButtonTextureSet textures,
                Component label,
                Runnable action
        ) {
            this(x, y, width, height, textures, label, () -> false, action);
        }

        private ClanTextureButton(
                int x,
                int y,
                int width,
                int height,
                ButtonTextureSet textures,
                Component label,
                BooleanSupplier selected,
                Runnable action
        ) {
            super(Button.builder(label, button -> {}).bounds(x, y, width, height));
            this.action = action;
            this.textures = textures;
            this.selected = selected;
            this.textColorSupplier = null;
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

            boolean isSelected = selected.getAsBoolean();
            ButtonTextureSpec texture = textures.select(this.active, isSelected, hover);
            GuiTextureRenderer.blitWithAlpha(g, texture, getX(), getY(), width, height);
            int labelColor = !this.active ? 0xFF77867B : textColorSupplier == null
                    ? (hover || isSelected ? 0xFFFFFFFF : 0xFF72D68A)
                    : textColorSupplier.getAsInt();
            int textX = getX() + width / 2;
            int textY = getY() + (height - 8) / 2;
            if (this.active && textColorSupplier != null && isBlackClanColor(labelColor)) {
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), textX - 1, textY, 0xFFFFFFFF);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), textX + 1, textY, 0xFFFFFFFF);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), textX, textY - 1, 0xFFFFFFFF);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), textX, textY + 1, 0xFFFFFFFF);
            }
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), textX, textY, labelColor);
        }
    }

    private class ClanColorButton extends Button {

        private final int color;
        private final BooleanSupplier selected;
        private final Runnable action;

        private ClanColorButton(int x, int y, int color, BooleanSupplier selected, Runnable action) {
            super(Button.builder(Component.empty(), button -> {}).bounds(x, y, 16, 16));
            this.color = color;
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onPress() {
            playSound(CLICK);
            action.run();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);
            ButtonTextureSpec texture = TabletButtonTextures.CLAN_COLOR.select(
                    this.active,
                    selected.getAsBoolean(),
                    hover
            );
            ClassButtonStyle.Tint tint = new ClassButtonStyle.Tint(
                    (color >> 16 & 0xFF) / 255.0F,
                    (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    1.0F
            );
            GuiTextureRenderer.blitWithAlpha(
                    g,
                    texture,
                    getX(),
                    getY(),
                    width,
                    height,
                    tint.red(),
                    tint.green(),
                    tint.blue(),
                    tint.alpha()
            );
            if (!this.active) {
                g.drawString(Minecraft.getInstance().font, "x", getX() + 5, getY() + 4, 0xFFD87575, false);
            }
        }
    }

    private class ConfirmTextureButton extends Button {

        private boolean wasHovered;
        private final Runnable action;
        private final ConfirmButtonKind kind;

        private ConfirmTextureButton(int x, int y, Component label, Runnable action) {
            this(x, y, label, ConfirmButtonKind.PRIMARY, action);
        }

        private ConfirmTextureButton(
                int x,
                int y,
                Component label,
                ConfirmButtonKind kind,
                Runnable action
        ) {
            super(Button.builder(label, button -> {}).bounds(x, y, CONFIRM_BUTTON_W, CONFIRM_BUTTON_H));
            this.action = action;
            this.kind = kind;
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

            ButtonTextureSet textureSet = kind == ConfirmButtonKind.CANCEL
                    ? TabletButtonTextures.CONFIRM_CANCEL
                    : TabletButtonTextures.CONFIRM_PRIMARY;
            ButtonTextureSpec texture = textureSet.select(this.active, false, hover);
            GuiTextureRenderer.blitWithAlpha(g, texture, getX(), getY(), width, height);

            g.drawCenteredString(
                    Minecraft.getInstance().font,
                    getMessage(),
                    getX() + width / 2,
                    getY() + (height - 8) / 2,
                    this.active ? hover ? 0xFFFFFFFF : 0xFF72D68A : 0xFF77867B
            );
        }
    }

    private enum ConfirmButtonKind {
        CANCEL,
        PRIMARY
    }

}
