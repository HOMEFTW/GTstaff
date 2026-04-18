package com.andgatech.gtstaff.fakeplayer;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

public class FollowService {

    public static final int DEFAULT_FOLLOW_RANGE = 3;
    public static final int DEFAULT_TELEPORT_RANGE = 32;
    public static final int CROSS_DIM_DELAY_TICKS = 100;
    private static final float Y_THRESHOLD = 0.5F;

    private UUID followTargetUUID;
    private int followRange = DEFAULT_FOLLOW_RANGE;
    private int teleportRange = DEFAULT_TELEPORT_RANGE;
    private int crossDimTicksRemaining = 0;
    private int previousTargetDimension = Integer.MIN_VALUE;
    private boolean pendingCrossDimMessage = false;

    private final FakePlayer fakePlayer;

    public FollowService(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    public void tick() {
        if (followTargetUUID == null) return;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            stop();
            return;
        }

        EntityPlayerMP target = findTargetPlayer(server);
        if (target == null || target.isDead) {
            stop();
            return;
        }

        if (fakePlayer.dimension != target.dimension) {
            handleCrossDimension(target);
            return;
        }

        crossDimTicksRemaining = 0;
        pendingCrossDimMessage = false;

        double dx = target.posX - fakePlayer.posX;
        double dy = target.posY - fakePlayer.posY;
        double dz = target.posZ - fakePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > teleportRange) {
            teleportNearTarget(target);
            return;
        }

        if (distance < followRange) {
            fakePlayer.moveForward = 0.0F;
            fakePlayer.moveStrafing = 0.0F;
            fakePlayer.setJumping(false);
            return;
        }

        fakePlayer.capabilities.isFlying = target.capabilities.isFlying;
        if (target.capabilities.isFlying) {
            fakePlayer.capabilities.allowFlying = true;
        }

        float[] movement = calculateMovement(fakePlayer.rotationYaw, fakePlayer.posX, fakePlayer.posZ, target.posX, target.posZ);
        fakePlayer.moveForward = movement[0];
        fakePlayer.moveStrafing = movement[1];

        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        fakePlayer.rotationYaw = targetYaw;
        fakePlayer.rotationYawHead = targetYaw;
        fakePlayer.renderYawOffset = targetYaw;

        boolean isAirborne = !fakePlayer.onGround || fakePlayer.capabilities.isFlying;
        if (isAirborne) {
            if (shouldJump(fakePlayer.posY, target.posY, fakePlayer.capabilities.isFlying)) {
                if (fakePlayer.capabilities.isFlying) {
                    fakePlayer.motionY = Math.min(fakePlayer.motionY + 0.2D, 0.8D);
                } else {
                    fakePlayer.setJumping(true);
                }
            } else if (fakePlayer.capabilities.isFlying && shouldDescend(fakePlayer.posY, target.posY, fakePlayer.capabilities.isFlying)) {
                fakePlayer.motionY = Math.max(fakePlayer.motionY - 0.2D, -0.8D);
            } else {
                fakePlayer.setJumping(false);
                if (fakePlayer.capabilities.isFlying && Math.abs(target.posY - fakePlayer.posY) <= Y_THRESHOLD) {
                    fakePlayer.motionY *= 0.5D;
                }
            }
        }
    }

    private EntityPlayerMP findTargetPlayer(MinecraftServer server) {
        for (Object playerObj : server.getConfigurationManager().playerEntityList) {
            if (playerObj instanceof EntityPlayerMP player && followTargetUUID.equals(player.getUniqueID())) {
                return player;
            }
        }
        return null;
    }

    private void handleCrossDimension(EntityPlayerMP target) {
        int targetDim = target.dimension;

        // If target moved to yet another dimension during countdown, reset
        if (previousTargetDimension != Integer.MIN_VALUE && targetDim != previousTargetDimension) {
            crossDimTicksRemaining = CROSS_DIM_DELAY_TICKS;
            pendingCrossDimMessage = true;
        }
        previousTargetDimension = targetDim;

        // Start countdown if not already running
        if (crossDimTicksRemaining <= 0 && !pendingCrossDimMessage) {
            crossDimTicksRemaining = CROSS_DIM_DELAY_TICKS;
            pendingCrossDimMessage = true;
        }

        // Send notification message (only once per countdown start or reset)
        if (pendingCrossDimMessage) {
            String botName = FakePlayer.colorizeName(fakePlayer.getCommandSenderName());
            int seconds = (crossDimTicksRemaining + 19) / 20; // ceil division
            target.addChatMessage(new ChatComponentText("[GTstaff] " + botName + " 将在 " + seconds + " 秒后传送至你的维度"));
            pendingCrossDimMessage = false;
        }

        // Count down
        if (crossDimTicksRemaining > 0) {
            crossDimTicksRemaining--;
            if (crossDimTicksRemaining == 0) {
                // Check target is still in a different dimension before teleporting
                if (fakePlayer.dimension != target.dimension) {
                    executeCrossDimensionTeleport(target);
                }
                previousTargetDimension = Integer.MIN_VALUE;
            }
        }
    }

    private void executeCrossDimensionTeleport(EntityPlayerMP target) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        int targetDim = target.dimension;
        WorldServer newWorld = server.worldServerForDimension(targetDim);
        if (newWorld == null) return;

        try {
            server.getConfigurationManager().transferPlayerToDimension(fakePlayer, targetDim);
        } catch (Exception e) {
            // If vanilla transfer fails, skip this attempt and retry next cycle
            previousTargetDimension = Integer.MIN_VALUE;
            return;
        }

        // Verify dimension actually changed
        if (fakePlayer.dimension != targetDim) {
            previousTargetDimension = Integer.MIN_VALUE;
            return;
        }

        // Override position to match target (vanilla transfer uses portal/spawn point)
        fakePlayer.setPositionAndUpdate(target.posX, target.posY, target.posZ);
        fakePlayer.theItemInWorldManager.setWorld(newWorld);

        if (fakePlayer.playerNetServerHandler != null) {
            fakePlayer.playerNetServerHandler.setPlayerLocation(target.posX, target.posY, target.posZ, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        }

        // Sync flying state after dimension transfer
        fakePlayer.capabilities.isFlying = target.capabilities.isFlying;
        if (target.capabilities.isFlying) {
            fakePlayer.capabilities.allowFlying = true;
        }
        fakePlayer.sendPlayerAbilities();
    }

    private void teleportNearTarget(EntityPlayerMP target) {
        float offsetX = -MathHelper.sin(target.rotationYaw * (float) Math.PI / 180.0F) * 2.0F;
        float offsetZ = MathHelper.cos(target.rotationYaw * (float) Math.PI / 180.0F) * 2.0F;
        double newX = target.posX + offsetX;
        double newY = target.posY;
        double newZ = target.posZ + offsetZ;

        fakePlayer.setPositionAndUpdate(newX, newY, newZ);
        if (fakePlayer.playerNetServerHandler != null) {
            fakePlayer.playerNetServerHandler.setPlayerLocation(newX, newY, newZ, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        }
    }

    public static float[] calculateMovement(float fakeYaw, double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.01D) {
            return new float[] { 0.0F, 0.0F };
        }

        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        float yawDiff = normalizeYawDiff(targetYaw - fakeYaw);
        double yawRad = Math.toRadians(yawDiff);

        float moveForward = (float) Math.cos(yawRad);
        float moveStrafing = (float) Math.sin(yawRad);

        float maxAbs = Math.max(Math.abs(moveForward), Math.abs(moveStrafing));
        if (maxAbs > 1.0F) {
            moveForward /= maxAbs;
            moveStrafing /= maxAbs;
        }

        return new float[] { moveForward, moveStrafing };
    }

    public static float normalizeYawDiff(float diff) {
        diff = diff % 360.0F;
        if (diff > 180.0F) {
            diff -= 360.0F;
        } else if (diff < -180.0F) {
            diff += 360.0F;
        }
        return diff;
    }

    public static boolean shouldJump(double fakeY, double targetY, boolean isFlying) {
        return (isFlying || !isOnGround(fakeY)) && targetY - fakeY > Y_THRESHOLD;
    }

    public static boolean shouldDescend(double fakeY, double targetY, boolean isFlying) {
        return isFlying && fakeY - targetY > Y_THRESHOLD;
    }

    private static boolean isOnGround(double y) {
        return y == Math.floor(y);
    }

    public boolean isFollowing() {
        return followTargetUUID != null;
    }

    public UUID getFollowTargetUUID() {
        return followTargetUUID;
    }

    public int getFollowRange() {
        return followRange;
    }

    public void setFollowRange(int range) {
        this.followRange = Math.max(1, range);
    }

    public int getTeleportRange() {
        return teleportRange;
    }

    public void setTeleportRange(int range) {
        this.teleportRange = Math.max(followRange + 1, range);
    }

    public void startFollowing(UUID targetUUID) {
        this.followTargetUUID = targetUUID;
        this.crossDimTicksRemaining = 0;
        this.pendingCrossDimMessage = false;
        this.previousTargetDimension = Integer.MIN_VALUE;
    }

    public void stop() {
        this.followTargetUUID = null;
        this.crossDimTicksRemaining = 0;
        this.pendingCrossDimMessage = false;
        this.previousTargetDimension = Integer.MIN_VALUE;
    }
}
