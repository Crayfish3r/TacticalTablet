package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lifecycle.LegacyMatchStateMapper;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateManagerCharacterizationTest {
    @Test
    void publicApiInventoryRemainsVisibleWithoutInitializingMinecraftStatics() throws ClassNotFoundException {
        Class<?> type = Class.forName("com.makar.tacticaltablet.game.GameStateManager", false,
                GameStateManagerCharacterizationTest.class.getClassLoader());

        Set<String> publicMethodNames = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertTrue(publicMethodNames.containsAll(Set.of(
                "getGameState",
                "setGameState",
                "isRunning",
                "getMatchPhase",
                "getCurrentMode",
                "getLivesPerPlayer",
                "isTabletAvailableInLobby",
                "isInLobby",
                "getLobbyLevel",
                "getOverworld",
                "onlinePlayers",
                "playingPlayers",
                "onServerTick",
                "checkForMatchEnd",
                "startGame",
                "endGame",
                "resetRuntime",
                "forceStopMatch",
                "validateRuntimeRequirements",
                "forceStartVoting",
                "forceStartMapVoting",
                "forceStartTeamSelect",
                "forceStartClanWar"
        )));
    }

    @Test
    void legacyRuntimeStateMapsToShadowLifecycleState() {
        assertEquals(MatchState.IDLE,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.WAITING));
        assertEquals(MatchState.IDLE,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.VOTING));
        assertEquals(MatchState.IDLE,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.TEAM_SELECT));
        assertEquals(MatchState.IDLE,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.MAP_VOTING));
        assertEquals(MatchState.IDLE,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.RESTARTING));
        assertEquals(MatchState.STARTING,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.STARTING));
        assertEquals(MatchState.RUNNING,
                LegacyMatchStateMapper.fromLegacyState(1, 1, MatchPhase.RUNNING));
        assertEquals(MatchState.ENDING,
                LegacyMatchStateMapper.fromLegacyState(1, 0, MatchPhase.POST_GAME));
    }
}
