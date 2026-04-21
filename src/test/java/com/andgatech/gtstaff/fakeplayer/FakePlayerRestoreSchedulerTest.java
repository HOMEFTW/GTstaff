package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventoryRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

class FakePlayerRestoreSchedulerTest {

    @AfterEach
    void resetScheduler() {
        FakePlayerRestoreScheduler.resetForTesting();
        FakePlayerSkinRestoreScheduler.resetForTests();
    }

    @Test
    void dedicatedServerRestoresOnNextTick() {
        List<MinecraftServer> restored = new ArrayList<MinecraftServer>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        setField(MinecraftServer.class, server, "serverConfigManager", new TestServerConfigurationManager(server));
        FakePlayerRestoreScheduler.setRestoreActionForTesting(minecraftServer -> {
            restored.add(minecraftServer);
            return Collections.emptyList();
        });

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
        FakePlayerRestoreScheduler.setRestoreActionForTesting(minecraftServer -> {
            restored.add(minecraftServer);
            return Collections.emptyList();
        });

        FakePlayerRestoreScheduler.schedule(server);
        configurationManager.playerEntityList.add(allocate(TestFakePlayer.class));

        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(0, restored.size());

        configurationManager.playerEntityList.add(allocate(TestRealPlayer.class));
        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(1, restored.size());
        assertEquals(server, restored.get(0));
    }

    @Test
    void integratedServerStillWaitsWhenOnlyNextGenBotIsPresent() {
        List<MinecraftServer> restored = new ArrayList<MinecraftServer>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = false;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);
        FakePlayerRestoreScheduler.setRestoreActionForTesting(minecraftServer -> {
            restored.add(minecraftServer);
            return Collections.emptyList();
        });

        FakePlayerRestoreScheduler.schedule(server);
        configurationManager.playerEntityList.add(allocate(TestNextGenPlayer.class));

        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(0, restored.size());

        configurationManager.playerEntityList.add(allocate(TestRealPlayer.class));
        FakePlayerRestoreScheduler.runPendingRestore();
        assertEquals(1, restored.size());
        assertEquals(server, restored.get(0));
    }

    @Test
    void restoreSchedulerHandsRestoredBotsToSkinScheduler() {
        List<BotRuntimeView> restoredBots = new ArrayList<BotRuntimeView>();
        List<BotRuntimeView> scheduledBots = new ArrayList<BotRuntimeView>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        setField(MinecraftServer.class, server, "serverConfigManager", new TestServerConfigurationManager(server));
        TestFakePlayer alpha = allocate(TestFakePlayer.class);
        TestFakePlayer beta = allocate(TestFakePlayer.class);
        alpha.dimension = 0;
        beta.dimension = 0;
        setField(TestFakePlayer.class, alpha, "name", "Alpha");
        setField(TestFakePlayer.class, beta, "name", "Beta");
        restoredBots.add(alpha.asRuntimeView());
        restoredBots.add(beta.asRuntimeView());
        FakePlayerRestoreScheduler.setRestoreActionForTesting(minecraftServer -> restoredBots);
        FakePlayerRestoreScheduler
            .setSkinScheduleActionForTesting((minecraftServer, fakePlayer) -> scheduledBots.add(fakePlayer));

        FakePlayerRestoreScheduler.schedule(server);
        FakePlayerRestoreScheduler.runPendingRestore();

        assertEquals(2, scheduledBots.size());
        assertEquals("Alpha", scheduledBots.get(0).name());
        assertEquals("Beta", scheduledBots.get(1).name());
    }

    @Test
    void restoreSchedulerAlsoHandsNextGenBotsToSkinScheduler() {
        List<BotRuntimeView> restoredBots = new ArrayList<BotRuntimeView>();
        List<BotRuntimeView> scheduledBots = new ArrayList<BotRuntimeView>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        setField(MinecraftServer.class, server, "serverConfigManager", new TestServerConfigurationManager(server));
        TestFakePlayer legacy = allocate(TestFakePlayer.class);
        legacy.dimension = 0;
        setField(TestFakePlayer.class, legacy, "name", "LegacyAlpha");
        restoredBots.add(legacy.asRuntimeView());
        restoredBots.add(new StubRuntimeView("NextGenBot", allocate(TestNextGenPlayer.class)));
        FakePlayerRestoreScheduler.setRestoreActionForTesting(minecraftServer -> restoredBots);
        FakePlayerRestoreScheduler.setSkinScheduleActionForTesting((minecraftServer, runtime) -> scheduledBots.add(runtime));

        FakePlayerRestoreScheduler.schedule(server);
        FakePlayerRestoreScheduler.runPendingRestore();

        assertEquals(2, scheduledBots.size());
        assertEquals("LegacyAlpha", scheduledBots.get(0).name());
        assertEquals("NextGenBot", scheduledBots.get(1).name());
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

        private String name;

        private TestFakePlayer() {
            super(null, null, "test-fake");
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }
    }

    private static final class TestRealPlayer extends EntityPlayerMP {

        private TestRealPlayer() {
            super(null, null, null, null);
        }
    }

    private static final class TestNextGenPlayer extends GTstaffForgePlayer {

        private TestNextGenPlayer() {
            super(null, null, null);
        }
    }

    private static final class StubRuntimeView implements BotRuntimeView {

        private final String name;
        private final EntityPlayerMP player;

        private StubRuntimeView(String name, EntityPlayerMP player) {
            this.name = name;
            this.player = player;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public java.util.UUID ownerUUID() {
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
            return () -> player;
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
