package com.makar.tacticaltablet.integration.replaymod.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional client-only bridge to ReplayMod's public replay viewer.
 *
 * <p>No ReplayMod class is resolved until Forge confirms that the mod is loaded.
 * The bridge was verified against ReplayMod 1.20.1-2.6.13.</p>
 */
public final class ReplayModClientAdapter {
    public static final String MOD_ID = "replaymod";
    public static final String VERIFIED_VERSION = "1.20.1-2.6.13";
    private static final String REPLAY_MODULE_CLASS = "com.replaymod.replay.ReplayModReplay";
    private static final String VIEWER_CLASS = "com.replaymod.replay.gui.screen.GuiReplayViewer";

    private ReplayModClientAdapter() {
    }

    public static boolean isInstalled() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean openViewer() {
        if (!isInstalled()) return false;

        try {
            ClassLoader loader = ReplayModClientAdapter.class.getClassLoader();
            Class<?> replayModuleClass = Class.forName(REPLAY_MODULE_CLASS, true, loader);
            Field instanceField = replayModuleClass.getField("instance");
            Object replayModule = instanceField.get(null);
            if (replayModule == null) {
                TacticalTabletMod.LOGGER.warn("ReplayMod is loaded but ReplayModReplay.instance is not initialized");
                return false;
            }

            Class<?> viewerClass = Class.forName(VIEWER_CLASS, true, loader);
            Constructor<?> constructor = viewerClass.getConstructor(replayModuleClass);
            Object viewer = constructor.newInstance(replayModule);
            Method display = viewerClass.getMethod("display");
            display.invoke(viewer);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn(
                    "ReplayMod is installed, but its replay viewer could not be opened; verified API version is {}",
                    VERIFIED_VERSION,
                    exception
            );
            return false;
        }
    }
}
