package com.makar.utopia.boundary;

public enum CityZone {
    OUTSIDE,
    HILL,
    PORT,
    NEUTRAL;

    public static final int PORT_PROTECTION_MILLIS = 5 * 60 * 1000;
}
