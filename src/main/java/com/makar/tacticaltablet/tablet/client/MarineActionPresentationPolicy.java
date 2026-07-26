package com.makar.tacticaltablet.tablet.client;

public final class MarineActionPresentationPolicy {
    static final int AVAILABLE_COLOR = 0xFF72D68A;
    static final int PURCHASE_COLOR = 0xFFE7C76A;
    static final int UNAVAILABLE_COLOR = 0xFFD87575;

    private MarineActionPresentationPolicy() {
    }

    public static Presentation describe(
            boolean inClan,
            boolean clanUnlocked,
            boolean canBuyForClan,
            boolean actionActive,
            boolean kitUsed,
            String cooldown,
            boolean gameRunning,
            int clanCost
    ) {
        if (!inClan) {
            return unavailable("Требуется клан", "C");
        }
        if (!clanUnlocked) {
            if (canBuyForClan) {
                return new Presentation(
                        "Покупка • " + clanCost + " КК",
                        "Глава клана может купить Морпеха",
                        true,
                        PURCHASE_COLOR,
                        "¤"
                );
            }
            return unavailable("Требуется разблокировка клана", "C");
        }
        if (cooldown != null && !cooldown.isBlank()) {
            return new Presentation(
                    TabletStatusFormatter.cooldown(cooldown),
                    "Класс на перезарядке",
                    false,
                    PURCHASE_COLOR,
                    "◷"
            );
        }
        if (kitUsed) {
            return unavailable("Уже использован", "✓");
        }
        if (!gameRunning) {
            return unavailable("Нет активной игры", "■");
        }
        return new Presentation(
                "Разблокирован кланом",
                "Класс доступен",
                actionActive,
                AVAILABLE_COLOR,
                "✓"
        );
    }

    private static Presentation unavailable(String detail, String marker) {
        return new Presentation(
                detail,
                detail,
                false,
                UNAVAILABLE_COLOR,
                marker
        );
    }

    public record Presentation(
            String status,
            String hint,
            boolean active,
            int color,
            String marker
    ) {
    }
}
