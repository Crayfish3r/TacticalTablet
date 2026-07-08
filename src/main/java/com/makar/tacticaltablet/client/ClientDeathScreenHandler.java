package com.makar.tacticaltablet.client;

public final class ClientDeathScreenHandler {

    private ClientDeathScreenHandler() {
    }

    public static void show(String title, String subtitle, int durationTicks, boolean playSadTrombone) {
        DeathScreenOverlay.show(title, subtitle, durationTicks, playSadTrombone);
    }
}
