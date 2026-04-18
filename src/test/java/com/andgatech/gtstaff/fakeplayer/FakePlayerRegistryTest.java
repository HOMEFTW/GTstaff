package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        FakePlayerRegistry.register(fakePlayer("Alpha"), ownerOne);
        FakePlayerRegistry.register(fakePlayer("Beta"), ownerTwo);

        File file = new File(tempDir, "gtstaff_registry.dat");
        FakePlayerRegistry.save(file);

        FakePlayerRegistry.clear();
        FakePlayerRegistry.load(file);

        assertEquals(ownerOne, FakePlayerRegistry.getOwnerUUID("alpha"));
        assertEquals(ownerTwo, FakePlayerRegistry.getOwnerUUID("BETA"));
        assertEquals(0, FakePlayerRegistry.getCount());
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
}
