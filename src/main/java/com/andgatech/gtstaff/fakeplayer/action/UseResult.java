package com.andgatech.gtstaff.fakeplayer.action;

public final class UseResult {

    private final boolean blockUsed;
    private final boolean itemUsed;
    private final boolean bridgeUsed;
    private final boolean swingTriggered;

    public UseResult(boolean blockUsed, boolean itemUsed, boolean bridgeUsed, boolean swingTriggered) {
        this.blockUsed = blockUsed;
        this.itemUsed = itemUsed;
        this.bridgeUsed = bridgeUsed;
        this.swingTriggered = swingTriggered;
    }

    public boolean blockUsed() {
        return blockUsed;
    }

    public boolean itemUsed() {
        return itemUsed;
    }

    public boolean bridgeUsed() {
        return bridgeUsed;
    }

    public boolean swingTriggered() {
        return swingTriggered;
    }

    public boolean accepted() {
        return blockUsed || itemUsed || bridgeUsed || swingTriggered;
    }
}
