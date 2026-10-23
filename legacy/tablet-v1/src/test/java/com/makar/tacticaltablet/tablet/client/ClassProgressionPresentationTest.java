package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.progression.ClassTier;
import org.junit.jupiter.api.Test;
import static com.makar.tacticaltablet.tablet.client.ClassProgressionPresentation.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassProgressionPresentationTest {
    private Input input(Kind kind, boolean unlocked, ClassTier tier, int xp, int coins,
                        boolean running, boolean used, boolean cooldown, boolean competitive, boolean chaos) {
        return new Input("sniper", "Sniper", kind, unlocked, tier.id(), xp, coins,
                running, used, cooldown, true, competitive, chaos, running, true, UpgradePending.State.NONE);
    }
    private Model model(ClassTier tier, int xp, int coins) {
        return describe(input(Kind.BASE, true, tier, xp, coins, true, false, false, false, false));
    }

    @Test void rare650IsSeventyPercentOfItsStage() {
        Model m = model(ClassTier.RARE, 650, 100);
        assertEquals(350, m.stageXp());
        assertEquals(500, m.stageSpan());
        assertEquals(.7F, m.progress(), .00001F);
        assertEquals(150, m.remainingXp());
        assertEquals(ClassTier.EPIC, m.nextTier());
        assertEquals(Reason.INSUFFICIENT_XP, m.upgradeAvailability().reason());
    }

    @Test void everyTierBoundaryUsesCatalogAndExactBalance() {
        for (ClassTier tier : ClassTier.values()) {
            if (tier.isMaximum()) continue;
            ClassTier next = tier.next().orElseThrow();
            Model start = model(tier, tier.requiredXp(), next.upgradeCost());
            assertEquals(0, start.stageXp());
            assertEquals(0F, start.progress());
            Model before = model(tier, next.requiredXp() - 1, next.upgradeCost());
            assertEquals(1, before.remainingXp());
            assertFalse(before.upgradeAvailability().available());
            Model at = model(tier, next.requiredXp(), next.upgradeCost());
            assertEquals(1F, at.progress());
            assertEquals(at.stageSpan(), at.stageXp());
            assertTrue(at.stageFilled());
            assertTrue(at.upgradeAvailability().available());
            assertEquals(next.upgradeCost(), at.upgradePrice());
            assertTrue(canSubmit(at, next.id()));
            assertFalse(canSubmit(at, tier.id()));
        }
    }

    @Test void clampsCorruptXpAndMaximumDoesNotDivideByZero() {
        assertEquals(0F, model(ClassTier.EPIC, -1, 500).progress());
        assertEquals(1F, model(ClassTier.EPIC, Integer.MAX_VALUE, 500).progress());
        Model maximum = model(ClassTier.MONSTER, ClassTier.MAX_XP, 0);
        assertTrue(maximum.maximum());
        assertNull(maximum.nextTier());
        assertEquals(0, maximum.stageSpan());
        assertEquals(0, maximum.upgradePrice());
        assertTrue(Float.isFinite(maximum.progress()));
        assertEquals(Reason.MAXIMUM, maximum.upgradeAvailability().reason());
        assertFalse(canSubmit(maximum, ClassTier.MONSTER.id()));
    }

    @Test void useAndUpgradeAreIndependentOutsideMatchOnCooldownAndAfterKitUse() {
        for (Input input : new Input[]{
                input(Kind.BASE,true,ClassTier.BASIC,300,50,false,false,false,false,false),
                input(Kind.BASE,true,ClassTier.BASIC,300,50,true,true,false,false,false),
                input(Kind.BASE,true,ClassTier.BASIC,300,50,true,false,true,false,false)}) {
            Model m = describe(input);
            assertFalse(m.useAvailability().available());
            assertTrue(m.upgradeAvailability().available());
            assertEquals(1F,m.progress());
        }
        Model m = model(ClassTier.BASIC,299,50);
        assertTrue(m.useAvailability().available());
        assertFalse(m.upgradeAvailability().available());
    }

    @Test void lockedInsufficientMoneyCompetitiveAndChaosNeverUpgrade() {
        assertEquals(Reason.LOCKED, describe(input(Kind.BASE,false,ClassTier.BASIC,300,50,true,false,false,false,false)).upgradeAvailability().reason());
        assertEquals(Reason.INSUFFICIENT_COINS,model(ClassTier.BASIC,300,49).upgradeAvailability().reason());
        assertEquals(Reason.COMPETITIVE,describe(input(Kind.BASE,true,ClassTier.BASIC,300,50,true,false,false,true,false)).upgradeAvailability().reason());
        Model chaos = describe(input(Kind.BASE,false,ClassTier.BASIC,300,50,true,false,false,false,true));
        assertTrue(chaos.useAvailability().available());
        assertEquals(Reason.CHAOS,chaos.upgradeAvailability().reason());
    }

    @Test void fixedShopVipAndMarineHaveNoFictionalProgression() {
        for (Kind kind : new Kind[]{Kind.SHOP, Kind.VIP, Kind.MARINE}) {
            Model m=describe(input(kind,true,ClassTier.LEGEND,2000,500,true,false,false,false,false));
            assertFalse(m.progressive());
            assertNull(m.nextTier());
            assertEquals(0F,m.progress());
            assertEquals(0,m.stageSpan());
            assertEquals(Reason.NOT_PROGRESSIVE,m.upgradeAvailability().reason());
        }
    }

    @Test void pendingAndUnknownDataCannotBeSubmitted() {
        Input waiting = new Input("sniper","Sniper",Kind.BASE,true,0,300,50,true,false,false,true,
                false,false,false,true,UpgradePending.State.WAITING);
        assertEquals(Reason.PENDING, describe(waiting).upgradeAvailability().reason());
        Input unknown = new Input("sniper","Sniper",Kind.BASE,true,0,300,50,true,false,false,true,
                false,false,false,false,UpgradePending.State.NONE);
        assertFalse(canSubmit(describe(unknown),1));
    }
}
