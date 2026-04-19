package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.Test;

class FollowServiceTest {

    @Test
    void calculateMoveForward_towardsSouthYawZero() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 10.0, 20.0);
        assertEquals(1.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMoveForward_towardsEastYawZero() {
        // Target is east (+X=20), fake at X=10 facing south (yaw=0)
        // targetYaw = atan2(-10, 0) = -90, yawDiff = -90
        // moveForward = cos(-90°) ≈ 0, moveStrafing = sin(-90°) = -1.0
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 20.0, 10.0);
        assertEquals(0.0F, result[0], 0.01F, "moveForward");
        assertEquals(-1.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMoveForward_towardsNorthYaw180() {
        float[] result = FollowService.calculateMovement(180.0F, 10.0, 10.0, 10.0, 0.0);
        assertEquals(1.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMovement_diagonal() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 15.0, 15.0);
        float expectedComponent = (float) (Math.sqrt(2) / 2.0);
        assertEquals(expectedComponent, result[0], 0.01F, "moveForward");
        assertEquals(-expectedComponent, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMovement_samePosition_returnsZero() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 10.0, 10.0);
        assertEquals(0.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void normalizeYawDiff_wrapsCorrectly() {
        assertEquals(0.0F, FollowService.normalizeYawDiff(0.0F), 0.01F);
        assertEquals(90.0F, FollowService.normalizeYawDiff(90.0F), 0.01F);
        assertEquals(-90.0F, FollowService.normalizeYawDiff(270.0F), 0.01F);
        assertEquals(-180.0F, FollowService.normalizeYawDiff(-180.0F), 0.01F);
        assertEquals(-170.0F, FollowService.normalizeYawDiff(190.0F), 0.01F);
    }

    @Test
    void shouldJump_targetAboveThreshold() {
        assertTrue(FollowService.shouldJump(5.0, 8.0, true));
    }

    @Test
    void shouldNotJump_targetWithinThreshold() {
        assertFalse(FollowService.shouldJump(5.0, 5.3, true));
    }

    @Test
    void shouldDescend_targetBelowThreshold() {
        assertTrue(FollowService.shouldDescend(5.0, 3.0, true));
    }

    @Test
    void shouldNotDescend_notFlying() {
        assertFalse(FollowService.shouldDescend(5.0, 3.0, false));
    }

    @Test
    void defaultFollowRange() {
        assertEquals(3, FollowService.DEFAULT_FOLLOW_RANGE);
    }

    @Test
    void defaultTeleportRange() {
        assertEquals(32, FollowService.DEFAULT_TELEPORT_RANGE);
    }

    @Test
    void crossDimensionDelay() {
        assertEquals(100, FollowService.CROSS_DIM_DELAY_TICKS);
    }

    @Test
    void missingTargetDoesNotClearFollowState() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);

        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = "FollowBot";

        FollowService service = new FollowService(fakePlayer, () -> server, (bot, player, currentServer) -> true);
        service.startFollowing(UUID.randomUUID());

        service.tick();

        assertTrue(service.isFollowing());
    }

    @Test
    void targetRejoiningInAnotherDimensionResumesCrossDimensionFollow() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);

        UUID targetId = UUID.randomUUID();
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = "FollowBot";
        WorldServer sourceWorld = allocate(WorldServer.class);
        WorldServer targetWorld = allocate(WorldServer.class);
        fakePlayer.dimension = 0;
        setField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj", sourceWorld);
        setField(
            net.minecraft.entity.Entity.class,
            fakePlayer,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0));

        int[] attempts = { 0 };
        FollowService service = new FollowService(fakePlayer, () -> server, (bot, player, currentServer) -> {
            attempts[0]++;
            bot.dimension = player.dimension;
            bot.setWorld(targetWorld);
            return true;
        });
        service.startFollowing(targetId);

        service.tick();
        assertTrue(service.isFollowing());
        assertEquals(0, attempts[0]);

        StubTargetPlayer target = allocate(StubTargetPlayer.class);
        target.uniqueId = targetId;
        target.dimension = 1;
        configurationManager.playerEntityList.add(target);

        runCrossDimCountdown(service);

        assertEquals(1, attempts[0]);
        assertEquals(1, fakePlayer.dimension);
        assertSame(targetWorld, getField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj"));
    }

    @Test
    void liveTargetWinsOverStaleDeadTargetWithSameUuid() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);

        UUID targetId = UUID.randomUUID();
        StubTargetPlayer staleTarget = allocate(StubTargetPlayer.class);
        staleTarget.uniqueId = targetId;
        staleTarget.dimension = 0;
        staleTarget.isDead = true;
        configurationManager.playerEntityList.add(staleTarget);

        StubTargetPlayer liveTarget = allocate(StubTargetPlayer.class);
        liveTarget.uniqueId = targetId;
        liveTarget.dimension = 2;
        configurationManager.playerEntityList.add(liveTarget);

        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = "FollowBot";
        WorldServer sourceWorld = allocate(WorldServer.class);
        WorldServer targetWorld = allocate(WorldServer.class);
        fakePlayer.dimension = 0;
        setField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj", sourceWorld);
        setField(
            net.minecraft.entity.Entity.class,
            fakePlayer,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0));

        int[] attempts = { 0 };
        FollowService service = new FollowService(fakePlayer, () -> server, (bot, player, currentServer) -> {
            attempts[0]++;
            assertSame(liveTarget, player);
            bot.dimension = player.dimension;
            bot.setWorld(targetWorld);
            return true;
        });
        service.startFollowing(targetId);

        runCrossDimCountdown(service);

        assertEquals(1, attempts[0]);
        assertEquals(2, fakePlayer.dimension);
        assertSame(targetWorld, getField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj"));
    }

    @Test
    void failedCrossDimensionMoveRollsBackMutatedDimensionState() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);

        StubTargetPlayer target = allocate(StubTargetPlayer.class);
        target.uniqueId = UUID.randomUUID();
        target.dimension = 1;
        configurationManager.playerEntityList.add(target);

        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = "FollowBot";
        WorldServer sourceWorld = allocate(WorldServer.class);
        WorldServer leakedTargetWorld = allocate(WorldServer.class);
        fakePlayer.dimension = 0;
        setField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj", sourceWorld);
        setField(
            net.minecraft.entity.Entity.class,
            fakePlayer,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0));

        FollowService service = new FollowService(fakePlayer, () -> server, (bot, player, currentServer) -> {
            bot.dimension = player.dimension;
            bot.setWorld(leakedTargetWorld);
            return false;
        });
        service.startFollowing(target.uniqueId);

        for (int i = 0; i < FollowService.CROSS_DIM_DELAY_TICKS; i++) {
            service.tick();
        }

        assertEquals(0, fakePlayer.dimension);
        assertSame(sourceWorld, getField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj"));
        assertTrue(service.isFollowing());
    }

    @Test
    void failedCrossDimensionMoveRetriesOnNextCountdown() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        server.dedicated = true;
        TestServerConfigurationManager configurationManager = new TestServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);

        StubTargetPlayer target = allocate(StubTargetPlayer.class);
        target.uniqueId = UUID.randomUUID();
        target.dimension = 1;
        configurationManager.playerEntityList.add(target);

        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = "FollowBot";
        WorldServer sourceWorld = allocate(WorldServer.class);
        WorldServer targetWorld = allocate(WorldServer.class);
        fakePlayer.dimension = 0;
        setField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj", sourceWorld);
        setField(
            net.minecraft.entity.Entity.class,
            fakePlayer,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0));

        int[] attempts = { 0 };
        FollowService service = new FollowService(fakePlayer, () -> server, (bot, player, currentServer) -> {
            attempts[0]++;
            bot.dimension = player.dimension;
            bot.setWorld(targetWorld);
            return attempts[0] > 1;
        });
        service.startFollowing(target.uniqueId);

        runCrossDimCountdown(service);
        assertEquals(1, attempts[0]);
        assertEquals(0, fakePlayer.dimension);
        assertSame(sourceWorld, getField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj"));

        runCrossDimCountdown(service);
        assertEquals(2, attempts[0]);
        assertEquals(1, fakePlayer.dimension);
        assertSame(targetWorld, getField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj"));
    }

    private static final class StubFakePlayer extends FakePlayer {

        private String name;

        private StubFakePlayer() {
            super(null, null, "stub");
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }
    }

    private static final class StubTargetPlayer extends EntityPlayerMP {

        private UUID uniqueId;

        private StubTargetPlayer() {
            super(null, null, null, null);
        }

        @Override
        public UUID getUniqueID() {
            return uniqueId;
        }

        @Override
        public void addChatMessage(IChatComponent component) {}
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

    private static void runCrossDimCountdown(FollowService service) {
        for (int i = 0; i < FollowService.CROSS_DIM_DELAY_TICKS; i++) {
            service.tick();
        }
    }

    private static Object getField(Class<?> owner, Object target, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
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
