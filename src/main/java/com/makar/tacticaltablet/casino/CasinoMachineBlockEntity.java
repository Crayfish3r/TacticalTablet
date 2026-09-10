package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class CasinoMachineBlockEntity extends BlockEntity {
    private final CasinoMachineAnimationState animation = new CasinoMachineAnimationState();

    public CasinoMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASINO_MACHINE.get(), pos, state);
    }

    public CasinoMachineAnimationState animation() { return animation; }
    public boolean reserveSpin() {
        if (level == null || level.isClientSide || isRemoved()) return false;
        if (animation.finish(level.getGameTime())) syncVisuals();
        return animation.reserve(level.getGameTime());
    }
    public void releaseSpin() { animation.release(); }
    public void startAnimation(long seed, CasinoReelResult result) {
        if (level == null || level.isClientSide || isRemoved()) { releaseSpin(); return; }
        animation.start(level.getGameTime(), seed, result);
        syncVisuals();
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, CasinoMachineBlockEntity machine) {
        if (machine.animation.finish(level.getGameTime())) machine.syncVisuals();
    }
    private void syncVisuals() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeVisuals(tag);
    }
    private void writeVisuals(CompoundTag tag) {
        writeVisuals(tag, animation);
    }
    static void writeVisuals(CompoundTag tag, CasinoMachineAnimationState animation) {
        tag.putBoolean("Spinning", animation.spinning());
        tag.putLong("SpinStart", animation.startTick());
        tag.putInt("Duration", animation.duration());
        tag.putLong("AnimationSeed", animation.seed());
        tag.putIntArray("Previous", new int[]{animation.previous().left(), animation.previous().center(), animation.previous().right()});
        tag.putIntArray("Target", new int[]{animation.target().left(), animation.target().center(), animation.target().right()});
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        readVisuals(tag, animation);
    }
    static void readVisuals(CompoundTag tag, CasinoMachineAnimationState animation) {
        animation.restore(tag.getBoolean("Spinning"), tag.getLong("SpinStart"), tag.getInt("Duration"),
                tag.getLong("AnimationSeed"), readReels(tag, "Previous"), readReels(tag, "Target"));
    }
    private static CasinoReelResult readReels(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length != 3) return CasinoReelResult.IDLE;
        for (int value : values) if (value < 0 || value > 7) return CasinoReelResult.IDLE;
        return new CasinoReelResult(values[0], values[1], values[2]);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeVisuals(tag);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(0.5, 1.5, 0.5); }
}
