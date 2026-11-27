package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.tablet.ClassDefinition;
import com.makar.tacticaltablet.tablet.client.ClassProgressionPresentation.Input;
import com.makar.tacticaltablet.tablet.client.ClassProgressionPresentation.Model;
import com.makar.tacticaltablet.tablet.client.ClassProgressionPresentation.Reason;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Base-class widgets only: rendering consumes cached presentation, networking belongs to the screen. */
final class BaseClassPanel {
    private static final String PREFIX = "screen.tacticaltablet.progression.";
    private static final String SELECT_SCROLL = "classes.select", PROGRESS_SCROLL = "classes.progress";
    private static final ClassTier[] TIERS = ClassTier.values();
    private final TabletPageState state;
    private final List<ClassDefinition> definitions;
    private final Function<ClassDefinition, Input> snapshot;
    private final Consumer<ClassDefinition> useClass;
    private final BiConsumer<ClassDefinition, Integer> upgrade;
    private final Consumer<String> rebuild;
    private final Map<String, View> views = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> controls = new ArrayList<>();
    private final AnimatedFloat progress = new AnimatedFloat(0.0F, 0.25F);
    private final AnimatedFloat appearance = new AnimatedFloat(1.0F, 0.16F);
    private final UpgradePending pending = TabletClientState.upgradePending();
    private String animatedClass = "";
    private String notice = "";
    private long noticeUntil;
    private int x, y;
    private Language language;
    private long resourceGeneration = -1;
    private Button upgradeButton;
    private View lastUpgradeView;
    private Component upgradeLabel = Component.empty();

    BaseClassPanel(TabletPageState state, List<ClassDefinition> definitions,
                   Function<ClassDefinition, Input> snapshot, Consumer<ClassDefinition> useClass,
                   BiConsumer<ClassDefinition, Integer> upgrade, Consumer<String> rebuild) {
        this.state = state;
        this.definitions = List.copyOf(definitions);
        this.snapshot = snapshot;
        this.useClass = useClass;
        this.upgrade = upgrade;
        this.rebuild = rebuild;
    }

    UpgradePending.State pendingState(String key) { return pending.state(key); }
    boolean beginUpgrade(String key, int tier) { return pending.begin(key, tier, Util.getMillis()); }
    private boolean progression() { return state.classMode() == TabletPageState.ClassMode.PROGRESSION; }
    private boolean details() { return progression() && state.classDetails(); }
    private String scrollKey() { return progression() ? PROGRESS_SCROLL : SELECT_SCROLL; }

    void initialize(int x, int y, Consumer<Button> register) {
        this.x = x;
        this.y = y;
        rows.clear();
        controls.clear();
        upgradeButton = null;
        lastUpgradeView = null;
        refresh();
        controls.add(TacticalButton.compact(x, y, 132, text("mode.select"), ignored -> switchMode(false))
                .selectedWhen(() -> !progression()).withFocusKey("classes.mode.select"));
        controls.add(TacticalButton.compact(x + 138, y, 136, text("mode.progression"), ignored -> switchMode(true))
                .selectedWhen(this::progression).withFocusKey("classes.mode.progression"));
        if (details() && views.containsKey(state.selectedClass())) {
            controls.add(TacticalButton.compact(x + 220, y + 24, 54, text("back"), ignored -> back())
                    .withFocusKey("classes.back"));
            upgradeButton = new TacticalButton(x, y + 126, 274, 24, Component.empty(), ignored -> {
                View view = views.get(state.selectedClass());
                if (view != null) upgrade.accept(view.definition, view.model.nextTier().id());
            }) {
                @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    line(graphics, upgradeLabel, getX() + 7, getY() + 8, width - 14,
                            active ? TacticalTheme.TEXT_PRIMARY : TacticalTheme.TEXT_DISABLED);
                }
            }.withFocusKey("classes.upgrade");
            controls.add(upgradeButton);
        } else {
            for (ClassDefinition definition : definitions) {
                View view = views.get(definition.classKey());
                if (progression() && !view.model.progressive()) continue;
                rows.add(new Row(rows.size(), view));
            }
            layoutRows();
        }
        controls.forEach(register);
        rows.forEach(register);
        updateUpgradeButton();
        appearance.snapTo(0.0F);
        appearance.setTarget(1.0F);
    }

    private void switchMode(boolean progression) {
        state.classMode(progression ? TabletPageState.ClassMode.PROGRESSION : TabletPageState.ClassMode.SELECT);
        rebuild.accept(progression ? "classes.mode.progression" : "classes.mode.select");
    }

    boolean back() {
        if (!details()) return false;
        state.closeClass();
        rebuild.accept("classes.row." + state.selectedClass());
        return true;
    }

    /** Called at screen tick/init, not per card or frame. Widgets are not recreated on data changes. */
    void refresh() {
        long now = Util.getMillis();
        pending.tick(now);
        Language newLanguage = Language.getInstance();
        long generation = ClientResourcePresenceCache.generation();
        boolean assetsChanged = generation != resourceGeneration || language != newLanguage;
        language = newLanguage;
        resourceGeneration = generation;
        if (assetsChanged && controls.size() >= 2) {
            controls.get(0).setMessage(text("mode.select"));
            controls.get(1).setMessage(text("mode.progression"));
            if (details() && controls.size() >= 3) controls.get(2).setMessage(text("back"));
        }
        for (ClassDefinition definition : definitions) {
            Input input = snapshot.apply(definition);
            if (input.permanentDataKnown() && pending.observe(definition.classKey(), input.savedTier(), now)) {
                notice = text("confirmed", input.displayName(), tier(ClassTier.clamp(input.savedTier()))).getString();
                noticeUntil = now + 4_000L;
            }
            // Read again after observing pending: confirmation must immediately unblock the next step.
            if (input.pending() != pending.state(definition.classKey())) input = snapshot.apply(definition);
            View previous = views.get(definition.classKey());
            if (previous == null || !input.equals(previous.input) || assetsChanged) {
                View next = new View(definition, input);
                views.put(definition.classKey(), next);
                for (Row row : rows) if (row.view.definition.classKey().equals(definition.classKey())) row.view = next;
            }
        }
        updateUpgradeButton();
    }

    private void updateUpgradeButton() {
        if (upgradeButton == null) return;
        View view = views.get(state.selectedClass());
        if (view == null || view == lastUpgradeView) return;
        lastUpgradeView = view;
        Model model = view.model;
        upgradeLabel = !model.permanentDataKnown() ? text("data_unavailable") : model.maximum() ? text("maximum") : model.nextTier() == null ? text("reason.not_progressive")
                : text("upgrade", tier(model.nextTier()), model.upgradePrice());
        // Blocked reasons remain narrated by the list row and visible in the details.
        upgradeButton.active = model.upgradeAvailability().available();
        upgradeButton.setMessage(view.narration);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (ScissorScope ignored = ScissorScope.open(graphics, x, y, BaseClassLayout.WIDTH, BaseClassLayout.HEIGHT)) {
            if (details()) renderDetails(graphics);
            controls.forEach(button -> button.render(graphics, mouseX, mouseY, partialTick));
            for (Row row : rows) if (row.visible) row.render(graphics, mouseX, mouseY, partialTick);
            if (!details()) renderScrollbar(graphics);
            if (TacticalUi.currentFrame().reducedMotion()) appearance.snapTo(1.0F);
            else appearance.update(TacticalUi.currentFrame().deltaSeconds());
            int opacity = Math.round((1.0F - appearance.value()) * 180);
            if (opacity > 0) graphics.fill(x, y + BaseClassLayout.TOP, x + 274, y + 150,
                    (opacity << 24) | (TacticalTheme.SURFACE & 0xFFFFFF));
        }
    }

    Component notice() { return Util.getMillis() < noticeUntil ? Component.literal(notice) : Component.empty(); }

    private void renderDetails(GuiGraphics graphics) {
        View view = views.get(state.selectedClass());
        if (view == null) return;
        Model model = view.model;
        icon(graphics, view, x + 2, y + 25, 24);
        line(graphics, view.name, x + 34, y + 29, 180, TacticalTheme.TEXT_PRIMARY);
        for (int index = 0; index < TIERS.length; index++) {
            ClassTier tier = TIERS[index];
            int left = x + index * 54;
            boolean current = model.permanentDataKnown() && tier == model.currentTier();
            boolean next = model.permanentDataKnown() && tier == model.nextTier();
            graphics.fill(left, y + 50, left + 52, y + 61,
                    current ? TacticalTheme.ACCENT_DARK : TacticalTheme.SURFACE_RAISED);
            if (next) graphics.fill(left, y + 60, left + 52, y + 61, TacticalTheme.WARNING);
            line(graphics, view.chain.get(index), left + 2, y + 51, 48,
                    current ? TacticalTheme.ACCENT : next ? TacticalTheme.WARNING : TacticalTheme.TEXT_SECONDARY);
        }
        line(graphics, view.stage, x + 2, y + 64, 270, TacticalTheme.TEXT_PRIMARY);
        float target = model.permanentDataKnown() ? model.progress() : 0.0F;
        if (!animatedClass.equals(model.classKey())) {
            animatedClass = model.classKey();
            progress.snapTo(target);
        }
        progress.setTarget(target);
        if (TacticalUi.currentFrame().reducedMotion()) progress.snapTo(target);
        else progress.update(TacticalUi.currentFrame().deltaSeconds());
        graphics.fill(x + 2, y + 76, x + 272, y + 80, TacticalTheme.BORDER);
        graphics.fill(x + 2, y + 76, x + 2 + Math.round(270 * progress.value()), y + 80, TacticalTheme.ACCENT);
        line(graphics, view.total, x + 2, y + 83, 270, TacticalTheme.TEXT_SECONDARY);
        line(graphics, view.balance, x + 2, y + 94, 270, TacticalTheme.WARNING);
        int lineY = y + 105;
        for (FormattedCharSequence line : view.reasonLines) {
            graphics.drawString(font(), line, x + 2, lineY, statusColor(model), false);
            lineY += 10;
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maximum = BaseClassLayout.maximumOffset(rows.size(), progression());
        if (maximum == 0) return;
        graphics.fill(x + 271, y + 24, x + 274, y + 148, TacticalTheme.BORDER);
        int totalRows = (rows.size() + BaseClassLayout.columns(progression()) - 1) / BaseClassLayout.columns(progression());
        int thumb = Math.max(18, 124 * BaseClassLayout.VISIBLE_ROWS / totalRows);
        int top = y + 24 + (124 - thumb) * state.offset(scrollKey()) / maximum;
        graphics.fill(x + 271, top, x + 274, top + thumb, TacticalTheme.ACCENT_MUTED);
    }

    private void layoutRows() {
        int offset = state.clampOffset(scrollKey(), BaseClassLayout.maximumOffset(rows.size(), progression()));
        int columns = BaseClassLayout.columns(progression());
        for (Row row : rows) {
            int rowIndex = row.index / columns;
            row.setX(x + row.index % columns * (BaseClassLayout.CARD_WIDTH + BaseClassLayout.GAP));
            row.setY(y + BaseClassLayout.TOP + (rowIndex - offset) * BaseClassLayout.ROW_STEP);
            row.visible = rowIndex >= offset && rowIndex < offset + BaseClassLayout.VISIBLE_ROWS;
        }
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (details() || mouseX < x || mouseX >= x + 274 || mouseY < y + 24 || mouseY >= y + 150 || delta == 0) return false;
        state.scroll(scrollKey(), -(int) Math.signum(delta), BaseClassLayout.maximumOffset(rows.size(), progression()));
        layoutRows();
        return true;
    }

    Button moveFocus(int key, GuiEventListener focused) {
        if (!(focused instanceof BaseClassPanel.Row row)) return null;
        int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (dx == 0 && dy == 0) return null;
        int next = BaseClassLayout.move(row.index, rows.size(), progression(), dx, dy);
        reveal(next);
        return rows.get(next);
    }

    Button tabFocus(GuiEventListener focused, boolean backwards) {
        if (!(focused instanceof BaseClassPanel.Row row)) return null;
        int next = BaseClassLayout.tabIndex(row.index, rows.size(), backwards);
        if (next < 0) return null;
        reveal(next);
        return rows.get(next);
    }

    void revealFocus(String focusKey) {
        for (Row row : rows) if (row.focusKey().equals(focusKey)) { reveal(row.index); return; }
    }
    private void reveal(int index) {
        int offset = BaseClassLayout.reveal(index, state.offset(scrollKey()), rows.size(), progression());
        state.scroll(scrollKey(), offset - state.offset(scrollKey()), BaseClassLayout.maximumOffset(rows.size(), progression()));
        layoutRows();
    }

    private final class Row extends TacticalButton {
        private final int index;
        private View view;
        Row(int index, View view) {
            super(0, 0, progression() ? 270 : BaseClassLayout.CARD_WIDTH, BaseClassLayout.ROW_HEIGHT,
                    view.narration, ignored -> { });
            this.index = index;
            this.view = view;
            withFocusKey("classes.row." + view.definition.classKey());
            selectedWhen(this::isFocused);
        }
        @Override public void onPress() {
            if (progression()) {
                state.openClass(view.definition.classKey());
                rebuild.accept("classes.back");
            } else useClass.accept(view.definition);
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (getMessage() != view.narration) setMessage(view.narration);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
        @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int iconSize = progression() ? 24 : 16;
            icon(graphics, view, getX() + 4, getY() + 7, iconSize);
            int textX = getX() + (progression() ? 34 : 24);
            int textWidth = width - (textX - getX()) - 4;
            line(graphics, view.name, textX, getY() + 3,
                    progression() ? textWidth - 86 : textWidth, TacticalTheme.TEXT_PRIMARY);
            if (progression()) line(graphics, view.shortState, getX() + width - 86, getY() + 3, 82, statusColor(view.model));
            line(graphics, progression() ? view.current : view.savedTier, textX, getY() + 14, textWidth, TacticalTheme.TEXT_SECONDARY);
            line(graphics, progression() ? view.next : view.use, textX, getY() + 26, textWidth,
                    progression() ? TacticalTheme.WARNING : view.model.useAvailability().available() ? TacticalTheme.SUCCESS : TacticalTheme.TEXT_SECONDARY);
        }
    }

    private final class View {
        final ClassDefinition definition;
        final Input input;
        final Model model;
        final ResourceLocation icon;
        final boolean iconExists;
        final Component name, savedTier, current, next, use, shortState, stage, total, balance, narration;
        final List<Component> chain;
        final List<FormattedCharSequence> reasonLines;
        View(ClassDefinition definition, Input input) {
            this.definition = definition;
            this.input = input;
            model = ClassProgressionPresentation.describe(input);
            name = Component.literal(model.displayName());
            chain = java.util.Arrays.stream(TIERS).map(tier -> (Component) Component.literal(tier.displayName())).toList();
            icon = ClassIconResolver.resolveTablet(definition, ClientResourcePresenceCache::exists);
            iconExists = ClientResourcePresenceCache.exists(icon);
            savedTier = model.permanentDataKnown() ? tier(model.currentTier()) : text("data_unavailable");
            current = model.permanentDataKnown() ? text("current", tier(model.currentTier()), model.totalXp()) : text("data_unavailable");
            next = !model.permanentDataKnown() ? text("data_unavailable") : model.maximum() ? text("maximum") : model.nextTier() == null ? text("reason.not_progressive")
                    : text("next", tier(model.nextTier()), model.upgradePrice());
            use = !model.unlocked() && !input.chaos() ? text("unlock", com.makar.tacticaltablet.progression.PlayerProgressManager.BASE_UNLOCK_COST)
                    : model.useAvailability().available() ? text("select") : reason(model.useAvailability().reason());
            shortState = text("short." + model.upgradeAvailability().reason().name().toLowerCase(java.util.Locale.ROOT));
            stage = !model.permanentDataKnown() ? text("data_unavailable") : model.maximum() ? text("maximum")
                    : text("stage", model.stageXp(), model.stageSpan(), Math.round(model.progress() * 100));
            total = !model.permanentDataKnown() ? text("data_unavailable") : model.stageFilled()
                    ? text("cap", model.totalXp()) : text("total", model.totalXp(), model.remainingXp());
            balance = !model.permanentDataKnown() || model.maximum() ? text("balance_only", model.coins())
                    : text("balance", model.upgradePrice(), model.coins(), Math.max(0, model.coins() - model.upgradePrice()));
            Component reason = model.upgradeAvailability().reason() == Reason.INSUFFICIENT_XP
                    ? text("need_xp", model.remainingXp()) : model.upgradeAvailability().reason() == Reason.INSUFFICIENT_COINS
                    ? text("need_coins", model.upgradePrice() - model.coins()) : reason(model.upgradeAvailability().reason());
            reasonLines = font().split(reason, 270).stream().limit(2).toList();
            narration = text("narration", model.displayName(), current, next, use, reason);
        }
    }

    static Component text(String suffix, Object... args) { return Component.translatable(PREFIX + suffix, args); }
    static Component tier(ClassTier tier) { return text("tier." + tier.systemName()); }
    private static Component reason(Reason reason) { return text("reason." + reason.name().toLowerCase(java.util.Locale.ROOT)); }
    private static Font font() { return Minecraft.getInstance().font; }
    private static int statusColor(Model model) {
        return model.upgradeAvailability().available() ? TacticalTheme.SUCCESS : TacticalTheme.TEXT_SECONDARY;
    }
    private static void line(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        // Scopes guarantee long translated strings cannot escape their allocated line.
        try (ScissorScope ignored = ScissorScope.open(graphics, x, y, Math.max(0, width), 10)) {
            graphics.drawString(font(), text, x, y, color, false);
        }
    }
    private static void icon(GuiGraphics graphics, View view, int x, int y, int size) {
        // Light neutral plate keeps dark supplied symbols legible without recolouring originals.
        graphics.fill(x, y, x + size, y + size, TacticalTheme.TEXT_PRIMARY);
        if (view.iconExists) GuiTextureRenderer.blitRegionWithAlpha(graphics, view.icon, x, y,
                size, size, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1);
    }
}
