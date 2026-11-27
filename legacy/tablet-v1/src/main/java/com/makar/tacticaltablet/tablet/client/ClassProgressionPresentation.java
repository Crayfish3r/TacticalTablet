package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.progression.ClassTier;
import java.util.Objects;

/** Pure presentation policy. Neither availability is an authorization decision. */
public final class ClassProgressionPresentation {
    public enum Kind { BASE, SHOP, VIP, MARINE }
    public enum Reason {
        AVAILABLE, LOCKED, NO_MATCH, KIT_USED, COOLDOWN, MODE_RESTRICTED,
        CHAOS_SELECTION, NOT_PROGRESSIVE, COMPETITIVE, CHAOS, MAXIMUM,
        INSUFFICIENT_XP, INSUFFICIENT_COINS, PENDING, UNCONFIRMED, DATA_UNAVAILABLE
    }
    public record Availability(boolean available, Reason reason) {
        public static Availability of(Reason reason) { return new Availability(reason == Reason.AVAILABLE, reason); }
    }
    public record Input(String classKey, String displayName, Kind kind, boolean unlocked,
                        int savedTier, int totalXp, int coins, boolean gameRunning,
                        boolean kitUsed, boolean cooldown, boolean useModeAllowed,
                        boolean competitive, boolean chaos, boolean chaosSelectable,
                        boolean permanentDataKnown, UpgradePending.State pending) { }
    public record Model(String classKey, String displayName, boolean unlocked, int savedTier,
                        int totalXp, ClassTier currentTier, ClassTier nextTier,
                        int stageXp, int stageSpan, float progress, int remainingXp,
                        int upgradePrice, int coins, Availability useAvailability,
                        Availability upgradeAvailability, boolean maximum, boolean stageFilled,
                        boolean progressive, boolean permanentDataKnown) { }

    private ClassProgressionPresentation() { }

    public static Model describe(Input input) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(input.kind());
        Objects.requireNonNull(input.pending());
        ClassTier current = ClassTier.clamp(input.savedTier());
        boolean progressive = input.kind() == Kind.BASE;
        ClassTier next = progressive ? current.next().orElse(null) : null;
        int xp = Math.max(0, input.totalXp());
        int span = next == null ? 0 : Math.max(0, next.requiredXp() - current.requiredXp());
        int stageXp = Math.max(0, Math.min(span, xp - current.requiredXp()));
        boolean maximum = progressive && current.isMaximum();
        float progress = span > 0 ? stageXp / (float) span : maximum ? 1.0F : 0.0F;
        int remaining = next == null ? 0 : Math.max(0, next.requiredXp() - xp);
        int price = next == null ? 0 : next.upgradeCost();
        Reason use = !input.useModeAllowed() ? Reason.MODE_RESTRICTED
                : input.chaos() ? input.chaosSelectable() ? Reason.AVAILABLE : Reason.CHAOS_SELECTION
                : !input.unlocked() ? Reason.LOCKED
                : !input.gameRunning() ? Reason.NO_MATCH
                : input.kitUsed() ? Reason.KIT_USED
                : input.cooldown() ? Reason.COOLDOWN : Reason.AVAILABLE;
        Reason upgrade = !progressive ? Reason.NOT_PROGRESSIVE
                : input.pending() == UpgradePending.State.WAITING ? Reason.PENDING
                : input.pending() == UpgradePending.State.UNCONFIRMED ? Reason.UNCONFIRMED
                : input.competitive() ? Reason.COMPETITIVE
                : input.chaos() ? Reason.CHAOS
                : !input.permanentDataKnown() ? Reason.DATA_UNAVAILABLE
                : !input.unlocked() ? Reason.LOCKED
                : maximum ? Reason.MAXIMUM
                : remaining > 0 ? Reason.INSUFFICIENT_XP
                : input.coins() < price ? Reason.INSUFFICIENT_COINS : Reason.AVAILABLE;
        return new Model(input.classKey(), input.displayName(), input.unlocked(), current.id(), xp,
                current, next, stageXp, span, progress, remaining, price, Math.max(0, input.coins()),
                Availability.of(use), Availability.of(upgrade), maximum,
                progressive && !maximum && span > 0 && stageXp == span,
                progressive, input.permanentDataKnown());
    }

    public static boolean canSubmit(Model current, int targetTier) {
        return current.upgradeAvailability().available() && current.nextTier() != null
                && current.nextTier().id() == targetTier;
    }
}
