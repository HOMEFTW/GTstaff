package com.andgatech.gtstaff.fakeplayer.runtime;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

final class LegacyRepelRuntime implements BotRepelRuntime {

    private final FakePlayer fakePlayer;

    LegacyRepelRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public boolean repelling() {
        return fakePlayer.isMonsterRepelling();
    }

    @Override
    public int repelRange() {
        return fakePlayer.getMonsterRepelRange();
    }

    @Override
    public void setRepelling(boolean repelling) {
        fakePlayer.setMonsterRepelling(repelling);
    }

    @Override
    public void setRepelRange(int range) {
        fakePlayer.setMonsterRepelRange(range);
    }
}
