package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotHandle;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventoryRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.mojang.authlib.GameProfile;

class FakePlayerRegistryTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void registerSupportsMultipleBotsPerOwnerAndCaseInsensitiveLookup() {
        UUID ownerOne = UUID.randomUUID();
        UUID ownerTwo = UUID.randomUUID();
        FakePlayer alpha = fakePlayer("Alpha");
        FakePlayer beta = fakePlayer("Beta");
        FakePlayer gamma = fakePlayer("Gamma");

        FakePlayerRegistry.register(alpha, ownerOne);
        FakePlayerRegistry.register(beta, ownerOne);
        FakePlayerRegistry.register(gamma, ownerTwo);

        assertSame(alpha, FakePlayerRegistry.getFakePlayer("alpha"));
        assertSame(beta, FakePlayerRegistry.getFakePlayer("BETA"));
        assertSame(gamma, FakePlayerRegistry.getFakePlayer("gAmMa"));
        assertEquals(ownerOne, alpha.getOwnerUUID());
        assertEquals(ownerOne, beta.getOwnerUUID());
        assertEquals(ownerTwo, gamma.getOwnerUUID());
        assertEquals(2, FakePlayerRegistry.getCountByOwner(ownerOne));
        assertEquals(1, FakePlayerRegistry.getCountByOwner(ownerTwo));
        assertEquals(3, FakePlayerRegistry.getCount());
    }

    @Test
    void saveAndLoadRestoresPersistedOwnerMappings(@TempDir File tempDir) {
        UUID ownerOne = UUID.randomUUID();
        UUID ownerTwo = UUID.randomUUID();
        UUID profileOne = UUID.randomUUID();
        UUID profileTwo = UUID.randomUUID();

        FakePlayerRegistry.register(fakePlayer("Alpha", profileOne), ownerOne);
        FakePlayerRegistry.register(fakePlayer("Beta", profileTwo), ownerTwo);

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        assertEquals(ownerOne, FakePlayerRegistry.getOwnerUUID("alpha"));
        assertEquals(ownerTwo, FakePlayerRegistry.getOwnerUUID("BETA"));
        assertEquals(profileOne, FakePlayerRegistry.getProfileId("alpha"));
        assertEquals(profileTwo, FakePlayerRegistry.getProfileId("BETA"));
        assertEquals(0, FakePlayerRegistry.getCount());
    }

    @Test
    void containsTracksPersistedBotsAfterLoadAndUnregister(@TempDir File tempDir) {
        FakePlayerRegistry.register(fakePlayer("Alpha"), null);

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        assertTrue(FakePlayerRegistry.contains("alpha"));

        FakePlayerRegistry.unregister("Alpha");

        assertTrue(!FakePlayerRegistry.contains("alpha"));
    }

    @Test
    void loadAndRestoreRecreatesPersistedFakePlayers(@TempDir File tempDir) {
        UUID owner = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        StubFakePlayer alpha = fakePlayer("Alpha", profileId);
        alpha.dimension = 5;
        alpha.posX = 12.5D;
        alpha.posY = 72.0D;
        alpha.posZ = -3.25D;
        alpha.rotationYaw = 45.0F;
        alpha.rotationPitch = -10.0F;
        alpha.monitoring = true;
        alpha.monitorRange = 24;

        FakePlayerRegistry.register(alpha, owner);

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        List<FakePlayerRegistry.PersistedBotData> restoredData = new ArrayList<FakePlayerRegistry.PersistedBotData>();
        StubFakePlayer restoredPlayer = fakePlayer("Alpha", profileId);
        FakePlayerRegistry.restorePersisted(data -> {
            restoredData.add(data);
            return restoredPlayer;
        });

        assertEquals(1, restoredData.size());
        FakePlayerRegistry.PersistedBotData persisted = restoredData.get(0);
        assertEquals("Alpha", persisted.getName());
        assertEquals(profileId, persisted.getProfileId());
        assertEquals(owner, persisted.getOwnerUUID());
        assertEquals(5, persisted.getDimension());
        assertEquals(12.5D, persisted.getPosX());
        assertEquals(72.0D, persisted.getPosY());
        assertEquals(-3.25D, persisted.getPosZ());
        assertEquals(45.0F, persisted.getYaw());
        assertEquals(-10.0F, persisted.getPitch());
        assertTrue(persisted.isMonitoring());
        assertEquals(24, persisted.getMonitorRange());

        assertSame(restoredPlayer, FakePlayerRegistry.getFakePlayer("alpha"));
        assertEquals(owner, restoredPlayer.getOwnerUUID());
    }

    @Test
    void restorePersistedReturnsRestoredBotsInRegistrationOrder(@TempDir File tempDir) {
        FakePlayerRegistry.register(fakePlayer("Alpha"), UUID.randomUUID());
        FakePlayerRegistry.register(fakePlayer("Beta"), UUID.randomUUID());

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        List<FakePlayer> restored = FakePlayerRegistry
            .restorePersisted(data -> fakePlayer(data.getName(), data.getProfileId()));

        assertEquals(2, restored.size());
        assertEquals("Alpha", restored.get(0).getCommandSenderName());
        assertEquals("Beta", restored.get(1).getCommandSenderName());
    }

    @Test
    void persistedBotDataDefaultsLegacyRuntimeWhenOldTagMissing() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", "LegacyBot");
        tag.setInteger("Dimension", 0);
        tag.setDouble("PosX", 1.0D);
        tag.setDouble("PosY", 2.0D);
        tag.setDouble("PosZ", 3.0D);
        tag.setFloat("Yaw", 0.0F);
        tag.setFloat("Pitch", 0.0F);
        tag.setInteger("GameType", 0);

        FakePlayerRegistry.PersistedBotData data = FakePlayerRegistry.PersistedBotData.fromTag(tag);

        assertEquals(BotRuntimeType.LEGACY, data.getRuntimeType());
        assertEquals(1, data.getSnapshotVersion());
    }

    @Test
    void registryExposesRuntimeNeutralHandleForLegacyBot() {
        FakePlayer fakePlayer = fakePlayer("RegistryBot");
        FakePlayerRegistry.clear();
        FakePlayerRegistry.register(fakePlayer, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));

        BotHandle handle = FakePlayerRegistry.getBotHandle("RegistryBot");

        assertNotNull(handle);
        assertEquals(BotRuntimeType.LEGACY, handle.runtimeType());
        assertEquals("RegistryBot", handle.name());
    }

    @Test
    void registryCanExposeRegisteredNextGenRuntimeHandle() {
        FakePlayerRegistry.clear();
        BotRuntimeView runtime = new StubRuntimeView("NextGenBot", BotRuntimeType.NEXTGEN);

        FakePlayerRegistry.registerRuntime(runtime);

        BotHandle handle = FakePlayerRegistry.getBotHandle("nextgenbot");

        assertNotNull(handle);
        assertEquals(BotRuntimeType.NEXTGEN, handle.runtimeType());
        assertEquals("NextGenBot", handle.name());
        assertEquals(1, FakePlayerRegistry.getCount());
    }

    @Test
    void registerRuntimePersistsNextGenSnapshotForLaterLoad(@TempDir File tempDir) {
        FakePlayerRegistry.clear();
        FakePlayerRegistry.registerRuntime(new StubRuntimeView("NextGenBot", BotRuntimeType.NEXTGEN));

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        BotHandle handle = FakePlayerRegistry.getBotHandle("nextgenbot");

        assertNotNull(handle);
        assertEquals(BotRuntimeType.NEXTGEN, handle.runtimeType());
        assertEquals("NextGenBot", handle.name());
    }

    @Test
    void saveRefreshesNextGenRuntimeStateInsteadOfKeepingInitialSnapshot(@TempDir File tempDir) {
        FakePlayerRegistry.clear();
        MutableRuntimeView runtime = new MutableRuntimeView("NextGenBot");
        FakePlayerRegistry.registerRuntime(runtime);

        runtime.monitor.setMonitoring(true);
        runtime.monitor.setMonitorRange(48);
        runtime.monitor.setReminderInterval(240);
        runtime.repel.setRepelling(true);
        runtime.repel.setRepelRange(96);
        UUID followTarget = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        runtime.follow.setFollowRange(5);
        runtime.follow.setTeleportRange(40);
        runtime.follow.startFollowing(followTarget);

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        List<FakePlayerRegistry.PersistedBotData> restoredData = new ArrayList<FakePlayerRegistry.PersistedBotData>();
        FakePlayerRegistry.restorePersistedRuntimes(data -> {
            restoredData.add(data);
            return new StubRuntimeView(data.getName(), data.getRuntimeType());
        });
        FakePlayerRegistry.PersistedBotData persisted = restoredData.get(0);

        assertTrue(persisted.isMonitoring());
        assertEquals(48, persisted.getMonitorRange());
        assertEquals(240, persisted.getReminderInterval());
        assertTrue(persisted.isMonsterRepelling());
        assertEquals(96, persisted.getMonsterRepelRange());
        assertEquals(followTarget, persisted.getFollowTarget());
        assertEquals(5, persisted.getFollowRange());
        assertEquals(40, persisted.getTeleportRange());
    }

    @Test
    void registerRuntimeClearsStaleLegacyLookupWhenReplacingWithNextGen() {
        StubFakePlayer legacy = fakePlayer("NextGenBot");
        FakePlayerRegistry.register(legacy, UUID.randomUUID());

        FakePlayerRegistry.registerRuntime(new StubRuntimeView("NextGenBot", BotRuntimeType.NEXTGEN));

        assertEquals(null, FakePlayerRegistry.getFakePlayer("nextgenbot"));
        assertEquals(BotRuntimeType.NEXTGEN, FakePlayerRegistry.getBotHandle("nextgenbot").runtimeType());
    }

    private static StubFakePlayer fakePlayer(String name) {
        return fakePlayer(name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    private static StubFakePlayer fakePlayer(String name, UUID profileId) {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = name;
        fakePlayer.profileId = profileId;
        fakePlayer.monitorRange = 16;
        return fakePlayer;
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return type.cast(unsafe.allocateInstance(type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubFakePlayer extends FakePlayer {

        private String name;
        private UUID profileId;
        private boolean monitoring;
        private int monitorRange;

        private StubFakePlayer() {
            super(null, null, "stub");
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }

        @Override
        public GameProfile getGameProfile() {
            return new GameProfile(profileId, name);
        }

        @Override
        public boolean isMonitoring() {
            return monitoring;
        }

        @Override
        public void setMonitoring(boolean monitoring) {
            this.monitoring = monitoring;
        }

        @Override
        public int getMonitorRange() {
            return monitorRange;
        }

        @Override
        public void setMonitorRange(int monitorRange) {
            this.monitorRange = monitorRange;
        }
    }

    private static final class StubRuntimeView implements BotRuntimeView {

        private final String name;
        private final BotRuntimeType runtimeType;

        private StubRuntimeView(String name, BotRuntimeType runtimeType) {
            this.name = name;
            this.runtimeType = runtimeType;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UUID ownerUUID() {
            return null;
        }

        @Override
        public int dimension() {
            return 0;
        }

        @Override
        public BotRuntimeType runtimeType() {
            return runtimeType;
        }

        @Override
        public BotEntityBridge entity() {
            return new BotEntityBridge() {

                @Override
                public EntityPlayerMP asPlayer() {
                    return null;
                }
            };
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public BotActionRuntime action() {
            return null;
        }

        @Override
        public BotFollowRuntime follow() {
            return null;
        }

        @Override
        public BotMonitorRuntime monitor() {
            return null;
        }

        @Override
        public BotRepelRuntime repel() {
            return null;
        }

        @Override
        public BotInventoryRuntime inventory() {
            return null;
        }
    }

    private static final class MutableRuntimeView implements BotRuntimeView {

        private final String name;
        private final MutableFollowRuntime follow = new MutableFollowRuntime();
        private final MutableMonitorRuntime monitor = new MutableMonitorRuntime();
        private final MutableRepelRuntime repel = new MutableRepelRuntime();

        private MutableRuntimeView(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UUID ownerUUID() {
            return null;
        }

        @Override
        public int dimension() {
            return 0;
        }

        @Override
        public BotRuntimeType runtimeType() {
            return BotRuntimeType.NEXTGEN;
        }

        @Override
        public BotEntityBridge entity() {
            return () -> null;
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public BotActionRuntime action() {
            return null;
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
            return null;
        }
    }

    private static final class MutableFollowRuntime implements BotFollowRuntime {

        private UUID targetUUID;
        private int followRange;
        private int teleportRange;

        @Override
        public boolean following() {
            return targetUUID != null;
        }

        @Override
        public UUID targetUUID() {
            return targetUUID;
        }

        @Override
        public int followRange() {
            return followRange;
        }

        @Override
        public int teleportRange() {
            return teleportRange;
        }

        @Override
        public void startFollowing(UUID targetUUID) {
            this.targetUUID = targetUUID;
        }

        @Override
        public void stop() {
            this.targetUUID = null;
        }

        @Override
        public void setFollowRange(int range) {
            this.followRange = range;
        }

        @Override
        public void setTeleportRange(int range) {
            this.teleportRange = range;
        }
    }

    private static final class MutableMonitorRuntime implements BotMonitorRuntime {

        private boolean monitoring;
        private int monitorRange;
        private int reminderInterval;

        @Override
        public boolean monitoring() {
            return monitoring;
        }

        @Override
        public int monitorRange() {
            return monitorRange;
        }

        @Override
        public int reminderInterval() {
            return reminderInterval;
        }

        @Override
        public void setMonitoring(boolean monitoring) {
            this.monitoring = monitoring;
        }

        @Override
        public void setMonitorRange(int range) {
            this.monitorRange = range;
        }

        @Override
        public void setReminderInterval(int ticks) {
            this.reminderInterval = ticks;
        }

        @Override
        public String overviewMessage(String botName) {
            return "";
        }
    }

    private static final class MutableRepelRuntime implements BotRepelRuntime {

        private boolean repelling;
        private int repelRange;

        @Override
        public boolean repelling() {
            return repelling;
        }

        @Override
        public int repelRange() {
            return repelRange;
        }

        @Override
        public void setRepelling(boolean repelling) {
            this.repelling = repelling;
        }

        @Override
        public void setRepelRange(int range) {
            this.repelRange = range;
        }
    }
}
