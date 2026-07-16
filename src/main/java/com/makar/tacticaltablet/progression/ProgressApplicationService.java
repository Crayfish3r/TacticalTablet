package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Prepares progression mutations and executes an immutable post-lock plan. Thread safety for preparation is
 * deliberately supplied by {@link PlayerProgressManager}; this service owns no locks or cache.
 */
public final class ProgressApplicationService {
    private final ProgressService progressService;

    ProgressApplicationService(ProgressService progressService) {
        this.progressService = Objects.requireNonNull(progressService, "progressService");
    }

    ProgressApplicationResult<ProgressPurchaseResult> prepareClassPurchase(
            MutableProgressState progress,
            String classId,
            ProgressContext context
    ) {
        ProgressPurchaseResult result = progressService.purchaseClass(progress, classId, context);
        return new ProgressApplicationResult<>(result, result.successful());
    }

    ProgressApplicationResult<BaseUnlockResult> prepareBaseUnlock(
            MutableProgressState progress,
            String classId,
            ProgressContext context
    ) {
        BaseUnlockResult result = progressService.unlockBaseClass(progress, classId, context);
        return new ProgressApplicationResult<>(result, result.changed());
    }

    ProgressApplicationResult<TierUpgradeResult> prepareTierUpgrade(
            MutableProgressState progress,
            String classId,
            int targetTier,
            ProgressContext context
    ) {
        TierUpgradeResult result = progressService.upgradeTier(progress, classId, targetTier, context);
        return new ProgressApplicationResult<>(result, result.changed());
    }

    <T> T executePostLockEffects(
            PreparedProgressOperation<T> operation,
            Consumer<T> response,
            SideEffects sideEffects
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(sideEffects, "sideEffects");
        Objects.requireNonNull(response, "response");
        response.accept(operation.result());
        operation.queuedSave().ifPresent(sideEffects::enqueueSave);
        if (operation.syncMode() != ProgressSyncMode.NONE) {
            sideEffects.sync(operation.syncMode());
        }
        return operation.result();
    }

    interface SideEffects {
        void enqueueSave(QueuedProgressSave save);

        void sync(ProgressSyncMode mode);
    }
}
