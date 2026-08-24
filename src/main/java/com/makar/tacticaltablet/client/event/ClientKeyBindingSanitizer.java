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

    private ClientKeyBindingSanitizer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (sanitized || event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft.options;
        boolean changed = false;
        for (KeyMapping mapping : options.keyMappings) {
            if (!KeyBindingVisibilityPolicy.mustBeUnbound(mapping.getName())) continue;
            if (!mapping.isUnbound()) {
                mapping.setKeyModifierAndCode(null, InputConstants.UNKNOWN);
                options.setKey(mapping, InputConstants.UNKNOWN);
                changed = true;
            }
        }

        if (changed) {
            KeyMapping.resetMapping();
            options.save();
        }
        sanitized = true;
    }
}
