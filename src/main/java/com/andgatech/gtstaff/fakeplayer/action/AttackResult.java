package com.andgatech.gtstaff.fakeplayer.action;

public final class AttackResult {

    private final boolean accepted;
    private final boolean usedFallback;
    private final boolean swung;

    public AttackResult(boolean accepted, boolean usedFallback, boolean swung) {
        this.accepted = accepted;
        this.usedFallback = usedFallback;
        this.swung = swung;
    }

    public boolean accepted() {
        return accepted;
    }

    public boolean usedFallback() {
        return usedFallback;
    }

    public boolean swung() {
        return swung;
    }
}
