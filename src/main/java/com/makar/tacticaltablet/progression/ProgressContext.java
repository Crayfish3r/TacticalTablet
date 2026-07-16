package com.makar.tacticaltablet.progression;

record ProgressContext(boolean competitiveSet) {
    static ProgressContext standard() {
        return new ProgressContext(false);
    }

    static ProgressContext competitive() {
        return new ProgressContext(true);
    }
}
