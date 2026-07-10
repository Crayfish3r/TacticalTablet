package com.makar.tacticaltablet.game.lifecycle.integration;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchStartRequest;
import com.makar.tacticaltablet.game.lifecycle.MatchStartStep;

import net.minecraft.server.MinecraftServer;

public interface MatchStartGateway {
    MatchStartPreflightResult preflight(MinecraftServer server, MatchLifecycleSnapshot lifecycleSnapshot);

    MatchStartRequest createRequest(MinecraftServer server);

    void apply(MinecraftServer server, MatchStartStep step) throws Exception;

    void rollback(MinecraftServer server, MatchStartStep step) throws Exception;

    void postCommit(MinecraftServer server) throws Exception;
}
