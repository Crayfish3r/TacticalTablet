package com.makar.tacticaltablet.integration.moderndamage;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernDamageBalanceSchemaTest {
    @Test
    void defaultsAreACompleteValidAllowList() {
        double[] defaults = ModernDamageBalanceSchema.defaults();
        assertEquals(68, ModernDamageBalanceSchema.fields().size());
        assertEquals(68, defaults.length);
        assertTrue(ModernDamageBalanceSchema.validate(ModernDamageBalanceSchema.toMap(defaults)).valid());
        assertTrue(ModernDamageBalanceSchema.fields().stream()
                .noneMatch(field -> field.key().contains("damageModel")
                        || field.key().contains("MaxHealth")
                        || field.key().contains("PartHealthRatio")
                        || field.key().contains("armor_properties")
                        || field.key().contains("entity_config")
                        || field.key().contains("hitbox")));
    }

    @Test
    void rejectsMissingUnknownNonFiniteAndOutOfRangeValues() {
        Map<Integer, Double> values = valid();
        values.remove(0);
        assertEquals(ModernDamageBalanceSchema.ValidationError.WRONG_FIELD_COUNT,
                ModernDamageBalanceSchema.validate(values).error());

        values = valid();
        values.remove(0);
        values.put(9999, 1.0D);
        assertEquals(ModernDamageBalanceSchema.ValidationError.UNKNOWN_FIELD,
                ModernDamageBalanceSchema.validate(values).error());

        values = valid();
        values.put(0, Double.NaN);
        assertEquals(ModernDamageBalanceSchema.ValidationError.NON_FINITE_VALUE,
                ModernDamageBalanceSchema.validate(values).error());
        values.put(0, Double.POSITIVE_INFINITY);
        assertEquals(ModernDamageBalanceSchema.ValidationError.NON_FINITE_VALUE,
                ModernDamageBalanceSchema.validate(values).error());

        values = valid();
        values.put(0, 0.0D);
        assertEquals(ModernDamageBalanceSchema.ValidationError.OUT_OF_RANGE,
                ModernDamageBalanceSchema.validate(values).error());
    }

    @Test
    void rejectsFractionalIntegersZeroDurationsAndInvalidDependencies() {
        Map<Integer, Double> values = valid();
        values.put(ModernDamageBalanceSchema.byKey("staminaRegenDelayTicks").id(), 1.5D);
        assertEquals(ModernDamageBalanceSchema.ValidationError.NON_INTEGER_VALUE,
                ModernDamageBalanceSchema.validate(values).error());

        values = valid();
        int duration = ModernDamageBalanceSchema.byKey(ModernDamageBalanceSchema.injuryKey(
                "head", "moderndamage:dizziness", ModernDamageBalanceSchema.Metric.DURATION)).id();
        values.put(duration, 0.0D);
        assertEquals(ModernDamageBalanceSchema.ValidationError.INVALID_DURATION,
                ModernDamageBalanceSchema.validate(values).error());

        values = valid();
        values.put(ModernDamageBalanceSchema.byKey("majorBleedingIntervalTicks").id(), 101.0D);
        assertEquals(ModernDamageBalanceSchema.ValidationError.INVALID_BLEEDING_ORDER,
                ModernDamageBalanceSchema.validate(values).error());

        values = valid();
        int major = ModernDamageBalanceSchema.byKey(ModernDamageBalanceSchema.injuryKey(
                "left_arm", "moderndamage:major_bleeding", ModernDamageBalanceSchema.Metric.THRESHOLD)).id();
        int minor = ModernDamageBalanceSchema.byKey(ModernDamageBalanceSchema.injuryKey(
                "left_arm", "moderndamage:minor_bleeding", ModernDamageBalanceSchema.Metric.THRESHOLD)).id();
        values.put(major, 5.0D);
        values.put(minor, 6.0D);
        assertEquals(ModernDamageBalanceSchema.ValidationError.INVALID_THRESHOLD_ORDER,
                ModernDamageBalanceSchema.validate(values).error());
    }

    @Test
    void onlyFiniteValidatedValuesCanReachTheAdapter() {
        Map<Integer, Double> values = valid();
        ModernDamageBalanceSchema.ValidationResult result = ModernDamageBalanceSchema.validate(values);
        assertTrue(result.valid());
        for (double value : result.values()) assertTrue(Double.isFinite(value));
        assertFalse(values.isEmpty());
    }

    private static Map<Integer, Double> valid() {
        return new HashMap<>(ModernDamageBalanceSchema.toMap(ModernDamageBalanceSchema.defaults()));
    }
}
