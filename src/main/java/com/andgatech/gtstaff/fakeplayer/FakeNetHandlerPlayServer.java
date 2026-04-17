package com.andgatech.gtstaff.fakeplayer;

import net.minecraft.network.NetHandlerPlayServer;

public class FakeNetHandlerPlayServer extends NetHandlerPlayServer {

    public FakeNetHandlerPlayServer() {
        super(null, null, null);
    }

    @Override
    public void disconnect(String reason) {
        // Only respond to idling and duplicate_login
        if ("idling".equals(reason) || "duplicate_login".equals(reason)) {
            // Trigger FakePlayer.kill()
        }
    }

    @Override
    public void setPlayerLocation(double x, double y, double z, float yaw, float pitch) {
        super.setPlayerLocation(x, y, z, yaw, pitch);
        // Immediately reset position sync after teleport
    }
}
