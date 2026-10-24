package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.tablet.preparation.PreparationSnapshot;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import net.minecraft.client.gui.GuiGraphics;

/** One session owns the retained preparation page, selected page and exclusive mouse recipient. */
final class TabletPageShell {
    private final TabletPreparationPage preparation;
    private final TabletProfilePage profile = new TabletProfilePage();
    private final TabletVotingPage voting;
    private final int height;
    private TabletPage selected = TabletPage.PREPARATION;
    private TabletPage pressedHardware, pressedDisplayPage;
    private boolean open, dirty = true;
    private MatchPhase phase = MatchPhase.WAITING;
    private long phaseRevision = -1;

    TabletPageShell(TabletPrototypeModel.DisplaySurface display) {
        preparation = new TabletPreparationPage(display);
        height = (int) TabletTerminalTheme.logicalHeight(display);
        voting = new TabletVotingPage(height);
    }

    TabletPage selected() { return selected; }
    void acceptSnapshot(PreparationSnapshot snapshot) {
        synchronizePhase();
        if (preparation.accept(snapshot)) {
            profile.confirmCurrentSession();
            profile.tick(preparation);
            voting.acceptPreparation(snapshot.requestId());
        }
    }
    void acceptMapSnapshot(long requestId) {
        synchronizePhase();
        voting.acceptMapSnapshot(requestId);
        voting.tick();
    }
    void tick() {
        synchronizePhase();
        preparation.tick(); profile.tick(preparation); voting.tick();
        if (voting.takeRefreshRequest()) preparation.refreshWhenIdle();
    }

    private boolean votingView() {
        return phase == MatchPhase.MAP_VOTING || phase == MatchPhase.VOTING || phase == MatchPhase.TEAM_SELECT;
    }
    private boolean navigationLocked() { return !votingView() && preparation.navigationLocked(); }
    private void synchronizePhase() {
        long revision = TabletClientState.getPhaseRevision();
        if (phaseRevision == revision) return;
        phaseRevision = revision; phase = TabletClientState.getMatchPhase();
        cancelPress(); preparation.dismissConfirmation(); voting.phaseChanged(phase);
        updateInteractivity(); dirty = true;
    }
    private void updateInteractivity() {
        preparation.interactive(open && selected == TabletPage.PREPARATION && !votingView());
        voting.interactive(open && selected == TabletPage.PREPARATION && votingView());
    }

    void interactive(boolean value) {
        synchronizePhase();
        if (open != value) { open = value; cancelPress(); dirty = true; }
        updateInteractivity();
    }

    void hover(double x, double y) {
        synchronizePhase();
        var hit = open ? TabletDisplayProjection.pick(x, y) : null;
        preparation.hover(hit == null ? null : hit.display(), open && selected == TabletPage.PREPARATION && !votingView());
        voting.hover(hit == null ? null : hit.display());
    }

    void press(double x, double y) {
        synchronizePhase(); voting.tick();
        cancelPress();
        if (!open) return;
        var hit = TabletDisplayProjection.pick(x, y);
        if (hit.hardwareKey() != null) {
            if (!navigationLocked()) pressedHardware = TabletPage.forHardwareKey(hit.hardwareKey());
        } else if (hit.display() != null && selected == TabletPage.PREPARATION) {
            pressedDisplayPage = selected;
            if (votingView()) voting.press(hit.display());
            else preparation.press(hit.display());
        }
    }

    void release(double x, double y) {
        synchronizePhase(); voting.tick();
        TabletPage hardware = pressedHardware, displayPage = pressedDisplayPage;
        pressedHardware = null; pressedDisplayPage = null;
        if (!open) { preparation.cancelPress(); voting.cancelPress(); return; }
        var hit = TabletDisplayProjection.pick(x, y);
        if (hardware != null) {
            preparation.cancelPress();
            voting.cancelPress();
            if (hardware == TabletPage.forHardwareKey(hit.hardwareKey()) && !navigationLocked()) select(hardware);
        } else if (displayPage == selected && selected == TabletPage.PREPARATION && hit.hardwareKey() == null) {
            if (votingView()) voting.release(hit.display());
            else preparation.release(hit.display());
        } else { preparation.cancelPress(); voting.cancelPress(); }
    }

    private void select(TabletPage page) {
        if (selected == page) return;
        cancelPress();
        selected = page; dirty = true;
        updateInteractivity();
    }

    void cancelPress() {
        pressedHardware = null; pressedDisplayPage = null;
        preparation.cancelPress();
        voting.cancelInput();
    }

    void scroll(double x, double y, double delta) {
        synchronizePhase(); voting.tick();
        if (open && selected == TabletPage.PREPARATION) {
            var hit = TabletDisplayProjection.pick(x, y);
            if (hit.hardwareKey() != null) return;
            if (votingView()) voting.scroll(hit.display(), delta);
            else preparation.scroll(hit.display(), delta);
        }
    }

    boolean dirty() {
        synchronizePhase();
        return dirty || selected == TabletPage.PREPARATION && (votingView() ? voting.dirty() : preparation.dirty())
                || selected == TabletPage.PROFILE && profile.dirty();
    }
    void rendered() {
        dirty = false;
        if (selected == TabletPage.PREPARATION) {
            if (votingView()) voting.rendered(); else preparation.rendered();
        }
        if (selected == TabletPage.PROFILE) profile.rendered();
    }
    void render(GuiGraphics graphics) {
        switch (selected) {
            case PREPARATION -> {
                if (votingView()) TabletVotingView.render(graphics, voting);
                else TabletPreparationView.render(graphics, preparation);
            }
            case PROFILE -> TabletProfileView.render(graphics, profile, height);
            case CONTRACT_TRACKER -> TabletPlaceholderViews.contractTracker(graphics, height);
        }
    }
}
