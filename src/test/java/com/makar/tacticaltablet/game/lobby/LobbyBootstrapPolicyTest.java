package com.makar.tacticaltablet.game.lobby;

import org.junit.jupiter.api.Test;

import static com.makar.tacticaltablet.game.lobby.LobbyBootstrapPolicy.Action.FAIL_MISSING_TEMPLATE;
import static com.makar.tacticaltablet.game.lobby.LobbyBootstrapPolicy.Action.MARK_EXISTING_CONTENT;
import static com.makar.tacticaltablet.game.lobby.LobbyBootstrapPolicy.Action.PLACE_STRUCTURE;
import static com.makar.tacticaltablet.game.lobby.LobbyBootstrapPolicy.Action.SKIP_ALREADY_BOOTSTRAPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyBootstrapPolicyTest {
    @Test
    void committedBootstrapNeverPlacesAgain() {
        assertEquals(SKIP_ALREADY_BOOTSTRAPPED,
                LobbyBootstrapPolicy.decide(1, 1, false, true));
        assertEquals(SKIP_ALREADY_BOOTSTRAPPED,
                LobbyBootstrapPolicy.decide(2, 1, false, true));
    }

    @Test
    void oldWorldContentOnlyReceivesMigrationMarker() {
        assertEquals(MARK_EXISTING_CONTENT,
                LobbyBootstrapPolicy.decide(0, 1, true, true));
        assertEquals(MARK_EXISTING_CONTENT,
                LobbyBootstrapPolicy.decide(0, 1, true, false));
    }

    @Test
    void emptyFreshWorldPlacesOnlyWhenTemplateExists() {
        assertEquals(PLACE_STRUCTURE,
                LobbyBootstrapPolicy.decide(0, 1, false, true));
        assertEquals(FAIL_MISSING_TEMPLATE,
                LobbyBootstrapPolicy.decide(0, 1, false, false));
    }
}
