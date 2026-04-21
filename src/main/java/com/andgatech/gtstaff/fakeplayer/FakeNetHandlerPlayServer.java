package com.andgatech.gtstaff.fakeplayer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

public class FakeNetHandlerPlayServer extends NetHandlerPlayServer {

    public FakeNetHandlerPlayServer(MinecraftServer server, NetworkManager networkManager, EntityPlayerMP player) {
        super(server, networkManager, player);
    }

    @Override
    public void kickPlayerFromServer(String reason) {
        handleFakePlayerKick(playerEntity, reason);
    }

    static void handleFakePlayerKick(EntityPlayerMP player, String reason) {
        if (!"You have been idle for too long!".equals(reason) && !"You logged in from another location".equals(reason)) {
            return;
        }
        if (player instanceof FakePlayer) {
            ((FakePlayer) player).kill();
            return;
        }
        if (player instanceof GTstaffForgePlayer forgePlayer) {
            forgePlayer.markDisconnected();
            FakePlayerRegistry.unregister(forgePlayer.getCommandSenderName());
            if (forgePlayer.mcServer != null && forgePlayer.mcServer.getConfigurationManager() != null) {
                forgePlayer.mcServer.getConfigurationManager()
                    .playerLoggedOut(forgePlayer);
            }
            forgePlayer.setDead();
        }
    }
}
