package com.makar.tacticaltablet.game.chaos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChaosPlayerClasses {
    private final List<String> offered;
    private final Set<String> spent = new LinkedHashSet<>();
    private String selected = "";

    public ChaosPlayerClasses(List<String> offered) {
        this.offered = List.copyOf(offered == null ? List.of() : offered);
    }

    public boolean select(String classId) {
        if (classId == null || selected.length() > 0 || spent.contains(classId) || !offered.contains(classId)) return false;
        selected = classId;
        return true;
    }

    public boolean consumeSelected() {
        if (selected.isBlank()) return false;
        spent.add(selected);
        selected = "";
        return true;
    }

    public boolean isAvailable(String classId) { return offered.contains(classId) && !spent.contains(classId) && selected.isBlank(); }
    public boolean requiresSelection() { return selected.isBlank() && spent.size() < offered.size(); }
    public List<String> offered() { return offered; }
    public Set<String> spent() { return Set.copyOf(spent); }
    public String selected() { return selected; }
}
