package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.client.gui.TacticalScreenBackground;
import com.makar.tacticaltablet.integration.moderndamage.ModernDamageBalanceSchema;
import com.makar.tacticaltablet.integration.moderndamage.net.MdcBalanceRequestPacket;
import com.makar.tacticaltablet.integration.moderndamage.net.MdcBalanceStatePacket;
import com.makar.tacticaltablet.integration.moderndamage.net.MdcBalanceUpdatePacket;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalDialog;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModernDamageBalanceScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {
    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 390;
    private static final int ROWS_PER_PAGE = 6;
    private final Screen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private final Set<Integer> invalidFields = new HashSet<>();
    private Layout layout;
    private ModernDamageBalanceSchema.Category category = ModernDamageBalanceSchema.Category.BLEEDING;
    private int page;
    private boolean loaded;
    private boolean canEdit;
    private boolean pending;
    private long revision;
    private long seenUpdateCounter = -1L;
    private double[] original = new double[0];
    private double[] draft = new double[0];
    private Component resultMessage = Component.translatable("screen.tacticaltablet.mdc.loading");
    private TacticalButton applyButton;

    public ModernDamageBalanceScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.mdc.balance.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        rebuildContent();
        if (!loaded && minecraft != null && minecraft.getConnection() != null) {
            PacketHandler.sendToServer(new MdcBalanceRequestPacket());
        }
    }

    @Override
    public void tick() {
        super.tick();
        long counter = ModernDamageClientState.updateCounter();
        if (counter != seenUpdateCounter) {
            seenUpdateCounter = counter;
            acceptState(ModernDamageClientState.snapshot());
        }
        updateApplyButton();
    }

    private void acceptState(MdcBalanceStatePacket packet) {
        if (packet == null) return;
        canEdit = packet.canEdit();
        pending = false;
        boolean validSnapshot = packet.values().length == ModernDamageBalanceSchema.fields().size();
        boolean shouldLoad = validSnapshot && (!loaded
                || packet.result() == MdcBalanceStatePacket.Result.SUCCESS
                || packet.result() == MdcBalanceStatePacket.Result.STALE_REVISION
                || packet.revision() != revision);
        if (shouldLoad) {
            revision = packet.revision();
            original = packet.values();
            draft = Arrays.copyOf(original, original.length);
            invalidFields.clear();
            loaded = true;
        }
        resultMessage = packet.result() == MdcBalanceStatePacket.Result.NONE
                ? Component.literal(packet.details())
                : Component.translatable("screen.tacticaltablet.mdc.result."
                + packet.result().name().toLowerCase(Locale.ROOT));
        rebuildContent();
    }

    private void rebuildContent() {
        clearWidgets();
        if (!loaded) {
            addBottomButtons(false);
            return;
        }

        int x = layout.contentX();
        int y = layout.categoryY();
        int navWidth = Math.min(42, Math.max(24, layout.contentWidth() / 7));
        TacticalButton previousCategory = TacticalButton.compact(x, y, navWidth, Component.literal("<"),
                ignored -> changeCategory(-1));
        TacticalButton nextCategory = TacticalButton.compact(x + layout.contentWidth() - navWidth, y, navWidth,
                Component.literal(">"), ignored -> changeCategory(1));
        addRenderableWidget(previousCategory);
        addRenderableWidget(nextCategory);
        addRenderableWidget(TacticalButton.compact(x + navWidth + 4, y,
                Math.max(1, layout.contentWidth() - navWidth * 2 - 8), categoryLabel(),
                ignored -> changePage(1)).withAccentBar(true));

        List<ModernDamageBalanceSchema.Field> fields = categoryFields();
        int pages = Math.max(1, (fields.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS_PER_PAGE;
        int to = Math.min(fields.size(), from + ROWS_PER_PAGE);
        int rowY = layout.firstRowY();
        for (ModernDamageBalanceSchema.Field field : fields.subList(from, to)) {
            addField(field, rowY);
            rowY += layout.rowHeight();
        }
        addBottomButtons(canEdit);
    }

    private void addField(ModernDamageBalanceSchema.Field field, int y) {
        EditBox input = new EditBox(font, layout.inputX(), y, layout.inputWidth(), 20,
                fieldLabel(field));
        input.setMaxLength(20);
        input.setValue(format(field, draft[field.id()]));
        input.active = canEdit && !pending;
        input.setTooltip(Tooltip.create(Component.translatable(field.descriptionKey())));
        input.setResponder(text -> updateDraft(field, input, text));
        addRenderableWidget(input);
    }

    private void updateDraft(ModernDamageBalanceSchema.Field field, EditBox input, String text) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            boolean valid = Double.isFinite(value) && value >= field.minimum() && value <= field.maximum()
                    && (!field.integer() || value == Math.rint(value))
                    && (field.metric() != ModernDamageBalanceSchema.Metric.DURATION || value != 0);
            if (!valid) throw new NumberFormatException("range");
            draft[field.id()] = value;
            invalidFields.remove(field.id());
            input.setTextColor(com.makar.tacticaltablet.client.ExternalUiTheme.TEXT_PRIMARY);
        } catch (NumberFormatException exception) {
            invalidFields.add(field.id());
            input.setTextColor(com.makar.tacticaltablet.client.ExternalUiTheme.DANGER);
        }
        updateApplyButton();
    }

    private void addBottomButtons(boolean editable) {
        int gap = 6;
        int buttonWidth = Math.max(1, (layout.contentWidth() - gap * 2) / 3);
        TacticalButton defaults = TacticalButton.compact(layout.contentX(), layout.buttonsY(), buttonWidth,
                Component.translatable("screen.tacticaltablet.mdc.defaults"), ignored -> restoreDefaults());
        defaults.active = editable && !pending;
        addRenderableWidget(defaults);
        addRenderableWidget(TacticalButton.compact(layout.contentX() + buttonWidth + gap, layout.buttonsY(), buttonWidth,
                Component.translatable("screen.tacticaltablet.common.cancel"), ignored -> onClose()));
        applyButton = TacticalButton.compact(layout.contentX() + (buttonWidth + gap) * 2, layout.buttonsY(), buttonWidth,
                Component.translatable("screen.tacticaltablet.mdc.apply"), ignored -> confirmApply()).withAccentBar(true);
        addRenderableWidget(applyButton);
        updateApplyButton();
    }

    private void updateApplyButton() {
        if (applyButton == null) return;
        applyButton.active = loaded && canEdit && !pending && invalidFields.isEmpty()
                && !changes().isEmpty() && validateDraft();
    }

    private boolean validateDraft() {
        if (draft.length != ModernDamageBalanceSchema.fields().size()) return false;
        return ModernDamageBalanceSchema.validate(ModernDamageBalanceSchema.toMap(draft)).valid();
    }

    private void restoreDefaults() {
        draft = ModernDamageBalanceSchema.defaults();
        invalidFields.clear();
        rebuildContent();
    }

    private void confirmApply() {
        List<Component> changes = changes();
        if (changes.isEmpty() || !validateDraft()) return;
        Component body = Component.literal(String.join("\n", changes.stream().map(Component::getString).toList()));
        minecraft.setScreen(new TacticalDialog(this,
                Component.translatable("screen.tacticaltablet.mdc.confirm.title"), body,
                Component.translatable("screen.tacticaltablet.mdc.apply"),
                Component.translatable("screen.tacticaltablet.common.cancel"),
                this::submit, () -> { }, true, false));
    }

    private void submit() {
        Map<Integer, Double> values = new LinkedHashMap<>();
        for (ModernDamageBalanceSchema.Field field : ModernDamageBalanceSchema.fields()) {
            values.put(field.id(), draft[field.id()]);
        }
        pending = true;
        PacketHandler.sendToServer(new MdcBalanceUpdatePacket(revision, values));
    }

    private List<Component> changes() {
        List<Component> result = new ArrayList<>();
        if (original.length != draft.length) return result;
        for (ModernDamageBalanceSchema.Field field : ModernDamageBalanceSchema.fields()) {
            if (Double.compare(original[field.id()], draft[field.id()]) != 0) {
                result.add(fieldLabel(field).copy().append(": ")
                        .append(format(field, original[field.id()]) + " -> " + format(field, draft[field.id()])));
            }
        }
        return result;
    }

    private void changeCategory(int delta) {
        ModernDamageBalanceSchema.Category[] values = ModernDamageBalanceSchema.Category.values();
        category = values[Math.floorMod(category.ordinal() + delta, values.length)];
        page = 0;
        rebuildContent();
    }

    private void changePage(int delta) {
        int pages = Math.max(1, (categoryFields().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.floorMod(page + delta, pages);
        rebuildContent();
    }

    private Component categoryLabel() {
        int pages = Math.max(1, (categoryFields().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        return Component.translatable(category.translationKey()).append("  ")
                .append(Component.literal((page + 1) + "/" + pages));
    }

    private List<ModernDamageBalanceSchema.Field> categoryFields() {
        return ModernDamageBalanceSchema.fields().stream().filter(field -> field.category() == category).toList();
    }

    private static Component fieldLabel(ModernDamageBalanceSchema.Field field) {
        Component label = Component.translatable(field.labelKey());
        return field.metric() == ModernDamageBalanceSchema.Metric.VALUE ? label
                : label.copy().append(" — ").append(Component.translatable(field.metric().translationKey()));
    }

    private static String format(ModernDamageBalanceSchema.Field field, double value) {
        if (field.integer()) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.4f", value).replaceFirst("\\.?0+$", "");
    }

    private static String range(ModernDamageBalanceSchema.Field field) {
        return "[" + format(field, field.minimum()) + ".." + format(field, field.maximum()) + "]";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frameClock.nextFrame(Util.getMillis(), false),
                com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.panelY() + 9,
                    TacticalUi.currentPalette().textPrimary());
            graphics.drawCenteredString(font, resultMessage, width / 2, layout.panelY() + 23,
                    resultColor());
            if (loaded) renderFieldLabels(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderFieldLabels(GuiGraphics graphics) {
        List<ModernDamageBalanceSchema.Field> fields = categoryFields();
        int from = Math.min(fields.size(), page * ROWS_PER_PAGE);
        int to = Math.min(fields.size(), from + ROWS_PER_PAGE);
        int y = layout.firstRowY() + 5;
        for (ModernDamageBalanceSchema.Field field : fields.subList(from, to)) {
            graphics.drawString(font, font.plainSubstrByWidth(fieldLabel(field).getString(), layout.labelWidth()),
                    layout.contentX(), y, canEdit ? TacticalUi.currentPalette().textPrimary()
                            : TacticalUi.currentPalette().textDisabled(), false);
            graphics.drawString(font, range(field), layout.rangeX(), y,
                    TacticalUi.currentPalette().textSecondary(), false);
            y += layout.rowHeight();
        }
        if (!canEdit) {
            graphics.drawCenteredString(font, Component.translatable("screen.tacticaltablet.mdc.admin_required"),
                    width / 2, layout.buttonsY() - 15, TacticalUi.currentPalette().warning());
        }
    }

    private int resultColor() {
        MdcBalanceStatePacket packet = ModernDamageClientState.snapshot();
        if (packet == null || packet.result() == MdcBalanceStatePacket.Result.NONE) {
            return com.makar.tacticaltablet.client.ExternalUiTheme.TEXT_SECONDARY;
        }
        return packet.result() == MdcBalanceStatePacket.Result.SUCCESS
                ? com.makar.tacticaltablet.client.ExternalUiTheme.SUCCESS
                : com.makar.tacticaltablet.client.ExternalUiTheme.DANGER;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, int categoryY, int firstRowY,
                          int rowHeight, int labelWidth, int inputX, int inputWidth,
                          int rangeX, int buttonsY) {
        static Layout calculate(int width, int height) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, width - 12));
            int panelHeight = Math.min(PANEL_HEIGHT, Math.max(1, height - 12));
            int panelX = (width - panelWidth) / 2;
            int panelY = (height - panelHeight) / 2;
            int contentX = panelX + 12;
            int contentWidth = Math.max(1, panelWidth - 24);
            int inputWidth = Math.max(58, Math.min(120, contentWidth / 4));
            int rangeWidth = Math.min(95, Math.max(55, contentWidth / 6));
            int inputX = contentX + contentWidth - inputWidth - rangeWidth - 5;
            int labelWidth = Math.max(30, inputX - contentX - 5);
            int rangeX = inputX + inputWidth + 5;
            return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                    panelY + 39, panelY + 72, 31, labelWidth, inputX, inputWidth,
                    rangeX, panelY + panelHeight - 30);
        }
    }
}
