package com.andgatech.gtstaff.fakeplayer;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.Packet;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IChatComponent;

public class FakeNetworkManager extends NetworkManager {

    public FakeNetworkManager() {
        super(false);
        this.channel = new EmbeddedChannel();
    }

    @Override
    public void scheduleOutboundPacket(Packet packet, GenericFutureListener[] listeners) {
        // No-op
    }

    @Override
    public void closeChannel(IChatComponent message) {
        // No-op
    }

    @Override
    public void processReceivedPackets() {
        // No-op
    }

    @Override
    public void disableAutoRead() {
        // No-op
    }
}
