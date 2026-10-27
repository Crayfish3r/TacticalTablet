package com.makar.tacticaltablet.progression;

import java.util.UUID;

/** Identities and immutable results for the durable tablet path; legacy actions keep their contract. */
public final class TabletProgressOperation {
    private TabletProgressOperation() { }
    public enum Kind { FETCH, PURCHASE_CLASS, UNLOCK_BASE, UPGRADE_TIER }
    public enum Status { ACCEPTED, SAVED, REJECTED, SAVE_FAILED }
    public record Identity(UUID session, long requestId, Kind kind, String classKey, int targetTier) { }
    public record Result(Identity identity, Status status, long revision, String message) { }
    public record PersonalClass(boolean owned, int tier, int xp) { }
}
