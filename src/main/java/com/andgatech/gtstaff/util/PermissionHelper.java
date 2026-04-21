package com.andgatech.gtstaff.util;

import java.util.Optional;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotHandle;

public class PermissionHelper {

    public static Optional<String> cantSpawn(ICommandSender sender, String botName, MinecraftServer server) {
        if (botName == null || botName.trim()
            .isEmpty()) {
            return Optional.of("Invalid bot name");
        }
        if (FakePlayerRegistry.contains(botName)) {
            return Optional.of("Fake player already exists");
        }
        if (server != null && server.getConfigurationManager() != null
            && server.getConfigurationManager()
                .func_152612_a(botName) != null) {
            return Optional.of("Player already online");
        }
        if (FakePlayerRegistry.getCount() >= Config.maxBotsTotal) {
            return Optional.of("Server bot limit reached");
        }
        if (!(sender instanceof EntityPlayerMP player)) {
            return Optional.empty();
        }
        if (FakePlayerRegistry.getCountByOwner(player.getUniqueID()) >= Config.maxBotsPerPlayer) {
            return Optional.of("Player bot limit reached");
        }
        return Optional.empty();
    }

    public static boolean cantManipulate(ICommandSender sender, FakePlayer target) {
        return cantManipulate(sender, target == null ? null : target.asRuntimeView());
    }

    public static boolean cantManipulate(ICommandSender sender, BotHandle target) {
        if (!(sender instanceof EntityPlayerMP player)) return false;
        if (isOp(player)) return false;
        if (Config.allowNonOpControlOwnBot && target != null && target.ownerUUID() != null
            && target.ownerUUID()
                .equals(player.getUniqueID()))
            return false;
        return true;
    }

    public static boolean cantRemove(ICommandSender sender, FakePlayer target) {
        return cantRemove(sender, target == null ? null : target.asRuntimeView());
    }

    public static boolean cantRemove(ICommandSender sender, BotHandle target) {
        if (!(sender instanceof EntityPlayerMP player)) return false;
        if (isOp(player)) return false;
        if (target != null && target.ownerUUID() != null && target.ownerUUID()
            .equals(player.getUniqueID())) return false;
        return true;
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.canCommandSenderUseCommand(Config.fakePlayerPermissionLevel, "gtstaff");
    }
}
