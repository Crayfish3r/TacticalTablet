package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;
import com.makar.tacticaltablet.tablet.net.ContractTrackerStatePacket;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class ContractClientPacketHandler {

    private static final int MAX_TARGETS = 16;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_CLASS_LENGTH = 32;

    private ContractClientPacketHandler() {
    }

    public static void handleSelection(ContractSelectionStatePacket packet) {
        if (packet == null) return;

        ContractClientState.updateSelection(
                packet.selectionActive(),
                packet.selectionSecondsLeft(),
                packet.cooldownLeftMs(),
                packet.hasActiveContract(),
                packet.soloMode(),
                sanitizeSelectionTargets(packet.targets())
        );
    }

    public static void handleTracker(ContractTrackerStatePacket packet) {
        if (packet == null || packet.zoneRadius() <= 0) return;

        List<ContractTrackerStatePacket.TargetEntry> targets = sanitizeTrackerTargets(packet.targets());
        ContractClientState.updateTracker(
                packet.active() && !targets.isEmpty(),
                packet.zoneCenterX(),
                packet.zoneCenterZ(),
                packet.zoneRadius(),
                packet.playerX(),
                packet.playerZ(),
                packet.signalSecondsLeft(),
                targets
        );

        Minecraft minecraft = Minecraft.getInstance();
        if (packet.openScreen() && minecraft.level != null && minecraft.player != null) {
            minecraft.setScreen(new ContractTrackerScreen());
        }
    }

    private static List<ContractSelectionStatePacket.TargetEntry> sanitizeSelectionTargets(
            List<ContractSelectionStatePacket.TargetEntry> targets
    ) {
        if (targets == null || targets.isEmpty()) return List.of();

        List<ContractSelectionStatePacket.TargetEntry> sanitized = new ArrayList<>();
        for (ContractSelectionStatePacket.TargetEntry target : targets) {
            if (target == null || target.uuid() == null) continue;
            if (sanitized.size() >= MAX_TARGETS) break;

            sanitized.add(new ContractSelectionStatePacket.TargetEntry(
                    target.uuid(),
                    sanitizeText(target.name(), MAX_NAME_LENGTH),
                    sanitizeText(target.selectedClass(), MAX_CLASS_LENGTH),
                    Math.max(0, target.kills()),
                    Math.max(0, target.wins()),
                    Math.max(0, Math.min(100, target.careerPercent())),
                    Math.max(0, target.difficulty()),
                    Math.max(0, target.price()),
                    Math.max(0, target.reward())
            ));
        }
        return sanitized;
    }

    private static List<ContractTrackerStatePacket.TargetEntry> sanitizeTrackerTargets(
            List<ContractTrackerStatePacket.TargetEntry> targets
    ) {
        if (targets == null || targets.isEmpty()) return List.of();

        List<ContractTrackerStatePacket.TargetEntry> sanitized = new ArrayList<>();
        for (ContractTrackerStatePacket.TargetEntry target : targets) {
            if (target == null || target.areaRadius() < 0) continue;
            if (sanitized.size() >= MAX_TARGETS) break;

            sanitized.add(new ContractTrackerStatePacket.TargetEntry(
                    sanitizeText(target.name(), MAX_NAME_LENGTH),
                    sanitizeText(target.selectedClass(), MAX_CLASS_LENGTH),
                    target.kills(),
                    target.wins(),
                    target.careerPercent(),
                    target.difficulty(),
                    target.price(),
                    target.reward(),
                    target.areaX(),
                    target.areaZ(),
                    target.areaRadius()
            ));
        }
        return sanitized;
    }

    private static String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
