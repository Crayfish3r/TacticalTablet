package com.makar.tacticaltablet.tablet.client.prototype;

import net.minecraft.client.gui.GuiGraphics;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

final class TabletShopView {
    private TabletShopView() { }
    static void render(GuiGraphics g, TabletShopPage page) {
        background(g, Kind.SCREEN, State.NORMAL, 0, 0, LOGICAL_WIDTH, page.height + 1);
        text(g, "МАГАЗИН", MARGIN, MARGIN, TEXT, TITLE_SCALE);
        var snapshot = TabletClassesClient.snapshot();
        text(g, "Баланс: " + (snapshot == null ? "…" : snapshot.coins()) + " монет", MARGIN, 43, SECONDARY, TEXT_SCALE);
        background(g, Kind.PANEL, State.NORMAL, page.list.x() - 3, page.list.y() - 3, page.list.width() + 6, page.list.height() + 6);
        if (snapshot == null) text(g, "Загрузка каталога с сервера…", MARGIN + 18, page.list.y() + 18, SECONDARY, TEXT_SCALE);
        for (int i = page.offset; i < Math.min(page.lines.size(), page.offset + page.capacity); i++) {
            var line = page.lines.get(i);
            if (line.entry() == null) {
                text(g, line.heading(), MARGIN + 10, page.list.y() + (i - page.offset) * ROW_HEIGHT + 12, OLIVE, TEXT_SCALE);
                continue;
            }
            var entry = line.entry(); var r = entry.bounds; var state = entry.state;
            tierRow(g, r.x(), r.y(), r.width(), r.height(), state.tier(), -1, 0,
                    page.hover == i, page.pressed == i, state.owned(), TabletClassesClient.busy() || !state.canPurchase());
            icon(g, entry.icon, r.x() + 9, r.y() + 6);
            text(g, entry.name, r.x() + 43, r.y() + 5, TEXT, TEXT_SCALE);
            text(g, fit(state.purchaseReason().isEmpty() ? "Открывает право на класс" : state.purchaseReason(), 370),
                    r.x() + 43, r.y() + 23, SECONDARY, DETAIL_SCALE);
            text(g, tierName(state.tier()), r.x() + r.width() - 86, r.y() + 5, TEXT, DETAIL_SCALE);
            text(g, state.owned() ? "✓" : state.price() + " монет", r.x() + r.width() - 86, r.y() + 23, TEXT, DETAIL_SCALE);
        }
        scrollbar(g, page.list, page.capacity, page.lines.size(), page.offset);
        text(g, fit(TabletClassesClient.feedback(), LOGICAL_WIDTH - 2 * MARGIN), MARGIN, page.height - 48, SECONDARY, DETAIL_SCALE);
        text(g, "ПКМ / ESC — убрать планшет", MARGIN, page.height - 18, MUTED, DETAIL_SCALE);
    }
}
