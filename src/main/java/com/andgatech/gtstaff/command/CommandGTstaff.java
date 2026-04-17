package com.andgatech.gtstaff.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.ui.FakePlayerManagerUI;

public class CommandGTstaff extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtstaff";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtstaff [ui]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            openUi(sender);
            return;
        }

        if (args.length == 1 && "ui".equalsIgnoreCase(args[0])) {
            openUi(sender);
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    protected void openUi(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP player)) {
            throw new CommandException("GTstaff UI can only be opened by a player");
        }
        FakePlayerManagerUI.INSTANCE.open(player);
        sender.addChatMessage(new ChatComponentText("[GTstaff] Opening manager UI..."));
    }
}
