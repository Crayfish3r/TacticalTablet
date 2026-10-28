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
    private final TabletArenaResultsView arenaResults;
    private final TabletShopPage shop;
    private final TabletArenaTeamsPage teams;
    private final TabletClassDialog classDialog;
    private final int height;
    private TabletPage selected = TabletPage.PREPARATION;
    private TabletPage pressedHardware, pressedDisplayPage;
    private boolean open, dirty = true;
    private MatchPhase phase = MatchPhase.WAITING;
    private long phaseRevision = -1;

    TabletPageShell(TabletPrototypeModel.DisplaySurface display) {
        height = (int) TabletTerminalTheme.logicalHeight(display);
        classDialog = new TabletClassDialog(height);
        preparation = new TabletPreparationPage(display, classDialog);
        shop = new TabletShopPage(height, classDialog); teams = new TabletArenaTeamsPage(height);
        voting = new TabletVotingPage(height);
        arenaResults = new TabletArenaResultsView(height);
    }

    TabletPage selected() { return selected; }
    void classesUpdated() {
        preparation.classesUpdated(); shop.update(); dirty = true;
    }
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
        preparation.tick(); profile.tick(preparation); voting.tick(); arenaResults.tick(); shop.update();
        if (voting.takeRefreshRequest()) preparation.refreshWhenIdle();
    }

    private boolean votingView() {
        return phase == MatchPhase.MAP_VOTING || phase == MatchPhase.VOTING || phase == MatchPhase.TEAM_SELECT;
    }
    private boolean arenaResultsView() {
        return !votingView() && phase != MatchPhase.RUNNING
                && com.makar.tacticaltablet.tablet.client.ArenaClientState.snapshot().finished();
    }
    private boolean navigationLocked() { return classDialog.active() || selected == TabletPage.PREPARATION && preparation.confirmation >= 0; }
    private void synchronizePhase() {
        long revision = TabletClientState.getPhaseRevision();
        if (phaseRevision == revision) return;
        phaseRevision = revision; phase = TabletClientState.getMatchPhase();
        cancelPress(); preparation.dismissConfirmation(); voting.phaseChanged(phase);
        updateInteractivity(); dirty = true;
    }
    private void updateInteractivity() {
        preparation.interactive(open && !classDialog.active() && selected == TabletPage.PREPARATION && !votingView() && !arenaResultsView());
        voting.interactive(open && !classDialog.active() && selected == TabletPage.PREPARATION && votingView());
        shop.interactive(open && !classDialog.active() && selected == TabletPage.SHOP);
    }

    void interactive(boolean value) {
        synchronizePhase();
        if (open != value) { open = value; cancelPress(); dirty = true; }
        updateInteractivity();
    }

    void hover(double x, double y) {
        synchronizePhase();
        var hit = open ? TabletDisplayProjection.pick(x, y) : null;
        if (classDialog.active()) {
            classDialog.hover(hit == null ? null : hit.display());
            preparation.hover(null, false); voting.hover(null); shop.hover(null); return;
        }
        preparation.hover(hit == null ? null : hit.display(), open && selected == TabletPage.PREPARATION && !votingView() && !arenaResultsView());
        voting.hover(hit == null ? null : hit.display());
        shop.hover(hit == null ? null : hit.display());
    }

    void press(double x, double y) {
        synchronizePhase(); voting.tick();
        cancelPress();
        if (!open) return;
        var hit = TabletDisplayProjection.pick(x, y);
        if (hit.hardwareKey() != null) {
            if (!navigationLocked()) pressedHardware = TabletPage.forHardwareKey(hit.hardwareKey());
        } else if (hit.display() != null) {
            pressedDisplayPage = selected;
            if (classDialog.active()) { classDialog.press(hit.display()); return; }
            if (selected == TabletPage.CONTRACT_TRACKER && TabletArenaTeamsPage.isArena()) { teams.press(hit.display()); return; }
            if (selected == TabletPage.SHOP) { shop.press(hit.display()); return; }
            if (selected != TabletPage.PREPARATION) return;
            if (arenaResultsView()) return;
            if (votingView()) voting.press(hit.display());
            else preparation.press(hit.display());
        }
    }

    void release(double x, double y) {
        synchronizePhase(); voting.tick();
        TabletPage hardware = pressedHardware, displayPage = pressedDisplayPage;
        pressedHardware = null; pressedDisplayPage = null;
        if (!open) { cancelPress(); return; }
        var hit = TabletDisplayProjection.pick(x, y);
        if (hardware != null) {
            preparation.cancelPress();
            voting.cancelPress();
            if (hardware == TabletPage.forHardwareKey(hit.hardwareKey()) && !navigationLocked()) select(hardware);
        } else if (displayPage == selected && hit.hardwareKey() == null && classDialog.active()) {
            classDialog.release(hit.display()); updateInteractivity(); dirty = true;
        } else if (displayPage == selected && selected == TabletPage.CONTRACT_TRACKER && TabletArenaTeamsPage.isArena()) {
            teams.release(hit.display()); dirty = true;
        } else if (displayPage == selected && selected == TabletPage.SHOP && hit.hardwareKey() == null) {
            shop.release(hit.display()); updateInteractivity(); dirty = true;
        } else if (displayPage == selected && selected == TabletPage.PREPARATION && hit.hardwareKey() == null) {
            if (arenaResultsView()) { preparation.cancelPress(); voting.cancelPress(); return; }
            if (votingView()) voting.release(hit.display());
            else preparation.release(hit.display());
            if (preparation.shopRequested) { preparation.shopRequested = false; select(TabletPage.SHOP); }
            updateInteractivity();
        } else { cancelPress(); }
    }

    private void select(TabletPage page) {
        if (selected == page) return;
        cancelPress();
        classDialog.dismiss(); preparation.dismissConfirmation();
        selected = page; dirty = true;
        updateInteractivity();
    }

    void cancelPress() {
        pressedHardware = null; pressedDisplayPage = null;
        preparation.cancelPress();
        voting.cancelInput();
        shop.cancelPress(); classDialog.cancelPress(); teams.cancelPress();
    }

    void scroll(double x, double y, double delta) {
        synchronizePhase(); voting.tick();
        if (classDialog.active()) return;
        if (open && selected == TabletPage.SHOP) {
            var hit = TabletDisplayProjection.pick(x, y);
            if (hit.hardwareKey() == null) shop.scroll(hit.display(), delta);
            return;
        }
        if (open && selected == TabletPage.PREPARATION) {
            var hit = TabletDisplayProjection.pick(x, y);
            if (hit.hardwareKey() != null) return;
            if (arenaResultsView()) arenaResults.scroll(hit.display(), delta);
            else if (votingView()) voting.scroll(hit.display(), delta);
            else preparation.scroll(hit.display(), delta);
        }
    }

    boolean dirty() {
        synchronizePhase();
        return dirty || selected == TabletPage.CONTRACT_TRACKER && teams.dirty() || classDialog.dirty() || selected == TabletPage.PREPARATION && (arenaResultsView() ? arenaResults.dirty() : votingView() ? voting.dirty() : preparation.dirty())
                || selected == TabletPage.PROFILE && profile.dirty() || selected == TabletPage.SHOP && shop.dirty();
    }
    void rendered() {
        dirty = false;
        if (selected == TabletPage.PREPARATION) {
            if (arenaResultsView()) arenaResults.rendered(); else if (votingView()) voting.rendered(); else preparation.rendered();
        }
        if (selected == TabletPage.PROFILE) profile.rendered();
        if (selected == TabletPage.SHOP) shop.rendered();
        classDialog.rendered(); teams.rendered();
    }
    void render(GuiGraphics graphics) {
        switch (selected) {
            case PREPARATION -> {
                if (arenaResultsView()) arenaResults.render(graphics);
                else if (votingView()) TabletVotingView.render(graphics, voting);
                else TabletPreparationView.render(graphics, preparation);
            }
            case PROFILE -> TabletProfileView.render(graphics, profile, height);
            case CONTRACT_TRACKER -> { if (TabletArenaTeamsPage.isArena()) teams.render(graphics); else TabletPlaceholderViews.contractTracker(graphics, height); }
            case SHOP -> TabletShopView.render(graphics, shop);
            case EMPTY_4, EMPTY_5 -> TabletTerminalTheme.background(graphics, TabletTerminalTheme.Kind.SCREEN,
                    TabletTerminalTheme.State.NORMAL, 0, 0, TabletTerminalTheme.LOGICAL_WIDTH, height + 1);
        }
        classDialog.render(graphics, height);
    }
}
