package com.makar.tacticaltablet.game.set;

import java.util.UUID;

public record SetPlacement(
        int place, UUID playerId, String playerName, int totalScore, int wins, int kills,
        int assists, double damage, int deaths) {
}
