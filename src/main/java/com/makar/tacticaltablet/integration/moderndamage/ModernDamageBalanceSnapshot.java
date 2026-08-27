package com.makar.tacticaltablet.integration.moderndamage;

import java.util.Arrays;

public record ModernDamageBalanceSnapshot(long revision, double[] values) {
    public ModernDamageBalanceSnapshot {
        values = values == null ? new double[0] : Arrays.copyOf(values, values.length);
    }

    @Override
    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
