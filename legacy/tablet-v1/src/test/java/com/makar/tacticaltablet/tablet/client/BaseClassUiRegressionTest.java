package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.ClassDefinitions;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BaseClassUiRegressionTest {
    private static final Path CLIENT=Path.of("src/main/java/com/makar/tacticaltablet/tablet/client");
    @Test void ordinarySelectionCannotTriggerUpgradeAndCancelDoesNotHideIt() throws Exception {
        String source=Files.readString(CLIENT.resolve("TabletScreen.java"));
        String press=source.substring(source.indexOf("private void pressAction"),source.indexOf("private boolean isActionActive"));
        assertFalse(press.contains("Upgrade"));
        assertFalse(press.contains("ConfirmAction.TIER_UPGRADE"));
        assertFalse(source.contains("dismissedUpgradePrompts"));
        assertTrue(source.contains("() -> canUpgrade(definition, targetTier)"));
        assertTrue(source.contains("baseClasses.beginUpgrade(definition.classKey(), targetTier)"));
    }
    @Test void optionalIconsRetainLegacyAndMissingResourceFallback() {
        var definition=ClassDefinitions.byClassKey("sniper").orElseThrow();
        assertSame(definition.icon(),ClassIconResolver.resolveTablet(definition,location -> location.equals(definition.icon())));
        assertEquals(ClassDefinitions.FALLBACK_ICON,ClassIconResolver.resolveTablet(definition,location -> false));
        assertTrue(ClassIconResolver.resolveTablet(definition,location -> true).getPath().contains("/unified/"));
    }
    @Test void gameDerivativesHaveCommonSizeAndTransparentMargins() throws Exception {
        Path folder=Path.of("src/main/resources/assets/tacticaltablet/textures/gui/classes/unified");
        for(String name:new String[]{"sniper","scout","droneoperator","mortarman","boomguy"}) {
            var image=ImageIO.read(folder.resolve(name+".png").toFile());
            assertNotNull(image);
            assertEquals(64,image.getWidth());
            assertEquals(64,image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            for(int edge=0;edge<64;edge++) {
                assertEquals(0,image.getRGB(edge,0)>>>24);
                assertEquals(0,image.getRGB(edge,63)>>>24);
                assertEquals(0,image.getRGB(0,edge)>>>24);
                assertEquals(0,image.getRGB(63,edge)>>>24);
            }
        }
    }
    @Test void reducedMotionAndSessionScopedPendingAreWired() throws Exception {
        String screen=Files.readString(CLIENT.resolve("TabletScreen.java"));
        String dialog=Files.readString(CLIENT.resolve("ui/widget/TacticalDialog.java"));
        assertTrue(screen.contains("minecraft.options.screenEffectScale().get() <= 0.0D"));
        assertTrue(dialog.contains("minecraft.options.screenEffectScale().get() <= 0.0D"));
        assertFalse(dialog.contains("cancelButton.setY"));
        assertFalse(dialog.contains("confirmButton.setY"));
        assertTrue(Files.readString(CLIENT.resolve("ClientEvents.java")).contains("TabletClientState.clearUpgradePending()"));
    }
}
