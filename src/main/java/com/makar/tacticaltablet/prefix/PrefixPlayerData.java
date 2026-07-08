package com.makar.tacticaltablet.prefix;

import java.util.UUID;

public record PrefixPlayerData(
        UUID uuid,
        String lastKnownName,
        PrefixRole role,
        long expiresAt
) {

    public boolean expired() {
        return expiresAt > 0L && System.currentTimeMillis() >= expiresAt;
    }

    public PrefixRole effectiveRole() {
        if (expired()) return PrefixRole.NONE;
        return role == null ? PrefixRole.NONE : role;
    }

    public PrefixPlayerData withRole(PrefixRole role, long expiresAt) {
        return new PrefixPlayerData(uuid, lastKnownName, role == null ? PrefixRole.NONE : role, Math.max(0L, expiresAt));
    }

    public PrefixPlayerData withLastKnownName(String name) {
        return new PrefixPlayerData(uuid, name == null ? "" : name, role, expiresAt);
    }
}
