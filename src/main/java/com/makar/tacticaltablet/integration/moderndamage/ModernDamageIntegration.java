package com.makar.tacticaltablet.integration.moderndamage;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.integration.moderndamage.client.ModernDamageClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Optional-integration gate. No MDC class is referenced until mod id and exact version pass. */
public final class ModernDamageIntegration {
    public static final String MOD_ID = "moderndamage";
    public static final String SUPPORTED_VERSION = "1.0.32";
    public static final String VERIFIED_JAR_SHA256 =
            "eaf15ce984a6af47f25fffa026abc7eb8be64d4ecb4b81b943a29d3c421fc461";

    public enum State { NOT_INITIALIZED, MISSING, SUPPORTED, UNSUPPORTED, ERROR }

    public record Status(State state, String detectedVersion, String details) {
        public boolean supported() {
            return state == State.SUPPORTED;
        }
    }

    public enum ApplyError {
        NONE,
        UNAVAILABLE,
        STALE_REVISION,
        VALIDATION_FAILED,
        SAVE_FAILED
    }

    public record ApplyResult(ApplyError error, ModernDamageBalanceSchema.ValidationError validationError,
                              long revision, List<String> changes) {
        public boolean success() {
            return error == ApplyError.NONE;
        }
    }

    private static volatile Status status = new Status(State.NOT_INITIALIZED, "", "ожидается инициализация");
    private static ModernDamageAdapter adapter;
    private static long revision = 1L;

    private ModernDamageIntegration() {
    }

    public static synchronized void initialize() {
        if (status.state() != State.NOT_INITIALIZED) return;
        if (!ModList.get().isLoaded(MOD_ID)) {
            status = new Status(State.MISSING, "", "Modern Damage Control не установлен; интеграция отключена");
            TacticalTabletMod.LOGGER.info("Modern Damage Control is not installed; optional integration is disabled.");
            return;
        }

        String detected = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        if (!SUPPORTED_VERSION.equals(detected)) {
            status = new Status(State.UNSUPPORTED, detected,
                    "поддерживается только MDC " + SUPPORTED_VERSION + "; HUD и редактор баланса отключены");
            TacticalTabletMod.LOGGER.warn(
                    "Unsupported Modern Damage Control version {}. TacticalTablet MDC HUD and balance editor are disabled; verified version is {}.",
                    detected, SUPPORTED_VERSION);
            return;
        }

        try {
            // This is the first point at which a class containing direct MDC references may load.
            adapter = new ModernDamageAdapterV1032();
            adapter.readBalance(revision);
            status = new Status(State.SUPPORTED, detected,
                    "проверенная версия " + SUPPORTED_VERSION + ", адаптер активен");
            TacticalTabletMod.LOGGER.info(
                    "Enabled Modern Damage Control {} integration (verified JAR SHA-256 {}).",
                    detected, VERIFIED_JAR_SHA256);
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ModernDamageClientBootstrap::initialize);
        } catch (LinkageError | RuntimeException exception) {
            adapter = null;
            status = new Status(State.ERROR, detected,
                    "API MDC не соответствует проверенной сборке; интеграция безопасно отключена");
            TacticalTabletMod.LOGGER.error(
                    "Modern Damage Control {} passed the version check but its verified API is unavailable. Integration disabled.",
                    detected, exception);
        }
    }

    public static Status status() {
        return status;
    }

    public static boolean isSupported() {
        return status.supported() && adapter != null;
    }

    public static synchronized ModernDamageBalanceSnapshot readBalance() {
        if (!isSupported()) return new ModernDamageBalanceSnapshot(revision, new double[0]);
        return adapter.readBalance(revision);
    }

    public static synchronized ApplyResult applyBalance(long expectedRevision,
                                                         Map<Integer, Double> submitted,
                                                         String actor) {
        if (!isSupported()) {
            return new ApplyResult(ApplyError.UNAVAILABLE, ModernDamageBalanceSchema.ValidationError.NONE,
                    revision, List.of());
        }
        if (expectedRevision != revision) {
            return new ApplyResult(ApplyError.STALE_REVISION, ModernDamageBalanceSchema.ValidationError.NONE,
                    revision, List.of());
        }
        ModernDamageBalanceSchema.ValidationResult validation = ModernDamageBalanceSchema.validate(submitted);
        if (!validation.valid()) {
            return new ApplyResult(ApplyError.VALIDATION_FAILED, validation.error(), revision, List.of());
        }

        ModernDamageBalanceSnapshot before = adapter.readBalance(revision);
        List<String> changes = describeChanges(before.values(), validation.values());
        if (changes.isEmpty()) {
            return new ApplyResult(ApplyError.NONE, ModernDamageBalanceSchema.ValidationError.NONE,
                    revision, List.of());
        }
        try {
            adapter.applyBalance(validation.values());
        } catch (Exception exception) {
            TacticalTabletMod.LOGGER.error("Failed to atomically save MDC balance changes requested by {}", actor, exception);
            return new ApplyResult(ApplyError.SAVE_FAILED, ModernDamageBalanceSchema.ValidationError.NONE,
                    revision, List.of());
        }

        revision++;
        TacticalTabletMod.LOGGER.info("MDC balance revision {} applied by {}: {}",
                revision, actor, String.join(", ", changes));
        return new ApplyResult(ApplyError.NONE, ModernDamageBalanceSchema.ValidationError.NONE,
                revision, changes);
    }

    private static List<String> describeChanges(double[] before, double[] after) {
        List<String> changes = new ArrayList<>();
        for (ModernDamageBalanceSchema.Field field : ModernDamageBalanceSchema.fields()) {
            double oldValue = before[field.id()];
            double newValue = after[field.id()];
            if (Double.compare(oldValue, newValue) != 0) {
                changes.add(field.key() + "=" + oldValue + "->" + newValue);
            }
        }
        return List.copyOf(changes);
    }
}
