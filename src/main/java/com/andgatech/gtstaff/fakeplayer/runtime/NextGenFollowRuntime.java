package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import com.andgatech.gtstaff.fakeplayer.FollowService;

final class NextGenFollowRuntime implements BotFollowRuntime {

    private final FollowService service;

    NextGenFollowRuntime(GTstaffForgePlayer player) {
        this(new FollowService(player));
    }

    NextGenFollowRuntime(GTstaffForgePlayer player, FollowService service) {
        this(service);
    }

    private NextGenFollowRuntime(FollowService service) {
        this.service = service;
    }

    @Override
    public boolean following() {
        return service != null && service.isFollowing();
    }

    @Override
    public UUID targetUUID() {
        return service == null ? null : service.getFollowTargetUUID();
    }

    @Override
    public int followRange() {
        return service == null ? FollowService.DEFAULT_FOLLOW_RANGE : service.getFollowRange();
    }

    @Override
    public int teleportRange() {
        return service == null ? FollowService.DEFAULT_TELEPORT_RANGE : service.getTeleportRange();
    }

    @Override
    public void startFollowing(UUID targetUUID) {
        if (service != null) {
            service.startFollowing(targetUUID);
        }
    }

    @Override
    public void stop() {
        if (service != null) {
            service.stop();
        }
    }

    @Override
    public void setFollowRange(int range) {
        if (service != null) {
            service.setFollowRange(range);
        }
    }

    @Override
    public void setTeleportRange(int range) {
        if (service != null) {
            service.setTeleportRange(range);
        }
    }

    void tick() {
        if (service != null) {
            service.tick();
        }
    }
}
