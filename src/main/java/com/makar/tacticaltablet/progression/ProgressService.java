package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.time.Clock;
import java.util.Optional;

/**
 * Object boundary for progression mutations. The synchronized legacy facade supplies thread safety,
 * profile lookup, persistence and synchronization.
 */
public final class ProgressService {
    private final ProgressCatalog catalog;

    ProgressService(ProgressCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    ProgressMutationResult addCoins(MutableProgressState progress, int amount) {
        Objects.requireNonNull(progress, "progress");
        ProgressMutationResult result = ProgressPolicy.changeCoins(progress.coins(), amount);
        progress.coins(result.currentValue());
        return result;
    }

    ProgressMutationResult setCoins(MutableProgressState progress, int amount) {
        Objects.requireNonNull(progress, "progress");
        int previous = progress.coins();
        int current = ProgressPolicy.normalizeCoins(amount);
        progress.coins(current);
        return new ProgressMutationResult(previous != current, previous, current);
    }

    ProgressPurchaseResult purchaseClass(
            MutableProgressState progress,
            String classId,
            ProgressContext context
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(context, "context");
        String normalizedClass = catalog.normalizeClassId(classId);
        int price = catalog.shopPrice(normalizedClass);
        ProgressPurchaseResult result = ProgressPolicy.evaluatePurchase(
                progress.coins(),
                price,
                progress.purchase(normalizedClass).value() > 0,
                !context.competitiveSet() && catalog.isShopClass(normalizedClass)
        );
        if (!result.successful()) return result;

        progress.coins(result.currentBalance());
        progress.purchase(normalizedClass, 1);
        progress.experience(normalizedClass, 0);
        return result;
    }

    ExperienceMutationResult addExperience(
            MutableProgressState progress,
            String classId,
            int amount,
            ProgressionRules rules
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(rules, "rules");
        String normalizedClass = catalog.normalizeClassId(classId);
        int current = progress.experience(normalizedClass).value();
        int savedTier = storedTier(progress, normalizedClass);
        if (amount <= 0) {
            return new ExperienceMutationResult(
                    false, current, current, ProgressPolicy.calculateLevel(current, rules), savedTier);
        }
        int capped = Math.min(ClassTier.clamp(savedTier).xpCap(), ProgressPolicy.saturatingAdd(current, amount));
        progress.experience(normalizedClass, capped);
        return new ExperienceMutationResult(
                current != capped,
                current,
                capped,
                ProgressPolicy.calculateLevel(capped, rules),
                savedTier
        );
    }

    ExperienceMutationResult setExperience(
            MutableProgressState progress,
            String classId,
            int amount,
            ProgressionRules rules
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(rules, "rules");
        String normalizedClass = catalog.normalizeClassId(classId);
        int current = progress.experience(normalizedClass).value();
        int savedTier = storedTier(progress, normalizedClass);
        int value = catalog.isShopClass(normalizedClass)
                ? 0
                : ProgressPolicy.normalizeExperience(amount, rules.maximumExperience());
        if (catalog.isBaseClass(normalizedClass)) {
            value = Math.min(value, ClassTier.clamp(savedTier).xpCap());
        }
        progress.experience(normalizedClass, value);
        return new ExperienceMutationResult(
                current != value,
                current,
                value,
                ProgressPolicy.calculateLevel(value, rules),
                savedTier
        );
    }

    BaseUnlockResult unlockBaseClass(
            MutableProgressState progress,
            String classId,
            ProgressContext context
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(context, "context");
        String normalizedClass = catalog.normalizeClassId(classId);
        int balance = ProgressPolicy.normalizeCoins(progress.coins());
        if (context.competitiveSet()) {
            return baseUnlockRejected(ProgressionStatus.ALREADY_UNLOCKED, balance);
        }
        if (!catalog.isUnlockableBaseClass(normalizedClass)) {
            return baseUnlockRejected(ProgressionStatus.INVALID_CLASS, balance);
        }
        if (isBaseClassUnlocked(progress, normalizedClass)) {
            return baseUnlockRejected(ProgressionStatus.ALREADY_UNLOCKED, balance);
        }
        if (!ProgressPolicy.canAfford(balance, catalog.baseUnlockCost())) {
            return baseUnlockRejected(ProgressionStatus.NOT_ENOUGH_COINS, balance);
        }

        int currentBalance = balance - catalog.baseUnlockCost();
        progress.coins(currentBalance);
        progress.baseUnlock(normalizedClass, 1);
        if (!progress.experience(normalizedClass).present()) progress.experience(normalizedClass, 0);
        if (!progress.tier(normalizedClass).present()) progress.tier(normalizedClass, ClassTier.BASIC.id());
        return new BaseUnlockResult(ProgressionStatus.SUCCESS, true, balance, currentBalance);
    }

    TierUpgradeResult upgradeTier(
            MutableProgressState progress,
            String classId,
            int targetTier,
            ProgressContext context
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(context, "context");
        String normalizedClass = catalog.normalizeClassId(classId);
        int balance = ProgressPolicy.normalizeCoins(progress.coins());
        int currentTier = storedTier(progress, normalizedClass);
        if (context.competitiveSet()) {
            return tierRejected(ProgressionStatus.WRONG_TIER, currentTier, balance);
        }
        if (!catalog.isBaseClass(normalizedClass)) {
            return tierRejected(ProgressionStatus.INVALID_CLASS, currentTier, balance);
        }
        if (!isBaseClassUnlocked(progress, normalizedClass)) {
            return tierRejected(ProgressionStatus.LOCKED, currentTier, balance);
        }

        ProgressionStatus validation = evaluateTierUpgrade(
                currentTier,
                progress.experience(normalizedClass).value(),
                balance,
                targetTier
        );
        if (validation != ProgressionStatus.SUCCESS) {
            return tierRejected(validation, currentTier, balance);
        }

        ClassTier target = ClassTier.byId(targetTier).orElseThrow();
        int currentBalance = balance - target.upgradeCost();
        progress.coins(currentBalance);
        progress.tier(normalizedClass, targetTier);
        return new TierUpgradeResult(
                ProgressionStatus.SUCCESS, true, currentTier, targetTier, balance, currentBalance);
    }

    ProgressMutationResult incrementCounter(MutableProgressState progress, MutableProgressState.Counter counter, int amount) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(counter, "counter");
        int previous = progress.counter(counter);
        int current = ProgressPolicy.saturatingAdd(previous, amount);
        progress.counter(counter, current);
        return new ProgressMutationResult(previous != current, previous, current);
    }

    boolean setFlag(MutableProgressState progress, MutableProgressState.Flag flag, boolean value) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(flag, "flag");
        boolean changed = progress.flag(flag) != value;
        progress.flag(flag, value);
        return changed;
    }

    IdempotentCreditResult applyIdempotentCredit(
            MutableProgressState progress,
            String receiptId,
            String operationType,
            int amount,
            Clock clock
    ) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(clock, "clock");
        int balance = progress.coins();
        if (receiptId == null || receiptId.isBlank() || operationType == null
                || operationType.isBlank() || operationType.length() > 64 || amount <= 0) {
            return creditResult(IdempotentCreditStatus.FAILED, balance, "Invalid coin credit receipt");
        }

        Optional<ProgressReceipt> existing = progress.receipt(receiptId);
        if (existing.isPresent()) {
            ProgressReceipt receipt = existing.get();
            if (operationType.equals(receipt.operationType())
                    && Integer.toString(amount).equals(receipt.payloadHash())) {
                return creditResult(IdempotentCreditStatus.ALREADY_APPLIED, balance, "");
            }
            return creditResult(IdempotentCreditStatus.CONFLICT, balance, "Coin credit receipt mismatch");
        }
        if (balance > Integer.MAX_VALUE - amount) {
            return creditResult(
                    IdempotentCreditStatus.FAILED, balance, "Coin credit would overflow player balance");
        }

        int currentBalance = balance + amount;
        ProgressReceipt receipt = new ProgressReceipt(
                receiptId,
                operationType,
                clock.millis(),
                balance,
                currentBalance,
                Integer.toString(amount)
        );
        progress.coins(currentBalance);
        progress.addReceipt(receipt);
        return new IdempotentCreditResult(
                IdempotentCreditStatus.APPLIED,
                balance,
                currentBalance,
                "",
                Optional.of(new IdempotentCreditRollback(balance, currentBalance, receipt))
        );
    }

    boolean rollbackIdempotentCredit(MutableProgressState progress, IdempotentCreditRollback rollback) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(rollback, "rollback");
        if (progress.coins() != rollback.appliedBalance()) return false;
        if (!progress.removeReceipt(rollback.receipt())) return false;
        progress.coins(rollback.previousBalance());
        return true;
    }

    ExclusiveUnlockResult grantExclusiveClass(ExclusiveUnlockState progress, String classId) {
        Objects.requireNonNull(progress, "progress");
        String normalizedClass = catalog.normalizeClassId(classId);
        if (!catalog.isExclusiveClass(normalizedClass)) {
            return new ExclusiveUnlockResult(
                    ExclusiveUnlockResult.Status.INVALID_CLASS, false, Optional.empty());
        }

        ProgressEntry previousPurchase = progress.purchase(normalizedClass);
        if (previousPurchase.value() > 0) {
            return new ExclusiveUnlockResult(
                    ExclusiveUnlockResult.Status.ALREADY_OWNED, false, Optional.empty());
        }

        ProgressEntry previousExperience = progress.experience(normalizedClass);
        progress.purchase(normalizedClass, 1);
        if (!previousExperience.present()) progress.experience(normalizedClass, 0);
        return new ExclusiveUnlockResult(
                ExclusiveUnlockResult.Status.GRANTED,
                true,
                Optional.of(new ExclusiveUnlockRollback(
                        normalizedClass, previousPurchase, previousExperience))
        );
    }

    boolean rollbackExclusiveClass(ExclusiveUnlockState progress, ExclusiveUnlockRollback rollback) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(rollback, "rollback");
        String classId = rollback.classId();
        if (!progress.purchase(classId).equals(new ProgressEntry(true, 1))) return false;

        ProgressEntry currentExperience = progress.experience(classId);
        ProgressEntry expectedExperience = rollback.previousExperience().present()
                ? rollback.previousExperience()
                : new ProgressEntry(true, 0);
        if (!currentExperience.equals(expectedExperience)) return false;

        restorePurchase(progress, classId, rollback.previousPurchase());
        restoreExperience(progress, classId, rollback.previousExperience());
        return true;
    }

    private boolean isBaseClassUnlocked(MutableProgressState progress, String classId) {
        return catalog.isInitialBaseClass(classId) || progress.baseUnlock(classId).value() > 0;
    }

    private int storedTier(MutableProgressState progress, String classId) {
        return ClassTier.clamp(progress.tier(classId).value()).id();
    }

    private ProgressionStatus evaluateTierUpgrade(int currentTier, int xp, int coins, int targetTier) {
        ClassTier current = ClassTier.clamp(currentTier);
        if (current.isMaximum()) return ProgressionStatus.MAX_TIER;

        var target = ClassTier.byId(targetTier);
        if (target.isEmpty() || target.get() == ClassTier.BASIC) return ProgressionStatus.INVALID_CLASS;
        if (target.get().id() != current.id() + 1) return ProgressionStatus.WRONG_TIER;
        if (xp < target.get().requiredXp()) return ProgressionStatus.NOT_ENOUGH_XP;
        if (coins < target.get().upgradeCost()) return ProgressionStatus.NOT_ENOUGH_COINS;
        return ProgressionStatus.SUCCESS;
    }

    private BaseUnlockResult baseUnlockRejected(ProgressionStatus status, int balance) {
        return new BaseUnlockResult(status, false, balance, balance);
    }

    private TierUpgradeResult tierRejected(ProgressionStatus status, int tier, int balance) {
        return new TierUpgradeResult(status, false, tier, tier, balance, balance);
    }

    private IdempotentCreditResult creditResult(
            IdempotentCreditStatus status,
            int balance,
            String diagnostic
    ) {
        return new IdempotentCreditResult(status, balance, balance, diagnostic, Optional.empty());
    }

    private void restorePurchase(ExclusiveUnlockState progress, String classId, ProgressEntry previous) {
        if (previous.present()) progress.purchase(classId, previous.value());
        else progress.removePurchase(classId);
    }

    private void restoreExperience(ExclusiveUnlockState progress, String classId, ProgressEntry previous) {
        if (previous.present()) progress.experience(classId, previous.value());
        else progress.removeExperience(classId);
    }
}
