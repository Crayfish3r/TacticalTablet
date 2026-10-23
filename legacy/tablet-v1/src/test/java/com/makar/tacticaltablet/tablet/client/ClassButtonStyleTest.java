package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.progression.ClassTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassButtonStyleTest {
    private static final float EPSILON = 0.00001F;

    @Test
    void normalTintMatchesRequiredTierColors() {
        assertTint(ClassTier.BASIC, 114, 214, 138);
        assertTint(ClassTier.RARE, 91, 141, 239);
        assertTint(ClassTier.EPIC, 165, 107, 232);
        assertTint(ClassTier.LEGEND, 231, 199, 106);
        assertTint(ClassTier.MONSTER, 216, 117, 117);
    }

    @Test
    void actualTierUsesFixedLevelOtherwiseCurrentSynchronizedTierAndClampsDamage() {
        assertEquals(ClassTier.EPIC, ClassButtonStyle.actualTier(-1, ClassTier.EPIC.id()));
        assertEquals(ClassTier.RARE, ClassButtonStyle.actualTier(ClassTier.RARE.id(), ClassTier.MONSTER.id()));
        assertEquals(ClassTier.BASIC, ClassButtonStyle.actualTier(-1, Integer.MIN_VALUE));
        assertEquals(ClassTier.MONSTER, ClassButtonStyle.actualTier(-1, Integer.MAX_VALUE));
        assertEquals(ClassTier.BASIC, ClassButtonStyle.actualTier(Integer.MIN_VALUE, ClassTier.BASIC.id()));
    }

    @Test
    void hoverAndDisabledPreserveTierHueWhileChangingBrightnessAndAlpha() {
        ClassButtonStyle.Tint normal = ClassButtonStyle.tint(ClassTier.EPIC, true, false);
        ClassButtonStyle.Tint hover = ClassButtonStyle.tint(ClassTier.EPIC, true, true);
        ClassButtonStyle.Tint disabled = ClassButtonStyle.tint(ClassTier.EPIC, false, true);

        assertTrue(hover.red() > normal.red());
        assertTrue(hover.green() > normal.green());
        assertTrue(hover.blue() > normal.blue());
        assertEquals(1.0F, hover.alpha(), EPSILON);
        assertTrue(disabled.red() < normal.red());
        assertTrue(disabled.green() < normal.green());
        assertTrue(disabled.blue() < normal.blue());
        assertTrue(disabled.alpha() >= 0.55F && disabled.alpha() <= 0.75F);
        assertTrue(disabled.red() / normal.red() >= 0.6F && disabled.red() / normal.red() <= 0.75F);
        assertEquals(normal.red() / normal.green(), disabled.red() / disabled.green(), EPSILON);
        assertEquals(normal.blue() / normal.green(), disabled.blue() / disabled.green(), EPSILON);
    }

    private static void assertTint(ClassTier tier, int red, int green, int blue) {
        ClassButtonStyle.Tint tint = ClassButtonStyle.tint(tier, true, false);
        assertEquals(red / 255.0F, tint.red(), EPSILON);
        assertEquals(green / 255.0F, tint.green(), EPSILON);
        assertEquals(blue / 255.0F, tint.blue(), EPSILON);
        assertEquals(1.0F, tint.alpha(), EPSILON);
        assertEquals((0xFF << 24) | (red << 16) | (green << 8) | blue, ClassButtonStyle.color(tier));
    }
}
