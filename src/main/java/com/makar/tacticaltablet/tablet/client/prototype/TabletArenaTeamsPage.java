package com.makar.tacticaltablet.tablet.client.prototype;
import com.makar.tacticaltablet.game.team.*;
import com.makar.tacticaltablet.tablet.net.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import java.util.UUID;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** Pulls snapshots while the physical team slot is open. Mutations have their own acknowledgement. */
public final class TabletArenaTeamsPage {
    private static final long POLL_MS = 2_000, TIMEOUT_MS = 10_000;
    private static final int LEAVE = 4;
    private static UUID session = UUID.randomUUID();
    private static ClientPacketListener connection;
    private static long sequence;
    private ArenaTeamStatePacket state;
    private final int height;
    private boolean active, dirty = true;
    private long firstFetch, requiredSnapshot, nextPoll, pending, deadline, feedbackUntil;
    private UUID pendingMatch, pressedMatch;
    private int pressed = -1, hovered = -1;
    private String feedback = "";

    TabletArenaTeamsPage(int height) { this.height = height; }

    public static void reset() { session = UUID.randomUUID(); connection = null; sequence = 0; }

    void interactive(boolean value) {
        if (active == value) return;
        active = value;
        cancelPress();
        hover(null);
        pending = 0;
        state = null;
        firstFetch = 0;
        requiredSnapshot = 0;
        nextPoll = 0;
        feedback = "";
        dirty = true;
    }

    void tick() {
        if (!active) return;
        var next = Minecraft.getInstance().getConnection();
        if (next != connection) {
            reset();
            connection = next;
            interactive(false);
            interactive(true);
        }
        if (connection == null) return;

        long now = net.minecraft.Util.getMillis();
        if (!feedback.isEmpty() && now >= feedbackUntil) {
            feedback = "";
            dirty = true;
        }
        if (pending != 0 && now >= deadline) {
            pending = 0;
            showFeedback("Сервер не ответил. Обновляем состав перед повторной попыткой.");
            requiredSnapshot = sequence + 1;
            nextPoll = 0;
        }
        if (pending == 0 && now >= nextPoll) {
            long request = send(ArenaTeamService.Action.FETCH, -1, ArenaTeamService.NONE);
            if (firstFetch == 0) firstFetch = request;
            nextPoll = now + POLL_MS;
        }
    }

    private static long send(ArenaTeamService.Action action, int team, UUID match) {
        long id = ++sequence;
        PacketHandler.sendToServer(new ArenaTeamActionPacket(session, id, match, action, team));
        return id;
    }

    public static void accept(ArenaTeamStatePacket next) {
        if (Minecraft.getInstance().screen instanceof TabletPrototypeScreen screen && screen.shell() != null) {
            screen.shell().acceptArenaTeams(next);
        }
    }

    void update(ArenaTeamStatePacket next) {
        if (!active || connection != Minecraft.getInstance().getConnection() || !session.equals(next.session())
                || firstFetch == 0 || next.requestId() < firstFetch
                || state != null && next.revision() <= state.revision()) return;
        if (state != null && (!state.matchId().equals(next.matchId()) || state.arena() != next.arena())) {
            cancelPress();
            hover(null);
        }
        state = next;
        if (pending != 0 && next.requestId() == pending) {
            pending = 0;
            showFeedback(switch (next.result()) {
                case OK -> next.matchId().equals(pendingMatch) ? "Состав подтверждён сервером" : "Матч изменился";
                case FULL -> "В этой команде нет мест";
                case STALE -> "Матч изменился";
                case INVALID -> "Ошибочный запрос";
                case FORBIDDEN -> next.reason().isEmpty() ? "Сейчас менять состав нельзя" : next.reason();
            });
        }
        if (pressed >= 0 && !enabled(pressed)) cancelPress();
        dirty = true;
    }

    private void showFeedback(String message) {
        feedback = message;
        feedbackUntil = net.minecraft.Util.getMillis() + TIMEOUT_MS;
        dirty = true;
    }

    boolean showsTracker() { return state != null && !state.arena(); }
    boolean dirty() { return dirty; }
    void rendered() { dirty = false; }

    void renderTrackerFeedback(GuiGraphics graphics) {
        if (!feedback.isEmpty()) text(graphics, fit(feedback, 608), 16, height - 35, SECONDARY, DETAIL_SCALE);
    }
    private TabletPreparationPage.Rect card(int i) {
        return new TabletPreparationPage.Rect(16 + i % 2 * 308, 52 + i / 2 * ((height - 122) / 2), 300, (height - 122) / 2 - 6);
    }
    private TabletPreparationPage.Rect button(int i) { var r = card(i); return new TabletPreparationPage.Rect(r.x()+8,r.y()+r.height()-30,r.width()-16,24); }
    private TabletPreparationPage.Rect leave() { return new TabletPreparationPage.Rect(392,height-58,232,28); }
    private boolean enabled(int i) {
        if (!active || state == null || !state.arena() || ArenaTeamService.NONE.equals(state.matchId())
                || state.requestId() < requiredSnapshot || pending != 0 || !state.reason().isEmpty()) return false;
        return i == LEAVE ? state.own() >= 0 : state.own() != i && state.members().stream().filter(m -> m.team() == i).count() < 5;
    }
    private int hit(TabletDisplayProjection.Point p) {
        if (p == null) return -1;
        for (int i=0;i<4;i++) if (button(i).contains(p.x(),p.y())) return i;
        return leave().contains(p.x(),p.y()) ? LEAVE : -1;
    }
    void hover(TabletDisplayProjection.Point point) {
        int button = hit(point);
        int next = button >= 0 && enabled(button) ? button : -1;
        if (next != hovered) {
            hovered = next;
            dirty = true;
        }
    }
    void press(TabletDisplayProjection.Point point) {
        cancelPress();
        int button = hit(point);
        if (button >= 0 && enabled(button)) {
            pressed = button;
            pressedMatch = state.matchId();
            dirty = true;
        }
    }
    void cancelPress() {
        if (pressed >= 0) dirty = true;
        pressed = -1;
        pressedMatch = null;
    }
    void release(TabletDisplayProjection.Point p) {
        int button = pressed;
        UUID match = pressedMatch;
        cancelPress();
        if (button < 0 || button != hit(p) || !enabled(button) || !state.matchId().equals(match)
                || connection != Minecraft.getInstance().getConnection()) return;
        pendingMatch = match;
        pending = send(button == LEAVE ? ArenaTeamService.Action.LEAVE : ArenaTeamService.Action.JOIN,
                button == LEAVE ? -1 : button, match);
        deadline = net.minecraft.Util.getMillis() + TIMEOUT_MS;
        nextPoll = net.minecraft.Util.getMillis() + POLL_MS;
        feedback = "";
        dirty = true;
    }
    void render(GuiGraphics g) {
        background(g,Kind.SCREEN,State.NORMAL,0,0,LOGICAL_WIDTH,height+1);
        if (state == null) { text(g,"Загрузка страницы…",16,55,SECONDARY,TEXT_SCALE); return; }
        text(g,"КОМАНДЫ",16,16,TEXT,TITLE_SCALE);
        for(int i=0;i<4;i++) {
            var r=card(i); var team=TeamId.standardValues()[i]; final int teamIndex=i;
            var members=state.members().stream().filter(m->m.team()==teamIndex).toList();
            background(g,Kind.PANEL,state.own()==i?State.SELECTED:State.NORMAL,r.x(),r.y(),r.width(),r.height());
            text(g,team.displayName()+"  "+members.size()+"/5",r.x()+8,r.y()+8,team.textColor(),TEXT_SCALE);
            int lineHeight=Math.max(12,Math.min(23,(r.height()-60)/5));
            for(int slot=0;slot<5;slot++) {
                String label="— свободно";
                if(slot<members.size()) {
                    var m=members.get(slot); boolean own=Minecraft.getInstance().player!=null && m.id().equals(Minecraft.getInstance().player.getUUID());
                    label=(own?"Вы: ":"")+m.name()+" • "+m.status();
                }
                text(g,fit(label,280),r.x()+8,r.y()+29+slot*lineHeight,TEXT,DETAIL_SCALE);
            }
            drawButton(g,button(i),state.own()==i?"Вы в команде":members.size()==5?"Мест нет":state.own()<0?"Вступить":"Перейти",enabled(i),hovered == i,pressed == i);
        }
        text(g,state.own()<0?"Без команды":"Ваша команда: "+TeamId.standardValues()[state.own()].displayName(),16,height-48,SECONDARY,DETAIL_SCALE);
        drawButton(g,leave(),"Покинуть команду",enabled(LEAVE),hovered == LEAVE,pressed == LEAVE);
        String message = pending != 0 ? "Обработка запроса…" : !feedback.isEmpty() ? feedback : state.reason();
        text(g,fit(message,608),16,height-18,SECONDARY,DETAIL_SCALE);
    }
}
