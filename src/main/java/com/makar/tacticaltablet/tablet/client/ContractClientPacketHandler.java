package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;
import com.makar.tacticaltablet.tablet.net.ContractTrackerStatePacket;

import net.minecraft.client.Minecraft;

public final class ContractClientPacketHandler {

    private ContractClientPacketHandler() {
    }

    public static void handleSelection(ContractSelectionStatePacket packet) {
        ContractClientState.updateSelection(
                packet.selectionActive(),
                packet.selectionSecondsLeft(),
                packet.cooldownLeftMs(),
                packet.hasActiveContract(),
                packet.soloMode(),
                packet.targets()
        );
    }

    public static void handleTracker(ContractTrackerStatePacket packet) {
        ContractClientState.updateTracker(
                packet.active(),
                packet.targetName(),
                packet.targetClass(),
                packet.targetKills(),
                packet.targetWins(),
                packet.targetCareerPercent(),
                packet.difficulty(),
                packet.price(),
                packet.reward(),
                packet.zoneCenterX(),
                packet.zoneCenterZ(),
                packet.zoneRadius(),
                packet.playerX(),
                packet.playerZ(),
                packet.targetAreaX(),
                packet.targetAreaZ(),
                packet.targetAreaRadius(),
                packet.signalSecondsLeft()
        );

        if (packet.openScreen()) {
            Minecraft.getInstance().setScreen(new ContractTrackerScreen());
        }
    }
}
