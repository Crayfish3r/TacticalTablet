package com.makar.tacticaltablet.tablet;

import com.makar.tacticaltablet.progression.PlayerProgressManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class TabletAppearanceManager {

    public static final int EPIC_MODEL_DATA = 1;
    public static final int LEGEND_MODEL_DATA = 2;

    private TabletAppearanceManager() {
    }

    public static int getAppearanceTier(ServerPlayer player) {
        return getModelData(player);
    }

    public static ItemStack apply(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;

        int modelData = getModelData(player);

        if (modelData <= 0) {
            if (stack.hasTag()) {
                stack.getTag().remove("CustomModelData");
            }
            return stack;
        }

        stack.getOrCreateTag().putInt("CustomModelData", modelData);
        return stack;
    }

    private static int getModelData(ServerPlayer player) {
        if (player == null) return 0;

        boolean allEpic = true;
        boolean allLegend = true;

        for (String clazz : PlayerProgressManager.getStandardClasses()) {
            int level = PlayerProgressManager.getLevel(player, clazz);

            if (level < 1) {
                allEpic = false;
            }

            if (level < 2) {
                allLegend = false;
            }
        }

        if (allLegend) return LEGEND_MODEL_DATA;
        if (allEpic) return EPIC_MODEL_DATA;
        return 0;
    }
}
