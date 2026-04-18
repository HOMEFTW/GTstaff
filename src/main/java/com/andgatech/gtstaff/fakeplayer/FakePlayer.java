package com.andgatech.gtstaff.fakeplayer;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.FoodStats;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import com.google.common.base.Charsets;
import com.mojang.authlib.GameProfile;

public class FakePlayer extends EntityPlayerMP {

    private UUID ownerUUID;
    private final MachineMonitorService machineMonitorService;
    private boolean disconnected;

    public FakePlayer(MinecraftServer server, WorldServer world, GameProfile profile) {
        super(server, world, profile, new ItemInWorldManager(world));
        this.machineMonitorService = new MachineMonitorService();
        this.playerNetServerHandler = new FakeNetHandlerPlayServer(server, new FakeNetworkManager(), this);
        this.dimension = world.provider.dimensionId;
        this.theItemInWorldManager.setWorld(world);
    }

    public FakePlayer(MinecraftServer server, WorldServer world, String username) {
        this(server, world, new GameProfile(null, username));
    }

    public static FakePlayer createFake(String username, MinecraftServer server, ChunkCoordinates pos, float yaw,
        float pitch, int dimension, WorldSettings.GameType gamemode, boolean flying) {
        String safeUsername = username == null ? "" : username;
        GameProfile profile = new GameProfile(
            EntityPlayer.func_146094_a(new GameProfile(null, safeUsername)),
            safeUsername);
        ChunkCoordinates spawnPoint = pos;
        FakePlayer fakePlayer = createWithProfile(
            profile,
            server,
            spawnPoint == null ? Double.NaN : spawnPoint.posX + 0.5D,
            spawnPoint == null ? Double.NaN : spawnPoint.posY,
            spawnPoint == null ? Double.NaN : spawnPoint.posZ + 0.5D,
            yaw,
            pitch,
            dimension,
            gamemode,
            flying);
        fakePlayer.replaceExistingRegistryEntry();
        FakePlayerRegistry.register(fakePlayer, null);
        fakePlayer.respawnFake();
        return fakePlayer;
    }

    public static FakePlayer createShadow(MinecraftServer server, EntityPlayerMP player) {
        if (player == null) {
            return null;
        }

        WorldServer world = resolveWorld(server, player.dimension);
        if (world == null) {
            return null;
        }

        UUID shadowUUID = UUID.nameUUIDFromBytes(("GTstaff-shadow:" + player.getUniqueID()).getBytes(Charsets.UTF_8));
        disconnectSourcePlayer(player);
        FakePlayer shadow = createWithProfile(
            new GameProfile(shadowUUID, player.getCommandSenderName()),
            server,
            player.posX,
            player.posY,
            player.posZ,
            player.rotationYaw,
            player.rotationPitch,
            player.dimension,
            player.theItemInWorldManager.getGameType(),
            player.capabilities.isFlying);
        shadow.clonePlayer(player, true);
        shadow.copyCapabilitiesFrom(player);
        shadow.applyCreationState(
            player.posX,
            player.posY,
            player.posZ,
            player.rotationYaw,
            player.rotationPitch,
            player.capabilities.isFlying,
            player.theItemInWorldManager.getGameType());
        shadow.setOwnerUUID(player.getUniqueID());
        shadow.replaceExistingRegistryEntry();
        FakePlayerRegistry.register(shadow, player.getUniqueID());
        shadow.respawnFake();
        return shadow;
    }

    public static FakePlayer restorePersisted(MinecraftServer server, FakePlayerRegistry.PersistedBotData data) {
        if (server == null || data == null || data.getName() == null) {
            return null;
        }

        UUID profileId = data.getProfileId();
        if (profileId == null) {
            profileId = EntityPlayer.func_146094_a(new GameProfile(null, data.getName()));
        }

        FakePlayer fakePlayer = createWithProfile(
            new GameProfile(profileId, data.getName()),
            server,
            data.getPosX(),
            data.getPosY(),
            data.getPosZ(),
            data.getYaw(),
            data.getPitch(),
            data.getDimension(),
            WorldSettings.GameType.getByID(data.getGameTypeId()),
            data.isFlying());
        fakePlayer.setOwnerUUID(data.getOwnerUUID());
        fakePlayer.setMonitoring(data.isMonitoring());
        fakePlayer.setMonitorRange(data.getMonitorRange());
        fakePlayer.replaceExistingRegistryEntry();
        fakePlayer.respawnFake();
        return fakePlayer;
    }

    public void respawnFake() {
        this.isDead = false;
        this.deathTime = 0;
        this.hurtTime = 0;
        this.hurtResistantTime = 0;
        this.recentlyHit = 0;
        this.fallDistance = 0.0F;
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.foodStats = new FoodStats();
        this.setHealth(this.getMaxHealth());
        this.clearItemInUse();
    }

    @Override
    public void onUpdate() {
        if (this.disconnected) {
            return;
        }

        if (this.isDead && this.deathTime > 0) {
            this.respawnFake();
        }

        super.onUpdate();
        // Tick action pack after EntityPlayerMP.onUpdate() resets moveForward/moveStrafing,
        // but before onLivingUpdate() which consumes them for EntityLivingBase movement.
        ((IFakePlayerHolder) this).getActionPack()
            .onUpdate();
        // EntityPlayerMP.onUpdate() does not advance the living movement path.
        // Fake players have no client packets, so we manually run the living update,
        // but avoid onUpdateEntity() because it would trigger EntityPlayer.onUpdate()
        // and post a second PlayerTickEvent to external mods.
        runLivingUpdate(this::onLivingUpdate);
        this.machineMonitorService.tick(this);
    }

    static void runLivingUpdate(Runnable livingUpdateAction) {
        livingUpdateAction.run();
    }

    @Override
    public void onDeath(DamageSource source) {
        if (this.disconnected) {
            return;
        }

        super.onDeath(source);
        this.respawnFake();
    }

    @Override
    protected void kill() {
        if (this.disconnected) {
            return;
        }

        this.disconnected = true;
        FakePlayerRegistry.unregister(this.getCommandSenderName());
        this.machineMonitorService.clear();

        if (this.mcServer != null) {
            this.mcServer.getConfigurationManager()
                .playerLoggedOut(this);
        }

        super.kill();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public boolean isMonitoring() {
        return this.machineMonitorService.isMonitoring();
    }

    public void setMonitoring(boolean monitoring) {
        this.machineMonitorService.setMonitoring(monitoring);
    }

    public int getMonitorRange() {
        return this.machineMonitorService.getMonitorRange();
    }

    public void setMonitorRange(int monitorRange) {
        this.machineMonitorService.setMonitorRange(monitorRange);
    }

    public int getReminderInterval() {
        return this.machineMonitorService == null ? 600 : this.machineMonitorService.getReminderInterval();
    }

    public void setReminderInterval(int reminderInterval) {
        if (this.machineMonitorService != null) {
            this.machineMonitorService.setReminderInterval(reminderInterval);
        }
    }

    public MachineMonitorService getMachineMonitorService() {
        return this.machineMonitorService;
    }

    private static final EnumChatFormatting[] BOT_COLORS = { EnumChatFormatting.GREEN, EnumChatFormatting.AQUA,
        EnumChatFormatting.LIGHT_PURPLE, EnumChatFormatting.GOLD, EnumChatFormatting.YELLOW, EnumChatFormatting.BLUE,
        EnumChatFormatting.RED, EnumChatFormatting.DARK_AQUA, EnumChatFormatting.DARK_GREEN,
        EnumChatFormatting.DARK_PURPLE, };

    public EnumChatFormatting getChatColor() {
        return getChatColorForName(this.getCommandSenderName());
    }

    public static EnumChatFormatting getChatColorForName(String name) {
        int index = Math.abs((name == null ? "" : name).hashCode()) % BOT_COLORS.length;
        return BOT_COLORS[index];
    }

    public static String colorizeName(String name) {
        EnumChatFormatting color = getChatColorForName(name);
        return (color != null ? color.toString() : "") + name;
    }

    private void configureGameType(WorldSettings.GameType gamemode, WorldServer world) {
        WorldSettings.GameType resolvedGameType = gamemode != null ? gamemode
            : world.getWorldInfo()
                .getGameType();
        this.theItemInWorldManager.setGameType(resolvedGameType);
    }

    private void copyCapabilitiesFrom(EntityPlayerMP player) {
        this.capabilities.disableDamage = player.capabilities.disableDamage;
        this.capabilities.isFlying = player.capabilities.isFlying;
        this.capabilities.allowFlying = player.capabilities.allowFlying;
        this.capabilities.isCreativeMode = player.capabilities.isCreativeMode;
        this.capabilities.allowEdit = player.capabilities.allowEdit;
    }

    private void attachToServer(MinecraftServer server) {
        if (server == null) {
            return;
        }

        FakeNetworkManager networkManager = new FakeNetworkManager();
        FakeNetHandlerPlayServer netHandler = new FakeNetHandlerPlayServer(server, networkManager, this);
        server.getConfigurationManager()
            .initializeConnectionToPlayer(networkManager, this, netHandler);
    }

    private static void disconnectSourcePlayer(EntityPlayerMP player) {
        if (player == null) {
            return;
        }

        if (player.playerNetServerHandler != null) {
            player.playerNetServerHandler.kickPlayerFromServer("You logged in from another location");
        }

        if (player.mcServer != null) {
            player.mcServer.getConfigurationManager()
                .playerLoggedOut(player);
        }
    }

    private void applyCreationState(double x, double y, double z, float yaw, float pitch, boolean flying,
        WorldSettings.GameType gameType) {
        this.setLocationAndAngles(x, y, z, yaw, pitch);

        this.capabilities.isFlying = flying;
        this.capabilities.allowFlying = flying || this.capabilities.allowFlying;
        if (gameType != null) {
            this.theItemInWorldManager.setGameType(gameType);
        }

        this.sendPlayerAbilities();

        if (this.playerNetServerHandler != null) {
            this.playerNetServerHandler
                .setPlayerLocation(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch);
        }
    }

    private void replaceExistingRegistryEntry() {
        FakePlayer existing = FakePlayerRegistry.getFakePlayer(this.getCommandSenderName());
        if (existing != null && existing != this) {
            existing.kill();
        }
    }

    private static FakePlayer createWithProfile(GameProfile profile, MinecraftServer server, double x, double y,
        double z, float yaw, float pitch, int dimension, WorldSettings.GameType gamemode, boolean flying) {
        WorldServer world = resolveWorld(server, dimension);
        if (world == null) {
            throw new IllegalStateException("Unable to resolve a world for fake player creation");
        }

        FakePlayer fakePlayer = new FakePlayer(server, world, profile);
        WorldSettings.GameType resolvedGameType = gamemode != null ? gamemode
            : world.getWorldInfo()
                .getGameType();
        ChunkCoordinates spawnPoint = world.provider.getRandomizedSpawnPoint();
        double spawnX = Double.isNaN(x) ? spawnPoint.posX + 0.5D : x;
        double spawnY = Double.isNaN(y) ? spawnPoint.posY : y;
        double spawnZ = Double.isNaN(z) ? spawnPoint.posZ + 0.5D : z;
        fakePlayer.dimension = world.provider.dimensionId;
        fakePlayer.setLocationAndAngles(spawnX, spawnY, spawnZ, yaw, pitch);
        fakePlayer.theItemInWorldManager.setWorld(world);
        fakePlayer.configureGameType(resolvedGameType, world);
        fakePlayer.capabilities.isFlying = flying;
        fakePlayer.capabilities.allowFlying = flying || fakePlayer.capabilities.allowFlying;
        fakePlayer.attachToServer(server);
        fakePlayer.applyCreationState(spawnX, spawnY, spawnZ, yaw, pitch, flying, resolvedGameType);
        return fakePlayer;
    }

    private static WorldServer resolveWorld(MinecraftServer server, int dimension) {
        if (server == null) {
            throw new IllegalArgumentException("server");
        }

        WorldServer world = server.worldServerForDimension(dimension);
        if (world == null) {
            world = server.worldServerForDimension(0);
        }
        return world;
    }

    /**
     * Sends equipment (held item + armor) packets to all nearby real players.
     */
    public void syncEquipmentToWatchers() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        WorldServer world = (WorldServer) this.worldObj;
        int entityId = this.getEntityId();
        for (Object watcherObj : world.playerEntities) {
            EntityPlayerMP watcher = (EntityPlayerMP) watcherObj;
            if (watcher == this) continue;
            watcher.playerNetServerHandler
                .sendPacket(new S04PacketEntityEquipment(entityId, 0, this.inventory.getCurrentItem()));
            for (int i = 0; i < 4; i++) {
                watcher.playerNetServerHandler
                    .sendPacket(new S04PacketEntityEquipment(entityId, i + 1, this.inventory.armorInventory[i]));
            }
        }
    }

    /**
     * Sends arm-swing animation packet to all nearby real players.
     */
    public void broadcastSwingAnimation() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        WorldServer world = (WorldServer) this.worldObj;
        S0BPacketAnimation packet = new S0BPacketAnimation(this, 0);
        for (Object watcherObj : world.playerEntities) {
            EntityPlayerMP watcher = (EntityPlayerMP) watcherObj;
            if (watcher != this) {
                watcher.playerNetServerHandler.sendPacket(packet);
            }
        }
    }
}
