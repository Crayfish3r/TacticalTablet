package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.clan.ClanManager;
import net.minecraft.server.level.ServerPlayer;

final class ClanWarWinnerLabel {
    private ClanWarWinnerLabel() {
    }

    static String resolve(ServerPlayer winner) {
        if (winner == null) return "Нет победителя";
        String clanName = ClanManager.getClanNameForPlayer(winner);
        return clanName.isBlank() ? winner.getName().getString() : clanName;
    }
}
