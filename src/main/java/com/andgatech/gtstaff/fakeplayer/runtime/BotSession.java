package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import com.andgatech.gtstaff.fakeplayer.FakeNetHandlerPlayServer;
import com.andgatech.gtstaff.fakeplayer.FakeNetworkManager;

public final class BotSession {

    private final GTstaffForgePlayer player;

    public BotSession(GTstaffForgePlayer player) {
        this.player = player;
    }

    public void attach(MinecraftServer server) {
        NetworkManager networkManager = new FakeNetworkManager();
        FakeNetHandlerPlayServer netHandler = new FakeNetHandlerPlayServer(server, networkManager, player);
        server.getConfigurationManager()
            .initializeConnectionToPlayer(networkManager, player, netHandler);
    }

    public EntityPlayerMP player() {
        return player;
    }
}
