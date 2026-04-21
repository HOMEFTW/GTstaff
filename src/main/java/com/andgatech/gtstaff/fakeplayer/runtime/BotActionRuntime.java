package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;

import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;

public interface BotActionRuntime {

    void start(ActionType type, Action action) throws CommandException;

    void stop(ActionType type) throws CommandException;

    void stopAll() throws CommandException;

    void setSlot(int slot) throws CommandException;

    void setForward(float value) throws CommandException;

    void setStrafing(float value) throws CommandException;

    void stopMovement() throws CommandException;

    void look(float yaw, float pitch) throws CommandException;

    void turn(float yaw, float pitch) throws CommandException;

    void setSneaking(boolean value) throws CommandException;

    void setSprinting(boolean value) throws CommandException;

    void dismount() throws CommandException;

    boolean supportsMount();
}
