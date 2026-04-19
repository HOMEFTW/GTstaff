package com.andgatech.gtstaff.fakeplayer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.util.Constants;

public class FakePlayerRegistry {

    private static final String ROOT_KEY = "Bots";
    private static final String NAME_KEY = "Name";
    private static final String PROFILE_ID_KEY = "ProfileId";
    private static final String OWNER_KEY = "Owner";
    private static final String DIMENSION_KEY = "Dimension";
    private static final String POS_X_KEY = "PosX";
    private static final String POS_Y_KEY = "PosY";
    private static final String POS_Z_KEY = "PosZ";
    private static final String YAW_KEY = "Yaw";
    private static final String PITCH_KEY = "Pitch";
    private static final String GAME_TYPE_KEY = "GameType";
    private static final String FLYING_KEY = "Flying";
    private static final String MONITORING_KEY = "Monitoring";
    private static final String MONITOR_RANGE_KEY = "MonitorRange";
    private static final String REMINDER_INTERVAL_KEY = "ReminderInterval";
    private static final String MONSTER_REPELLING_KEY = "MonsterRepelling";
    private static final String MONSTER_REPEL_RANGE_KEY = "MonsterRepelRange";
    private static final String FOLLOW_TARGET_KEY = "FollowTarget";
    private static final String FOLLOW_RANGE_KEY = "FollowRange";
    private static final String TELEPORT_RANGE_KEY = "TeleportRange";

    private static final Map<String, FakePlayer> fakePlayers = new LinkedHashMap<String, FakePlayer>();
    private static final Map<String, PersistedBotData> persistedBots = new LinkedHashMap<String, PersistedBotData>();

    @FunctionalInterface
    public interface BotRestorer {

        FakePlayer restore(PersistedBotData data);
    }

    public static final class PersistedBotData {

        private final String name;
        private final UUID profileId;
        private final UUID ownerUUID;
        private final int dimension;
        private final double posX;
        private final double posY;
        private final double posZ;
        private final float yaw;
        private final float pitch;
        private final int gameTypeId;
        private final boolean flying;
        private final boolean monitoring;
        private final int monitorRange;
        private final int reminderInterval;
        private final boolean monsterRepelling;
        private final int monsterRepelRange;
        private final UUID followTarget;
        private final int followRange;
        private final int teleportRange;

        private PersistedBotData(String name, UUID profileId, UUID ownerUUID, int dimension, double posX, double posY,
            double posZ, float yaw, float pitch, int gameTypeId, boolean flying, boolean monitoring, int monitorRange,
            int reminderInterval, boolean monsterRepelling, int monsterRepelRange, UUID followTarget, int followRange,
            int teleportRange) {
            this.name = name;
            this.profileId = profileId;
            this.ownerUUID = ownerUUID;
            this.dimension = dimension;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.yaw = yaw;
            this.pitch = pitch;
            this.gameTypeId = gameTypeId;
            this.flying = flying;
            this.monitoring = monitoring;
            this.monitorRange = monitorRange;
            this.reminderInterval = reminderInterval;
            this.monsterRepelling = monsterRepelling;
            this.monsterRepelRange = monsterRepelRange;
            this.followTarget = followTarget;
            this.followRange = followRange;
            this.teleportRange = teleportRange;
        }

        public String getName() {
            return this.name;
        }

        public UUID getProfileId() {
            return this.profileId;
        }

        public UUID getOwnerUUID() {
            return this.ownerUUID;
        }

        public int getDimension() {
            return this.dimension;
        }

        public double getPosX() {
            return this.posX;
        }

        public double getPosY() {
            return this.posY;
        }

        public double getPosZ() {
            return this.posZ;
        }

        public float getYaw() {
            return this.yaw;
        }

        public float getPitch() {
            return this.pitch;
        }

        public int getGameTypeId() {
            return this.gameTypeId;
        }

        public boolean isFlying() {
            return this.flying;
        }

        public boolean isMonitoring() {
            return this.monitoring;
        }

        public int getMonitorRange() {
            return this.monitorRange;
        }

        public int getReminderInterval() {
            return this.reminderInterval;
        }

        public boolean isMonsterRepelling() {
            return this.monsterRepelling;
        }

        public int getMonsterRepelRange() {
            return this.monsterRepelRange;
        }

        public UUID getFollowTarget() {
            return this.followTarget;
        }

        public int getFollowRange() {
            return this.followRange;
        }

        public int getTeleportRange() {
            return this.teleportRange;
        }
    }

    public static void register(FakePlayer fakePlayer, UUID ownerUUID) {
        String normalizedName = normalize(fakePlayer.getCommandSenderName());
        fakePlayer.setOwnerUUID(ownerUUID);
        fakePlayers.put(normalizedName, fakePlayer);
        persistedBots.put(normalizedName, snapshot(fakePlayer, ownerUUID));
    }

    public static void unregister(String name) {
        String normalizedName = normalize(name);
        fakePlayers.remove(normalizedName);
        persistedBots.remove(normalizedName);
    }

    public static FakePlayer getFakePlayer(String name) {
        return fakePlayers.get(normalize(name));
    }

    public static boolean contains(String name) {
        String normalizedName = normalize(name);
        return normalizedName != null
            && (fakePlayers.containsKey(normalizedName) || persistedBots.containsKey(normalizedName));
    }

    public static UUID getOwnerUUID(String name) {
        PersistedBotData data = persistedBots.get(normalize(name));
        return data == null ? null : data.getOwnerUUID();
    }

    public static UUID getProfileId(String name) {
        PersistedBotData data = persistedBots.get(normalize(name));
        return data == null ? null : data.getProfileId();
    }

    public static Map<String, FakePlayer> getAll() {
        return fakePlayers;
    }

    public static int getCount() {
        return fakePlayers.size();
    }

    public static int getCountByOwner(UUID ownerUUID) {
        return (int) fakePlayers.values()
            .stream()
            .filter(
                fp -> fp.getOwnerUUID() != null && fp.getOwnerUUID()
                    .equals(ownerUUID))
            .count();
    }

    public static void clear() {
        fakePlayers.clear();
        persistedBots.clear();
    }

    public static void save(File file) {
        if (file == null) {
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        NBTTagCompound root = new NBTTagCompound();
        NBTTagList botList = new NBTTagList();
        Map<String, PersistedBotData> snapshotByName = new LinkedHashMap<String, PersistedBotData>(persistedBots);
        for (FakePlayer fakePlayer : fakePlayers.values()) {
            snapshotByName
                .put(normalize(fakePlayer.getCommandSenderName()), snapshot(fakePlayer, fakePlayer.getOwnerUUID()));
        }
        for (PersistedBotData data : snapshotByName.values()) {
            if (data == null || data.getName() == null) {
                continue;
            }
            NBTTagCompound bot = new NBTTagCompound();
            bot.setString(NAME_KEY, data.getName());
            if (data.getProfileId() != null) {
                bot.setString(
                    PROFILE_ID_KEY,
                    data.getProfileId()
                        .toString());
            }
            if (data.getOwnerUUID() != null) {
                bot.setString(
                    OWNER_KEY,
                    data.getOwnerUUID()
                        .toString());
            }
            bot.setInteger(DIMENSION_KEY, data.getDimension());
            bot.setDouble(POS_X_KEY, data.getPosX());
            bot.setDouble(POS_Y_KEY, data.getPosY());
            bot.setDouble(POS_Z_KEY, data.getPosZ());
            bot.setFloat(YAW_KEY, data.getYaw());
            bot.setFloat(PITCH_KEY, data.getPitch());
            bot.setInteger(GAME_TYPE_KEY, data.getGameTypeId());
            bot.setBoolean(FLYING_KEY, data.isFlying());
            bot.setBoolean(MONITORING_KEY, data.isMonitoring());
            bot.setInteger(MONITOR_RANGE_KEY, data.getMonitorRange());
            bot.setInteger(REMINDER_INTERVAL_KEY, data.getReminderInterval());
            bot.setBoolean(MONSTER_REPELLING_KEY, data.isMonsterRepelling());
            bot.setInteger(MONSTER_REPEL_RANGE_KEY, data.getMonsterRepelRange());
            if (data.getFollowTarget() != null) {
                bot.setString(
                    FOLLOW_TARGET_KEY,
                    data.getFollowTarget()
                        .toString());
            }
            bot.setInteger(FOLLOW_RANGE_KEY, data.getFollowRange());
            bot.setInteger(TELEPORT_RANGE_KEY, data.getTeleportRange());
            botList.appendTag(bot);
        }
        root.setTag(ROOT_KEY, botList);

        try {
            CompressedStreamTools.write(root, file);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save fake player registry to " + file.getAbsolutePath(), e);
        }
    }

    public static void saveServerRegistry(MinecraftServer server) {
        save(getRegistryFile(server));
    }

    public static void load(File file) {
        clear();
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            NBTTagList botList = root.getTagList(ROOT_KEY, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < botList.tagCount(); index++) {
                NBTTagCompound bot = botList.getCompoundTagAt(index);
                String name = bot.getString(NAME_KEY);
                String normalizedName = normalize(name);
                if (normalizedName == null) {
                    continue;
                }

                UUID profileId = null;
                if (bot.hasKey(PROFILE_ID_KEY)) {
                    profileId = UUID.fromString(bot.getString(PROFILE_ID_KEY));
                }

                UUID owner = null;
                if (bot.hasKey(OWNER_KEY)) {
                    owner = UUID.fromString(bot.getString(OWNER_KEY));
                }

                int dimension = bot.hasKey(DIMENSION_KEY) ? bot.getInteger(DIMENSION_KEY) : 0;
                double posX = bot.hasKey(POS_X_KEY) ? bot.getDouble(POS_X_KEY) : Double.NaN;
                double posY = bot.hasKey(POS_Y_KEY) ? bot.getDouble(POS_Y_KEY) : Double.NaN;
                double posZ = bot.hasKey(POS_Z_KEY) ? bot.getDouble(POS_Z_KEY) : Double.NaN;
                float yaw = bot.hasKey(YAW_KEY) ? bot.getFloat(YAW_KEY) : 0.0F;
                float pitch = bot.hasKey(PITCH_KEY) ? bot.getFloat(PITCH_KEY) : 0.0F;
                int gameTypeId = bot.hasKey(GAME_TYPE_KEY) ? bot.getInteger(GAME_TYPE_KEY)
                    : WorldSettings.GameType.SURVIVAL.getID();
                boolean flying = bot.hasKey(FLYING_KEY) && bot.getBoolean(FLYING_KEY);
                boolean monitoring = bot.hasKey(MONITORING_KEY) && bot.getBoolean(MONITORING_KEY);
                int monitorRange = bot.hasKey(MONITOR_RANGE_KEY) ? bot.getInteger(MONITOR_RANGE_KEY) : 16;
                int reminderInterval = bot.hasKey(REMINDER_INTERVAL_KEY) ? bot.getInteger(REMINDER_INTERVAL_KEY) : 600;
                boolean monsterRepelling = bot.hasKey(MONSTER_REPELLING_KEY) && bot.getBoolean(MONSTER_REPELLING_KEY);
                int monsterRepelRange = bot.hasKey(MONSTER_REPEL_RANGE_KEY) ? bot.getInteger(MONSTER_REPEL_RANGE_KEY)
                    : 64;
                UUID followTarget = null;
                if (bot.hasKey(FOLLOW_TARGET_KEY)) {
                    followTarget = UUID.fromString(bot.getString(FOLLOW_TARGET_KEY));
                }
                int followRange = bot.hasKey(FOLLOW_RANGE_KEY) ? bot.getInteger(FOLLOW_RANGE_KEY)
                    : FollowService.DEFAULT_FOLLOW_RANGE;
                int teleportRange = bot.hasKey(TELEPORT_RANGE_KEY) ? bot.getInteger(TELEPORT_RANGE_KEY)
                    : FollowService.DEFAULT_TELEPORT_RANGE;

                persistedBots.put(
                    normalizedName,
                    new PersistedBotData(
                        name,
                        profileId,
                        owner,
                        dimension,
                        posX,
                        posY,
                        posZ,
                        yaw,
                        pitch,
                        gameTypeId,
                        flying,
                        monitoring,
                        monitorRange,
                        reminderInterval,
                        monsterRepelling,
                        monsterRepelRange,
                        followTarget,
                        followRange,
                        teleportRange));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load fake player registry from " + file.getAbsolutePath(), e);
        }
    }

    public static List<FakePlayer> restorePersisted(MinecraftServer server) {
        return restorePersisted(data -> FakePlayer.restorePersisted(server, data));
    }

    public static List<FakePlayer> restorePersisted(BotRestorer restorer) {
        List<FakePlayer> restored = new ArrayList<FakePlayer>();
        if (restorer == null) {
            return restored;
        }

        for (PersistedBotData data : new ArrayList<PersistedBotData>(persistedBots.values())) {
            if (data == null || data.getName() == null || getFakePlayer(data.getName()) != null) {
                continue;
            }

            FakePlayer fakePlayer = restorer.restore(data);
            if (fakePlayer == null) {
                continue;
            }

            fakePlayer.setOwnerUUID(data.getOwnerUUID());
            fakePlayer.setMonitoring(data.isMonitoring());
            fakePlayer.setMonitorRange(data.getMonitorRange());
            fakePlayer.setReminderInterval(data.getReminderInterval());
            fakePlayer.setMonsterRepelling(data.isMonsterRepelling());
            fakePlayer.setMonsterRepelRange(data.getMonsterRepelRange());
            if (data.getFollowTarget() != null) {
                fakePlayer.getFollowService()
                    .setFollowRange(data.getFollowRange());
                fakePlayer.getFollowService()
                    .setTeleportRange(data.getTeleportRange());
                fakePlayer.getFollowService()
                    .startFollowing(data.getFollowTarget());
            }
            register(fakePlayer, data.getOwnerUUID());
            restored.add(fakePlayer);
        }
        return restored;
    }

    private static String normalize(String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private static File getRegistryFile(MinecraftServer server) {
        return server == null ? new File("data", "gtstaff_registry.dat") : server.getFile("data/gtstaff_registry.dat");
    }

    private static PersistedBotData snapshot(FakePlayer fakePlayer, UUID ownerUUID) {
        UUID profileId = fakePlayer.getGameProfile() == null ? null
            : fakePlayer.getGameProfile()
                .getId();
        WorldSettings.GameType gameType = fakePlayer.theItemInWorldManager == null ? null
            : fakePlayer.theItemInWorldManager.getGameType();
        int gameTypeId = gameType == null ? WorldSettings.GameType.SURVIVAL.getID() : gameType.getID();
        boolean flying = fakePlayer.capabilities != null && fakePlayer.capabilities.isFlying;
        UUID followTarget = fakePlayer.isFollowing() ? fakePlayer.getFollowService()
            .getFollowTargetUUID() : null;
        int followRange = fakePlayer.getFollowService() != null ? fakePlayer.getFollowService()
            .getFollowRange() : FollowService.DEFAULT_FOLLOW_RANGE;
        int teleportRange = fakePlayer.getFollowService() != null ? fakePlayer.getFollowService()
            .getTeleportRange() : FollowService.DEFAULT_TELEPORT_RANGE;
        return new PersistedBotData(
            fakePlayer.getCommandSenderName(),
            profileId,
            ownerUUID,
            fakePlayer.dimension,
            fakePlayer.posX,
            fakePlayer.posY,
            fakePlayer.posZ,
            fakePlayer.rotationYaw,
            fakePlayer.rotationPitch,
            gameTypeId,
            flying,
            fakePlayer.isMonitoring(),
            fakePlayer.getMonitorRange(),
            fakePlayer.getReminderInterval(),
            fakePlayer.isMonsterRepelling(),
            fakePlayer.getMonsterRepelRange(),
            followTarget,
            followRange,
            teleportRange);
    }
}
