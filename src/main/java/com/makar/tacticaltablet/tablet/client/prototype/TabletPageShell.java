package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.tablet.preparation.PreparationSnapshot;
import net.minecraft.client.gui.GuiGraphics;

/** One session owns the retained preparation page, selected page and exclusive mouse recipient. */
final class TabletPageShell {
    private final TabletPreparationPage preparation;
    private final int height;
    private TabletPage selected = TabletPage.PREPARATION;
    private TabletPage pressedHardware, pressedDisplayPage;
    private boolean open, dirty = true;

    TabletPageShell(TabletPrototypeModel.DisplaySurface display) {
        preparation = new TabletPreparationPage(display);
        height = (int) TabletTerminalTheme.logicalHeight(display);
    }

    TabletPage selected() { return selected; }
    void acceptSnapshot(PreparationSnapshot snapshot) { preparation.accept(snapshot); }
    void tick() { preparation.tick(); }

    void interactive(boolean value) {
        if (open != value) { open = value; cancelPress(); dirty = true; }
        preparation.interactive(open && selected == TabletPage.PREPARATION);
    }

    void hover(double x, double y) {
        var hit = open ? TabletDisplayProjection.pick(x, y) : null;
        preparation.hover(hit == null ? null : hit.display(), open && selected == TabletPage.PREPARATION);
    }

    void press(double x, double y) {
        cancelPress();
        if (!open) return;
        var hit = TabletDisplayProjection.pick(x, y);
        if (hit.hardwareKey() != null) {
            if (!preparation.navigationLocked()) pressedHardware = TabletPage.forHardwareKey(hit.hardwareKey());
        } else if (hit.display() != null && selected == TabletPage.PREPARATION) {
            pressedDisplayPage = selected;
            preparation.press(hit.display());
        }
    }

    void release(double x, double y) {
        TabletPage hardware = pressedHardware, displayPage = pressedDisplayPage;
        pressedHardware = null; pressedDisplayPage = null;
        if (!open) { preparation.cancelPress(); return; }
        var hit = TabletDisplayProjection.pick(x, y);
        if (hardware != null) {
            preparation.cancelPress();
            if (hardware == TabletPage.forHardwareKey(hit.hardwareKey()) && !preparation.navigationLocked()) select(hardware);
        } else if (displayPage == selected && selected == TabletPage.PREPARATION && hit.hardwareKey() == null) {
            preparation.release(hit.display());
        } else preparation.cancelPress();
    }

    private void select(TabletPage page) {
        if (selected == page) return;
        cancelPress();
        selected = page; dirty = true;
        preparation.interactive(open && selected == TabletPage.PREPARATION);
    }

    void cancelPress() {
        pressedHardware = null; pressedDisplayPage = null;
        preparation.cancelPress();
    }

    void scroll(double x, double y, double delta) {
        if (open && selected == TabletPage.PREPARATION) preparation.scroll(TabletDisplayProjection.pick(x, y).display(), delta);
    }

    boolean dirty() { return dirty || selected == TabletPage.PREPARATION && preparation.dirty(); }
    void rendered() { dirty = false; if (selected == TabletPage.PREPARATION) preparation.rendered(); }
    void render(GuiGraphics graphics) {
        switch (selected) {
            case PREPARATION -> TabletPreparationView.render(graphics, preparation);
            case PROFILE -> TabletPlaceholderViews.profile(graphics, height);
            case CONTRACT_TRACKER -> TabletPlaceholderViews.contractTracker(graphics, height);
        }
    }
}
