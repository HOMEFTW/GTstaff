package com.andgatech.gtstaff.util;

import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

public class PermissionHelper {

    public static boolean cantSpawn(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP player)) return false;
        if (FakePlayerRegistry.getCount() >= Config.maxBotsTotal) return true;
        if (FakePlayerRegistry.getCountByOwner(player.getUniqueID()) >= Config.maxBotsPerPlayer) return true;
        return false;
    }

    public static boolean cantManipulate(ICommandSender sender, FakePlayer target) {
        if (!(sender instanceof EntityPlayerMP player)) return false;
        if (isOp(player)) return false;
        if (Config.allowNonOpControlOwnBot && target.getOwnerUUID() != null && target.getOwnerUUID()
            .equals(player.getUniqueID())) return false;
        return true;
    }

    public static boolean cantRemove(ICommandSender sender, FakePlayer target) {
        if (!(sender instanceof EntityPlayerMP player)) return false;
        if (isOp(player)) return false;
        if (target.getOwnerUUID() != null && target.getOwnerUUID()
            .equals(player.getUniqueID())) return false;
        return true;
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.canCommandSenderUseCommand(Config.fakePlayerPermissionLevel, "gtstaff");
    }
}
