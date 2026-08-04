package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.net.KillFeedPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class KillFeedClientState {
    public static final int MAX_ENTRIES = 5;
    public static final int VISIBLE_TICKS = 5 * 20;
    public static final int FADE_TICKS = 20;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private KillFeedClientState() {
    }

    public static void handle(KillFeedPacket packet) {
        if (packet == null || packet.victimName().isBlank()) return;
        ENTRIES.add(0, new Entry(packet, VISIBLE_TICKS));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(ENTRIES.size() - 1);
    }

    public static void tick() {
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            ENTRIES.set(i, new Entry(entry.packet(), entry.ticksLeft() - 1));
        }
        for (Iterator<Entry> iterator = ENTRIES.iterator(); iterator.hasNext();) {
            if (iterator.next().ticksLeft() <= 0) iterator.remove();
        }
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public record Entry(KillFeedPacket packet, int ticksLeft) {
        public float alpha() {
            return Math.max(0.0F, Math.min(1.0F, ticksLeft / (float) FADE_TICKS));
        }
    }
}
