package com.makar.tacticaltablet.progression;

/** Pure policy guarding eviction of an offline profile from the authoritative in-memory cache. */
final class ProgressUnloadPolicy {
    private ProgressUnloadPolicy() {
    }

    static boolean canEvict(
            boolean playerOnline,
            boolean dirty,
            Long submittedRevision,
            long completedRevision
    ) {
        return !playerOnline
                && !dirty
                && submittedRevision != null
                && completedRevision >= submittedRevision;
    }
}
