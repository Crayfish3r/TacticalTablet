package com.makar.tacticaltablet.game;
import net.minecraft.server.MinecraftServer;
/** Match restrictions apply only to dedicated servers. */
public final class ServerRules {
    private ServerRules() { }
    public static boolean enabled(MinecraftServer server) {
        return server != null && server.isDedicatedServer();
    }
}
