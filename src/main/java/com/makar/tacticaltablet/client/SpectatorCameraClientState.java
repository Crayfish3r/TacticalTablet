package com.makar.tacticaltablet.client;

public final class SpectatorCameraClientState {

    private static boolean locked;

    private SpectatorCameraClientState() {
    }

    public static void setLocked(boolean value) {
        locked = value;
    }

    public static boolean isLocked() {
        return locked;
    }

    public static void clear() {
        locked = false;
    }
}
