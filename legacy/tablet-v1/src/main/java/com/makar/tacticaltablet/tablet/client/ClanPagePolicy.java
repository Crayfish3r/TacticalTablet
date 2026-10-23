package com.makar.tacticaltablet.tablet.client;

/** Pure client presentation policy; the server remains authoritative for every clan mutation. */
final class ClanPagePolicy {
    private ClanPagePolicy() {
    }

    static Permissions permissions(boolean owner, boolean member, boolean pending) {
        return new Permissions(
                !owner && !member && !pending,
                member && !owner,
                owner,
                owner,
                owner,
                owner
        );
    }

    record Permissions(
            boolean canRequestJoin,
            boolean canLeave,
            boolean canChangeColor,
            boolean canDisband,
            boolean canReviewRequests,
            boolean canKickMembers
    ) {
    }
}
