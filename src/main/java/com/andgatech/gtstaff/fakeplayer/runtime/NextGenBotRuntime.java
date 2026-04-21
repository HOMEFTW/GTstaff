package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

public final class NextGenBotRuntime implements BotRuntimeView {

    private final GTstaffForgePlayer player;
    private final BotSession session;
    private final UUID ownerUUID;
    private final BotActionRuntime action;
    private final NextGenFollowRuntime follow;
    private final NextGenMonitorRuntime monitor;
    private final BotRepelRuntime repel;
    private final BotInventoryRuntime inventory;

    public NextGenBotRuntime(GTstaffForgePlayer player, BotSession session) {
        this(player, session, player == null ? null : player.getOwnerUUID());
    }

    public NextGenBotRuntime(GTstaffForgePlayer player, BotSession session, UUID ownerUUID) {
        this.player = player;
        this.session = session;
        this.ownerUUID = ownerUUID;
        this.action = new NextGenActionRuntime(player);
        this.follow = new NextGenFollowRuntime(player);
        this.monitor = new NextGenMonitorRuntime(player);
        this.repel = new NextGenRepelRuntime();
        this.inventory = new NextGenInventoryRuntime(player);
        this.player.bindRuntime(this);
    }

    public BotSession session() {
        return session;
    }

    @Override
    public String name() {
        return player.getCommandSenderName();
    }

    @Override
    public UUID ownerUUID() {
        return ownerUUID;
    }

    @Override
    public int dimension() {
        return player.dimension;
    }

    @Override
    public BotRuntimeType runtimeType() {
        return BotRuntimeType.NEXTGEN;
    }

    @Override
    public BotEntityBridge entity() {
        return () -> player;
    }

    @Override
    public boolean online() {
        return !player.isDead;
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

    void tickRuntimeServices() {
        follow.tick();
        monitor.tick();
    }
}
