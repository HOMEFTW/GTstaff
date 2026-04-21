package com.andgatech.gtstaff.fakeplayer.runtime;

final class NextGenRepelRuntime implements BotRepelRuntime {

    private boolean repelling;
    private int repelRange = 64;

    @Override
    public boolean repelling() {
        return repelling;
    }

    @Override
    public int repelRange() {
        return repelRange;
    }

    @Override
    public void setRepelling(boolean repelling) {
        this.repelling = repelling;
    }

    @Override
    public void setRepelRange(int range) {
        repelRange = range;
    }
}
