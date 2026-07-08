package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.client.DeathScreenOverlay;
import com.makar.tacticaltablet.client.SpectatorCameraClientState;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.prefix.PrefixClientState;
import com.makar.tacticaltablet.tablet.TacticalTabletItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {

        if (!event.isUseItem()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;
        if (DeathScreenOverlay.isActive()) return;

        if (hasTabletInHand(player)) {
            mc.setScreen(createScreenForCurrentPhase());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Screen current = mc.screen;
        if (DeathScreenOverlay.isActive()) {
            return;
        }

        boolean voting = TabletClientState.getMatchPhase() == MatchPhase.VOTING;
        boolean teamSelect = TabletClientState.getMatchPhase() == MatchPhase.TEAM_SELECT;
        boolean mapVoting = TabletClientState.getMatchPhase() == MatchPhase.MAP_VOTING
                && MapVoteClientState.isActive();

        if (mapVoting && !(current instanceof MapVotingScreen)) {
            mc.setScreen(new MapVotingScreen());
            return;
        }

        if (current instanceof MapVotingScreen && !mapVoting) {
            mc.setScreen(null);
            return;
        }

        if (current instanceof VotingScreen && teamSelect) {
            mc.setScreen(new TeamSelectScreen());
            return;
        }

        if (current instanceof TeamSelectScreen && voting) {
            mc.setScreen(new VotingScreen());
            return;
        }

        if (!voting && !teamSelect && (current instanceof VotingScreen || current instanceof TeamSelectScreen)) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PrefixClientState.clear();
        SpectatorCameraClientState.clear();
    }

    private static boolean hasTabletInHand(Player player) {
        if (player == null) return false;
        return isTablet(player.getItemInHand(InteractionHand.MAIN_HAND))
                || isTablet(player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private static boolean isTablet(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TacticalTabletItem;
    }

    private static Screen createScreenForCurrentPhase() {
        MatchPhase phase = TabletClientState.getMatchPhase();
        if (phase == MatchPhase.MAP_VOTING) return new MapVotingScreen();
        if (phase == MatchPhase.VOTING) return new VotingScreen();
        if (phase == MatchPhase.TEAM_SELECT) return new TeamSelectScreen();
        return new TabletScreen();
    }
}
