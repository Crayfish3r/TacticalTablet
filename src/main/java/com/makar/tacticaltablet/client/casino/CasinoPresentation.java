package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoRewardKind;
import net.minecraft.network.chat.Component;

/** Reward descriptions reused by physical and virtual casino menus. */
final class CasinoPresentation {
    private CasinoPresentation() { }
    static Component symbol(int position) {
        return Component.literal(switch (position) {
            case 0 -> "COINS"; case 1 -> "★"; case 2 -> "◆"; case 3 -> "●";
            case 4 -> "■"; case 5 -> "КЛАСС"; case 6 -> "VIP"; case 7 -> "♪";
            default -> "—";
        });
    }
    static Component rewardDescription(CasinoRewardKind rewardKind, int awardedCoins, String classId, boolean duplicate) {
        if (duplicate) {
            return Component.translatable("screen.tacticaltablet.casino.reward.duplicate", awardedCoins);
        }
        return switch (rewardKind) {
            case COINS -> awardedCoins > 0
                    ? Component.translatable("screen.tacticaltablet.casino.reward.coins", awardedCoins)
                    : Component.translatable("screen.tacticaltablet.casino.reward.none");
            case SHOP_CLASS -> Component.translatable("screen.tacticaltablet.casino.reward.shop_class",
                    classDisplayName(classId));
            case VIP_CLASS -> Component.translatable("screen.tacticaltablet.casino.reward.vip_class",
                    classDisplayName(classId));
            case SAD_TROMBONE -> Component.translatable("screen.tacticaltablet.casino.reward.sad_trombone");
        };
    }

    private static String classDisplayName(String id) {
        return switch (id) {
            case "solider" -> "Солдат";
            case "blackops" -> "Black Ops";
            case "rebel" -> "Повстанец";
            case "saboteur" -> "Саботёр";
            case "dream" -> "Dream";
            case "shahed" -> "Шахед оператор";
            case "miniboss" -> "Мини-босс";
            case "cowboy" -> "Ковбой";
            case "boomguy" -> "Подрывник";
            case "tagilla" -> "Тагилла";
            case "killer" -> "Киллер";
            case "crossbowman" -> "Арбалетчик";
            case "krot" -> "Крот";
            case "medic" -> "Медик";
            case "microwave" -> "Микровэйв";
            case "railgunner" -> "Рэйл-ганнер";
            case "smartstormtrooper" -> "Smart-штурмовик";
            default -> id == null || id.isBlank() ? "Неизвестный класс" : id;
        };
    }

}
