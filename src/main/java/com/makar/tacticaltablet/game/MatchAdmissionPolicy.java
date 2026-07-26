package com.makar.tacticaltablet.game;

final class MatchAdmissionPolicy {
    private MatchAdmissionPolicy() {
    }

    static MatchAdmissionStatus classify(
            boolean activeMatch,
            boolean alreadyAdmitted,
            boolean admissionWindowOpen
    ) {
        if (!activeMatch) {
            return MatchAdmissionStatus.NO_ACTIVE_MATCH;
        }
        if (alreadyAdmitted || admissionWindowOpen) {
            return MatchAdmissionStatus.ADMITTED;
        }
        return MatchAdmissionStatus.LATE_SPECTATOR;
    }
}
