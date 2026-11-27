package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.progression.ClassTier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** UI-local uncertainty tracking; a timeout never implies rejection or permits an automatic retry. */
public final class UpgradePending {
    public enum State { NONE, WAITING, UNCONFIRMED, CONFIRMED }
    public record Key(String classKey, int targetTier) { }
    private record Attempt(long startedAt, State state) { }
    public static final long TIMEOUT_MILLIS = 10_000L;
    private final Map<Key, Attempt> attempts = new HashMap<>();

    public void clear() { attempts.clear(); }

    public boolean begin(String classKey, int targetTier, long now) {
        Objects.requireNonNull(classKey);
        if (ClassTier.byId(targetTier).isEmpty() || targetTier <= ClassTier.BASIC.id()
                || state(classKey) == State.WAITING || state(classKey) == State.UNCONFIRMED) return false;
        attempts.put(new Key(classKey, targetTier), new Attempt(now, State.WAITING));
        return true;
    }

    /** Called only with authoritative permanent progression data, never competitive kit tiers. */
    public boolean observe(String classKey, int savedTier, long now) {
        boolean confirmed = false;
        for (Map.Entry<Key, Attempt> entry : attempts.entrySet()) {
            if (!entry.getKey().classKey().equals(classKey)) continue;
            Attempt attempt = entry.getValue();
            if (attempt.state() == State.CONFIRMED) continue;
            if (savedTier >= entry.getKey().targetTier()) {
                entry.setValue(new Attempt(attempt.startedAt(), State.CONFIRMED));
                confirmed = true;
            } else if (now - attempt.startedAt() >= TIMEOUT_MILLIS) {
                entry.setValue(new Attempt(attempt.startedAt(), State.UNCONFIRMED));
            }
        }
        return confirmed;
    }

    public void tick(long now) {
        attempts.replaceAll((key, attempt) -> attempt.state() == State.WAITING
                && now - attempt.startedAt() >= TIMEOUT_MILLIS
                ? new Attempt(attempt.startedAt(), State.UNCONFIRMED) : attempt);
    }

    public State state(String classKey) {
        State result = State.NONE;
        for (Map.Entry<Key, Attempt> entry : attempts.entrySet()) {
            if (!entry.getKey().classKey().equals(classKey)) continue;
            if (entry.getValue().state() == State.WAITING || entry.getValue().state() == State.UNCONFIRMED)
                return entry.getValue().state();
            result = State.CONFIRMED;
        }
        return result;
    }

    public State state(String classKey, int targetTier) {
        Attempt attempt = attempts.get(new Key(classKey, targetTier));
        return attempt == null ? State.NONE : attempt.state();
    }
}
