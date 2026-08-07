package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.net.KillFeedPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class KillFeedClientState {
    public static final int MAX_ENTRIES = 5;
    public static final int VISIBLE_TICKS = 5 * 20;
    public static final int FADE_TICKS = 20;
    private static final int NORMAL_STEP = 23;
    private static final int REWARD_STEP = 34;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private KillFeedClientState() { }

    public static void handle(KillFeedPacket packet) {
        if (packet == null || packet.victimName().isBlank() || isDuplicate(packet)) return;
        ENTRIES.add(0, new Entry(packet, VISIBLE_TICKS, 0.0F));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(ENTRIES.size() - 1);
    }

    public static void tick() {
        float targetOffset = 0.0F;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            float nextOffset = entry.visualOffset() + (targetOffset - entry.visualOffset()) * 0.35F;
            ENTRIES.set(i, new Entry(entry.packet(), entry.ticksLeft() - 1, nextOffset));
            targetOffset += hasReward(entry.packet()) ? REWARD_STEP : NORMAL_STEP;
        }
        for (Iterator<Entry> iterator = ENTRIES.iterator(); iterator.hasNext();) {
            if (iterator.next().ticksLeft() <= 0) iterator.remove();
        }
    }

    public static List<Entry> entries() { return List.copyOf(ENTRIES); }
    public static void clear() { ENTRIES.clear(); }

    private static boolean isDuplicate(KillFeedPacket packet) {
        return ENTRIES.stream().anyMatch(entry -> entry.packet().victimUuid().equals(packet.victimUuid())
                && entry.packet().serverTime() == packet.serverTime());
    }

    private static boolean hasReward(KillFeedPacket packet) {
        return packet.awardedCoins() > 0 || packet.awardedXp() > 0;
    }

    public record Entry(KillFeedPacket packet, int ticksLeft, float visualOffset) {
        public float alpha() {
            return Math.max(0.0F, Math.min(1.0F, ticksLeft / (float) FADE_TICKS));
        }
    }
}
