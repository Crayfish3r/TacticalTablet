package com.makar.tacticaltablet.client.event;

import com.makar.tacticaltablet.client.gui.CustomMainMenu;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class ClientScreenEvents {

    private ClientScreenEvents() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen)) return;

        Screen currentScreen = event.getCurrentScreen();
        if (currentScreen instanceof CustomMainMenu) {
            event.setNewScreen(currentScreen);
            return;
        }

        event.setNewScreen(new CustomMainMenu());
    }
}
