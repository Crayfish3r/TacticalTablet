package com.makar.tacticaltablet.game.extraction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ExtractionPointData {
    public UUID eventId;
    public BlockPos center;
    public double radius;
    public double halfHeight;
    public ExtractionPointState state = ExtractionPointState.IDLE;
    public long scheduledStartTick;
    public long activeStartTick;
    public long expireAtMatchTick;
    public long endingUntilTick;
    public int requiredCaptureTicks;
    public int globalCaptureProgressTicks;
    public UUID currentOwnerPlayerId;
    public String currentOwnerTeamId;
    public int continuousOwnerCaptureTicks;
    public int nextMilestoneRewardAtTicks;
    public boolean contested;
    public final Set<UUID> playersInside = new HashSet<>();
    public final Set<String> teamsInside = new HashSet<>();
    public ServerBossEvent bossbar;

    public static ExtractionPointData idle() {
        ExtractionPointData data = new ExtractionPointData();
        data.state = ExtractionPointState.IDLE;
        return data;
    }
}
