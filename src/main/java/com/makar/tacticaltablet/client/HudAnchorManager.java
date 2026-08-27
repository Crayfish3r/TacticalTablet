package com.makar.tacticaltablet.client;

import java.util.ArrayList;
import java.util.List;

/** Pure safe-area placement for TacticalTablet HUD overlays. */
public final class HudAnchorManager {
    static final int SAFE_MARGIN = 6;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int ZONE_GAP = 4;

    private HudAnchorManager() {
    }

    public enum Side { AUTO, LEFT, RIGHT }

    public static Rect hotbarSide(int screenWidth, int screenHeight, int width, int height) {
        int safeWidth = fit(width, screenWidth);
        int safeHeight = Math.max(0, height);
        int hotbarRight = screenWidth / 2 + HOTBAR_WIDTH / 2;
        int sideX = hotbarRight + SAFE_MARGIN;
        if (sideX + safeWidth <= screenWidth - SAFE_MARGIN && safeHeight <= HOTBAR_HEIGHT) {
            return new Rect(sideX, screenHeight - HOTBAR_HEIGHT + (HOTBAR_HEIGHT - safeHeight) / 2,
                    safeWidth, safeHeight);
        }

        int x = clamp(screenWidth / 2 - safeWidth / 2, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - safeWidth));
        int y = Math.max(SAFE_MARGIN, screenHeight - HOTBAR_HEIGHT - ZONE_GAP - safeHeight);
        return new Rect(x, y, safeWidth, safeHeight);
    }

    public static Rect topCenter(int screenWidth, int screenHeight, int width, int height) {
        int safeWidth = fit(width, screenWidth);
        int safeHeight = Math.min(Math.max(0, height), Math.max(0, screenHeight - SAFE_MARGIN * 2));
        int x = clamp(screenWidth / 2 - safeWidth / 2, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - safeWidth));
        return new Rect(x, Math.min(28, Math.max(SAFE_MARGIN, screenHeight - SAFE_MARGIN - safeHeight)),
                safeWidth, safeHeight);
    }

    public static Rect spectatorHint(int screenWidth, int screenHeight, int width, int height) {
        int safeWidth = fit(width, screenWidth);
        int safeHeight = Math.max(0, height);
        int x = clamp(screenWidth / 2 - safeWidth / 2, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - safeWidth));
        int y = Math.max(SAFE_MARGIN, screenHeight - HOTBAR_HEIGHT - 24 - safeHeight);
        return new Rect(x, y, safeWidth, safeHeight);
    }

    public static Rect spectatorPanel(int screenWidth, int screenHeight, int width, int height, Rect hint) {
        int safeWidth = fit(width, screenWidth);
        int safeHeight = Math.min(Math.max(0, height), Math.max(0, screenHeight - SAFE_MARGIN * 2));
        int x = clamp(screenWidth / 2 - safeWidth / 2, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - safeWidth));
        int upperLimit = hint == null ? screenHeight - SAFE_MARGIN : hint.y() - ZONE_GAP;
        int y = clamp(upperLimit - safeHeight, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenHeight - SAFE_MARGIN - safeHeight));
        return new Rect(x, y, safeWidth, safeHeight);
    }

    /** Chooses and adjusts a side candidate until it no longer intersects known occupied HUD zones. */
    public static Rect staminaBars(int screenWidth, int screenHeight, int width, int height,
                                   Side side, int xOffset, int yOffset, List<Rect> occupied) {
        int safeWidth = fit(width, screenWidth);
        int safeHeight = Math.min(Math.max(0, height), Math.max(0, screenHeight - SAFE_MARGIN * 2));
        int left = SAFE_MARGIN;
        int right = Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - safeWidth);
        int bottom = Math.max(SAFE_MARGIN, screenHeight - SAFE_MARGIN - safeHeight);
        int top = SAFE_MARGIN;
        List<Rect> candidates = new ArrayList<>();
        Side requested = side == null ? Side.AUTO : side;
        if (requested == Side.RIGHT) {
            candidates.add(new Rect(right, bottom, safeWidth, safeHeight));
            candidates.add(new Rect(left, bottom, safeWidth, safeHeight));
        } else {
            candidates.add(new Rect(left, bottom, safeWidth, safeHeight));
            candidates.add(new Rect(right, bottom, safeWidth, safeHeight));
        }
        candidates.add(new Rect(left, top, safeWidth, safeHeight));
        candidates.add(new Rect(right, top, safeWidth, safeHeight));

        List<Rect> reservations = occupied == null ? List.of() : occupied;
        for (Rect candidate : candidates) {
            Rect shifted = offsetAndClamp(candidate, screenWidth, screenHeight, xOffset, yOffset);
            shifted = moveAboveIntersections(shifted, reservations);
            if (shifted.y() >= SAFE_MARGIN && reservations.stream().noneMatch(shifted::intersects)) {
                return shifted;
            }
        }
        return offsetAndClamp(candidates.get(0), screenWidth, screenHeight, xOffset, yOffset);
    }

    private static Rect moveAboveIntersections(Rect initial, List<Rect> occupied) {
        Rect current = initial;
        for (int attempt = 0; attempt <= occupied.size(); attempt++) {
            Rect collision = null;
            for (Rect reservation : occupied) {
                if (current.intersects(reservation)) {
                    collision = reservation;
                    break;
                }
            }
            if (collision == null) return current;
            current = new Rect(current.x(), collision.y() - ZONE_GAP - current.height(),
                    current.width(), current.height());
        }
        return current;
    }

    private static Rect offsetAndClamp(Rect rect, int screenWidth, int screenHeight,
                                       int xOffset, int yOffset) {
        int x = clamp(rect.x() + xOffset, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - rect.width()));
        int y = clamp(rect.y() + yOffset, SAFE_MARGIN,
                Math.max(SAFE_MARGIN, screenHeight - SAFE_MARGIN - rect.height()));
        return new Rect(x, y, rect.width(), rect.height());
    }

    private static int fit(int requested, int screenWidth) {
        return Math.min(Math.max(0, requested), Math.max(0, screenWidth - SAFE_MARGIN * 2));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean intersects(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }
}
