package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;

import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

final class LegacyActionRuntime implements BotActionRuntime {

    private final FakePlayer fakePlayer;

    LegacyActionRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public void start(ActionType type, Action action) throws CommandException {
        pack().start(type, action);
    }

    @Override
    public void stop(ActionType type) throws CommandException {
        pack().stop(type);
    }

    @Override
    public void stopAll() throws CommandException {
        pack().stopAll();
    }

    @Override
    public void setSlot(int slot) throws CommandException {
        pack().setSlot(slot);
    }

    @Override
    public void setForward(float value) throws CommandException {
        pack().setForward(value);
    }

    @Override
    public void setStrafing(float value) throws CommandException {
        pack().setStrafing(value);
    }

    @Override
    public void stopMovement() throws CommandException {
        pack().stopMovement();
    }

    @Override
    public void look(float yaw, float pitch) throws CommandException {
        pack().look(yaw, pitch);
    }

    @Override
    public void turn(float yaw, float pitch) throws CommandException {
        pack().turn(yaw, pitch);
    }

    @Override
    public void setSneaking(boolean value) throws CommandException {
        pack().setSneaking(value);
    }

    @Override
    public void setSprinting(boolean value) throws CommandException {
        pack().setSprinting(value);
    }

    @Override
    public void dismount() {
        fakePlayer.mountEntity((Entity) null);
    }

    @Override
    public boolean supportsMount() {
        return true;
    }

    private PlayerActionPack pack() throws CommandException {
        if (!(fakePlayer instanceof IFakePlayerHolder holder) || holder.getActionPack() == null) {
            throw new CommandException("Action pack is unavailable for " + fakePlayer.getCommandSenderName());
        }
        return holder.getActionPack();
    }
}
