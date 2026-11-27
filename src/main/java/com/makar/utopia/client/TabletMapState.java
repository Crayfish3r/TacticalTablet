package com.makar.utopia.client;

import com.makar.utopia.boundary.CityZone;
import com.makar.utopia.network.MapNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Accessed only on the client thread. */
public final class TabletMapState {
    private static MapNetwork.Geometry geometry;
    private static Sample sample;

    record Sample(ClientLevel level, MapNetwork.Status status, long receivedAt) {
        CityZone zone() { return status.zone(); }

        int portSeconds() {
            long elapsed = status.zone() == CityZone.PORT ? Util.getMillis() - receivedAt : 0;
            return (int) ((Math.max(0, status.portRemainingMillis() - elapsed) + 999) / 1000);
        }
    }

    private TabletMapState() { }

    public static void setGeometry(MapNetwork.Geometry next, MapNetwork.Status status) {
        geometry = next;
        setStatus(status);
    }

    public static void setStatus(MapNetwork.Status status) {
        sample = new Sample(Minecraft.getInstance().level, status, Util.getMillis());
    }

    static MapNetwork.Geometry geometry() {
        return geometry;
    }

    static Sample status(ClientLevel level) {
        discardStale(level);
        return sample;
    }

    static void discardStale(ClientLevel level) {
        if (sample != null && sample.level != level) sample = null;
    }

    static void clear() {
        geometry = null;
        sample = null;
    }
}
