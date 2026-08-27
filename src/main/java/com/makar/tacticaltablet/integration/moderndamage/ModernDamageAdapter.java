package com.makar.tacticaltablet.integration.moderndamage;

interface ModernDamageAdapter {
    ModernDamageBalanceSnapshot readBalance(long revision);

    void applyBalance(double[] values) throws Exception;
}
