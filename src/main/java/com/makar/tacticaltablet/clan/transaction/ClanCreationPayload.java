package com.makar.tacticaltablet.clan.transaction;

import java.util.Objects;
import java.util.UUID;

/** Immutable clan data used both by the journal and by idempotent repository writes. */
public record ClanCreationPayload(
        String clanId,
        String name,
        String tag,
        int color,
        UUID ownerUuid,
        String ownerName
) {
    public ClanCreationPayload {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerName, "ownerName");
    }
}
