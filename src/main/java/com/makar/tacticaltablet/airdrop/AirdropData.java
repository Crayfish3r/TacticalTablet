package com.makar.tacticaltablet.airdrop;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class AirdropData {
    public UUID id;
    public AirdropState state;
    public ResourceKey<Level> dimension;
    public BlockPos realDropPos;
    public BlockPos compassTargetPos;
    public BlockPos chestPos;
    public int ticksUntilDrop;
    public int ticksSinceLanded;
    public int ticksSinceOpened;
    public UUID openedBy;
    public boolean opened;
    public boolean greenSmoke;
    public double currentCrateY;
    public UUID visualEntityId;

    public AirdropData(
            UUID id,
            AirdropState state,
            ResourceKey<Level> dimension,
            BlockPos realDropPos,
            BlockPos compassTargetPos,
            int ticksUntilDrop,
            double currentCrateY
    ) {
        this.id = id;
        this.state = state;
        this.dimension = dimension;
        this.realDropPos = realDropPos;
        this.compassTargetPos = compassTargetPos;
        this.ticksUntilDrop = ticksUntilDrop;
        this.currentCrateY = currentCrateY;
    }
}
