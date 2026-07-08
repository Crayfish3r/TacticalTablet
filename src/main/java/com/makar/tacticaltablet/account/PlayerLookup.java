package com.makar.tacticaltablet.account;

import com.makar.tacticaltablet.prefix.PrefixManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class PlayerLookup {

    public static final String NOT_FOUND_MESSAGE = "Игрок не найден. Он должен хотя бы раз зайти на сервер, либо используйте UUID.";

    private PlayerLookup() {
    }

    public static Optional<PlayerIdentity> resolve(MinecraftServer server, String input) {
        if (server == null || input == null || input.isBlank()) {
            return Optional.empty();
        }

        String value = input.trim();
        UUID uuid = parseUuid(value);
        if (uuid != null) {
            return Optional.of(resolveUuid(server, uuid));
        }

        ServerPlayer online = findOnlineByName(server, value);
        if (online != null) {
            return Optional.of(new PlayerIdentity(
                    online.getUUID(),
                    online.getGameProfile().getName(),
                    true
            ));
        }

        Optional<PlayerProgressManager.KnownPlayer> knownProgress = PlayerProgressManager.findKnownPlayerByName(server, value);
        if (knownProgress.isPresent()) {
            PlayerProgressManager.KnownPlayer known = knownProgress.get();
            return Optional.of(new PlayerIdentity(known.uuid(), known.name(), false));
        }

        Optional<PrefixManager.KnownPlayer> knownPrefix = PrefixManager.findKnownPlayerByName(value);
        if (knownPrefix.isPresent()) {
            PrefixManager.KnownPlayer known = knownPrefix.get();
            return Optional.of(new PlayerIdentity(known.uuid(), known.name(), false));
        }

        return Optional.empty();
    }

    public static ServerPlayer getOnline(MinecraftServer server, PlayerIdentity identity) {
        if (server == null || identity == null || identity.uuid() == null) return null;
        return server.getPlayerList().getPlayer(identity.uuid());
    }

    private static PlayerIdentity resolveUuid(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return new PlayerIdentity(uuid, online.getGameProfile().getName(), true);
        }

        Optional<GameProfile> cachedProfile = server.getProfileCache().get(uuid);
        if (cachedProfile.isPresent()) {
            GameProfile profile = cachedProfile.get();
            return new PlayerIdentity(uuid, profile.getName() == null ? uuid.toString() : profile.getName(), false);
        }

        Optional<PlayerProgressManager.KnownPlayer> knownProgress = PlayerProgressManager.findKnownPlayerByUuid(server, uuid);
        if (knownProgress.isPresent()) {
            PlayerProgressManager.KnownPlayer known = knownProgress.get();
            return new PlayerIdentity(uuid, known.name(), false);
        }

        String prefixName = PrefixManager.getKnownName(uuid);
        return new PlayerIdentity(uuid, prefixName == null || prefixName.isBlank() ? uuid.toString() : prefixName, false);
    }

    private static ServerPlayer findOnlineByName(MinecraftServer server, String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().toLowerCase(Locale.ROOT).equals(normalized)) {
                return player;
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
