package com.makar.tacticaltablet.game.respawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnusedLifeRewardTest {
    @Test
    void rewardsTenCoinsAndTenXpPerCompensatedLife() {
        assertEquals(new RespawnControlManager.UnusedLifeReward(2, 20, 20),
                RespawnControlManager.calculateUnusedLifeReward(2, 4));
    }

    @Test
    void preservesExistingCompensatedLifeCap() {
        assertEquals(new RespawnControlManager.UnusedLifeReward(3, 30, 30),
                RespawnControlManager.calculateUnusedLifeReward(99, 4));
        assertEquals(new RespawnControlManager.UnusedLifeReward(0, 0, 0),
                RespawnControlManager.calculateUnusedLifeReward(-1, 4));
    }
}
