package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.prototype.TabletPrototypeClient;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/** Single presentation boundary, including packet callbacks and nested tablet dialogs. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class TabletPresentation {
    private TabletPresentation() { }

    public static boolean prototype() {
        return TacticalTabletClientConfig.TABLET_PRESENTATION.get()
                == TacticalTabletClientConfig.TabletPresentationMode.PROTOTYPE;
    }

    public static void openLegacy(Supplier<? extends Screen> screen) {
        if (!prototype()) Minecraft.getInstance().setScreen(screen.get());
    }

    public static boolean isLegacyTablet(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof TabletScreen || screen instanceof TacticalPhaseScreen) return true;
        for (Class<?> type = screen.getClass(); type != null; type = type.getEnclosingClass()) {
            if (type == TabletScreen.class) return true;
        }
        return screen instanceof TacticalDialog dialog && isLegacyTablet(dialog.parentScreen());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void opening(ScreenEvent.Opening event) {
        if (prototype() && isLegacyTablet(event.getNewScreen())) {
            event.setCanceled(true); // Before init(), sounds, input callbacks or any GUI actions.
            return;
        }
        TabletPrototypeClient.screenOpening(event.getNewScreen());
    }
}
