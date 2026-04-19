package com.andgatech.gtstaff.fakeplayer;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

public class FollowService {

    interface ServerProvider {

        MinecraftServer getServer();
    }

    interface CrossDimensionMover {

        boolean move(FakePlayer fakePlayer, EntityPlayerMP target, MinecraftServer server);
    }

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
    private final ServerProvider serverProvider;
    private final CrossDimensionMover crossDimensionMover;

    public FollowService(FakePlayer fakePlayer) {
        this(fakePlayer, MinecraftServer::getServer, null);
    }

    FollowService(FakePlayer fakePlayer, ServerProvider serverProvider, CrossDimensionMover crossDimensionMover) {
        this.fakePlayer = fakePlayer;
        this.serverProvider = serverProvider;
        this.crossDimensionMover = crossDimensionMover != null ? crossDimensionMover : this::moveToTargetDimension;
    }

    public void tick() {
        if (followTargetUUID == null) return;

        MinecraftServer server = serverProvider.getServer();
        if (server == null) {
            stop();
            return;
        }

        EntityPlayerMP target = findTargetPlayer(server);
        if (target == null || target.isDead) {
            resetCrossDimensionState();
            return;
        }

        if (fakePlayer.dimension != target.dimension) {
            handleCrossDimension(target, server);
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

        float[] movement = calculateMovement(
            fakePlayer.rotationYaw,
            fakePlayer.posX,
            fakePlayer.posZ,
            target.posX,
            target.posZ);
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
            } else if (fakePlayer.capabilities.isFlying
                && shouldDescend(fakePlayer.posY, target.posY, fakePlayer.capabilities.isFlying)) {
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
        EntityPlayerMP deadMatch = null;
        for (Object playerObj : server.getConfigurationManager().playerEntityList) {
            if (playerObj instanceof EntityPlayerMP player && followTargetUUID.equals(player.getUniqueID())) {
                if (!player.isDead) {
                    return player;
                }
                if (deadMatch == null) {
                    deadMatch = player;
                }
            }
        }
        return deadMatch;
    }

    private void handleCrossDimension(EntityPlayerMP target, MinecraftServer server) {
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
                    executeCrossDimensionTeleport(target, server);
                }
                previousTargetDimension = Integer.MIN_VALUE;
            }
        }
    }

    private boolean executeCrossDimensionTeleport(EntityPlayerMP target, MinecraftServer server) {
        TeleportSnapshot snapshot = TeleportSnapshot.capture(fakePlayer);
        boolean moved = false;

        try {
            moved = crossDimensionMover.move(fakePlayer, target, server);
            return moved;
        } finally {
            if (!moved) {
                snapshot.restore(fakePlayer);
            }
        }
    }

    private boolean moveToTargetDimension(FakePlayer fakePlayer, EntityPlayerMP target, MinecraftServer server) {
        int targetDim = target.dimension;
        WorldServer oldWorld = (WorldServer) fakePlayer.worldObj;
        WorldServer newWorld = server.worldServerForDimension(targetDim);
        if (newWorld == null) return false;
        if (fakePlayer.dimension == targetDim && oldWorld == newWorld) return true;

        TeleportSnapshot snapshot = TeleportSnapshot.capture(fakePlayer);
        ServerConfigurationManager configurationManager = server.getConfigurationManager();

        try {
            detachPlayerFromWorld(fakePlayer, oldWorld);
            if (!attachPlayerToWorld(
                fakePlayer,
                targetDim,
                newWorld,
                configurationManager,
                target.posX,
                target.posY,
                target.posZ)) {
                rollbackFailedMove(fakePlayer, snapshot, configurationManager);
                return false;
            }

            fakePlayer.capabilities.isFlying = target.capabilities.isFlying;
            if (target.capabilities.isFlying) {
                fakePlayer.capabilities.allowFlying = true;
            }
            fakePlayer.sendPlayerAbilities();
            fakePlayer.fallDistance = 0.0F;
            return true;
        } catch (RuntimeException e) {
            rollbackFailedMove(fakePlayer, snapshot, configurationManager);
            return false;
        }
    }

    private void detachPlayerFromWorld(FakePlayer fakePlayer, WorldServer world) {
        if (world == null) {
            return;
        }

        world.getPlayerManager()
            .removePlayer(fakePlayer);
        world.playerEntities.remove(fakePlayer);
        world.updateAllPlayersSleepingFlag();

        int chunkX = fakePlayer.chunkCoordX;
        int chunkZ = fakePlayer.chunkCoordZ;
        if (fakePlayer.addedToChunk && world.getChunkProvider()
            .chunkExists(chunkX, chunkZ)) {
            world.getChunkFromChunkCoords(chunkX, chunkZ)
                .removeEntity(fakePlayer);
            world.getChunkFromChunkCoords(chunkX, chunkZ).isModified = true;
        }

        world.loadedEntityList.remove(fakePlayer);
        world.onEntityRemoved(fakePlayer);
        fakePlayer.addedToChunk = false;
        fakePlayer.isDead = false;
    }

    private boolean attachPlayerToWorld(FakePlayer fakePlayer, int targetDimension, WorldServer world,
        ServerConfigurationManager configurationManager, double x, double y, double z) {
        if (world == null) {
            return false;
        }

        world.theChunkProviderServer.loadChunk(MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4);

        fakePlayer.isDead = false;
        fakePlayer.dimension = targetDimension;
        fakePlayer.setWorld(world);
        if (fakePlayer.theItemInWorldManager != null) {
            fakePlayer.theItemInWorldManager.setWorld(world);
        }
        fakePlayer.setLocationAndAngles(x, y, z, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        fakePlayer.addedToChunk = false;

        if (!world.spawnEntityInWorld(fakePlayer)) {
            return false;
        }

        fakePlayer.setWorld(world);
        fakePlayer.setLocationAndAngles(x, y, z, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        world.updateEntityWithOptionalForce(fakePlayer, false);

        configurationManager.func_72375_a(fakePlayer, world);
        if (!configurationManager.playerEntityList.contains(fakePlayer)) {
            configurationManager.playerEntityList.add(fakePlayer);
        }

        if (fakePlayer.playerNetServerHandler != null) {
            fakePlayer.playerNetServerHandler
                .setPlayerLocation(x, y, z, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        }

        if (fakePlayer.theItemInWorldManager != null) {
            fakePlayer.theItemInWorldManager.setWorld(world);
        }
        configurationManager.updateTimeAndWeatherForPlayer(fakePlayer, world);
        configurationManager.syncPlayerInventory(fakePlayer);
        fakePlayer.sendPlayerAbilities();
        return fakePlayer.dimension == targetDimension && fakePlayer.worldObj == world;
    }

    private void rollbackFailedMove(FakePlayer fakePlayer, TeleportSnapshot snapshot,
        ServerConfigurationManager configurationManager) {
        WorldServer currentWorld = fakePlayer.worldObj instanceof WorldServer ? (WorldServer) fakePlayer.worldObj
            : null;
        if (currentWorld != null) {
            detachPlayerFromWorld(fakePlayer, currentWorld);
        }

        snapshot.restore(fakePlayer);
        WorldServer originalWorld = snapshot.world;
        if (originalWorld != null) {
            attachPlayerToWorld(
                fakePlayer,
                snapshot.dimension,
                originalWorld,
                configurationManager,
                snapshot.posX,
                snapshot.posY,
                snapshot.posZ);
        }
    }

    private void teleportNearTarget(EntityPlayerMP target) {
        float offsetX = -MathHelper.sin(target.rotationYaw * (float) Math.PI / 180.0F) * 2.0F;
        float offsetZ = MathHelper.cos(target.rotationYaw * (float) Math.PI / 180.0F) * 2.0F;
        double newX = target.posX + offsetX;
        double newY = target.posY;
        double newZ = target.posZ + offsetZ;

        fakePlayer.setPositionAndUpdate(newX, newY, newZ);
        if (fakePlayer.playerNetServerHandler != null) {
            fakePlayer.playerNetServerHandler
                .setPlayerLocation(newX, newY, newZ, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
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
        resetCrossDimensionState();
    }

    public void stop() {
        this.followTargetUUID = null;
        resetCrossDimensionState();
    }

    private void resetCrossDimensionState() {
        this.crossDimTicksRemaining = 0;
        this.pendingCrossDimMessage = false;
        this.previousTargetDimension = Integer.MIN_VALUE;
    }

    private static final class TeleportSnapshot {

        private final int dimension;
        private final WorldServer world;
        private final double posX;
        private final double posY;
        private final double posZ;
        private final float rotationYaw;
        private final float rotationPitch;
        private final boolean isDead;
        private final boolean addedToChunk;
        private final boolean isFlying;
        private final boolean allowFlying;

        private TeleportSnapshot(FakePlayer fakePlayer) {
            this.dimension = fakePlayer.dimension;
            this.world = fakePlayer.worldObj instanceof WorldServer ? (WorldServer) fakePlayer.worldObj : null;
            this.posX = fakePlayer.posX;
            this.posY = fakePlayer.posY;
            this.posZ = fakePlayer.posZ;
            this.rotationYaw = fakePlayer.rotationYaw;
            this.rotationPitch = fakePlayer.rotationPitch;
            this.isDead = fakePlayer.isDead;
            this.addedToChunk = fakePlayer.addedToChunk;
            this.isFlying = fakePlayer.capabilities != null && fakePlayer.capabilities.isFlying;
            this.allowFlying = fakePlayer.capabilities != null && fakePlayer.capabilities.allowFlying;
        }

        private static TeleportSnapshot capture(FakePlayer fakePlayer) {
            return new TeleportSnapshot(fakePlayer);
        }

        private void restore(FakePlayer fakePlayer) {
            fakePlayer.dimension = dimension;
            if (world != null) {
                fakePlayer.setWorld(world);
                if (fakePlayer.theItemInWorldManager != null) {
                    fakePlayer.theItemInWorldManager.setWorld(world);
                }
            }
            fakePlayer.setLocationAndAngles(posX, posY, posZ, rotationYaw, rotationPitch);
            fakePlayer.isDead = isDead;
            fakePlayer.addedToChunk = addedToChunk;
            if (fakePlayer.capabilities != null) {
                fakePlayer.capabilities.isFlying = isFlying;
                fakePlayer.capabilities.allowFlying = allowFlying;
            }
        }
    }
}
