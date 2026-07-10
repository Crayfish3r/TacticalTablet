package com.makar.tacticaltablet.moderation;

import java.util.UUID;

public final class PunishmentRecord {

    private final PunishmentType type;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID issuerUuid;
    private final String issuerName;
    private final long createdAt;
    private final long expiresAt;
    private final String reason;

    public PunishmentRecord(
            PunishmentType type,
            UUID targetUuid,
            String targetName,
            UUID issuerUuid,
            String issuerName,
            long createdAt,
            long expiresAt,
            String reason
    ) {
        this.type = type == null ? PunishmentType.MUTE : type;
        this.targetUuid = targetUuid;
        this.targetName = targetName == null ? "" : targetName;
        this.issuerUuid = issuerUuid;
        this.issuerName = issuerName == null ? "" : issuerName;
        this.createdAt = Math.max(0L, createdAt);
        this.expiresAt = Math.max(0L, expiresAt);
        this.reason = reason == null ? "" : reason;
    }

    public boolean expired() {
        return expiresAt > 0L && System.currentTimeMillis() >= expiresAt;
    }

    public PunishmentType type() {
        return type;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public UUID issuerUuid() {
        return issuerUuid;
    }

    public String issuerName() {
        return issuerName;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public String reason() {
        return reason;
    }
}
