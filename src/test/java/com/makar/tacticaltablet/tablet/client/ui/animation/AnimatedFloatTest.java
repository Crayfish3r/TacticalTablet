package com.makar.tacticaltablet.tablet.client.ui.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedFloatTest {
    @Test
    void reachesTheSameValueAtDifferentFrameRates() {
        AnimatedFloat sixtyFps = new AnimatedFloat(0.0F, 0.2F);
        AnimatedFloat thirtyFps = new AnimatedFloat(0.0F, 0.2F);
        sixtyFps.setTarget(1.0F);
        thirtyFps.setTarget(1.0F);

        for (int frame = 0; frame < 6; frame++) sixtyFps.update(1.0F / 60.0F);
        for (int frame = 0; frame < 3; frame++) thirtyFps.update(1.0F / 30.0F);

        assertEquals(sixtyFps.value(), thirtyFps.value(), 0.0001F);
        assertEquals(Easing.easeOutCubic(0.5F), sixtyFps.value(), 0.0001F);
    }

    @Test
    void clampsPauseSizedDeltaAndCanSnap() {
        AnimatedFloat animation = new AnimatedFloat(0.0F, 0.2F);
        animation.setTarget(1.0F);
        animation.update(10.0F);

        assertFalse(animation.isFinished());
        assertEquals(Easing.easeOutCubic(0.5F), animation.value(), 0.0001F);

        animation.snapTo(1.0F);
        assertTrue(animation.isFinished());
        assertEquals(1.0F, animation.target());
    }

    @Test
    void easingFunctionsClampTheirInputs() {
        assertEquals(0.0F, Easing.linear(-2.0F));
        assertEquals(1.0F, Easing.easeOutCubic(2.0F));
        assertEquals(0.5F, Easing.easeInOutCubic(0.5F), 0.0001F);
        assertEquals(0.5F, Easing.smoothStep(0.5F), 0.0001F);
    }
}
