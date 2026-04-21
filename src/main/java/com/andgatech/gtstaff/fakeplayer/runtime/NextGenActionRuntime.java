package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.entity.Entity;

import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

final class NextGenActionRuntime implements BotActionRuntime {

    private final GTstaffForgePlayer player;

    NextGenActionRuntime(GTstaffForgePlayer player) {
        this.player = player;
    }

    @Override
    public void start(ActionType type, Action action) {
        pack().start(type, action);
    }

    @Override
    public void stop(ActionType type) {
        pack().stop(type);
    }

    @Override
    public void stopAll() {
        pack().stopAll();
    }

    @Override
    public void setSlot(int slot) {
        pack().setSlot(slot);
    }

    @Override
    public void setForward(float value) {
        pack().setForward(value);
    }

    @Override
    public void setStrafing(float value) {
        pack().setStrafing(value);
    }

    @Override
    public void stopMovement() {
        pack().stopMovement();
    }

    @Override
    public void look(float yaw, float pitch) {
        pack().look(yaw, pitch);
    }

    @Override
    public void turn(float yaw, float pitch) {
        pack().turn(yaw, pitch);
    }

    @Override
    public void setSneaking(boolean value) {
        pack().setSneaking(value);
    }

    @Override
    public void setSprinting(boolean value) {
        pack().setSprinting(value);
    }

    @Override
    public void dismount() {
        player.mountEntity((Entity) null);
    }

    @Override
    public boolean supportsMount() {
        return true;
    }

    private PlayerActionPack pack() {
        return player.getActionPack();
    }
}
