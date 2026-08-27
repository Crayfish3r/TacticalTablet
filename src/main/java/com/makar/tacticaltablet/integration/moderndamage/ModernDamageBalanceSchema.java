package com.makar.tacticaltablet.integration.moderndamage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure allow-list and validation rules for the MDC 1.0.32 hot-editable surface. */
public final class ModernDamageBalanceSchema {
    public enum Category {
        BLEEDING("screen.tacticaltablet.mdc.category.bleeding"),
        ARM_STAMINA("screen.tacticaltablet.mdc.category.arm_stamina"),
        LEG_STAMINA("screen.tacticaltablet.mdc.category.leg_stamina"),
        INJURY_HEAD_TORSO("screen.tacticaltablet.mdc.category.injury_head_torso"),
        INJURY_ARMS("screen.tacticaltablet.mdc.category.injury_arms"),
        INJURY_LEGS("screen.tacticaltablet.mdc.category.injury_legs");

        private final String translationKey;

        Category(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum Metric {
        VALUE(""),
        THRESHOLD("screen.tacticaltablet.mdc.metric.threshold"),
        CHANCE("screen.tacticaltablet.mdc.metric.chance"),
        DURATION("screen.tacticaltablet.mdc.metric.duration");

        private final String translationKey;

        Metric(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record Field(int id, String key, String labelKey, String descriptionKey,
                        Category category, Metric metric, double minimum, double maximum,
                        double defaultValue, boolean integer) {
    }

    public record InjuryProfile(String partKey, String effectId, String labelKey,
                                Category category, double defaultThreshold,
                                double defaultChance, int defaultDuration) {
        public String prefix() {
            return "injury." + partKey + "." + effectId + ".";
        }
    }

    public enum ValidationError {
        NONE,
        WRONG_FIELD_COUNT,
        UNKNOWN_FIELD,
        DUPLICATE_FIELD,
        NON_FINITE_VALUE,
        OUT_OF_RANGE,
        NON_INTEGER_VALUE,
        INVALID_DURATION,
        INVALID_THRESHOLD_ORDER,
        INVALID_BLEEDING_ORDER
    }

    public record ValidationResult(ValidationError error, double[] values) {
        public boolean valid() {
            return error == ValidationError.NONE;
        }
    }

    private static final List<Field> FIELDS;
    private static final List<InjuryProfile> INJURY_PROFILES;
    private static final Map<Integer, Field> BY_ID;
    private static final Map<String, Field> BY_KEY;

    static {
        List<Field> fields = new ArrayList<>();
        addScalar(fields, "minorBleedingIntervalTicks", "screen.tacticaltablet.mdc.field.minor_bleeding_interval",
                "screen.tacticaltablet.mdc.desc.bleeding_interval", Category.BLEEDING, 1, 1200, 100, true);
        addScalar(fields, "minorBleedingDamagePerLevel", "screen.tacticaltablet.mdc.field.minor_bleeding_damage",
                "screen.tacticaltablet.mdc.desc.bleeding_damage", Category.BLEEDING, 0, 20, 1, false);
        addScalar(fields, "majorBleedingIntervalTicks", "screen.tacticaltablet.mdc.field.major_bleeding_interval",
                "screen.tacticaltablet.mdc.desc.bleeding_interval", Category.BLEEDING, 1, 1200, 50, true);
        addScalar(fields, "majorBleedingDamagePerLevel", "screen.tacticaltablet.mdc.field.major_bleeding_damage",
                "screen.tacticaltablet.mdc.desc.bleeding_damage", Category.BLEEDING, 0, 20, 1, false);

        addScalar(fields, "meleeAttackCost", "screen.tacticaltablet.mdc.field.melee_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.ARM_STAMINA, 0, 100, 5, false);
        addScalar(fields, "bowDrawCostPerTick", "screen.tacticaltablet.mdc.field.bow_cost",
                "screen.tacticaltablet.mdc.desc.per_tick_cost", Category.ARM_STAMINA, 0, 10, 0.5, false);
        addScalar(fields, "adsCostPerTick", "screen.tacticaltablet.mdc.field.ads_cost",
                "screen.tacticaltablet.mdc.desc.per_tick_cost", Category.ARM_STAMINA, 0, 10, 0.3, false);
        addScalar(fields, "miningCostPerBlock", "screen.tacticaltablet.mdc.field.mining_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.ARM_STAMINA, 0, 100, 30, false);
        addScalar(fields, "staminaRegenDelayTicks", "screen.tacticaltablet.mdc.field.arm_regen_delay",
                "screen.tacticaltablet.mdc.desc.regen_delay", Category.ARM_STAMINA, 0, 1200, 10, true);

        addScalar(fields, "legSprintingCostPerTick", "screen.tacticaltablet.mdc.field.sprint_cost",
                "screen.tacticaltablet.mdc.desc.per_tick_cost", Category.LEG_STAMINA, 0, 10, 0.5, false);
        addScalar(fields, "legSwimmingCostPerTick", "screen.tacticaltablet.mdc.field.swim_cost",
                "screen.tacticaltablet.mdc.desc.per_tick_cost", Category.LEG_STAMINA, 0, 10, 0.3, false);
        addScalar(fields, "legJumpCost", "screen.tacticaltablet.mdc.field.jump_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.LEG_STAMINA, 0, 100, 10, false);
        addScalar(fields, "legCrouchEnterCost", "screen.tacticaltablet.mdc.field.crouch_enter_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.LEG_STAMINA, 0, 50, 2, false);
        addScalar(fields, "legCrouchExitCost", "screen.tacticaltablet.mdc.field.crouch_exit_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.LEG_STAMINA, 0, 50, 1, false);
        addScalar(fields, "legCrawlEnterCost", "screen.tacticaltablet.mdc.field.crawl_enter_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.LEG_STAMINA, 0, 50, 3, false);
        addScalar(fields, "legCrawlExitCost", "screen.tacticaltablet.mdc.field.crawl_exit_cost",
                "screen.tacticaltablet.mdc.desc.action_cost", Category.LEG_STAMINA, 0, 50, 1, false);
        addScalar(fields, "legStaminaRegenDelayTicks", "screen.tacticaltablet.mdc.field.leg_regen_delay",
                "screen.tacticaltablet.mdc.desc.regen_delay", Category.LEG_STAMINA, 0, 1200, 20, true);

        List<InjuryProfile> profiles = List.of(
                injury("head", "moderndamage:dizziness", "screen.tacticaltablet.mdc.injury.head_dizziness",
                        Category.INJURY_HEAD_TORSO, 10, 1, 20),
                injury("chest", "moderndamage:minor_bleeding", "screen.tacticaltablet.mdc.injury.chest_minor_bleeding",
                        Category.INJURY_HEAD_TORSO, 8, 0.3, -1),
                injury("stomach", "minecraft:nausea", "screen.tacticaltablet.mdc.injury.stomach_nausea",
                        Category.INJURY_HEAD_TORSO, 10, 1, 100),
                injury("stomach", "moderndamage:major_bleeding", "screen.tacticaltablet.mdc.injury.stomach_major_bleeding",
                        Category.INJURY_HEAD_TORSO, 12, 0.2, -1),
                injury("stomach", "moderndamage:minor_bleeding", "screen.tacticaltablet.mdc.injury.stomach_minor_bleeding",
                        Category.INJURY_HEAD_TORSO, 8, 0.3, -1),
                injury("left_arm", "moderndamage:left_arm_fracture", "screen.tacticaltablet.mdc.injury.left_arm_fracture",
                        Category.INJURY_ARMS, 8, 1, -1),
                injury("left_arm", "moderndamage:major_bleeding", "screen.tacticaltablet.mdc.injury.left_arm_major_bleeding",
                        Category.INJURY_ARMS, 12, 0.2, -1),
                injury("left_arm", "moderndamage:minor_bleeding", "screen.tacticaltablet.mdc.injury.left_arm_minor_bleeding",
                        Category.INJURY_ARMS, 8, 0.3, -1),
                injury("right_arm", "moderndamage:right_arm_fracture", "screen.tacticaltablet.mdc.injury.right_arm_fracture",
                        Category.INJURY_ARMS, 8, 1, -1),
                injury("right_arm", "moderndamage:major_bleeding", "screen.tacticaltablet.mdc.injury.right_arm_major_bleeding",
                        Category.INJURY_ARMS, 12, 0.2, -1),
                injury("right_arm", "moderndamage:minor_bleeding", "screen.tacticaltablet.mdc.injury.right_arm_minor_bleeding",
                        Category.INJURY_ARMS, 8, 0.3, -1),
                injury("left_leg", "moderndamage:left_leg_fracture", "screen.tacticaltablet.mdc.injury.left_leg_fracture",
                        Category.INJURY_LEGS, 8, 1, -1),
                injury("left_leg", "moderndamage:major_bleeding", "screen.tacticaltablet.mdc.injury.left_leg_major_bleeding",
                        Category.INJURY_LEGS, 12, 0.2, -1),
                injury("left_leg", "moderndamage:minor_bleeding", "screen.tacticaltablet.mdc.injury.left_leg_minor_bleeding",
                        Category.INJURY_LEGS, 8, 0.3, -1),
                injury("right_leg", "moderndamage:right_leg_fracture", "screen.tacticaltablet.mdc.injury.right_leg_fracture",
                        Category.INJURY_LEGS, 8, 1, -1),
                injury("right_leg", "moderndamage:major_bleeding", "screen.tacticaltablet.mdc.injury.right_leg_major_bleeding",
                        Category.INJURY_LEGS, 12, 0.2, -1),
                injury("right_leg", "moderndamage:minor_bleeding", "screen.tacticaltablet.mdc.injury.right_leg_minor_bleeding",
                        Category.INJURY_LEGS, 8, 0.3, -1)
        );
        for (InjuryProfile profile : profiles) {
            addInjuryField(fields, profile, Metric.THRESHOLD, 0, 1000, profile.defaultThreshold(), false);
            addInjuryField(fields, profile, Metric.CHANCE, 0, 1, profile.defaultChance(), false);
            addInjuryField(fields, profile, Metric.DURATION, -1, 72000, profile.defaultDuration(), true);
        }

        FIELDS = List.copyOf(fields);
        INJURY_PROFILES = profiles;
        Map<Integer, Field> byId = new LinkedHashMap<>();
        Map<String, Field> byKey = new LinkedHashMap<>();
        for (Field field : FIELDS) {
            if (byId.put(field.id(), field) != null || byKey.put(field.key(), field) != null) {
                throw new IllegalStateException("Duplicate MDC balance field " + field.key());
            }
        }
        BY_ID = Map.copyOf(byId);
        BY_KEY = Map.copyOf(byKey);
    }

    private ModernDamageBalanceSchema() {
    }

    public static List<Field> fields() {
        return FIELDS;
    }

    public static List<InjuryProfile> injuryProfiles() {
        return INJURY_PROFILES;
    }

    public static Optional<Field> byId(int id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Field byKey(String key) {
        Field field = BY_KEY.get(key);
        if (field == null) throw new IllegalArgumentException("Unknown MDC balance field " + key);
        return field;
    }

    public static double[] defaults() {
        double[] values = new double[FIELDS.size()];
        for (Field field : FIELDS) values[field.id()] = field.defaultValue();
        return values;
    }

    public static ValidationResult validate(Map<Integer, Double> submitted) {
        if (submitted == null || submitted.size() != FIELDS.size()) {
            return invalid(ValidationError.WRONG_FIELD_COUNT);
        }
        double[] values = new double[FIELDS.size()];
        Arrays.fill(values, Double.NaN);
        for (Map.Entry<Integer, Double> entry : submitted.entrySet()) {
            Field field = BY_ID.get(entry.getKey());
            if (field == null) return invalid(ValidationError.UNKNOWN_FIELD);
            double value = entry.getValue() == null ? Double.NaN : entry.getValue();
            if (!Double.isFinite(value)) return invalid(ValidationError.NON_FINITE_VALUE);
            if (value < field.minimum() || value > field.maximum()) return invalid(ValidationError.OUT_OF_RANGE);
            if (field.integer() && value != Math.rint(value)) return invalid(ValidationError.NON_INTEGER_VALUE);
            if (field.metric() == Metric.DURATION && value == 0) return invalid(ValidationError.INVALID_DURATION);
            values[field.id()] = value;
        }
        if (Arrays.stream(values).anyMatch(Double::isNaN)) return invalid(ValidationError.WRONG_FIELD_COUNT);
        if (value(values, "majorBleedingIntervalTicks") > value(values, "minorBleedingIntervalTicks")
                || value(values, "majorBleedingDamagePerLevel") < value(values, "minorBleedingDamagePerLevel")) {
            return invalid(ValidationError.INVALID_BLEEDING_ORDER);
        }
        for (String part : List.of("stomach", "left_arm", "right_arm", "left_leg", "right_leg")) {
            double major = injuryValue(values, part, "moderndamage:major_bleeding", Metric.THRESHOLD);
            double minor = injuryValue(values, part, "moderndamage:minor_bleeding", Metric.THRESHOLD);
            if (major < minor) return invalid(ValidationError.INVALID_THRESHOLD_ORDER);
        }
        return new ValidationResult(ValidationError.NONE, values);
    }

    public static Map<Integer, Double> toMap(double[] values) {
        Map<Integer, Double> result = new HashMap<>();
        if (values == null || values.length != FIELDS.size()) return result;
        for (Field field : FIELDS) result.put(field.id(), values[field.id()]);
        return result;
    }

    public static String injuryKey(String part, String effectId, Metric metric) {
        return "injury." + part + "." + effectId + "." + metric.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static double injuryValue(double[] values, String part, String effect, Metric metric) {
        return value(values, injuryKey(part, effect, metric));
    }

    private static double value(double[] values, String key) {
        return values[byKey(key).id()];
    }

    private static ValidationResult invalid(ValidationError error) {
        return new ValidationResult(error, new double[0]);
    }

    private static void addScalar(List<Field> fields, String key, String labelKey, String descriptionKey,
                                  Category category, double min, double max, double defaultValue,
                                  boolean integer) {
        fields.add(new Field(fields.size(), key, labelKey, descriptionKey, category, Metric.VALUE,
                min, max, defaultValue, integer));
    }

    private static InjuryProfile injury(String part, String effect, String labelKey, Category category,
                                        double threshold, double chance, int duration) {
        return new InjuryProfile(part, effect, labelKey, category, threshold, chance, duration);
    }

    private static void addInjuryField(List<Field> fields, InjuryProfile profile, Metric metric,
                                       double min, double max, double defaultValue, boolean integer) {
        fields.add(new Field(fields.size(), profile.prefix() + metric.name().toLowerCase(java.util.Locale.ROOT),
                profile.labelKey(), "screen.tacticaltablet.mdc.desc.injury_" + metric.name().toLowerCase(java.util.Locale.ROOT),
                profile.category(), metric, min, max, defaultValue, integer));
    }
}
