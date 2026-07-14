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
import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

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

    private static final ResourceLocation BTN_RARE =
            new ResourceLocation("tacticaltablet", "textures/gui/button_rare.png");
    private static final ResourceLocation BTN_RARE_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_hover_rare.png");
    private static final ResourceLocation BTN_RARE_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/button_disabled_rare.png");

    private static final ResourceLocation BTN_LEGEND =
            new ResourceLocation("tacticaltablet", "textures/gui/button_legend.png");
    private static final ResourceLocation BTN_LEGEND_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_hover_legend.png");
    private static final ResourceLocation BTN_LEGEND_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/button_disabled_legend.png");

    private static final ResourceLocation BTN_MONSTER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_monster.png");
    private static final ResourceLocation BTN_MONSTER_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/button_hover_monster.png");
    private static final ResourceLocation BTN_MONSTER_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/button_disabled_monster.png");

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

    private static final ResourceLocation CLAN_ROW =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_row.png");
    private static final ResourceLocation CLAN_ROW_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_row_hover.png");
    private static final ResourceLocation CLAN_ROW_ACTIVE =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_row_active.png");
    private static final ResourceLocation CLAN_CREATE_BUTTON =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_create_button.png");
    private static final ResourceLocation CLAN_CREATE_BUTTON_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_create_button_hover.png");
    private static final ResourceLocation CLAN_CREATE_BUTTON_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_create_button_disabled.png");
    private static final ResourceLocation CLAN_BACK_BUTTON =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_back_button.png");
    private static final ResourceLocation CLAN_BACK_BUTTON_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_back_button_hover.png");
    private static final ResourceLocation CLAN_ACTION_BUTTON =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_action_button.png");
    private static final ResourceLocation CLAN_ACTION_BUTTON_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_action_button_hover.png");
    private static final ResourceLocation CLAN_ACTION_BUTTON_DISABLED =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_action_button_disabled.png");
    private static final ResourceLocation CLAN_DANGER_BUTTON =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_danger_button.png");
    private static final ResourceLocation CLAN_DANGER_BUTTON_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_danger_button_hover.png");
    private static final ResourceLocation CLAN_SMALL_BUTTON =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_small_button.png");
    private static final ResourceLocation CLAN_SMALL_BUTTON_HOVER =
            new ResourceLocation("tacticaltablet", "textures/gui/clan_small_button_hover.png");
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
    private static final int TAB_W = 66;
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
    private static final int CLAN_LIST_LEFT = 48;
    private static final int CLAN_LIST_TOP = 74;
    private static final int CLAN_ROW_W = 284;
    private static final int CLAN_ROW_H = 22;
    private static final int CLAN_ROW_GAP = 24;
    private static final int CLAN_CREATE_W = 160;
    private static final int CLAN_CREATE_H = 30;
    private static final int CLAN_BACK_W = 76;
    private static final int CLAN_BACK_H = 22;
    private static final int CLAN_ACTION_W = 120;
    private static final int CLAN_ACTION_H = 24;
    private static final int CLAN_SMALL_W = 58;
    private static final int CLAN_SMALL_H = 18;
    private static final int CLAN_INFO_LEFT = 48;
    private static final int CLAN_INFO_TOP = 74;
    private static final int CLAN_MEMBERS_LEFT = 48;
    private static final int CLAN_MEMBERS_TOP = 124;
    private static final int CLAN_MEMBERS_WIDTH = 160;
    private static final int CLAN_MEMBERS_HEIGHT = 52;
    private static final int CLAN_MEMBER_ROW_H = 13;
    private static final int CLAN_VISIBLE_MEMBERS = 3;
    private static final int CLAN_PENDING_LEFT = 218;
    private static final int CLAN_PENDING_TOP = 76;
    private static final int CLAN_PENDING_WIDTH = 148;
    private static final int CLAN_PENDING_HEIGHT = 74;
    private static final int CLAN_PENDING_ROW_H = 18;
    private static final int CLAN_VISIBLE_PENDING = 3;
    private static final int CLAN_BOTTOM_BUTTON_Y = 184;
    private static final String MARINE_CLASS = "marine";
    private static final float GUI_SOUND_VOLUME = 0.0625F;

    private static final String SERVER_INFO_TEXT = "";

    private static final TabletAction STORMTROOPER =
            TabletAction.classKit("\u0428\u0422\u0423\u0420\u041c\u041e\u0412\u0418\u041a", "stormtrooper", 0);
    private static final TabletAction SNIPER =
            TabletAction.classKit("\u0421\u041d\u0410\u0419\u041f\u0415\u0420", "sniper", 1);
    private static final TabletAction SCOUT =
            TabletAction.classKit("\u0420\u0410\u0417\u0412\u0415\u0414\u0427\u0418\u041a", "scout", 2);
    private static final TabletAction DRONE_OPERATOR =
            TabletAction.classKit("\u0414\u0420\u041e\u041d \u041e\u041f.", "droneoperator", 3);
    private static final TabletAction MORTARMAN =
            TabletAction.classKit("\u041c\u0418\u041d\u041e\u041c\u0401\u0422\u0427\u0418\u041a", "mortarman", 5);
    private static final TabletAction TELEPORT_RTP =
            TabletAction.rtp("\u0422\u0415\u041b\u0415\u041f\u041e\u0420\u0422 (RTP)", 7);
    private static final TabletAction MACHINE_GUNNER =
            TabletAction.classKit("\u041f\u0423\u041b\u0415\u041c\u0401\u0422\u0427\u0418\u041a", "machinegunner", 8);
    private static final TabletAction RPG_TROOPER =
            TabletAction.classKit("\u0420\u041f\u0413-\u0411\u041e\u0415\u0426", "rpgtrooper", 9);

    private static final TabletAction BOOMGUY_SHOP =
            TabletAction.shopClass("\u041f\u041e\u0414\u0420\u042b\u0412\u041d\u0418\u041a", "boomguy", 4, 500, 2);
    private static final TabletAction DREAM_SHOP =
            TabletAction.shopClass("\u0414\u0420\u0418\u041c", "dream", 6, 500, 2);
    private static final TabletAction TAGILLA_SHOP =
            TabletAction.shopClass("\u0422\u0410\u0413\u0418\u041b\u041b\u0410", "tagilla", 10, 750, 2);
    private static final TabletAction BLACK_OPS_SHOP =
            TabletAction.shopClass("\u0421\u041f\u0415\u0426\u041d\u0410\u0417", "blackops", 11, 1000, 2);
    private static final TabletAction COWBOY_SHOP =
            TabletAction.shopClass("\u041a\u041e\u0412\u0411\u041e\u0419", "cowboy", 12, 100, 1);
    private static final TabletAction SOLIDER_SHOP =
            TabletAction.shopClass("\u0421\u041e\u041b\u0414\u0410\u0422", "solider", 13, 50, 0);
    private static final TabletAction REBEL_SHOP =
            TabletAction.shopClass("\u041f\u041e\u0412\u0421\u0422\u0410\u041d\u0415\u0426", "rebel", 14, 1000, 2);
    private static final TabletAction SABOTEUR_SHOP =
            TabletAction.shopClass("\u0414\u0418\u0412\u0415\u0420\u0421\u0410\u041d\u0422", "saboteur", 15, 1000, 2);

    private static final TabletAction KILLER_EXCLUSIVE =
            TabletAction.exclusiveClass("\u041a\u0418\u041b\u041b\u0415\u0420", "killer", 16);
    private static final TabletAction MINIBOSS_EXCLUSIVE =
            TabletAction.exclusiveClass("\u041c\u0418\u041d\u0418-\u0411\u041e\u0421\u0421", "miniboss", 17);
    private static final TabletAction SHAHED_EXCLUSIVE =
            TabletAction.exclusiveClass("\u0428\u0410\u0425\u0415\u0414 \u041e\u041f.", "shahed", 18, 2);
    private static final TabletAction KROT_EXCLUSIVE =
            TabletAction.exclusiveClass("\u041a\u0420\u041e\u0422", "krot", 19, 1);
    private static final TabletAction MARINE_EXCLUSIVE =
            TabletAction.exclusiveClass("\u041c\u041e\u0420\u041f\u0415\u0425", MARINE_CLASS, 20, 2);
    private static final TabletAction MEDIC_EXCLUSIVE =
            TabletAction.exclusiveClass("\u041c\u0415\u0414\u0418\u041a", "medic", 21, 2);
    private static final TabletAction MICROWAVE_EXCLUSIVE =
            TabletAction.exclusiveClass("\u041c\u0418\u041a\u0420\u041e\u0412\u042d\u0419\u0412", "microwave", 22, 2);
    private static final TabletAction RAILGUNNER_EXCLUSIVE =
            TabletAction.exclusiveClass("\u0420\u042d\u0419\u041b-\u0413\u0410\u041d\u041d\u0415\u0420", "railgunner", 23, 2);
    private static final TabletAction SOON =
            TabletAction.locked("\u0421\u041a\u041e\u0420\u041e...");

    private static final TabletAction LOCKED =
            TabletAction.locked("???");

    private static final TabletPage[] PAGES = new TabletPage[]{
            new TabletPage("\u041a\u041b\u0410\u0421\u0421\u042b", PageType.ACTIONS, new TabletAction[]{
                    STORMTROOPER,
                    SNIPER,
                    SCOUT,
                    DRONE_OPERATOR,
                    MACHINE_GUNNER,
                    MORTARMAN,
                    RPG_TROOPER,
                    TELEPORT_RTP
            }),
            new TabletPage("\u041a\u041b\u0410\u041d\u042b", PageType.CLAN, new TabletAction[0]),
            new TabletPage("\u041f\u0420\u041e\u0424\u0418\u041b\u042c", PageType.PROFILE, new TabletAction[0]),
            new TabletPage("\u041c\u0410\u0413\u0410\u0417\u0418\u041d", PageType.ACTIONS, new TabletAction[]{
                    BOOMGUY_SHOP,
                    DREAM_SHOP,
                    TAGILLA_SHOP,
                    BLACK_OPS_SHOP,
                    COWBOY_SHOP,
                    SOLIDER_SHOP,
                    REBEL_SHOP,
                    SABOTEUR_SHOP
            }),
            new TabletPage("\u042d\u041a\u0421\u041a\u041b\u042e\u0417\u0418\u0412", PageType.ACTIONS, new TabletAction[]{
                    KILLER_EXCLUSIVE,
                    MINIBOSS_EXCLUSIVE,
                    SHAHED_EXCLUSIVE,
                    KROT_EXCLUSIVE,
                    MARINE_EXCLUSIVE,
                    MEDIC_EXCLUSIVE,
                    MICROWAVE_EXCLUSIVE,
                    RAILGUNNER_EXCLUSIVE
            })
    };

    private final Set<String> dismissedUpgradePrompts = new HashSet<>();
    private int currentPage;
    private int infoScroll;
    private int clanScrollOffset;
    private int pendingScrollOffset;
    private int memberScrollOffset;
    private int selectedClanIndex = -1;


    public TabletScreen() {
        super(Component.literal("\u0422\u0430\u043a\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u043f\u043b\u0430\u043d\u0448\u0435\u0442"));
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
        } else if (page.type() == PageType.CLAN) {
            addClanButtons(x0, y0);
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

    private void addClanButtons(int x0, int y0) {
        List<ClanListPacket.ClanEntry> clans = TabletClientState.getClans();
        boolean selectedIndexInvalid = selectedClanIndex >= clans.size();
        selectedClanIndex = clamp(selectedClanIndex, -1, clans.size() - 1);
        if (selectedIndexInvalid) {
            pendingScrollOffset = 0;
            memberScrollOffset = 0;
        }
        if (selectedClanIndex < 0) {
            memberScrollOffset = 0;
        }
        clanScrollOffset = clamp(clanScrollOffset, 0, Math.max(0, clans.size() - 4));

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
                        CLAN_ROW,
                        CLAN_ROW_HOVER,
                        CLAN_ROW_ACTIVE,
                        Component.literal("[" + clan.tag() + "] " + clan.name()),
                        () -> clan.color(),
                        () -> {
                            selectedClanIndex = index;
                            pendingScrollOffset = 0;
                            memberScrollOffset = 0;
                            TabletScreen.this.init();
                        }
                ));
            }

            ClanTextureButton createClanButton = new ClanTextureButton(
                    x0 + 110,
                    y0 + 184,
                    CLAN_CREATE_W,
                    CLAN_CREATE_H,
                    CLAN_CREATE_BUTTON,
                    CLAN_CREATE_BUTTON_HOVER,
                    CLAN_CREATE_BUTTON_DISABLED,
                    Component.literal("\u0421\u043e\u0437\u0434\u0430\u0442\u044c"),
                    () -> Minecraft.getInstance().setScreen(new ClanCreateConfirmScreen())
            );
            createClanButton.active = clans.size() < ClanConstants.MAX_CLANS && hasFreeClanColor("");
            this.addRenderableWidget(createClanButton);
            return;
        }

        ClanListPacket.ClanEntry clan = clans.get(selectedClanIndex);
        this.addRenderableWidget(new ClanTextureButton(
                x0 + 38,
                y0 + 50,
                CLAN_BACK_W,
                CLAN_BACK_H,
                CLAN_BACK_BUTTON,
                CLAN_BACK_BUTTON_HOVER,
                CLAN_BACK_BUTTON,
                Component.literal("\u041d\u0430\u0437\u0430\u0434"),
                () -> {
                    selectedClanIndex = -1;
                    pendingScrollOffset = 0;
                    memberScrollOffset = 0;
                    TabletScreen.this.init();
                }
        ));

        if (!clan.owner() && !clan.member() && !clan.pending()) {
            this.addRenderableWidget(newClanActionButton(x0 + 130, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0417\u0430\u044f\u0432\u043a\u0430",
                    () -> Minecraft.getInstance().setScreen(new ClanJoinConfirmScreen(clan))));
        } else if (clan.member() && !clan.owner()) {
            this.addRenderableWidget(newClanDangerButton(x0 + 130, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0412\u044b\u0439\u0442\u0438",
                    () -> Minecraft.getInstance().setScreen(new ClanSimpleConfirmScreen(
                            "\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 \u043a\u043b\u0430\u043d\u0430?",
                            clan.name(),
                            () -> PacketHandler.sendToServer(new ClanLeavePacket())
                    ))));
        } else if (clan.owner()) {
            this.addRenderableWidget(newClanActionButton(x0 + 130, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0426\u0432\u0435\u0442 10\u041a\u041a",
                    () -> Minecraft.getInstance().setScreen(new ClanChangeColorScreen(clan))));
            this.addRenderableWidget(newClanDangerButton(x0 + 254, y0 + CLAN_BOTTOM_BUTTON_Y, "\u0420\u0430\u0441\u043f\u0443\u0441\u0442\u0438\u0442\u044c",
                    () -> Minecraft.getInstance().setScreen(new ClanSimpleConfirmScreen(
                            "\u0420\u0430\u0441\u043f\u0443\u0441\u0442\u0438\u0442\u044c \u043a\u043b\u0430\u043d?",
                            clan.name(),
                            () -> PacketHandler.sendToServer(new ClanDisbandPacket(clan.id()))
                    ))));

            addOwnerRequestButtons(x0, y0, clan);
            addOwnerKickButtons(x0, y0, clan);
        }
    }

    private ClanTextureButton newClanActionButton(int x, int y, String label, Runnable action) {
        return new ClanTextureButton(x, y, CLAN_ACTION_W, CLAN_ACTION_H,
                CLAN_ACTION_BUTTON, CLAN_ACTION_BUTTON_HOVER, CLAN_ACTION_BUTTON_DISABLED,
                Component.literal(label), action);
    }

    private ClanTextureButton newClanDangerButton(int x, int y, String label, Runnable action) {
        return new ClanTextureButton(x, y, CLAN_ACTION_W, CLAN_ACTION_H,
                CLAN_DANGER_BUTTON, CLAN_DANGER_BUTTON_HOVER, CLAN_ACTION_BUTTON_DISABLED,
                Component.literal(label), action);
    }

    private ClanTextureButton newClanSmallButton(int x, int y, String label, Runnable action) {
        return new ClanTextureButton(x, y, CLAN_SMALL_W, CLAN_SMALL_H,
                CLAN_SMALL_BUTTON, CLAN_SMALL_BUTTON_HOVER, CLAN_SMALL_BUTTON,
                Component.literal(label), action);
    }

    private ClanTextureButton newClanPlainButton(int x, int y, int width, int height, String label, Runnable action) {
        return new ClanTextureButton(x, y, width, height, Component.literal(label), action);
    }

    private void addOwnerRequestButtons(int x0, int y0, ClanListPacket.ClanEntry clan) {
        if (!clan.owner()) return;
        if (clan.pendingEntries() == null || clan.pendingEntries().isEmpty()) return;

        pendingScrollOffset = clamp(pendingScrollOffset, 0, Math.max(0, clan.pendingEntries().size() - CLAN_VISIBLE_PENDING));
        int count = Math.min(CLAN_VISIBLE_PENDING, clan.pendingEntries().size() - pendingScrollOffset);
        int listTop = y0 + CLAN_PENDING_TOP + 18;
        int okX = x0 + CLAN_PENDING_LEFT + 60;
        int rejectX = x0 + CLAN_PENDING_LEFT + 104;

        for (int i = 0; i < count; i++) {
            ClanListPacket.PendingEntry pending = clan.pendingEntries().get(pendingScrollOffset + i);
            int buttonY = listTop + i * CLAN_PENDING_ROW_H - 3;
            this.addRenderableWidget(newClanPlainButton(okX, buttonY, 40, 14, "\u041e\u041a",
                    () -> PacketHandler.sendToServer(new ClanAcceptJoinPacket(clan.id(), pending.uuid()))));
            this.addRenderableWidget(newClanPlainButton(rejectX, buttonY, 44, 14, "\u041e\u0442\u043a\u043b.",
                    () -> PacketHandler.sendToServer(new ClanRejectJoinPacket(clan.id(), pending.uuid()))));
        }
    }

    private void addOwnerKickButtons(int x0, int y0, ClanListPacket.ClanEntry clan) {
        if (!clan.owner()) return;

        List<ClanListPacket.MemberEntry> members = clan.memberEntries() == null ? List.of() : clan.memberEntries();
        if (members.isEmpty()) return;

        memberScrollOffset = clamp(memberScrollOffset, 0, Math.max(0, members.size() - CLAN_VISIBLE_MEMBERS));
        int count = Math.min(CLAN_VISIBLE_MEMBERS, members.size() - memberScrollOffset);
        int buttonX = x0 + CLAN_MEMBERS_LEFT + 112;
        int listTop = y0 + CLAN_MEMBERS_TOP + 14;

        for (int i = 0; i < count; i++) {
            ClanListPacket.MemberEntry member = members.get(memberScrollOffset + i);
            if (member.uuid().equals(clan.ownerUuid())) continue;

            int buttonY = listTop + i * CLAN_MEMBER_ROW_H - 3;
            this.addRenderableWidget(newClanPlainButton(buttonX, buttonY, 42, 14, "\u041a\u0438\u043a",
                    () -> Minecraft.getInstance().setScreen(new ClanSimpleConfirmScreen(
                            "\u0418\u0441\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0438\u0433\u0440\u043e\u043a\u0430?",
                            member.name(),
                            () -> PacketHandler.sendToServer(new ClanKickMemberPacket(clan.id(), member.uuid()))
                    ))));
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

        if (page.type() == PageType.CLAN) {
            drawClans(g, x, y);
            return;
        }

        if (page.type() == PageType.SERVER_INFO) {
            drawServerInfo(g, x, y);
            return;
        }

        if (page.type() == PageType.PROFILE) {
            drawHeader(g, x, y, "\u041f\u0440\u043e\u0444\u0438\u043b\u044c");
            drawInfoLine(g, x, y, 0, "\u041c\u043e\u043d\u0435\u0442\u044b", TabletClientState.getCoins() + " \u043c", 0xFFFFD966);
            drawInfoLine(g, x, y, 1, "\u041f\u043e\u0431\u0435\u0434\u044b", String.valueOf(TabletClientState.getWins()));
            drawInfoLine(g, x, y, 2, "\u041c\u0430\u0442\u0447\u0438", String.valueOf(TabletClientState.getMatchesPlayed()));
            drawInfoLine(g, x, y, 3, "\u0423\u0431\u0438\u0439\u0441\u0442\u0432\u0430", String.valueOf(TabletClientState.getKills()));
            drawInfoLine(g, x, y, 4, "\u0421\u043c\u0435\u0440\u0442\u0438", String.valueOf(TabletClientState.getDeaths()));
            drawInfoLine(g, x, y, 5, "KDA", TabletClientState.getKdaText());
            drawInfoLine(g, x, y, 6, "\u041f\u0440\u043e\u0433\u0440\u0435\u0441\u0441", TabletClientState.getCareerProgressPercent() + "%");
        }
    }

    private ResourceLocation getPanelTexture() {
        int tier = TabletClientState.getTabletAppearanceTier();
        if (tier >= 2) return PANEL_LEGEND;
        if (tier == 1) return PANEL_EPIC;
        return PANEL;
    }

    private void drawClans(GuiGraphics g, int x, int y) {
        List<ClanListPacket.ClanEntry> clans = TabletClientState.getClans();
        if (selectedClanIndex >= 0 && selectedClanIndex < clans.size()) {
            drawClanDetails(g, x, y, clans.get(selectedClanIndex));
            return;
        }

        drawHeader(g, x, y, "\u041a\u043b\u0430\u043d\u044b");

        int listLeft = x + CLAN_LIST_LEFT;
        int listTop = y + CLAN_LIST_TOP;

        if (clans.isEmpty()) {
            drawWrappedText(g, "\u041a\u043b\u0430\u043d\u043e\u0432 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442.", listLeft, listTop + 12, CLAN_ROW_W, 0xFFE6E6E6);
            drawWrappedText(g, "\u0421\u043e\u0437\u0434\u0430\u0439 \u043f\u0435\u0440\u0432\u044b\u0439 \u043a\u043b\u0430\u043d \u0437\u0430 " + ClanConstants.CREATE_COST + " \u043c\u043e\u043d\u0435\u0442.", listLeft, listTop + 38, CLAN_ROW_W, 0xFFAAAAAA);
            return;
        }

        clanScrollOffset = clamp(clanScrollOffset, 0, Math.max(0, clans.size() - 4));
        int rowCount = Math.min(4, clans.size() - clanScrollOffset);
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
        drawCenteredClanText(g, "[" + clan.tag() + "] " + clan.name(), x + UI_WIDTH / 2, y + 56, clan.color());

        String status = clan.owner()
                ? "\u0421\u0442\u0430\u0442\u0443\u0441: \u0433\u043b\u0430\u0432\u0430"
                : clan.member() ? "\u0421\u0442\u0430\u0442\u0443\u0441: \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a"
                : clan.pending() ? "\u0421\u0442\u0430\u0442\u0443\u0441: \u0437\u0430\u044f\u0432\u043a\u0430"
                : "\u0421\u0442\u0430\u0442\u0443\u0441: \u043d\u0435 \u0432 \u043a\u043b\u0430\u043d\u0435";
        int statusColor = clan.owner() || clan.pending() ? 0xFFFFD966 : clan.member() ? 0xFF66FF66 : 0xFFAAAAAA;

        int left = x + CLAN_INFO_LEFT;
        int top = y + CLAN_INFO_TOP;

        g.drawString(Minecraft.getInstance().font, status, left, top, statusColor, false);
        g.drawString(Minecraft.getInstance().font, "\u0413\u043b\u0430\u0432\u0430: " + fitText(clan.ownerName(), 112), left, top + 14, 0xFFE6E6E6, false);
        g.drawString(Minecraft.getInstance().font, "\u0423\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432: " + clan.memberCount() + "/" + ClanConstants.MAX_MEMBERS, left, top + 28, 0xFFE6E6E6, false);
        g.drawString(Minecraft.getInstance().font, "\u041a\u041a: " + clan.clanCoins(), left, top + 42, 0xFF66FF66, false);

        drawClanMembers(g, clan, x + CLAN_MEMBERS_LEFT, y + CLAN_MEMBERS_TOP);
        drawClanPending(g, clan, x + CLAN_PENDING_LEFT, y + CLAN_PENDING_TOP);
    }

    private void drawClanMembers(GuiGraphics g, ClanListPacket.ClanEntry clan, int x, int y) {
        List<ClanListPacket.MemberEntry> members = clan.memberEntries() == null ? List.of() : clan.memberEntries();
        g.drawString(Minecraft.getInstance().font, "\u0423\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u0438", x, y, 0xFFE6E6E6, false);

        if (members.isEmpty()) {
            g.drawString(Minecraft.getInstance().font, "\u041d\u0435\u0442 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432", x, y + 14, 0xFF777777, false);
            return;
        }

        memberScrollOffset = clamp(memberScrollOffset, 0, Math.max(0, members.size() - CLAN_VISIBLE_MEMBERS));
        int listTop = y + 14;
        int count = Math.min(CLAN_VISIBLE_MEMBERS, members.size() - memberScrollOffset);

        g.enableScissor(x, listTop, x + CLAN_MEMBERS_WIDTH, y + CLAN_MEMBERS_HEIGHT);
        for (int i = 0; i < count; i++) {
            ClanListPacket.MemberEntry member = members.get(memberScrollOffset + i);
            boolean owner = member.uuid().equals(clan.ownerUuid());
            String suffix = owner ? " *" : "";
            int rowY = listTop + i * CLAN_MEMBER_ROW_H;
            int nameWidth = clan.owner() && !owner ? 104 : 148;
            int color = owner ? 0xFFFFD966 : 0xFFE6E6E6;
            g.drawString(Minecraft.getInstance().font, fitText(member.name() + suffix, nameWidth), x, rowY, color, false);
        }
        g.disableScissor();

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
        g.drawString(Minecraft.getInstance().font, "\u0417\u0430\u044f\u0432\u043a\u0438: " + pending, x, y, clan.owner() ? 0xFFFFD966 : 0xFFAAAAAA, false);
        if (!clan.owner()) return;

        if (pending <= 0) {
            g.drawString(Minecraft.getInstance().font, "\u041d\u0435\u0442 \u0437\u0430\u044f\u0432\u043e\u043a", x, y + 18, 0xFF777777, false);
            return;
        }

        pendingScrollOffset = clamp(pendingScrollOffset, 0, Math.max(0, pending - CLAN_VISIBLE_PENDING));
        int count = Math.min(CLAN_VISIBLE_PENDING, pending - pendingScrollOffset);
        int listTop = y + 18;

        g.enableScissor(x, listTop, x + CLAN_PENDING_WIDTH, y + CLAN_PENDING_HEIGHT);
        for (int i = 0; i < count; i++) {
            ClanListPacket.PendingEntry entry = clan.pendingEntries().get(pendingScrollOffset + i);
            g.drawString(Minecraft.getInstance().font, fitText(entry.name(), 54), x, listTop + i * CLAN_PENDING_ROW_H, 0xFFE6E6E6, false);
        }
        g.disableScissor();

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
        drawHeader(g, x, y, "\u0418\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f");

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
        if (PAGES[currentPage].type() == PageType.CLAN) {
            List<ClanListPacket.ClanEntry> clans = TabletClientState.getClans();
            if (selectedClanIndex < 0 && isMouseOverClanList(mouseX, mouseY)) {
                clanScrollOffset = clamp(clanScrollOffset - (int) Math.signum(delta), 0, Math.max(0, clans.size() - 4));
                TabletScreen.this.init();
                return true;
            }

            if (selectedClanIndex >= 0 && selectedClanIndex < clans.size() && isMouseOverMemberList(mouseX, mouseY)) {
                ClanListPacket.ClanEntry clan = clans.get(selectedClanIndex);
                int members = clan.memberEntries() == null ? 0 : clan.memberEntries().size();
                memberScrollOffset = clamp(
                        memberScrollOffset - (int) Math.signum(delta),
                        0,
                        Math.max(0, members - CLAN_VISIBLE_MEMBERS)
                );
                TabletScreen.this.init();
                return true;
            }

            if (selectedClanIndex >= 0 && selectedClanIndex < clans.size() && isMouseOverPendingList(mouseX, mouseY)) {
                ClanListPacket.ClanEntry clan = clans.get(selectedClanIndex);
                int pending = clan.pendingEntries() == null ? 0 : clan.pendingEntries().size();
                pendingScrollOffset = clamp(
                        pendingScrollOffset - (int) Math.signum(delta),
                        0,
                        Math.max(0, pending - CLAN_VISIBLE_PENDING)
                );
                TabletScreen.this.init();
                return true;
            }
        }

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
        PROFILE,
        CLAN
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
            boolean exclusive,
            int price,
            int fixedLevel
    ) {
        private static TabletAction classKit(String label, String classKey, int actionId) {
            return new TabletAction(label, classKey, actionId, false, false, false, false, 0, -1);
        }

        private static TabletAction shopClass(String label, String classKey, int actionId, int price, int fixedLevel) {
            return new TabletAction(label, classKey, actionId, false, false, true, false, price, fixedLevel);
        }

        private static TabletAction exclusiveClass(String label, String classKey, int actionId) {
            return exclusiveClass(label, classKey, actionId, 2);
        }

        private static TabletAction exclusiveClass(String label, String classKey, int actionId, int fixedLevel) {
            return new TabletAction(label, classKey, actionId, false, false, false, true, 0, fixedLevel);
        }

        private static TabletAction rtp(String label, int actionId) {
            return new TabletAction(label, "", actionId, true, false, false, false, 0, -1);
        }

        private static TabletAction locked(String label) {
            return new TabletAction(label, "", -1, false, true, false, false, 0, -1);
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
                    this::cancel
            ));
            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal(confirmAction == ConfirmAction.SHOP_PURCHASE ? "\u041a\u0423\u041f\u0418\u0422\u042c" : "\u041e\u041a"),
                    this::confirm
            ));
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

            g.drawCenteredString(Minecraft.getInstance().font, getConfirmTitle(), x + CONFIRM_W / 2, y + 17, 0xFF66FF66);
            g.drawCenteredString(Minecraft.getInstance().font, action.label(), x + CONFIRM_W / 2, y + 44, getShopTitleColor(action));
            g.drawCenteredString(Minecraft.getInstance().font, getPrice() + " \u043c\u043e\u043d\u0435\u0442", x + CONFIRM_W / 2, y + 58, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, "\u0423 \u0442\u0435\u0431\u044f: " + TabletClientState.getCoins() + " \u043c\u043e\u043d\u0435\u0442", x + CONFIRM_W / 2, y + 72, 0xFFAAAAAA);

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
            if (action.fixedLevel() >= 2) {
                return 0xFFFFD966;
            }

            if (action.fixedLevel() == 1) {
                return 0xFFB266FF;
            }

            return 0xFF66FF66;
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
            g.fill(0, 0, this.width, this.height, 0xAA000000);

            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, CONFIRM_PANEL);
            g.blit(CONFIRM_PANEL, x, y, 0, 0, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);
            g.drawCenteredString(Minecraft.getInstance().font, "\u0421\u043e\u0437\u0434\u0430\u0442\u044c \u043a\u043b\u0430\u043d?", x + CONFIRM_W / 2, y + 18, 0xFF66FF66);
            g.drawCenteredString(Minecraft.getInstance().font, "\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanConstants.CREATE_COST + " \u043c\u043e\u043d\u0435\u0442", x + CONFIRM_W / 2, y + 48, 0xFFFFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, "\u041c\u043e\u043d\u0435\u0442\u044b: " + TabletClientState.getCoins(), x + CONFIRM_W / 2, y + 66, 0xFFAAAAAA);
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

        private ClanJoinConfirmScreen(ClanListPacket.ClanEntry clan) {
            super(Component.literal("\u0412\u0441\u0442\u0443\u043f\u043b\u0435\u043d\u0438\u0435 \u0432 \u043a\u043b\u0430\u043d"));
            this.clan = clan;
        }

        @Override
        protected void init() {
            int x = (this.width - CONFIRM_W) / 2;
            int y = (this.height - CONFIRM_H) / 2;
            int buttonY = y + 94;

            this.addRenderableWidget(new ConfirmTextureButton(x + 18, buttonY, Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"), this::returnToTablet));
            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal("\u0417\u0410\u042f\u0412\u041a\u0410"),
                    () -> {
                        PacketHandler.sendToServer(new ClanJoinRequestPacket(clan.id()));
                        returnToTablet();
                    }
            ));
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
            g.drawCenteredString(Minecraft.getInstance().font, "\u0412\u0441\u0442\u0443\u043f\u0438\u0442\u044c \u0432 \u043a\u043b\u0430\u043d?", x + CONFIRM_W / 2, y + 18, 0xFF66FF66);
            drawCenteredClanText(g, "[" + clan.tag() + "] " + clan.name(), x + CONFIRM_W / 2, y + 48, clan.color());
            g.drawCenteredString(Minecraft.getInstance().font, "\u0413\u043b\u0430\u0432\u0430 \u0440\u0430\u0441\u0441\u043c\u043e\u0442\u0440\u0438\u0442 \u0437\u0430\u044f\u0432\u043a\u0443", x + CONFIRM_W / 2, y + 66, 0xFFAAAAAA);
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

    private class ClanSimpleConfirmScreen extends Screen {

        private final String title;
        private final String detail;
        private final Runnable confirmAction;

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

            this.addRenderableWidget(new ConfirmTextureButton(x + 18, buttonY, Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"), this::returnToTablet));
            this.addRenderableWidget(new ConfirmTextureButton(
                    x + CONFIRM_W - CONFIRM_BUTTON_W - 18,
                    buttonY,
                    Component.literal("\u041e\u041a"),
                    () -> {
                        confirmAction.run();
                        returnToTablet();
                    }
            ));
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
            g.drawCenteredString(Minecraft.getInstance().font, title, x + CONFIRM_W / 2, y + 18, 0xFF66FF66);
            g.drawCenteredString(Minecraft.getInstance().font, detail, x + CONFIRM_W / 2, y + 52, 0xFFFFFFFF);
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
            }

            this.addRenderableWidget(new ConfirmTextureButton(x + 70, y + 170, Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"), this::returnToTablet));
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
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, panel);
            g.blit(panel, x, y, 0, 0, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);

            drawHeader(g, x, y, "\u0426\u0432\u0435\u0442 \u043a\u043b\u0430\u043d\u0430");
            drawCenteredClanText(g, "[" + clan.tag() + "] " + clan.name(), x + UI_WIDTH / 2, y + 74, selectedColor);
            g.drawString(Minecraft.getInstance().font, "\u0421\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0435 \u0446\u0432\u0435\u0442\u0430", x + 106, y + 94, 0xFFAAAAAA, false);
            g.drawString(Minecraft.getInstance().font, "\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanManager.CHANGE_COLOR_COST + " \u041a\u041a", x + 106, y + 136, 0xFFFFD966, false);
            g.drawString(Minecraft.getInstance().font, "\u041a\u041a \u043a\u043b\u0430\u043d\u0430: " + clan.clanCoins(), x + 106, y + 150, clan.clanCoins() >= ClanManager.CHANGE_COLOR_COST ? 0xFF66FF66 : 0xFFFF6666, false);
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

        private EditBox nameBox;
        private EditBox tagBox;
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

            nameBox = new EditBox(Minecraft.getInstance().font, x + 82, y + 78, 190, 18, Component.literal("\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435"));
            nameBox.setMaxLength(ClanConstants.MAX_NAME_LENGTH);
            this.addRenderableWidget(nameBox);

            tagBox = new EditBox(Minecraft.getInstance().font, x + 82, y + 112, 68, 18, Component.literal("\u0422\u0435\u0433"));
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

            this.addRenderableWidget(new ConfirmTextureButton(x + 70, y + 170, Component.literal("\u041e\u0422\u041c\u0415\u041d\u0410"), this::returnToTablet));
            createButton = new ConfirmTextureButton(
                    x + UI_WIDTH - CONFIRM_BUTTON_W - 70,
                    y + 170,
                    Component.literal("\u0421\u041e\u0417\u0414\u0410\u0422\u042c"),
                    this::createClan
            );
            createButton.active = false;
            this.addRenderableWidget(createButton);
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
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, panel);
            g.blit(panel, x, y, 0, 0, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);
            drawHeader(g, x, y, "\u041d\u043e\u0432\u044b\u0439 \u043a\u043b\u0430\u043d");
            g.drawString(Minecraft.getInstance().font, "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435", x + 82, y + 66, 0xFFAAAAAA, false);
            g.drawString(Minecraft.getInstance().font, "\u0422\u0435\u0433", x + 82, y + 100, 0xFFAAAAAA, false);
            g.drawString(Minecraft.getInstance().font, "\u0426\u0432\u0435\u0442", x + 170, y + 100, 0xFFAAAAAA, false);
            g.drawString(Minecraft.getInstance().font, "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435: 3-" + ClanConstants.MAX_NAME_LENGTH + " \u0441\u0438\u043c\u0432.", x + 82, y + 134, 0xFF777777, false);
            g.drawString(Minecraft.getInstance().font, "\u0422\u0435\u0433: 1-" + ClanConstants.MAX_TAG_LENGTH + " \u0441\u0438\u043c\u0432.", x + 82, y + 146, 0xFF777777, false);
            g.drawString(Minecraft.getInstance().font, "\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: " + ClanConstants.CREATE_COST + " \u043c\u043e\u043d\u0435\u0442", x + 82, y + 158, 0xFFFFD966, false);
            if (!errorMessage.isBlank()) {
                g.drawString(Minecraft.getInstance().font, errorMessage, x + 82, y + 184, 0xFFFF6666, false);
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
            return name.length() >= 3
                    && name.length() <= ClanConstants.MAX_NAME_LENGTH
                    && !tag.isBlank()
                    && tag.length() <= ClanConstants.MAX_TAG_LENGTH
                    && TabletClientState.getClans().size() < ClanConstants.MAX_CLANS
                    && !isClanColorTaken(selectedColor, "");
        }

        private void returnToTablet() {
            Minecraft.getInstance().setScreen(TabletScreen.this);
        }
    }

    private class ClanTextureButton extends Button {

        private final Runnable action;
        private final ResourceLocation normalTexture;
        private final ResourceLocation hoverTexture;
        private final ResourceLocation disabledTexture;
        private IntSupplier textColorSupplier;
        private boolean wasHovered;

        private ClanTextureButton(int x, int y, int width, int height, Component label, Runnable action) {
            this(x, y, width, height, BTN, BTN_HOVER, BTN_DISABLED, label, action);
        }

        private ClanTextureButton(
                int x,
                int y,
                int width,
                int height,
                ResourceLocation normalTexture,
                ResourceLocation hoverTexture,
                ResourceLocation disabledTexture,
                Component label,
                IntSupplier textColorSupplier,
                Runnable action
        ) {
            this(x, y, width, height, normalTexture, hoverTexture, disabledTexture, label, action);
            this.textColorSupplier = textColorSupplier;
        }

        private ClanTextureButton(
                int x,
                int y,
                int width,
                int height,
                ResourceLocation normalTexture,
                ResourceLocation hoverTexture,
                ResourceLocation disabledTexture,
                Component label,
                Runnable action
        ) {
            super(Button.builder(label, button -> {}).bounds(x, y, width, height));
            this.action = action;
            this.normalTexture = normalTexture;
            this.hoverTexture = hoverTexture;
            this.disabledTexture = disabledTexture;
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

            int border = !this.active ? 0xFF333333 : hover ? 0xFFFFFFFF : 0xFF1E8F1E;
            int fill = !this.active ? 0xAA111111 : hover ? 0xCC174617 : 0xAA082808;
            g.fill(getX(), getY(), getX() + width, getY() + height, border);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
            int labelColor = !this.active ? 0xFF555555 : textColorSupplier == null
                    ? (hover ? 0xFFFFFFFF : 0xFF66FF66)
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
            int border = selected.getAsBoolean() ? 0xFFFFFFFF : hover ? 0xFFAAAAAA : 0xFF444444;
            g.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, border);
            if (isBlackClanColor(color)) {
                g.fill(getX(), getY(), getX() + width, getY() + height, 0xFFFFFFFF);
                g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, color);
            } else {
                g.fill(getX(), getY(), getX() + width, getY() + height, color);
            }
            if (!this.active) {
                g.fill(getX(), getY(), getX() + width, getY() + height, 0xAA000000);
                g.drawString(Minecraft.getInstance().font, "x", getX() + 5, getY() + 4, 0xFFFF6666, false);
            }
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
            this.active = !action.locked()
                    && (!action.exclusive() || isMarineAction(action) || TabletClientState.isClassPurchased(action.classKey()));
        }

        public void updateState() {
            if (action.locked()) {
                this.active = false;
                return;
            }

            if (isClanWarSetupOnly()) {
                this.active = false;
                return;
            }

            if (isMarineAction(action) && !isMarineUnlockedForCurrentClan()) {
                this.active = canBuyMarineForCurrentClan();
                return;
            }

            if (action.exclusive() && !isMarineAction(action) && !TabletClientState.isClassPurchased(action.classKey())) {
                this.active = false;
                return;
            }

            if (TabletClientState.isCompetitiveSet() && action.shop()) {
                this.active = false;
                return;
            }

            if (isClanWarSoloShopRestricted(action)) {
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
                if (targetTier > PlayerProgressManager.BASIC_TIER
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

            if (isClanWarSoloShopRestricted(action)) {
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

            boolean running = TabletClientState.isGameRunning();
            if (isBaseClassAction() && !TabletClientState.isBaseClassUnlocked(action.classKey())) {
                showBaseUnlockConfirmation(action);
                return;
            }

            if (isBaseClassAction() && !dismissedUpgradePrompts.contains(action.classKey())) {
                int targetTier = getAvailableUpgradeTier();
                if (targetTier > PlayerProgressManager.BASIC_TIER
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

            String label = fitLabel(getRenderedLabel());
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
            String prefix = getProgressPrefix();
            if (action.locked()) return "[???] " + action.label();

            if (isMarineAction(action)) {
                if (!isCurrentPlayerInClan()) return "[\u041a\u041b\u0410\u041d\u042b] " + action.label();
                if (isMarineUnlockedForCurrentClan()) return action.label();
                if (canBuyMarineForCurrentClan()) return "[" + ClanManager.MARINE_CLASS_COST + " \u041a\u041a] " + action.label();
                return "[\u041a\u041b\u0410\u041d] " + action.label();
            }

            if (action.exclusive() && !TabletClientState.isClassPurchased(action.classKey())) {
                return "[\u041d\u0415\u0414\u041e\u0421\u0422\u0423\u041f\u041d\u041e] " + action.label();
            }

            long cooldown = action.rtp() ? 0L : TabletClientState.getCooldown(action.actionId());
            if (cooldown > 0L) {
                return "[\u041a\u0414 " + formatTime(cooldown) + "] " + action.label();
            }

            if (!action.shop() || TabletClientState.isClassPurchased(action.classKey())) return prefix + action.label();
            return "[" + action.price() + " \u041c\u041e\u041d\u0415\u0422] " + action.label();
        }

        private String fitLabel(String label) {
            int availableWidth = width - 12;
            var font = Minecraft.getInstance().font;
            if (font.width(label) <= availableWidth) return label;

            String suffix = "...";
            return font.plainSubstrByWidth(label, Math.max(0, availableWidth - font.width(suffix))) + suffix;
        }

        private String getProgressPrefix() {
            if (action.exclusive() || action.rtp() || action.locked()) return "";
            if (action.shop()) return TabletClientState.isClassPurchased(action.classKey()) ? "\u041a\u0423\u041f\u041b\u0415\u041d\u041e " : "";

            int tier = TabletClientState.getClassTier(action.classKey());
            int xp = TabletClientState.getXP(action.classKey());
            if (!TabletClientState.isBaseClassUnlocked(action.classKey())) {
                return "[\u041e\u0422\u041a\u0420. " + PlayerProgressManager.BASE_UNLOCK_COST + "] ";
            }
            ClassTier current = ClassTier.clamp(tier);
            if (current.isMaximum()) return "[MAX " + xp + "/" + current.xpCap() + "] ";
            ClassTier next = current.next().orElseThrow();
            int coins = TabletClientState.getCoins();
            if (xp < next.requiredXp()) {
                return "[" + xp + "/" + next.requiredXp() + " XP] ";
            }
            if (coins < next.upgradeCost()) {
                return "[" + coins + "/" + next.upgradeCost() + " COINS] ";
            }
            return "[UPGRADE " + next.upgradeCost() + " COINS] ";
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

            ClassTier tier = ClassTier.clamp(level);
            if (!this.active) return switch (tier) {
                case MONSTER -> BTN_MONSTER_DISABLED;
                case LEGEND -> BTN_LEGEND_DISABLED;
                case EPIC -> BTN_EPIC_DISABLED;
                case RARE -> BTN_RARE_DISABLED;
                case BASIC -> BTN_DISABLED;
            };
            return switch (tier) {
                case MONSTER -> hover ? BTN_MONSTER_HOVER : BTN_MONSTER;
                case LEGEND -> hover ? BTN_LEGEND_HOVER : BTN_LEGEND;
                case EPIC -> hover ? BTN_EPIC_HOVER : BTN_EPIC;
                case RARE -> hover ? BTN_RARE_HOVER : BTN_RARE;
                case BASIC -> hover ? BTN_HOVER : BTN;
            };
        }

        private boolean isBaseClassAction() {
            return !action.shop() && !action.exclusive() && !action.rtp() && !action.locked();
        }

        private int getAvailableUpgradeTier() {
            int tier = TabletClientState.getClassTier(action.classKey());
            int xp = TabletClientState.getXP(action.classKey());

            ClassTier current = ClassTier.clamp(tier);
            return current.next()
                    .filter(next -> xp >= next.requiredXp())
                    .map(ClassTier::id)
                    .orElse(PlayerProgressManager.BASIC_TIER);
        }
    }

}
