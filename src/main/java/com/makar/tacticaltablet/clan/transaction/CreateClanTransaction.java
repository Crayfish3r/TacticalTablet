package com.makar.tacticaltablet.clan.transaction;

import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record CreateClanTransaction(
        int schemaVersion,
        UUID transactionId,
        String operationType,
        UUID playerUuid,
        String playerName,
        String clanId,
        int expectedOldBalance,
        int newBalance,
        ClanCreationPayload clanPayload,
        String payloadHash,
        TransactionState state,
        long createdAt,
        long updatedAt,
        String diagnostic
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String OPERATION_TYPE = "CREATE_CLAN";

    public CreateClanTransaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(clanPayload, "clanPayload");
        Objects.requireNonNull(state, "state");
        String calculatedPayloadHash = payloadHash(clanPayload);
        if (payloadHash == null || payloadHash.isBlank()) {
            payloadHash = calculatedPayloadHash;
        } else if (!payloadHash.equals(calculatedPayloadHash)) {
            throw new IllegalArgumentException("Journal payload hash does not match clan payload");
        }
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public CreateClanTransaction withState(TransactionState newState, long now, String newDiagnostic) {
        return new CreateClanTransaction(
                schemaVersion,
                transactionId,
                operationType,
                playerUuid,
                playerName,
                clanId,
                expectedOldBalance,
                newBalance,
                clanPayload,
                payloadHash,
                newState,
                createdAt,
                now,
                newDiagnostic
        );
    }

    public static String payloadHash(ClanCreationPayload payload) {
        String canonical = payload.clanId() + '\n' + payload.name() + '\n' + payload.tag() + '\n'
                + payload.color() + '\n' + payload.ownerUuid() + '\n' + payload.ownerName();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
