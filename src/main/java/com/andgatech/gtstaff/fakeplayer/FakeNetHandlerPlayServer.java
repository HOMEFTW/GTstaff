package com.andgatech.gtstaff.fakeplayer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

public class FakeNetHandlerPlayServer extends NetHandlerPlayServer {

    public FakeNetHandlerPlayServer(MinecraftServer server, NetworkManager networkManager, EntityPlayerMP player) {
        super(server, networkManager, player);
    }

    @Override
    public void kickPlayerFromServer(String reason) {
        if ("You have been idle for too long!".equals(reason) || "You logged in from another location".equals(reason)) {
            if (playerEntity instanceof FakePlayer) {
                ((FakePlayer) playerEntity).kill();
            }
        }
    }
}
