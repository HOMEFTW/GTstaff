package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FollowService;

final class LegacyFollowRuntime implements BotFollowRuntime {

    private final FakePlayer fakePlayer;

    LegacyFollowRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public boolean following() {
        return fakePlayer.isFollowing();
    }

    @Override
    public UUID targetUUID() {
        FollowService service = fakePlayer.getFollowService();
        return service == null ? null : service.getFollowTargetUUID();
    }

    @Override
    public int followRange() {
        FollowService service = fakePlayer.getFollowService();
        return service == null ? FollowService.DEFAULT_FOLLOW_RANGE : service.getFollowRange();
    }

    @Override
    public int teleportRange() {
        FollowService service = fakePlayer.getFollowService();
        return service == null ? FollowService.DEFAULT_TELEPORT_RANGE : service.getTeleportRange();
    }

    @Override
    public void startFollowing(UUID targetUUID) {
        fakePlayer.getFollowService().startFollowing(targetUUID);
    }

    @Override
    public void stop() {
        fakePlayer.getFollowService().stop();
    }

    @Override
    public void setFollowRange(int range) {
        fakePlayer.getFollowService().setFollowRange(range);
    }

    @Override
    public void setTeleportRange(int range) {
        fakePlayer.getFollowService().setTeleportRange(range);
    }
}
