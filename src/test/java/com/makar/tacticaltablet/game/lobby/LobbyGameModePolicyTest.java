package com.makar.tacticaltablet.game.lobby;

import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyGameModePolicyTest {
    @Test
    void moderatorRemainsSpectator() {
        assertEquals(GameType.SPECTATOR,
                LobbyGameModePolicy.target(GameType.SPECTATOR, true, false, false));
    }

    @Test
    void legitimateAndEliminatedSpectatorsRemainSpectator() {
        assertEquals(GameType.SPECTATOR,
                LobbyGameModePolicy.target(GameType.SPECTATOR, false, false, false));
        assertEquals(GameType.SPECTATOR,
                LobbyGameModePolicy.target(GameType.SURVIVAL, false, true, false));
    }

    @Test
    void ordinaryWaitingPlayerUsesSurvival() {
        assertEquals(GameType.SURVIVAL,
                LobbyGameModePolicy.target(GameType.ADVENTURE, false, false, false));
        assertEquals(GameType.SURVIVAL,
                LobbyGameModePolicy.target(GameType.SURVIVAL, false, false, false));
    }

    @Test
    void temporaryDeathScreenSpectatorReturnsToSurvivalForRespawn() {
        assertEquals(GameType.SURVIVAL,
                LobbyGameModePolicy.target(GameType.SPECTATOR, false, false, true));
        assertEquals(GameType.SPECTATOR,
                LobbyGameModePolicy.target(GameType.SPECTATOR, true, false, true));
        assertEquals(GameType.SPECTATOR,
                LobbyGameModePolicy.target(GameType.SPECTATOR, false, true, true));
    }
}
