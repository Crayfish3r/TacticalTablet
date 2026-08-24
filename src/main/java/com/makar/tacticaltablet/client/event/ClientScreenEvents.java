package com.makar.tacticaltablet.client.event;

import com.makar.tacticaltablet.client.gui.CustomMainMenu;
import com.makar.tacticaltablet.client.gui.CustomPauseScreen;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class ClientScreenEvents {

    private static final Component VANILLA_PAUSE_MENU_TITLE = Component.translatable("menu.game");

    private ClientScreenEvents() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen newScreen = event.getNewScreen();
        if (newScreen instanceof PauseScreen && VANILLA_PAUSE_MENU_TITLE.equals(newScreen.getTitle())) {
            if (event.getCurrentScreen() instanceof CustomPauseScreen currentPauseScreen) {
                event.setNewScreen(currentPauseScreen);
            } else {
                event.setNewScreen(new CustomPauseScreen());
            }
            return;
        }

        if (!(newScreen instanceof TitleScreen)) return;

        Screen currentScreen = event.getCurrentScreen();
        if (currentScreen instanceof CustomMainMenu) {
            event.setNewScreen(currentScreen);
            return;
        }

        event.setNewScreen(new CustomMainMenu());
    }
}
