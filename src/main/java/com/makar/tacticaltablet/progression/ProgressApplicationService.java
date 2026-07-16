package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Coordinates one progression mutation with the side effects still owned by the synchronized facade.
 * Thread safety is deliberately supplied by {@link PlayerProgressManager}; this service owns no locks or cache.
 */
public final class ProgressApplicationService {
    private final ProgressService progressService;

    ProgressApplicationService(ProgressService progressService) {
        this.progressService = Objects.requireNonNull(progressService, "progressService");
    }

    ProgressApplicationResult<ProgressPurchaseResult> purchaseClass(
            MutableProgressState progress,
            String classId,
            ProgressContext context,
            SideEffects sideEffects,
            Consumer<ProgressPurchaseResult> response
    ) {
        ProgressPurchaseResult result = progressService.purchaseClass(progress, classId, context);
        complete(result, result.successful(), sideEffects, response);
        return new ProgressApplicationResult<>(result, result.successful());
    }

    ProgressApplicationResult<BaseUnlockResult> unlockBaseClass(
            MutableProgressState progress,
            String classId,
            ProgressContext context,
            SideEffects sideEffects,
            Consumer<BaseUnlockResult> response
    ) {
        BaseUnlockResult result = progressService.unlockBaseClass(progress, classId, context);
        complete(result, result.changed(), sideEffects, response);
        return new ProgressApplicationResult<>(result, result.changed());
    }

    ProgressApplicationResult<TierUpgradeResult> upgradeTier(
            MutableProgressState progress,
            String classId,
            int targetTier,
            ProgressContext context,
            SideEffects sideEffects,
            Consumer<TierUpgradeResult> response
    ) {
        TierUpgradeResult result = progressService.upgradeTier(progress, classId, targetTier, context);
        complete(result, result.changed(), sideEffects, response);
        return new ProgressApplicationResult<>(result, result.changed());
    }

    private <T> void complete(
            T result,
            boolean changed,
            SideEffects sideEffects,
            Consumer<T> response
    ) {
        Objects.requireNonNull(sideEffects, "sideEffects");
        Objects.requireNonNull(response, "response");
        if (changed) sideEffects.markDirty();
        response.accept(result);
        if (changed) sideEffects.queueSave();
        sideEffects.sync();
    }

    interface SideEffects {
        void markDirty();

        void queueSave();

        void sync();
    }
}
