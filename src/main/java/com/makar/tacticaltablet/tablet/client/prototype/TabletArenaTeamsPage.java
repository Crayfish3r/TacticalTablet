package com.makar.tacticaltablet.tablet.client.prototype;
import com.makar.tacticaltablet.game.team.*;
import com.makar.tacticaltablet.tablet.net.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.UUID;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletTerminalTheme.*;

/** One connection-scoped subscription, shared with preparation refresh, independent of kit/RTP readiness. */
public final class TabletArenaTeamsPage {
    private static UUID session = UUID.randomUUID();
    private static Object connection;
    private static long sequence, pending, changed;
    private static ArenaTeamStatePacket state;
    private final int height;
    private long rendered = -1;
    private int pressed = -1;
    TabletArenaTeamsPage(int height) { this.height = height; }
    public static void reset() { session = UUID.randomUUID(); connection = null; state = null; sequence = pending = 0; changed++; }
    static void observeConnection() {
        if (Minecraft.getInstance().getConnection() != connection) fetch();
    }
    static void fetch() {
        var next = Minecraft.getInstance().getConnection();
        if (next != connection) { reset(); connection = next; }
        if (next != null) send(ArenaTeamService.Action.FETCH, -1);
    }
    private static void send(ArenaTeamService.Action action, int team) {
        long id = ++sequence;
        if (action != ArenaTeamService.Action.FETCH) pending = id;
        PacketHandler.sendToServer(new ArenaTeamActionPacket(session, id, state == null ? ArenaTeamService.NONE : state.matchId(), action, team));
        changed++;
    }
    public static void accept(ArenaTeamStatePacket next) {
        if (!session.equals(next.session()) || state != null && next.revision() <= state.revision()) return;
        state = next; if (next.requestId() >= pending) pending = 0; changed++;
    }
    static boolean isArena() { return state == null || state.arena(); }
    boolean dirty() { return rendered != changed; }
    void rendered() { rendered = changed; }
    private TabletPreparationPage.Rect card(int i) {
        return new TabletPreparationPage.Rect(16 + i % 2 * 308, 52 + i / 2 * ((height - 122) / 2), 300, (height - 122) / 2 - 6);
    }
    private TabletPreparationPage.Rect button(int i) { var r = card(i); return new TabletPreparationPage.Rect(r.x()+8,r.y()+r.height()-30,r.width()-16,24); }
    private TabletPreparationPage.Rect leave() { return new TabletPreparationPage.Rect(392,height-58,232,28); }
    private boolean enabled(int i) {
        if (state == null || pending != 0 || !state.reason().isEmpty()) return false;
        return i == 4 ? state.own() >= 0 : state.own() != i && state.members().stream().filter(m -> m.team() == i).count() < 5;
    }
    private int hit(TabletDisplayProjection.Point p) {
        if (p == null) return -1;
        for (int i=0;i<4;i++) if (button(i).contains(p.x(),p.y())) return i;
        return leave().contains(p.x(),p.y()) ? 4 : -1;
    }
    void press(TabletDisplayProjection.Point p) { pressed = hit(p); }
    void cancelPress() { pressed = -1; }
    void release(TabletDisplayProjection.Point p) {
        int id=pressed; pressed=-1;
        if (id>=0 && id==hit(p) && enabled(id)) send(id==4 ? ArenaTeamService.Action.LEAVE : ArenaTeamService.Action.JOIN, id==4 ? -1 : id);
    }
    void render(GuiGraphics g) {
        background(g,Kind.SCREEN,State.NORMAL,0,0,LOGICAL_WIDTH,height+1);
        text(g,"КОМАНДЫ",16,16,TEXT,TITLE_SCALE);
        if(state==null) { text(g,"Загрузка составов…",16,55,SECONDARY,TEXT_SCALE); return; }
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
            drawButton(g,button(i),state.own()==i?"Вы в команде":members.size()==5?"Мест нет":state.own()<0?"Вступить":"Перейти",enabled(i),false,false);
        }
        text(g,state.own()<0?"Без команды":"Ваша команда: "+TeamId.standardValues()[state.own()].displayName(),16,height-48,SECONDARY,DETAIL_SCALE);
        drawButton(g,leave(),"Покинуть команду",enabled(4),false,false);
        String message = pending!=0?"Обработка запроса…":!state.reason().isEmpty()?state.reason():switch(state.result()) {
            case OK -> "Состав подтверждён сервером"; case FULL -> "Мест нет"; case STALE -> "Матч изменился";
            case INVALID -> "Ошибочный запрос"; case FORBIDDEN -> "Сейчас менять состав нельзя";
        };
        text(g,fit(message,608),16,height-18,SECONDARY,DETAIL_SCALE);
    }
}
