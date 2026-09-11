package com.makar.tacticaltablet.camouflage;

import java.util.List;
import java.util.Objects;

public record CamouflagePreset(String id, List<CamouflagePiece> pieces) {
    public CamouflagePreset {
        Objects.requireNonNull(id, "id");
        pieces = List.copyOf(pieces);
    }
}
