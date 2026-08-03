package com.makar.tacticaltablet.tablet.client.ui.widget;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalDialogArchitectureTest {
    private static final Path DIALOG = Path.of(
            "src/main/java/com/makar/tacticaltablet/tablet/client/ui/widget/TacticalDialog.java");

    @Test
    void dangerDialogsDefaultToCancelAndDoNotOverwriteCallbackNavigation() throws IOException {
        String source = source();

        assertTrue(source.contains("setInitialFocus(danger ? cancelButton : confirmButton)"));
        assertTrue(source.contains("if (Minecraft.getInstance().screen == this) returnToParent()"));
        assertFalse(source.contains("confirmAction.run();\n        returnToParent();"));
    }

    @Test
    void focusRestorationUsesStableKeysWithIndexFallback() throws IOException {
        String source = source();

        assertTrue(source.contains("private record FocusTarget(String key, int fallbackIndex)"));
        assertTrue(source.contains("focused instanceof FocusKeyProvider"));
        assertTrue(source.contains("key.equals(provider.focusKey())"));
        assertTrue(source.contains("screen.children().get(fallbackIndex)"));
    }

    @Test
    void longBodyIsClippedAndScrollableInsideResponsiveBounds() throws IOException {
        String source = source();

        assertTrue(source.contains("ScissorScope.open("));
        assertTrue(source.contains("public boolean mouseScrolled("));
        assertTrue(source.contains("bodyLines.size() - visibleLines"));
        assertFalse(source.contains("Math.max(72,"));
    }

    private static String source() throws IOException {
        return Files.readString(DIALOG).replace("\r\n", "\n");
    }
}
