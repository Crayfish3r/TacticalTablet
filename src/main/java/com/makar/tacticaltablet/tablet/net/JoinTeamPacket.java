package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class JoinTeamPacket {

    private final TeamId team;

    public JoinTeamPacket(TeamId team) {
        this.team = team == null ? TeamId.ALFA : team;
    }

    public JoinTeamPacket(FriendlyByteBuf buf) {
        this.team = TeamId.byId(buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(team.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (GameStateManager.getMatchPhase() != MatchPhase.TEAM_SELECT) {
                LobbyManager.sync(player);
                return;
            }

            TeamMatchManager.joinTeam(player, team, GameStateManager.getCurrentMode());
            LobbyManager.sync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
