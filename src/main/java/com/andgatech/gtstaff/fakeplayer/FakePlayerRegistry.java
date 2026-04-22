package com.andgatech.gtstaff.fakeplayer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.util.Constants;

import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotHandle;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandle;

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
    private static final String RUNTIME_TYPE_KEY = "RuntimeType";
    private static final String SNAPSHOT_VERSION_KEY = "SnapshotVersion";

    private static final Map<String, FakePlayer> fakePlayers = new LinkedHashMap<String, FakePlayer>();
    private static final Map<String, BotRuntimeView> onlineRuntimes = new LinkedHashMap<String, BotRuntimeView>();
    private static final Map<String, PersistedBotData> persistedBots = new LinkedHashMap<String, PersistedBotData>();

    @FunctionalInterface
    public interface BotRestorer {

        FakePlayer restore(PersistedBotData data);
    }

    @FunctionalInterface
    public interface RuntimeRestorer {

        BotRuntimeView restore(PersistedBotData data);
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
        private final BotRuntimeType runtimeType;
        private final int snapshotVersion;

        private PersistedBotData(String name, UUID profileId, UUID ownerUUID, int dimension, double posX, double posY,
            double posZ, float yaw, float pitch, int gameTypeId, boolean flying, boolean monitoring, int monitorRange,
            int reminderInterval, boolean monsterRepelling, int monsterRepelRange, UUID followTarget, int followRange,
            int teleportRange, BotRuntimeType runtimeType, int snapshotVersion) {
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
            this.runtimeType = runtimeType;
            this.snapshotVersion = snapshotVersion;
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

        public BotRuntimeType getRuntimeType() {
            return this.runtimeType;
        }

        public int getSnapshotVersion() {
            return this.snapshotVersion;
        }

        public static PersistedBotData fromTag(NBTTagCompound bot) {
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
            int monsterRepelRange = bot.hasKey(MONSTER_REPEL_RANGE_KEY) ? bot.getInteger(MONSTER_REPEL_RANGE_KEY) : 64;
            UUID followTarget = null;
            if (bot.hasKey(FOLLOW_TARGET_KEY)) {
                followTarget = UUID.fromString(bot.getString(FOLLOW_TARGET_KEY));
            }
            int followRange = bot.hasKey(FOLLOW_RANGE_KEY) ? bot.getInteger(FOLLOW_RANGE_KEY)
                : FollowService.DEFAULT_FOLLOW_RANGE;
            int teleportRange = bot.hasKey(TELEPORT_RANGE_KEY) ? bot.getInteger(TELEPORT_RANGE_KEY)
                : FollowService.DEFAULT_TELEPORT_RANGE;
            BotRuntimeType runtimeType = BotRuntimeType.LEGACY;
            if (bot.hasKey(RUNTIME_TYPE_KEY, Constants.NBT.TAG_STRING)) {
                try {
                    runtimeType = BotRuntimeType.valueOf(bot.getString(RUNTIME_TYPE_KEY).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    runtimeType = BotRuntimeType.LEGACY;
                }
            }
            int snapshotVersion = bot.hasKey(SNAPSHOT_VERSION_KEY, Constants.NBT.TAG_INT)
                ? bot.getInteger(SNAPSHOT_VERSION_KEY)
                : 1;

            return new PersistedBotData(
                bot.getString(NAME_KEY),
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
                teleportRange,
                runtimeType,
                snapshotVersion);
        }
    }

    private static final class PersistedBotHandle implements BotHandle {

        private final PersistedBotData data;

        private PersistedBotHandle(PersistedBotData data) {
            this.data = data;
        }

        @Override
        public String name() {
            return data.getName();
        }

        @Override
        public UUID ownerUUID() {
            return data.getOwnerUUID();
        }

        @Override
        public int dimension() {
            return data.getDimension();
        }

        @Override
        public BotRuntimeType runtimeType() {
            return data.getRuntimeType();
        }

        @Override
        public BotEntityBridge entity() {
            return () -> null;
        }
    }

    public static void register(FakePlayer fakePlayer, UUID ownerUUID) {
        String normalizedName = normalize(fakePlayer.getCommandSenderName());
        fakePlayer.setOwnerUUID(ownerUUID);
        fakePlayers.put(normalizedName, fakePlayer);
        onlineRuntimes.put(normalizedName, fakePlayer.asRuntimeView());
        persistedBots.put(normalizedName, snapshot(fakePlayer, ownerUUID));
    }

    public static void registerRuntime(BotRuntimeView runtime) {
        registerRuntimeInternal(runtime, null);
    }

    public static void unregister(String name) {
        String normalizedName = normalize(name);
        fakePlayers.remove(normalizedName);
        onlineRuntimes.remove(normalizedName);
        persistedBots.remove(normalizedName);
    }

    public static FakePlayer getFakePlayer(String name) {
        return fakePlayers.get(normalize(name));
    }

    public static BotHandle getBotHandle(String name) {
        BotRuntimeView runtime = getRuntimeView(name);
        if (runtime != null) {
            return runtime;
        }
        PersistedBotData data = persistedBots.get(normalize(name));
        if (data == null) {
            return null;
        }
        return new PersistedBotHandle(data);
    }

    public static BotRuntimeView getRuntimeView(String name) {
        return onlineRuntimes.get(normalize(name));
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

    public static List<BotHandle> getAllBotHandles() {
        return new ArrayList<BotHandle>(onlineRuntimes.values());
    }

    public static int getCount() {
        return onlineRuntimes.size();
    }

    public static int getCountByOwner(UUID ownerUUID) {
        return (int) onlineRuntimes.values()
            .stream()
            .filter(runtime -> runtime.ownerUUID() != null && runtime.ownerUUID().equals(ownerUUID))
            .count();
    }

    public static void clear() {
        fakePlayers.clear();
        onlineRuntimes.clear();
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
        for (BotRuntimeView runtime : onlineRuntimes.values()) {
            if (runtime == null || runtime.name() == null) {
                continue;
            }
            PersistedBotData previous = snapshotByName.get(normalize(runtime.name()));
            snapshotByName.put(normalize(runtime.name()), snapshot(runtime, previous));
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
            bot.setString(RUNTIME_TYPE_KEY, data.getRuntimeType().name());
            bot.setInteger(SNAPSHOT_VERSION_KEY, data.getSnapshotVersion());
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

                persistedBots.put(normalizedName, PersistedBotData.fromTag(bot));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load fake player registry from " + file.getAbsolutePath(), e);
        }
    }

    public static List<FakePlayer> restorePersisted(MinecraftServer server) {
        return restorePersisted(data -> FakePlayer.restorePersisted(server, data));
    }

    public static List<FakePlayer> restorePersisted(BotRestorer restorer) {
        List<BotRuntimeView> restoredRuntimes = restorePersistedViews(data -> {
            FakePlayer fakePlayer = restorer == null ? null : restorer.restore(data);
            if (fakePlayer == null) {
                return null;
            }
            applyPersistedState(fakePlayer, data);
            return fakePlayer.asRuntimeView();
        });

        List<FakePlayer> restored = new ArrayList<FakePlayer>();
        for (BotRuntimeView runtime : restoredRuntimes) {
            EntityPlayerMP player = runtime == null || runtime.entity() == null ? null : runtime.entity()
                .asPlayer();
            if (player instanceof FakePlayer) {
                restored.add((FakePlayer) player);
            }
        }
        return restored;
    }

    public static List<BotRuntimeView> restorePersistedRuntimes(RuntimeRestorer restorer) {
        return restorePersistedViews(restorer);
    }

    private static List<BotRuntimeView> restorePersistedViews(RuntimeRestorer restorer) {
        List<BotRuntimeView> restored = new ArrayList<BotRuntimeView>();
        if (restorer == null) {
            return restored;
        }

        for (PersistedBotData data : new ArrayList<PersistedBotData>(persistedBots.values())) {
            if (data == null || data.getName() == null || getRuntimeView(data.getName()) != null) {
                continue;
            }

            BotRuntimeView runtime = restorer.restore(data);
            if (runtime == null) {
                continue;
            }

            registerRuntimeInternal(runtime, data);
            restored.add(runtime);
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
            teleportRange,
            BotRuntimeType.LEGACY,
            1);
    }

    private static void applyPersistedState(FakePlayer fakePlayer, PersistedBotData data) {
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
    }

    private static void registerRuntimeInternal(BotRuntimeView runtime, PersistedBotData fallbackSnapshot) {
        if (runtime == null || runtime.name() == null) {
            return;
        }
        String normalizedName = normalize(runtime.name());
        onlineRuntimes.put(normalizedName, runtime);
        EntityPlayerMP player = runtime.entity() == null ? null : runtime.entity()
            .asPlayer();
        if (player instanceof FakePlayer) {
            fakePlayers.put(normalizedName, (FakePlayer) player);
        } else {
            fakePlayers.remove(normalizedName);
        }
        PersistedBotData snapshot = snapshot(runtime, fallbackSnapshot);
        if (snapshot != null) {
            persistedBots.put(normalizedName, snapshot);
        }
    }

    private static PersistedBotData snapshot(BotRuntimeView runtime, PersistedBotData fallback) {
        if (runtime == null || runtime.name() == null) {
            return fallback;
        }
        EntityPlayerMP player = runtime.entity() == null ? null : runtime.entity()
            .asPlayer();
        UUID profileId = player != null && player.getGameProfile() != null ? player.getGameProfile()
            .getId() : fallback == null ? null : fallback.getProfileId();
        WorldSettings.GameType gameType = player == null || player.theItemInWorldManager == null ? null
            : player.theItemInWorldManager.getGameType();
        int gameTypeId = gameType == null ? fallback == null ? WorldSettings.GameType.SURVIVAL.getID()
            : fallback.getGameTypeId() : gameType.getID();
        boolean flying = player != null && player.capabilities != null ? player.capabilities.isFlying
            : fallback != null && fallback.isFlying();
        UUID followTarget = runtime.follow() == null ? fallback == null ? null : fallback.getFollowTarget()
            : runtime.follow()
                .targetUUID();
        int followRange = runtime.follow() == null ? fallback == null ? FollowService.DEFAULT_FOLLOW_RANGE
            : fallback.getFollowRange() : runtime.follow()
                .followRange();
        int teleportRange = runtime.follow() == null ? fallback == null ? FollowService.DEFAULT_TELEPORT_RANGE
            : fallback.getTeleportRange() : runtime.follow()
                .teleportRange();
        boolean monitoring = runtime.monitor() == null ? fallback != null && fallback.isMonitoring() : runtime.monitor()
            .monitoring();
        int monitorRange = runtime.monitor() == null ? fallback == null ? 1 : fallback.getMonitorRange()
            : runtime.monitor()
                .monitorRange();
        int reminderInterval = runtime.monitor() == null ? fallback == null ? 60 : fallback.getReminderInterval()
            : runtime.monitor()
                .reminderInterval();
        boolean repelling = runtime.repel() == null ? fallback != null && fallback.isMonsterRepelling() : runtime.repel()
            .repelling();
        int repelRange = runtime.repel() == null ? fallback == null ? 64 : fallback.getMonsterRepelRange()
            : runtime.repel()
                .repelRange();
        BotRuntimeType runtimeType = runtime.runtimeType() == null ? fallback == null ? BotRuntimeType.LEGACY
            : fallback.getRuntimeType() : runtime.runtimeType();
        int snapshotVersion = fallback == null ? 1 : fallback.getSnapshotVersion();
        return new PersistedBotData(
            runtime.name(),
            profileId,
            runtime.ownerUUID() == null ? fallback == null ? null : fallback.getOwnerUUID() : runtime.ownerUUID(),
            player == null ? runtime.dimension() : player.dimension,
            player == null ? fallback == null ? 0.0D : fallback.getPosX() : player.posX,
            player == null ? fallback == null ? 0.0D : fallback.getPosY() : player.posY,
            player == null ? fallback == null ? 0.0D : fallback.getPosZ() : player.posZ,
            player == null ? fallback == null ? 0.0F : fallback.getYaw() : player.rotationYaw,
            player == null ? fallback == null ? 0.0F : fallback.getPitch() : player.rotationPitch,
            gameTypeId,
            flying,
            monitoring,
            monitorRange,
            reminderInterval,
            repelling,
            repelRange,
            followTarget,
            followRange,
            teleportRange,
            runtimeType,
            snapshotVersion);
    }
}
