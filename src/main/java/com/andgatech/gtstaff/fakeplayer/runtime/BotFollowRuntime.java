package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

public interface BotFollowRuntime {

    boolean following();

    UUID targetUUID();

    int followRange();

    int teleportRange();

    void startFollowing(UUID targetUUID);

    void stop();

    void setFollowRange(int range);

    void setTeleportRange(int range);
}
