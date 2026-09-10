package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.casino.net.CasinoAnimationPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CasinoMachineTest {
    @Test void oneMachineAllowsOneSpinAndCloseDoesNotCancelCommittedAnimation() {
        var machine = new CasinoMachineAnimationState();
        assertTrue(machine.reserve(100));
        assertFalse(machine.reserve(100));
        machine.start(100, 123, new CasinoReelResult(5, 5, 5));
        machine.release();
        assertTrue(machine.busy());
        assertFalse(machine.reserve(179));
        assertTrue(machine.reserve(180));
    }
    @Test void differentMachinesRunIndependently() {
        var a = new CasinoMachineAnimationState();
        var b = new CasinoMachineAnimationState();
        assertTrue(a.reserve(100)); a.start(100, 1, new CasinoReelResult(0, 0, 0));
        assertTrue(b.reserve(100)); b.start(100, 2, new CasinoReelResult(6, 6, 6));
        assertNotEquals(a.target(), b.target());
        assertTrue(a.spinning() && b.spinning());
    }
    @Test void failedPaymentReleasesWithoutWinAnimation() {
        var machine = new CasinoMachineAnimationState();
        assertTrue(machine.reserve(0)); machine.release();
        assertFalse(machine.busy()); assertFalse(machine.spinning());
        assertEquals(CasinoReelResult.IDLE, machine.target());
        assertTrue(machine.reserve(1));
    }
    @Test void unreservedAnimationCannotStart() {
        assertThrows(IllegalStateException.class, () -> new CasinoMachineAnimationState().start(0, 0, CasinoReelResult.IDLE));
    }
    @Test void nbtRoundTripRetainsVisualsAndExpiresAfterChunkReload() {
        var source = new CasinoMachineAnimationState(); source.reserve(100);
        source.start(100, 72, new CasinoReelResult(7, 7, 7));
        var tag = new CompoundTag(); CasinoMachineBlockEntity.writeVisuals(tag, source);
        assertEquals(java.util.Set.of("Spinning", "SpinStart", "Duration", "AnimationSeed", "Previous", "Target"), tag.getAllKeys());
        var loaded = new CasinoMachineAnimationState(); CasinoMachineBlockEntity.readVisuals(tag, loaded);
        assertEquals(source.target(), loaded.target()); assertEquals(source.seed(), loaded.seed());
        assertFalse(loaded.reserve(150)); assertTrue(loaded.finish(200)); assertFalse(loaded.busy());
        assertEquals(loaded.target(), loaded.previous());
    }
    @Test void missingOrMalformedTemplateDataInitializesSafeIdleVisuals() {
        var tag = new CompoundTag(); tag.putIntArray("Target", new int[]{8, 0, 0});
        var state = new CasinoMachineAnimationState(); CasinoMachineBlockEntity.readVisuals(tag, state);
        assertEquals(CasinoReelResult.IDLE, state.target()); assertFalse(state.busy());
    }
    @Test void futureTemplateClockCannotRemainBusy() {
        var state = new CasinoMachineAnimationState();
        state.restore(true, 500000, 80, 1, CasinoReelResult.IDLE, new CasinoReelResult(6, 6, 6));
        assertTrue(state.finish(0)); assertFalse(state.busy());
    }
    @Test void reelsStartSeparatelyAndStopOnEveryTarget() {
        for (int reel = 0; reel < 3; reel++) for (int from = 0; from < 8; from++) for (int to = 0; to < 8; to++) {
            assertEquals(-from * 45.0, CasinoAnimation.reelDegrees(reel, from, to, 4 + reel * 2, 80, 29));
            double end = CasinoAnimation.reelDegrees(reel, from, to, 60 + reel * 9, 80, 29);
            assertEquals(to, Math.floorMod((int) Math.round(-end / 45), 8));
            assertEquals(end, CasinoAnimation.reelDegrees(reel, from, to, 1000, 80, 29));
        }
        assertNotEquals(0, CasinoAnimation.reelDegrees(0, 0, 1, 5, 80, 0));
        assertEquals(0, CasinoAnimation.reelDegrees(1, 0, 1, 5, 80, 0));
    }
    @Test void leverReturnsAndReelsDecelerate() {
        assertEquals(0, CasinoAnimation.leverDegrees(0, 80), 0.0001); assertEquals(-55, CasinoAnimation.leverDegrees(6, 80));
        assertEquals(0, CasinoAnimation.leverDegrees(14, 80));
        double early = Math.abs(CasinoAnimation.reelDegrees(0, 0, 5, 11, 80, 0) - CasinoAnimation.reelDegrees(0, 0, 5, 10, 80, 0));
        double late = Math.abs(CasinoAnimation.reelDegrees(0, 0, 5, 59, 80, 0) - CasinoAnimation.reelDegrees(0, 0, 5, 58, 80, 0));
        assertTrue(early > late && late > 0);
    }
    @Test void rewardMappingIsDeterministicAndLossNeverShowsTriple() {
        for (long seed : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var loss = CasinoReelResult.fromReward(CasinoRewardKind.COINS, 0, seed);
            assertNotEquals(loss.left(), loss.center()); assertNotEquals(loss.center(), loss.right());
            assertEquals(loss, CasinoReelResult.fromReward(CasinoRewardKind.COINS, 0, seed));
            assertEquals(new CasinoReelResult(0, 0, 0), CasinoReelResult.fromReward(CasinoRewardKind.COINS, 100, seed));
            assertEquals(new CasinoReelResult(5, 5, 5), CasinoReelResult.fromReward(CasinoRewardKind.SHOP_CLASS, 500, seed));
            assertEquals(new CasinoReelResult(6, 6, 6), CasinoReelResult.fromReward(CasinoRewardKind.VIP_CLASS, 0, seed));
            assertEquals(new CasinoReelResult(7, 7, 7), CasinoReelResult.fromReward(CasinoRewardKind.SAD_TROMBONE, 0, seed));
        }
        assertThrows(IllegalArgumentException.class, () -> new CasinoReelResult(0, 8, 0));
    }
    @Test void accessRejectsOfflineWrongDimensionMissingReplacementDistanceOrWrongMenu() {
        assertTrue(CasinoMachineAccess.allows(true, true, true, true, 64, true));
        assertFalse(CasinoMachineAccess.allows(false, true, true, true, 1, true));
        assertFalse(CasinoMachineAccess.allows(true, false, true, true, 1, true));
        assertFalse(CasinoMachineAccess.allows(true, true, false, true, 1, true));
        assertFalse(CasinoMachineAccess.allows(true, true, true, false, 1, true));
        assertFalse(CasinoMachineAccess.allows(true, true, true, true, 64.01, true));
        assertFalse(CasinoMachineAccess.allows(true, true, true, true, Double.NaN, true));
        assertFalse(CasinoMachineAccess.allows(true, true, true, true, 1, false));
    }
    @Test void menuAnchorRejectsOtherPositionDimensionAndSessionButSupportsSpectator() {
        UUID id = UUID.randomUUID(); BlockPos pos = new BlockPos(1, 65, 2);
        assertTrue(CasinoMachineAccess.matchesAnchor(id, "minecraft:overworld", pos, id, "minecraft:overworld", pos));
        assertFalse(CasinoMachineAccess.matchesAnchor(id, "minecraft:overworld", pos, id, "minecraft:overworld", pos.above()));
        assertFalse(CasinoMachineAccess.matchesAnchor(id, "minecraft:overworld", pos, id, "minecraft:the_nether", pos));
        assertFalse(CasinoMachineAccess.matchesAnchor(id, "minecraft:overworld", pos, UUID.randomUUID(), "minecraft:overworld", pos));
        assertTrue(CasinoMachineAccess.matchesAnchor(id, null, null, id, null, null));
    }
    @Test void animationPacketRoundTripPreservesClockAndReels() {
        var packet = new CasinoAnimationPacket(UUID.randomUUID(), UUID.randomUUID(), 123, 80, -456,
                CasinoReelResult.IDLE, new CasinoReelResult(6, 6, 6));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buf); assertEquals(packet, new CasinoAnimationPacket(buf)); assertEquals(0, buf.readableBytes()); }
        finally { buf.release(); }
    }
}
