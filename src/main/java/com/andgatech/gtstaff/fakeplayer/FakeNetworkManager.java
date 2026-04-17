package com.andgatech.gtstaff.fakeplayer;

import net.minecraft.network.NetworkManager;

public class FakeNetworkManager extends NetworkManager {

    public FakeNetworkManager() {
        // EmbeddedChannel is created to make isChannelOpen() return true
    }

    @Override
    public void sendPacket(Object packet) {
        // No-op
    }

    @Override
    public void closeChannel(Object message) {
        // No-op
    }

    @Override
    public void processReceivedPackets() {
        // No-op
    }
}
