package com.makar.tacticaltablet.camouflage;

public final class CamouflageDeploymentEligibility {
    private CamouflageDeploymentEligibility() { }

    public static boolean canDeliver(boolean participating, boolean ownsUpgrade,
                                     boolean hasPreset, boolean presetExists) {
        return participating && ownsUpgrade && hasPreset && presetExists;
    }
}
