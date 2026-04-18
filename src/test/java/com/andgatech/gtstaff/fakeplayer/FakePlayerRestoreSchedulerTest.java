package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakePlayerRestoreSchedulerTest {

    @AfterEach
    void resetScheduler() {
        FakePlayerRestoreScheduler.resetForTesting();
    }

    @Test
    void dedicatedServerRestoresOnNextTick() {
        List<MinecraftServer> restored = new ArrayList<MinecraftServer>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        setField(MinecraftServer.class, server, "serverConfigManager", new TestServerConfigurationManager(server));
        FakePlayerRestoreScheduler.setRestoreActionForTesting(restored::add);

        FakePlayerRestoreScheduler.schedule(server);

        assertEquals(0, restored.size());
        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(1, restored.size());
        assertEquals(server, restored.get(0));

        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(1, restored.size());
    }

    @Test
    void integratedServerWaitsForRealPlayerBeforeRestoring() {
        List<MinecraftServer> restored = new ArrayList<MinecraftServer>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = false;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);
        FakePlayerRestoreScheduler.setRestoreActionForTesting(restored::add);

        FakePlayerRestoreScheduler.schedule(server);
        configurationManager.playerEntityList.add(allocate(TestFakePlayer.class));

        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(0, restored.size());

        configurationManager.playerEntityList.add(allocate(TestRealPlayer.class));
        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(1, restored.size());
        assertEquals(server, restored.get(0));
    }

    private static final class TestMinecraftServer extends MinecraftServer {

        private boolean dedicated;

        private TestMinecraftServer() {
            super(new File("."), java.net.Proxy.NO_PROXY);
        }

        @Override
        protected boolean startServer() {
            return false;
        }

        @Override
        public boolean canStructuresSpawn() {
            return false;
        }

        @Override
        public WorldSettings.GameType getGameType() {
            return WorldSettings.GameType.SURVIVAL;
        }

        @Override
        public EnumDifficulty func_147135_j() {
            return EnumDifficulty.NORMAL;
        }

        @Override
        public boolean isHardcore() {
            return false;
        }

        @Override
        public int getOpPermissionLevel() {
            return 4;
        }

        @Override
        public boolean func_152363_m() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return this.dedicated;
        }

        @Override
        public boolean isCommandBlockEnabled() {
            return false;
        }

        @Override
        public String shareToLAN(WorldSettings.GameType type, boolean allowCheats) {
            return null;
        }
    }

    private static final class TestServerConfigurationManager extends ServerConfigurationManager {

        private TestServerConfigurationManager(MinecraftServer server) {
            super(server);
        }
    }

    private static final class TestFakePlayer extends FakePlayer {

        private TestFakePlayer() {
            super(null, null, "test-fake");
        }
    }

    private static final class TestRealPlayer extends EntityPlayerMP {

        private TestRealPlayer() {
            super(null, null, null, null);
        }
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

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
