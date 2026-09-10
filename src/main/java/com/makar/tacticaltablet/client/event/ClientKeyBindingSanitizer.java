package com.makar.tacticaltablet.client.event;

import com.makar.tacticaltablet.client.gui.KeyBindingVisibilityPolicy;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class ClientKeyBindingSanitizer {

    private static boolean sanitized;
    private static final java.util.Map<KeyMapping, SavedBinding> previous = new java.util.HashMap<>();
    private record SavedBinding(InputConstants.Key key, net.minecraftforge.client.settings.KeyModifier modifier) { }

    private ClientKeyBindingSanitizer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft.options;
        if (minecraft.getConnection() == null || minecraft.hasSingleplayerServer()) {
            if (sanitized) {
                previous.forEach((mapping, binding) -> mapping.setKeyModifierAndCode(binding.modifier(), binding.key()));
                previous.clear();
                KeyMapping.resetMapping();
                sanitized = false;
            }
            return;
        }
        if (sanitized) return;
        boolean changed = false;
        for (KeyMapping mapping : options.keyMappings) {
            if (!KeyBindingVisibilityPolicy.mustBeUnbound(mapping.getName())) continue;
            if (!mapping.isUnbound()) {
                previous.put(mapping, new SavedBinding(mapping.getKey(), mapping.getKeyModifier()));
                mapping.setKeyModifierAndCode(null, InputConstants.UNKNOWN);
                options.setKey(mapping, InputConstants.UNKNOWN);
                changed = true;
            }
        }

        if (changed) {
            KeyMapping.resetMapping();
            // Do not persist dedicated-server restrictions into singleplayer controls.
        }
        sanitized = true;
    }
}
