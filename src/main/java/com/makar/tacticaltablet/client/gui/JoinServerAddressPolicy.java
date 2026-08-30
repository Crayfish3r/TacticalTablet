package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.Optional;

final class JoinServerAddressPolicy {

    static final int MAX_LENGTH = 255;

    private JoinServerAddressPolicy() {
    }

    static Optional<String> normalize(String candidate) {
        if (candidate == null) return Optional.empty();
        String normalized = candidate.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) return Optional.empty();
        if (normalized.contains("://") || normalized.contains("/") || normalized.contains("\\")) {
            return Optional.empty();
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) return Optional.empty();
        }
        return ServerAddress.isValidAddress(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    static String configuredOrDefault() {
        return normalize(TacticalTabletClientConfig.getJoinServerAddress())
                .orElse(TacticalTabletClientConfig.DEFAULT_JOIN_SERVER_ADDRESS);
    }
}
