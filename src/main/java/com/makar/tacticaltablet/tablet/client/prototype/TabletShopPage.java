package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.tablet.ClassDefinition;
import com.makar.tacticaltablet.tablet.ClassDefinitions;
import com.makar.tacticaltablet.tablet.client.ClassIconResolver;
import com.makar.tacticaltablet.tablet.classes.TabletClassSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Retained catalog layout. Entries are rebuilt only on confirmed server snapshots, never during render. */
final class TabletShopPage {
    static final class Entry {
        final ClassDefinition definition;
        final ResourceLocation icon;
        final String name;
        TabletClassSnapshot.Row state;
        TabletPreparationPage.Rect bounds;
        Entry(ClassDefinition definition) {
            this.definition = definition; name = definition.name().getString();
            icon = ClassIconResolver.resolve(definition, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
        }
    }
    record Line(String heading, Entry entry) { }
    final int height;
    final TabletPreparationPage.Rect list;
    final List<Line> lines = new ArrayList<>();
    private final List<Entry> catalog = ClassDefinitions.all().stream()
            .filter(d -> d.category() == com.makar.tacticaltablet.tablet.ClassCategory.BASE
                    || d.category() == com.makar.tacticaltablet.tablet.ClassCategory.SHOP).map(Entry::new).toList();
    private final TabletClassDialog dialog;
    int offset, capacity, hover = -1, pressed = -1;
    private boolean interactive, dirty = true;
    private long revision = -1;
    TabletShopPage(int height, TabletClassDialog dialog) {
        this.height = height; this.dialog = dialog;
        list = new TabletPreparationPage.Rect(MARGIN, 65, LOGICAL_WIDTH - MARGIN * 2, height - 132);
        capacity = Math.max(1, list.height() / ROW_HEIGHT);
    }
    void update() {
        if (revision == TabletClassesClient.revision()) return;
        revision = TabletClassesClient.revision();
        var snapshot = TabletClassesClient.snapshot();
        if (snapshot != null) {
            lines.clear(); lines.add(new Line("БАЗОВЫЕ КЛАССЫ", null));
            for (Entry entry : catalog) {
                entry.state = snapshot.rows().stream().filter(r -> r.classKey().equals(entry.definition.classKey())).findFirst().orElse(null);
                if (entry.state != null && entry.state.purchaseKind() == com.makar.tacticaltablet.progression.TabletProgressOperation.Kind.UNLOCK_BASE)
                    lines.add(new Line("", entry));
            }
            lines.add(new Line("МАГАЗИННЫЕ КЛАССЫ", null));
            for (Entry entry : catalog) if (entry.state != null && entry.definition.category() == com.makar.tacticaltablet.tablet.ClassCategory.SHOP)
                lines.add(new Line("", entry));
            layout();
        }
        dirty = true;
    }
    private void layout() {
        offset = Math.max(0, Math.min(offset, Math.max(0, lines.size() - capacity)));
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).entry() != null)
            lines.get(i).entry().bounds = new TabletPreparationPage.Rect(list.x(), list.y() + (i - offset) * ROW_HEIGHT, list.width() - 9, ROW_HEIGHT - 4);
    }
    private int hit(TabletDisplayProjection.Point p) {
        if (!interactive || p == null || !list.contains(p.x(), p.y())) return -1;
        for (int i = offset; i < Math.min(lines.size(), offset + capacity); i++) {
            Entry entry = lines.get(i).entry();
            if (entry != null && entry.bounds.contains(p.x(), p.y())) return i;
        }
        return -1;
    }
    void hover(TabletDisplayProjection.Point p) { int next = hit(p); if (next != hover) { hover = next; dirty = true; } }
    void press(TabletDisplayProjection.Point p) { pressed = hit(p); dirty = true; }
    void release(TabletDisplayProjection.Point p) {
        int id = pressed; cancelPress();
        if (id >= 0 && id == hit(p)) {
            Entry entry = lines.get(id).entry();
            // Insufficient balance and mode restrictions remain visible in a disabled confirmation.
            if (!entry.state.owned() && !TabletClassesClient.busy()) dialog.purchase(entry.definition, entry.state);
        }
    }
    void cancelPress() { if (pressed >= 0) { pressed = -1; dirty = true; } }
    void interactive(boolean value) { if (interactive != value) { interactive = value; hover = -1; cancelPress(); dirty = true; } }
    void scroll(TabletDisplayProjection.Point p, double delta) {
        if (!interactive || p == null || !list.contains(p.x(), p.y())) return;
        int next = Math.max(0, Math.min(Math.max(0, lines.size() - capacity), offset - (int) Math.signum(delta)));
        if (next != offset) { offset = next; cancelPress(); layout(); dirty = true; }
    }
    boolean dirty() { return dirty; }
    void rendered() { dirty = false; }
}
