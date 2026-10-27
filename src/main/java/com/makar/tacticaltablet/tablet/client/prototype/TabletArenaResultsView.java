package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.tablet.client.ArenaClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.UUID;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Final per-game and set results within the first physical page, with projected scrolling. */
final class TabletArenaResultsView {
    private static final int ROW_HEIGHT = 30;
    private final int height, capacity;
    private int offset;
    private long revision = -1;
    private UUID matchId;
    private boolean setResults, dirty = true;
    TabletArenaResultsView(int height) { this.height = height; capacity = Math.max(1, (height - 126) / ROW_HEIGHT); }
    void tick() {
        if (revision == ArenaClientState.revision()) return;
        var state = ArenaClientState.snapshot();
        if (!java.util.Objects.equals(matchId, state.matchId()) || setResults != state.setResults()) offset = 0;
        matchId = state.matchId(); setResults = state.setResults();
        offset = Math.max(0, Math.min(offset, state.rows().size() - capacity));
        revision = ArenaClientState.revision(); dirty = true;
    }
    void scroll(TabletDisplayProjection.Point point, double delta) {
        if (point == null || point.y() < 84 || point.y() > height - 40) return;
        int next = Math.max(0, Math.min(Math.max(0, ArenaClientState.snapshot().rows().size() - capacity),
                offset - (int) Math.signum(delta)));
        if (next != offset) { offset = next; dirty = true; }
    }
    boolean dirty() { return dirty; }
    void rendered() { dirty = false; }
    void render(GuiGraphics g) {
        var state = ArenaClientState.snapshot(); var mc = Minecraft.getInstance();
        background(g, Kind.SCREEN, State.NORMAL, 0, 0, LOGICAL_WIDTH, height + 1);
        text(g, setResults ? "АРЕНА — ИТОГИ СЕТА" : "АРЕНА — ИТОГИ ИГРЫ", MARGIN, MARGIN, 608, TEXT, TITLE_SCALE);
        text(g, "Игра " + state.gameNumber() + "/" + state.gamesPerSet() + " • Очки сохраняются в итогах сета",
                MARGIN, 44, 608, SECONDARY, DETAIL_SCALE);
        String[] labels = {"Игрок", "Очки", "PvP", "NPC", "Асс.", "Смерти"};
        int[] columns = {24, 266, 346, 406, 466, 546};
        for (int i = 0; i < labels.length; i++) text(g, labels[i], columns[i], 67, i == 0 ? 220 : 64, MUTED, DETAIL_SCALE);
        for (int i = offset; i < Math.min(state.rows().size(), offset + capacity); i++) {
            var row = state.rows().get(i); int y = 84 + (i - offset) * ROW_HEIGHT;
            boolean own = mc.player != null && row.playerId().equals(mc.player.getUUID());
            background(g, Kind.ROW, own ? State.SELECTED : State.NORMAL, MARGIN, y, LOGICAL_WIDTH - 2 * MARGIN, ROW_HEIGHT - 3);
            String[] cells = {(i + 1) + ". " + row.name(), "" + row.score(), "" + row.pvpKills(),
                    "" + row.npcKills(), "" + row.assists(), "" + row.deaths()};
            for (int c = 0; c < cells.length; c++) text(g, cells[c], columns[c], y + 8,
                    c == 0 ? 220 : 64, c == 1 ? OLIVE : TEXT, DETAIL_SCALE);
        }
        text(g, "Прокрутка — таблица • ПКМ / ESC — убрать планшет", MARGIN, height - 21, 608, MUTED, DETAIL_SCALE);
    }
    private static void text(GuiGraphics g, String value, int x, int y, int width, int color, float scale) {
        var font = Minecraft.getInstance().font;
        int limit = (int) (width / scale);
        String fitted = font.width(value) <= limit ? value : font.plainSubstrByWidth(value,
                Math.max(0, limit - font.width("…"))) + "…";
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, fitted, 0, 0, color, false); g.pose().popPose();
    }
}
