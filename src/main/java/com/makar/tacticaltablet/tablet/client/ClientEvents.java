package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.TacticalTabletItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ToastAddEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onToastAdded(ToastAddEvent event) {
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {

        if (!event.isUseItem()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof TacticalTabletItem) {
            mc.setScreen(createScreenForCurrentPhase());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Screen current = mc.screen;
        boolean voting = TabletClientState.getMatchPhase() == MatchPhase.VOTING;
        boolean teamSelect = TabletClientState.getMatchPhase() == MatchPhase.TEAM_SELECT;

        if (voting && !(current instanceof VotingScreen)) {
            mc.setScreen(new VotingScreen());
            return;
        }

        if (teamSelect && !(current instanceof TeamSelectScreen)) {
            mc.setScreen(new TeamSelectScreen());
            return;
        }

        if (!voting && !teamSelect && (current instanceof VotingScreen || current instanceof TeamSelectScreen)) {
            mc.setScreen(null);
        }
    }

    private static Screen createScreenForCurrentPhase() {
        MatchPhase phase = TabletClientState.getMatchPhase();
        if (phase == MatchPhase.VOTING) return new VotingScreen();
        if (phase == MatchPhase.TEAM_SELECT) return new TeamSelectScreen();
        return new TabletScreen();
    }
}
