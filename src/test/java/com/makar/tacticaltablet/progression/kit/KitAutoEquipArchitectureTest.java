package com.makar.tacticaltablet.progression.kit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitAutoEquipArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void kitItemsPreferEmptyArmorAndCuriosSlotsBeforeInventory() throws IOException {
        String kits = source("progression/kit/KitManager.java");
        String giveItem = kits.substring(kits.indexOf("private static void giveItem"),
                kits.indexOf("private static ItemStack equipArmor"));
        String equipArmor = kits.substring(kits.indexOf("private static ItemStack equipArmor"),
                kits.indexOf("/** Returns ordered file stems"));

        assertOrdered(giveItem,
                "equipArmor(player, configuredStack.copy())",
                "CuriosInventoryBridge.equipFirstAvailable(player, remainder)",
                "player.getInventory().add(remainder)");
        assertTrue(equipArmor.contains("player.getItemBySlot(slot).isEmpty()"));
        assertTrue(equipArmor.contains("stack.canEquip(slot, player)"));
        assertTrue(equipArmor.contains("player.setItemSlot(slot, stack.split(1))"));
    }

    @Test
    void optionalCuriosBridgeUsesValidatedFunctionalInsertionAndReturnsRemainder() throws IOException {
        String bridge = source("integration/curios/CuriosInventoryBridge.java");
        String commonApi = bridge.substring(0, bridge.indexOf("private static final class CuriosLoaded"));

        assertTrue(commonApi.contains("ModList.get().isLoaded(\"curios\")"));
        assertFalse(commonApi.contains("top.theillusivec4.curios"));
        assertTrue(bridge.contains("handler.getCurios().values()"));
        assertTrue(bridge.contains("stacks.getStackInSlot(slot).isEmpty()"));
        assertTrue(bridge.contains("stacks.insertItem(slot, stack, false)"));
        assertTrue(bridge.contains("remainder.getCount() < stack.getCount()"));
        assertFalse(bridge.contains("getCosmeticStacks().insertItem"));
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertTrue(current > previous, () -> "Expected ordered marker: " + marker);
            previous = current;
        }
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath)).replace("\r\n", "\n");
    }
}
