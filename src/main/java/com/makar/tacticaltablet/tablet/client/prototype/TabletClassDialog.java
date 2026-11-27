package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.TabletProgressOperation.Kind;
import com.makar.tacticaltablet.tablet.ClassDefinition;
import com.makar.tacticaltablet.tablet.classes.TabletClassSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** A confirmation pins the stable class identity and exact target. No row index survives into an action. */
final class TabletClassDialog {
    private record Confirmation(Kind kind, String key, String name, int currentTier, int targetTier, int price) { }
    private Confirmation confirmation;
    private final TabletPreparationPage.Rect panel, confirm, cancel;
    private int hover = -1, pressed = -1;
    private boolean submitted;
    private boolean dirty = true;
    private long revision = -1;
    TabletClassDialog(int height) {
        panel = new TabletPreparationPage.Rect(72, (height - 186) / 2, 496, 186);
        confirm = new TabletPreparationPage.Rect(88, panel.y() + 138, 284, 32);
        cancel = new TabletPreparationPage.Rect(380, panel.y() + 138, 172, 32);
    }
    void upgrade(ClassDefinition definition, TabletClassSnapshot.Row row) {
        confirmation = new Confirmation(Kind.UPGRADE_TIER, row.classKey(), definition.name().getString(), row.tier(), row.nextTier(), row.upgradePrice());
        submitted = false; dirty = true;
    }
    void purchase(ClassDefinition definition, TabletClassSnapshot.Row row) {
        confirmation = new Confirmation(row.purchaseKind(), row.classKey(), definition.name().getString(), row.tier(), -1, row.price());
        submitted = false; dirty = true;
    }
    boolean active() { return confirmation != null; }
    void dismiss() { confirmation = null; cancelPress(); hover = -1; dirty = true; }
    void cancelPress() { if (pressed != -1) { pressed = -1; dirty = true; } }
    private TabletClassSnapshot.Row row() {
        var state = TabletClassesClient.snapshot();
        return state == null || confirmation == null ? null : state.rows().stream()
                .filter(r -> r.classKey().equals(confirmation.key())).findFirst().orElse(null);
    }
    private String reason() {
        var row = row();
        if (submitted) return TabletClassesClient.feedback();
        if (TabletClassesClient.busy()) return "Ожидаем сохранения операции.";
        if (row == null) return "Обновите данные сервера.";
        if (confirmation.kind() == Kind.UPGRADE_TIER) {
            if (row.tier() != confirmation.currentTier() || row.nextTier() != confirmation.targetTier()
                    || row.upgradePrice() != confirmation.price()) return "Условия изменились. Откройте подтверждение заново.";
            return row.upgradeReason();
        }
        if (row.purchaseKind() != confirmation.kind() || row.price() != confirmation.price())
            return "Условия изменились. Откройте подтверждение заново.";
        return row.purchaseReason();
    }
    private int hit(TabletDisplayProjection.Point p) {
        if (p == null) return -1;
        return confirm.contains(p.x(), p.y()) ? 1 : cancel.contains(p.x(), p.y()) ? 2 : -1;
    }
    void hover(TabletDisplayProjection.Point p) { int next = hit(p); if (hover != next) { hover = next; dirty = true; } }
    void press(TabletDisplayProjection.Point p) { pressed = hit(p); dirty = true; }
    void release(TabletDisplayProjection.Point p) {
        int id = pressed; cancelPress();
        if (id < 0 || id != hit(p)) return;
        if (id == 2) dismiss();
        else if (!submitted && reason().isEmpty()) {
            submitted = TabletClassesClient.submit(confirmation.kind(), confirmation.key(), confirmation.targetTier()); dirty = true;
        }
    }
    boolean dirty() {
        if (revision != TabletClassesClient.revision()) { revision = TabletClassesClient.revision(); dirty = true; }
        return active() && dirty;
    }
    void rendered() { dirty = false; }
    void render(GuiGraphics g, int height) {
        if (!active()) return;
        g.fill(0, 0, LOGICAL_WIDTH, height + 1, SCRIM);
        background(g, TabletTerminalTheme.Kind.PANEL, State.SELECTED, panel.x(), panel.y(), panel.width(), panel.height());
        text(g, confirmation.kind() == com.makar.tacticaltablet.progression.TabletProgressOperation.Kind.UPGRADE_TIER
                ? "УЛУЧШЕНИЕ КЛАССА" : "ПОКУПКА КЛАССА", panel.x() + 16, panel.y() + 14, TEXT, TITLE_SCALE);
        text(g, confirmation.name(), panel.x() + 16, panel.y() + 43, TEXT, TEXT_SCALE);
        if (confirmation.kind() == com.makar.tacticaltablet.progression.TabletProgressOperation.Kind.UPGRADE_TIER)
            text(g, tierName(confirmation.currentTier()) + " → " + tierName(confirmation.targetTier()), panel.x() + 16, panel.y() + 65, SECONDARY, TEXT_SCALE);
        var state = TabletClassesClient.snapshot();
        text(g, "Стоимость: " + confirmation.price() + " монет • Баланс: " + (state == null ? "…" : state.coins()),
                panel.x() + 16, panel.y() + 87, SECONDARY, DETAIL_SCALE);
        text(g, fit(reason(), 460), panel.x() + 16, panel.y() + 113, ERROR, DETAIL_SCALE);
        String label = submitted ? (TabletClassesClient.busy() ? "Обработка…" : "Ответ получен")
                : confirmation.kind() == com.makar.tacticaltablet.progression.TabletProgressOperation.Kind.UPGRADE_TIER ? "Улучшить" : "Купить";
        drawButton(g, confirm, label, !submitted && reason().isEmpty(), hover == 1, pressed == 1);
        drawButton(g, cancel, submitted ? "Закрыть" : "Отмена", true, hover == 2, pressed == 2);
    }
}
