package com.makar.tacticaltablet.camouflage;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CamouflageCatalog {
    public static final int SCHEMA_VERSION = 1;
    private static final CamouflageCatalog EMPTY = new CamouflageCatalog(Map.of());

    private final Map<String, CamouflagePreset> presets;

    public CamouflageCatalog(Map<String, CamouflagePreset> presets) {
        this.presets = Map.copyOf(presets);
    }

    public static CamouflageCatalog empty() { return EMPTY; }

    public Optional<CamouflagePreset> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(presets.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Set<String> presetIds() { return presets.keySet(); }
}
