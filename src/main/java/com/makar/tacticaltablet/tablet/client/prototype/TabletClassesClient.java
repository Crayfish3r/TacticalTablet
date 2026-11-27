package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.progression.TabletProgressOperation.*;
import com.makar.tacticaltablet.tablet.classes.TabletClassSnapshot;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TabletClassesRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import java.util.UUID;

/** One connection-wide state and operation identity, retained across tabs and closing/reopening the tablet. */
public final class TabletClassesClient {
    private static UUID session = UUID.randomUUID();
    private static ClientPacketListener connection;
    private static long sequence, deadline;
    private static Identity inFlight;
    private static TabletClassSnapshot snapshot;
    private static String feedback = "Загрузка классов…";
    private static long revision;
    private TabletClassesClient() { }
    private static void connection() {
        if (connection == Minecraft.getInstance().getConnection()) return;
        reset(); connection = Minecraft.getInstance().getConnection();
    }
    public static void reset() {
        connection = null; session = UUID.randomUUID(); snapshot = null; inFlight = null;
        sequence = 0; revision = 0; feedback = "Загрузка классов…";
    }
    /** Shares the existing preparation poll. No hidden page owns a timer. */
    static void fetch() {
        connection();
        if (connection != null) PacketHandler.sendToServer(new TabletClassesRequestPacket(new Identity(session, ++sequence, Kind.FETCH, "", -1)));
    }
    static boolean submit(Kind kind, String key, int targetTier) {
        connection();
        if (connection == null || snapshot == null || busy()) return false;
        inFlight = new Identity(session, ++sequence, kind, key, targetTier);
        deadline = net.minecraft.Util.getMillis() + 10_000;
        feedback = "Запрос отправлен. Ожидаем подтверждения записи…"; revision++;
        PacketHandler.sendToServer(new TabletClassesRequestPacket(inFlight));
        return true;
    }
    static void tick() {
        connection();
        if (inFlight != null && net.minecraft.Util.getMillis() >= deadline
                && (snapshot == null || snapshot.pending() == null || !snapshot.pending().identity().equals(inFlight))) {
            String text = "Ответ задерживается. Проверяем операцию без повторного списания…";
            if (!feedback.equals(text)) { feedback = text; revision++; }
        }
    }
    public static void accept(TabletClassSnapshot next) {
        connection();
        if (!next.session().equals(session) || snapshot != null && next.revision() <= snapshot.revision()) return;
        boolean pendingFinished = snapshot != null && snapshot.pending() != null && next.pending() == null;
        snapshot = next; revision++;
        Result result = next.operation();
        if (result != null && inFlight != null && result.identity().equals(inFlight)) {
            feedback = result.message();
            if (result.status() == Status.SAVED || result.status() == Status.REJECTED) inFlight = null;
        } else if (next.pending() != null) feedback = next.pending().message();
        else if (inFlight == null && pendingFinished) feedback = "Изменение сохранено.";
        else if (inFlight == null && feedback.equals("Загрузка классов…")) feedback = "Данные подтверждены сервером";
        if (Minecraft.getInstance().screen instanceof TabletPrototypeScreen screen && screen.shell() != null)
            screen.shell().classesUpdated();
    }
    static TabletClassSnapshot snapshot() { return snapshot; }
    static long revision() { return revision; }
    static boolean busy() { return inFlight != null || snapshot != null && snapshot.pending() != null; }
    static String feedback() { return feedback; }
}
