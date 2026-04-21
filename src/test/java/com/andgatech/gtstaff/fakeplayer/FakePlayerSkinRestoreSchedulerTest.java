package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventoryRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.BotSession;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;
import com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntime;

class FakePlayerSkinRestoreSchedulerTest {

    @AfterEach
    void resetState() {
        FakePlayerRegistry.clear();
        FakePlayerSkinRestoreScheduler.resetForTests();
        FakePlayer.setRestoreFactoryForTests(null);
    }

    @Test
    void successfulSkinResolveSchedulesRebuildForSameBotInstance() {
        Queue<Runnable> asyncQueue = new ArrayDeque<Runnable>();
        Queue<Runnable> mainThreadQueue = new ArrayDeque<Runnable>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        StubSkinBot bot = skinBot("SkinBot");
        GameProfile profile = texturedProfile("SkinBot");
        StringBuilder rebuilt = new StringBuilder();

        FakePlayerSkinRestoreScheduler.setAsyncExecutorForTests(asyncQueue::add);
        FakePlayerSkinRestoreScheduler.setMainThreadExecutorForTests(mainThreadQueue::add);
        FakePlayerSkinRestoreScheduler.setResolverForTests(name -> java.util.Optional.of(profile));
        FakePlayerSkinRestoreScheduler.setRebuildActionForTests((minecraftServer, oldBot, newProfile) -> {
            rebuilt.append(oldBot.name());
            return oldBot;
        });

        FakePlayerSkinRestoreScheduler.schedule(server, bot.asRuntimeView());
        runQueue(asyncQueue);
        runQueue(mainThreadQueue);

        assertEquals("SkinBot", rebuilt.toString());
    }

    @Test
    void failedSkinResolveLeavesOriginalBotUntouched() {
        Queue<Runnable> asyncQueue = new ArrayDeque<Runnable>();
        Queue<Runnable> mainThreadQueue = new ArrayDeque<Runnable>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        StubSkinBot bot = skinBot("FallbackBot");
        int[] rebuilt = { 0 };

        FakePlayerSkinRestoreScheduler.setAsyncExecutorForTests(asyncQueue::add);
        FakePlayerSkinRestoreScheduler.setMainThreadExecutorForTests(mainThreadQueue::add);
        FakePlayerSkinRestoreScheduler.setResolverForTests(name -> java.util.Optional.empty());
        FakePlayerSkinRestoreScheduler.setRebuildActionForTests((minecraftServer, oldBot, newProfile) -> {
            rebuilt[0]++;
            return oldBot;
        });

        FakePlayerSkinRestoreScheduler.schedule(server, bot.asRuntimeView());
        runQueue(asyncQueue);
        runQueue(mainThreadQueue);

        assertEquals(0, rebuilt[0]);
    }

    @Test
    void successfulSkinResolveSchedulesRebuildForNextGenRuntime() {
        Queue<Runnable> asyncQueue = new ArrayDeque<Runnable>();
        Queue<Runnable> mainThreadQueue = new ArrayDeque<Runnable>();
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        GTstaffForgePlayer player = nextGenPlayer("NextGenSkinBot");
        NextGenBotRuntime runtime = new NextGenBotRuntime(player, new BotSession(player), UUID.randomUUID());
        GameProfile profile = texturedProfile("NextGenSkinBot");
        StringBuilder rebuilt = new StringBuilder();

        FakePlayerSkinRestoreScheduler.setAsyncExecutorForTests(asyncQueue::add);
        FakePlayerSkinRestoreScheduler.setMainThreadExecutorForTests(mainThreadQueue::add);
        FakePlayerSkinRestoreScheduler.setResolverForTests(name -> java.util.Optional.of(profile));
        FakePlayerSkinRestoreScheduler.setRebuildActionForTests((minecraftServer, oldBot, newProfile) -> {
            rebuilt.append(oldBot.name());
            return oldBot;
        });

        FakePlayerSkinRestoreScheduler.schedule(server, runtime);
        runQueue(asyncQueue);
        runQueue(mainThreadQueue);

        assertEquals("NextGenSkinBot", rebuilt.toString());
    }

    @Test
    void rebuildWithProfileKeepsKeyStateAndReplacesRegistryEntry() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        StubSkinBot oldBot = skinBot("SkinBot");
        oldBot.dimension = 7;
        oldBot.posX = 10.5D;
        oldBot.posY = 64.0D;
        oldBot.posZ = -3.5D;
        oldBot.rotationYaw = 90.0F;
        oldBot.rotationPitch = 15.0F;
        oldBot.setOwnerUUID(UUID.randomUUID());
        oldBot.setMonitoring(true);
        oldBot.setMonitorRange(32);
        oldBot.setReminderInterval(1200);
        oldBot.setMonsterRepelling(true);
        oldBot.setMonsterRepelRange(128);
        oldBot.getFollowService().setFollowRange(5);
        oldBot.getFollowService().setTeleportRange(40);
        oldBot.getFollowService().startFollowing(UUID.randomUUID());
        FakePlayerRegistry.register(oldBot, oldBot.getOwnerUUID());

        StubSkinBot rebuilt = skinBot("SkinBot");
        FakePlayer.setRestoreFactoryForTests((minecraftServer, profile, state) -> rebuilt);

        FakePlayer result = FakePlayer.rebuildRestoredWithProfile(server, oldBot, texturedProfile("SkinBot"));

        assertSame(rebuilt, result);
        assertTrue(oldBot.killed);
        assertEquals(oldBot.getOwnerUUID(), rebuilt.getOwnerUUID());
        assertTrue(rebuilt.isMonitoring());
        assertEquals(32, rebuilt.getMonitorRange());
        assertEquals(1200, rebuilt.getReminderInterval());
        assertTrue(rebuilt.isMonsterRepelling());
        assertEquals(128, rebuilt.getMonsterRepelRange());
        assertEquals(5, rebuilt.getFollowService().getFollowRange());
        assertEquals(40, rebuilt.getFollowService().getTeleportRange());
        assertEquals(oldBot.getFollowService().getFollowTargetUUID(), rebuilt.getFollowService().getFollowTargetUUID());
        assertSame(rebuilt, FakePlayerRegistry.getFakePlayer("SkinBot"));
    }

    @Test
    void rebuildDoesNothingWhenRegistryEntryIsAlreadyStale() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        StubSkinBot oldBot = skinBot("SkinBot");
        StubSkinBot replacement = skinBot("SkinBot");
        FakePlayerRegistry.register(oldBot, UUID.randomUUID());
        FakePlayerRegistry.register(replacement, UUID.randomUUID());

        FakePlayer result = FakePlayer.rebuildRestoredWithProfile(server, oldBot, texturedProfile("SkinBot"));

        assertSame(oldBot, result);
        assertTrue(!oldBot.killed);
        assertSame(replacement, FakePlayerRegistry.getFakePlayer("SkinBot"));
    }

    private static void runQueue(Queue<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove().run();
        }
    }

    private static GameProfile texturedProfile(String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        profile.getProperties().put("textures", new Property("textures", "value", "signature"));
        return profile;
    }

    private static StubSkinBot skinBot(String name) {
        StubSkinBot fakePlayer = allocate(StubSkinBot.class);
        fakePlayer.name = name;
        fakePlayer.profile = new GameProfile(UUID.randomUUID(), name);
        fakePlayer.followService = new FollowService(fakePlayer, () -> null, (bot, target, server) -> true);
        return fakePlayer;
    }

    private static GTstaffForgePlayer nextGenPlayer(String name) {
        GTstaffForgePlayer player = allocate(GTstaffForgePlayer.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        setField(net.minecraft.entity.Entity.class, player, "worldObj", allocate(net.minecraft.world.WorldServer.class));
        setField(
            net.minecraft.entity.player.EntityPlayer.class,
            player,
            "field_146106_i",
            new GameProfile(UUID.randomUUID(), name));
        return player;
    }

    private static final class StubSkinBot extends FakePlayer {

        private String name;
        private GameProfile profile;
        private UUID ownerUUID;
        private boolean monitoring;
        private int monitorRange = 16;
        private int reminderInterval = 600;
        private boolean monsterRepelling;
        private int monsterRepelRange = 64;
        private FollowService followService;
        private boolean killed;

        private StubSkinBot() {
            super(null, null, "stub");
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }

        @Override
        public GameProfile getGameProfile() {
            return profile;
        }

        @Override
        public UUID getOwnerUUID() {
            return ownerUUID;
        }

        @Override
        public void setOwnerUUID(UUID ownerUUID) {
            this.ownerUUID = ownerUUID;
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

        @Override
        public int getReminderInterval() {
            return reminderInterval;
        }

        @Override
        public void setReminderInterval(int reminderInterval) {
            this.reminderInterval = reminderInterval;
        }

        @Override
        public boolean isMonsterRepelling() {
            return monsterRepelling;
        }

        @Override
        public void setMonsterRepelling(boolean repelling) {
            this.monsterRepelling = repelling;
        }

        @Override
        public int getMonsterRepelRange() {
            return monsterRepelRange;
        }

        @Override
        public void setMonsterRepelRange(int range) {
            this.monsterRepelRange = range;
        }

        @Override
        public FollowService getFollowService() {
            return followService;
        }

        @Override
        public boolean isFollowing() {
            return followService != null && followService.isFollowing();
        }

        @Override
        protected void kill() {
            this.killed = true;
            FakePlayerRegistry.unregister(this.name);
        }

        @Override
        public void respawnFake() {}
    }

    private static final class StubRuntimeView implements BotRuntimeView {

        private final String name;

        private StubRuntimeView(String name) {
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

    private static final class TestMinecraftServer extends MinecraftServer {

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
            return true;
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
