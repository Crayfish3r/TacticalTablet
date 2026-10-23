package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.tablet.client.TabletClientState;
import net.minecraft.client.Minecraft;
import java.util.Arrays;

/** Session-local confirmed profile. Shares preparation polling; never sends its own requests. */
final class TabletProfilePage {
    final String[] values = new String[6];
    String playerName = "", status = "Загрузка личной статистики…";
    int careerPercent;
    boolean loaded, error;
    private long revision = -1;
    private boolean dirty = true;

    TabletProfilePage() { Arrays.fill(values, "…"); }

    void confirmCurrentSession() {
        // Called only for a matching nonce after this session's TabletStatePacket updated client state.
        loaded = true;
        capture();
    }

    void tick(TabletPreparationPage preparation) {
        if (loaded && revision != TabletClientState.getProfileRevision()) capture();
        var mc = Minecraft.getInstance();
        String name = mc.player == null ? "" : mc.player.getGameProfile().getName();
        if (!playerName.equals(name)) { playerName = name; dirty = true; }
        boolean connected = mc.getConnection() != null;
        boolean failed = !connected || preparation.synchronizationFailed();
        String next = !connected ? "Нет соединения. Обновление статистики недоступно."
                : failed ? (loaded ? "Обновление не получено. Показаны последние подтверждённые данные."
                        : "Не удалось загрузить статистику. Ожидаем повторного ответа сервера.")
                : !loaded ? "Загрузка личной статистики…"
                : preparation.awaiting ? "Обновление данных…" : "Данные подтверждены сервером";
        if (!status.equals(next) || error != failed) { status = next; error = failed; dirty = true; }
    }

    private void capture() {
        values[0] = Integer.toString(TabletClientState.getMatchesPlayed());
        values[1] = Integer.toString(TabletClientState.getWins());
        values[2] = Integer.toString(TabletClientState.getKills());
        values[3] = Integer.toString(TabletClientState.getDeaths());
        values[4] = TabletClientState.getKdaText(); // Historical method name; the metric is kills/deaths.
        values[5] = Integer.toString(TabletClientState.getCoins());
        careerPercent = TabletClientState.getCareerProgressPercent();
        revision = TabletClientState.getProfileRevision();
        dirty = true;
    }

    boolean dirty() { return dirty; }
    void rendered() { dirty = false; }
}
