package com.andgatech.gtstaff.fakeplayer.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import com.andgatech.gtstaff.fakeplayer.FakePlayerProfiles;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.mojang.authlib.GameProfile;

public final class NextGenBotFactory {

    @FunctionalInterface
    interface PlayerCreator {

        GTstaffForgePlayer create(MinecraftServer server, WorldServer world, GameProfile profile);
    }

    private final IntFunction<WorldServer> worldResolver;
    private final PlayerCreator playerCreator;
    private final BiConsumer<BotSession, MinecraftServer> sessionAttacher;
    private final BooleanSupplier duplicateNameChecker;

    public NextGenBotFactory() {
        this(null, GTstaffForgePlayer::new, BotSession::attach, () -> false);
    }

    NextGenBotFactory(IntFunction<WorldServer> worldResolver, PlayerCreator playerCreator,
        BiConsumer<BotSession, MinecraftServer> sessionAttacher, BooleanSupplier duplicateNameChecker) {
        this.worldResolver = worldResolver;
        this.playerCreator = Objects.requireNonNull(playerCreator, "playerCreator");
        this.sessionAttacher = Objects.requireNonNull(sessionAttacher, "sessionAttacher");
        this.duplicateNameChecker = Objects.requireNonNull(duplicateNameChecker, "duplicateNameChecker");
    }

    public NextGenBotRuntime spawn(String botName, MinecraftServer server, ChunkCoordinates position, float yaw,
        float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        Objects.requireNonNull(botName, "botName");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(position, "position");
        if (duplicateNameChecker.getAsBoolean()) {
            throw new IllegalStateException("Duplicate fake player names are not supported");
        }

        return createRuntime(
            FakePlayerProfiles.createSpawnProfile(botName),
            server,
            position.posX,
            position.posY,
            position.posZ,
            yaw,
            pitch,
            dimension,
            gameType,
            flying,
            ownerUUID);
    }

    public NextGenBotRuntime restore(MinecraftServer server, FakePlayerRegistry.PersistedBotData data) {
        if (server == null || data == null || data.getName() == null) {
            return null;
        }
        UUID profileId = data.getProfileId();
        if (profileId == null) {
            profileId = EntityPlayer.func_146094_a(new GameProfile(null, data.getName()));
        }
        WorldSettings.GameType gameType = WorldSettings.GameType.getByID(data.getGameTypeId());
        if (gameType == null) {
            gameType = WorldSettings.GameType.SURVIVAL;
        }
        return createRuntime(
            new GameProfile(profileId, data.getName()),
            server,
            data.getPosX(),
            data.getPosY(),
            data.getPosZ(),
            data.getYaw(),
            data.getPitch(),
            data.getDimension(),
            gameType,
            data.isFlying(),
            data.getOwnerUUID());
    }

    public NextGenBotRuntime shadow(MinecraftServer server, EntityPlayerMP sourcePlayer) {
        if (server == null || sourcePlayer == null) {
            return null;
        }

        disconnectSourcePlayer(sourcePlayer);
        WorldSettings.GameType gameType = sourcePlayer.theItemInWorldManager == null ? WorldSettings.GameType.SURVIVAL
            : sourcePlayer.theItemInWorldManager.getGameType();
        if (gameType == null) {
            gameType = WorldSettings.GameType.SURVIVAL;
        }
        boolean flying = sourcePlayer.capabilities != null && sourcePlayer.capabilities.isFlying;
        UUID profileId = UUID.nameUUIDFromBytes(
            ("GTstaff-shadow:" + sourcePlayer.getUniqueID()).getBytes(StandardCharsets.UTF_8));
        NextGenBotRuntime runtime = createRuntime(
            new GameProfile(profileId, sourcePlayer.getCommandSenderName()),
            server,
            sourcePlayer.posX,
            sourcePlayer.posY,
            sourcePlayer.posZ,
            sourcePlayer.rotationYaw,
            sourcePlayer.rotationPitch,
            sourcePlayer.dimension,
            gameType,
            flying,
            sourcePlayer.getUniqueID());
        copyShadowState((GTstaffForgePlayer) runtime.entity()
            .asPlayer(), sourcePlayer, gameType);
        return runtime;
    }

    public BotRuntimeView rebuildRestoredWithProfile(MinecraftServer server, BotRuntimeView oldRuntime,
        GameProfile profile) {
        if (server == null || oldRuntime == null || profile == null || oldRuntime.name() == null) {
            return oldRuntime;
        }
        if (FakePlayerRegistry.getRuntimeView(oldRuntime.name()) != oldRuntime) {
            return oldRuntime;
        }
        EntityPlayerMP oldPlayer = oldRuntime.entity() == null ? null : oldRuntime.entity().asPlayer();
        if (!(oldPlayer instanceof GTstaffForgePlayer forgePlayer)) {
            return oldRuntime;
        }

        WorldSettings.GameType gameType = oldPlayer.theItemInWorldManager == null ? WorldSettings.GameType.SURVIVAL
            : oldPlayer.theItemInWorldManager.getGameType();
        if (gameType == null) {
            gameType = WorldSettings.GameType.SURVIVAL;
        }
        boolean flying = oldPlayer.capabilities != null && oldPlayer.capabilities.isFlying;
        NextGenBotRuntime rebuilt = createRuntime(
            FakePlayerProfiles.copyOf(profile, oldRuntime.name()),
            server,
            oldPlayer.posX,
            oldPlayer.posY,
            oldPlayer.posZ,
            oldPlayer.rotationYaw,
            oldPlayer.rotationPitch,
            oldPlayer.dimension,
            gameType,
            flying,
            oldRuntime.ownerUUID(),
            false);
        GTstaffForgePlayer rebuiltPlayer = (GTstaffForgePlayer) rebuilt.entity().asPlayer();
        copyPlayerState(rebuiltPlayer, oldPlayer, gameType);
        copyRuntimeServices(oldRuntime, rebuilt);
        rebuiltPlayer.respawnFake();
        disconnectRebuiltPlayer(forgePlayer);
        sessionAttacher.accept(rebuilt.session(), server);
        FakePlayerRegistry.registerRuntime(rebuilt);
        return rebuilt;
    }

    private WorldServer resolveWorld(MinecraftServer server, int dimension) {
        if (worldResolver != null) {
            return worldResolver.apply(dimension);
        }
        return server.worldServerForDimension(dimension);
    }

    private void copyShadowState(GTstaffForgePlayer player, EntityPlayerMP sourcePlayer, WorldSettings.GameType gameType) {
        copyPlayerState(player, sourcePlayer, gameType);
    }

    private void copyPlayerState(GTstaffForgePlayer player, EntityPlayerMP sourcePlayer, WorldSettings.GameType gameType) {
        if (player == null || sourcePlayer == null) {
            return;
        }
        player.setPositionAndRotation(
            sourcePlayer.posX,
            sourcePlayer.posY,
            sourcePlayer.posZ,
            sourcePlayer.rotationYaw,
            sourcePlayer.rotationPitch);
        if (player.capabilities != null && sourcePlayer.capabilities != null) {
            player.capabilities.disableDamage = sourcePlayer.capabilities.disableDamage;
            player.capabilities.isFlying = sourcePlayer.capabilities.isFlying;
            player.capabilities.allowFlying = sourcePlayer.capabilities.allowFlying;
            player.capabilities.isCreativeMode = sourcePlayer.capabilities.isCreativeMode;
            player.capabilities.allowEdit = sourcePlayer.capabilities.allowEdit;
        }
        if (player.theItemInWorldManager != null && gameType != null) {
            player.theItemInWorldManager.setGameType(gameType);
        }
        if (player.inventory != null && sourcePlayer.inventory != null) {
            if (sourcePlayer.inventory.mainInventory != null && player.inventory.mainInventory != null) {
                System.arraycopy(
                    sourcePlayer.inventory.mainInventory,
                    0,
                    player.inventory.mainInventory,
                    0,
                    Math.min(sourcePlayer.inventory.mainInventory.length, player.inventory.mainInventory.length));
            }
            if (sourcePlayer.inventory.armorInventory != null && player.inventory.armorInventory != null) {
                System.arraycopy(
                    sourcePlayer.inventory.armorInventory,
                    0,
                    player.inventory.armorInventory,
                    0,
                    Math.min(sourcePlayer.inventory.armorInventory.length, player.inventory.armorInventory.length));
            }
            player.inventory.currentItem = sourcePlayer.inventory.currentItem;
        }
        player.experience = sourcePlayer.experience;
        player.experienceLevel = sourcePlayer.experienceLevel;
        player.experienceTotal = sourcePlayer.experienceTotal;
        try {
            player.setHealth(sourcePlayer.getHealth());
        } catch (RuntimeException ignored) {}
        try {
            player.setSneaking(sourcePlayer.isSneaking());
        } catch (RuntimeException ignored) {}
        try {
            player.setSprinting(sourcePlayer.isSprinting());
        } catch (RuntimeException ignored) {}
    }

    private void copyRuntimeServices(BotRuntimeView sourceRuntime, BotRuntimeView targetRuntime) {
        if (sourceRuntime == null || targetRuntime == null) {
            return;
        }
        if (sourceRuntime.monitor() != null && targetRuntime.monitor() != null) {
            targetRuntime.monitor().setMonitoring(sourceRuntime.monitor().monitoring());
            targetRuntime.monitor().setMonitorRange(sourceRuntime.monitor().monitorRange());
            targetRuntime.monitor().setReminderInterval(sourceRuntime.monitor().reminderInterval());
        }
        if (sourceRuntime.repel() != null && targetRuntime.repel() != null) {
            targetRuntime.repel().setRepelling(sourceRuntime.repel().repelling());
            targetRuntime.repel().setRepelRange(sourceRuntime.repel().repelRange());
        }
        if (sourceRuntime.follow() != null && targetRuntime.follow() != null) {
            targetRuntime.follow().setFollowRange(sourceRuntime.follow().followRange());
            targetRuntime.follow().setTeleportRange(sourceRuntime.follow().teleportRange());
            if (sourceRuntime.follow().targetUUID() != null) {
                targetRuntime.follow().startFollowing(sourceRuntime.follow().targetUUID());
            } else {
                targetRuntime.follow().stop();
            }
        }
    }

    private void disconnectSourcePlayer(EntityPlayerMP sourcePlayer) {
        if (sourcePlayer.playerNetServerHandler != null) {
            sourcePlayer.playerNetServerHandler.kickPlayerFromServer("You logged in from another location");
        }
        if (sourcePlayer.mcServer != null && sourcePlayer.mcServer.getConfigurationManager() != null) {
            sourcePlayer.mcServer.getConfigurationManager()
                .playerLoggedOut(sourcePlayer);
        }
    }

    private void disconnectRebuiltPlayer(GTstaffForgePlayer player) {
        if (player == null) {
            return;
        }
        player.markDisconnected();
        if (player.mcServer != null && player.mcServer.getConfigurationManager() != null) {
            player.mcServer.getConfigurationManager().playerLoggedOut(player);
        }
        player.setDead();
    }

    private NextGenBotRuntime createRuntime(GameProfile profile, MinecraftServer server, double x, double y, double z,
        float yaw, float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        return createRuntime(profile, server, x, y, z, yaw, pitch, dimension, gameType, flying, ownerUUID, true);
    }

    private NextGenBotRuntime createRuntime(GameProfile profile, MinecraftServer server, double x, double y, double z,
        float yaw, float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID,
        boolean attachSession) {
        WorldServer world = resolveWorld(server, dimension);
        GTstaffForgePlayer player = playerCreator.create(server, world, profile);
        player.setOwnerUUID(ownerUUID);
        player.getActionPack();
        player.dimension = dimension;
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        player.setPositionAndRotation(x, y, z, yaw, pitch);
        if (player.capabilities != null) {
            player.capabilities.allowFlying = flying || gameType == WorldSettings.GameType.CREATIVE;
            player.capabilities.isFlying = flying;
        }
        if (player.theItemInWorldManager != null && gameType != null) {
            player.theItemInWorldManager.setGameType(gameType);
        }

        BotSession session = new BotSession(player);
        NextGenBotRuntime runtime = new NextGenBotRuntime(player, session, ownerUUID);
        if (attachSession) {
            sessionAttacher.accept(session, server);
        }
        return runtime;
    }
}
