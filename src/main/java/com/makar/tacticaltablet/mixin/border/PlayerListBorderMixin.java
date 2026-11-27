package com.makar.tacticaltablet.mixin.border;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.ServerRules;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
abstract class PlayerListBorderMixin {
    @Shadow public abstract MinecraftServer getServer();
    @Shadow public abstract List<ServerPlayer> getPlayers();

    @Redirect(method = "sendLevelInfo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"),
            require = 1)
    private ServerLevel tacticaltablet$initialBorderLevel(MinecraftServer server, ServerPlayer player, ServerLevel destination) {
        return ServerRules.enabled(server) && destination.dimension().equals(GameStateManager.LOBBY_DIMENSION)
                ? destination : server.overworld();
    }

    @Inject(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void tacticaltablet$keepMatchBorderOutOfLobby(Packet<?> packet, CallbackInfo callback) {
        if (!(packet instanceof ClientboundSetBorderCenterPacket
                || packet instanceof ClientboundSetBorderSizePacket
                || packet instanceof ClientboundSetBorderLerpSizePacket
                || packet instanceof ClientboundSetBorderWarningDelayPacket
                || packet instanceof ClientboundSetBorderWarningDistancePacket)
                || !ServerRules.enabled(getServer())) return;

        for (ServerPlayer player : getPlayers()) {
            if (!GameStateManager.isInLobby(player)) player.connection.send(packet);
        }
        callback.cancel();
    }
}
