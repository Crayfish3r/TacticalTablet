package com.makar.tacticaltablet.game.lobby;

/** Pure decision policy for the destructive part of lobby bootstrap. */
public final class LobbyBootstrapPolicy {
    public enum Action {
        SKIP_ALREADY_BOOTSTRAPPED,
        MARK_EXISTING_CONTENT,
        PLACE_STRUCTURE,
        FAIL_MISSING_TEMPLATE
    }

    private LobbyBootstrapPolicy() {
    }

    public static Action decide(
            int savedVersion,
            int currentVersion,
            boolean targetVolumeHasContent,
            boolean templateAvailable
    ) {
        if (savedVersion >= currentVersion) return Action.SKIP_ALREADY_BOOTSTRAPPED;
        if (targetVolumeHasContent) return Action.MARK_EXISTING_CONTENT;
        return templateAvailable ? Action.PLACE_STRUCTURE : Action.FAIL_MISSING_TEMPLATE;
    }
}
