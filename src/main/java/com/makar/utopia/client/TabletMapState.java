package com.makar.utopia.client;

import com.makar.utopia.network.MapNetwork;

/** Accessed only on the client thread. */
public final class TabletMapState {
    private static MapNetwork.Geometry geometry;
    private static boolean insideHill;

    private TabletMapState() { }

    public static void setGeometry(MapNetwork.Geometry next, boolean inside) {
        geometry = next;
        insideHill = inside;
    }

    public static void setHillStatus(boolean inside) {
        insideHill = inside;
    }

    static MapNetwork.Geometry geometry() {
        return geometry;
    }

    static boolean insideHill() {
        return insideHill;
    }

    static void clear() {
        setGeometry(null, false);
    }
}
