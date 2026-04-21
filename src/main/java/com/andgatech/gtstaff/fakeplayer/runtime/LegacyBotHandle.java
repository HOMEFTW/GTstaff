package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public final class LegacyBotHandle implements BotRuntimeView {

    private final FakePlayer fakePlayer;
    private final BotActionRuntime action;
    private final BotFollowRuntime follow;
    private final BotMonitorRuntime monitor;
    private final BotRepelRuntime repel;
    private final BotInventoryRuntime inventory;

    public LegacyBotHandle(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
        this.action = new LegacyActionRuntime(fakePlayer);
        this.follow = new LegacyFollowRuntime(fakePlayer);
        this.monitor = new LegacyMonitorRuntime(fakePlayer);
        this.repel = new LegacyRepelRuntime(fakePlayer);
        this.inventory = new LegacyInventoryRuntime(fakePlayer);
    }

    @Override
    public String name() {
        return fakePlayer.getCommandSenderName();
    }

    @Override
    public UUID ownerUUID() {
        return fakePlayer.getOwnerUUID();
    }

    @Override
    public int dimension() {
        return fakePlayer.dimension;
    }

    @Override
    public BotRuntimeType runtimeType() {
        return BotRuntimeType.LEGACY;
    }

    @Override
    public BotEntityBridge entity() {
        return () -> fakePlayer;
    }

    @Override
    public boolean online() {
        return !fakePlayer.isDead;
    }

    @Override
    public BotActionRuntime action() {
        return action;
    }

    @Override
    public BotFollowRuntime follow() {
        return follow;
    }

    @Override
    public BotMonitorRuntime monitor() {
        return monitor;
    }

    @Override
    public BotRepelRuntime repel() {
        return repel;
    }

    @Override
    public BotInventoryRuntime inventory() {
        return inventory;
    }
}
