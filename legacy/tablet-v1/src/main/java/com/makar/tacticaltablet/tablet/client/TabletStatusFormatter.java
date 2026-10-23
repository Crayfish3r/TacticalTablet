package com.makar.tacticaltablet.tablet.client;

public final class TabletStatusFormatter {
    private TabletStatusFormatter() {
    }

    public static String progress(String tierName, int xp, int requiredXp) {
        return tierName + " • " + xp + "/" + requiredXp + " XP";
    }

    public static String purchase(int price) {
        return "Покупка • " + price + " монет";
    }

    public static String upgrade(int price) {
        return "Улучшение • " + price + " монет";
    }

    public static String cooldown(String formattedTime) {
        return "КД • " + formattedTime;
    }
}
