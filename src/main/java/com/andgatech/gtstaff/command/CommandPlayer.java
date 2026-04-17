package com.andgatech.gtstaff.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class CommandPlayer extends CommandBase {

    @Override
    public String getCommandName() {
        return "player";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/player <name> spawn|kill|shadow|attack|use|jump|drop|dropStack|move|look|turn|sneak|unsneak|sprint|unsprint|mount|dismount|hotbar|stop|monitor ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return super.canCommandSenderUseCommand(sender);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // TODO: implement command processing
    }
}
