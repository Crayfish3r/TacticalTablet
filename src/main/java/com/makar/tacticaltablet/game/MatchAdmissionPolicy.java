package com.makar.tacticaltablet.game;

final class MatchAdmissionPolicy {
    static final int LATE_JOIN_PHASE = 3;

    private MatchAdmissionPolicy() {
    }

    static MatchAdmissionStatus classify(
            boolean activeMatch,
            boolean alreadyAdmitted,
            int currentZonePhase
    ) {
        if (!activeMatch || currentZonePhase < 1) {
            return MatchAdmissionStatus.NO_ACTIVE_MATCH;
        }
        if (alreadyAdmitted || currentZonePhase < LATE_JOIN_PHASE) {
            return MatchAdmissionStatus.ADMITTED;
        }
        return MatchAdmissionStatus.LATE_SPECTATOR;
    }
}
