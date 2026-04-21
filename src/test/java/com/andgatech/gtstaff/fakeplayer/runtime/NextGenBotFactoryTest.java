package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.mojang.authlib.GameProfile;

class NextGenBotFactoryTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void spawnCreatesNextGenRuntimeWithBoundActionPack() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        WorldServer world = allocate(WorldServer.class);
        UUID ownerUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        NextGenBotFactory factory = new NextGenBotFactory(
            dimension -> world,
            (minecraftServer, resolvedWorld, profile) -> player(profile.getName()),
            (botSession, minecraftServer) -> {},
            () -> false);

        NextGenBotRuntime runtime = factory.spawn(
            "WaveDBot",
            server,
            new ChunkCoordinates(10, 64, 12),
            90.0F,
            0.0F,
            0,
            WorldSettings.GameType.SURVIVAL,
            false,
            ownerUuid);

        assertNotNull(runtime);
        assertEquals(BotRuntimeType.NEXTGEN, runtime.runtimeType());
        assertNotNull(runtime.action());
        assertEquals(ownerUuid, runtime.ownerUUID());
        GTstaffForgePlayer player = (GTstaffForgePlayer) runtime.entity().asPlayer();
        assertSame(runtime, player.runtime());
        assertNotNull(player.getActionPack());
    }

    @Test
    void shadowCreatesNextGenRuntimeUsingSourcePlayerIdentity() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        WorldServer world = allocate(WorldServer.class);
        TestRealPlayer sourcePlayer = realPlayer("WaveDShadowBot");
        sourcePlayer.dimension = 7;
        sourcePlayer.posX = 11.5D;
        sourcePlayer.posY = 70.0D;
        sourcePlayer.posZ = -4.5D;
        sourcePlayer.rotationYaw = 45.0F;
        sourcePlayer.rotationPitch = 10.0F;
        NextGenBotFactory factory = new NextGenBotFactory(
            dimension -> world,
            (minecraftServer, resolvedWorld, profile) -> player(profile.getName()),
            (botSession, minecraftServer) -> {},
            () -> false);

        NextGenBotRuntime runtime = factory.shadow(server, sourcePlayer);

        assertNotNull(runtime);
        assertEquals("WaveDShadowBot", runtime.name());
        assertEquals(sourcePlayer.getUniqueID(), runtime.ownerUUID());
        assertEquals(7, runtime.dimension());
    }

    @Test
    void rebuildRestoredWithProfileReplacesRegisteredRuntimeAndPreservesServiceState() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        WorldServer world = allocate(WorldServer.class);
        UUID ownerUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID followTarget = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        List<BotSession> attachedSessions = new ArrayList<BotSession>();
        NextGenBotFactory factory = new NextGenBotFactory(
            dimension -> world,
            (minecraftServer, resolvedWorld, profile) -> trackingPlayer(profile),
            (botSession, minecraftServer) -> attachedSessions.add(botSession),
            () -> false);

        NextGenBotRuntime oldRuntime = factory.spawn(
            "WaveDRebuildBot",
            server,
            new ChunkCoordinates(10, 64, 12),
            90.0F,
            0.0F,
            0,
            WorldSettings.GameType.SURVIVAL,
            true,
            ownerUuid);
        TrackingForgePlayer oldPlayer = (TrackingForgePlayer) oldRuntime.entity().asPlayer();
        oldRuntime.monitor().setMonitoring(true);
        oldRuntime.monitor().setMonitorRange(48);
        oldRuntime.monitor().setReminderInterval(240);
        oldRuntime.repel().setRepelling(true);
        oldRuntime.repel().setRepelRange(96);
        oldRuntime.follow().setFollowRange(5);
        oldRuntime.follow().setTeleportRange(40);
        oldRuntime.follow().startFollowing(followTarget);
        oldPlayer.experience = 0.75F;
        oldPlayer.experienceLevel = 12;
        oldPlayer.experienceTotal = 345;
        oldPlayer.inventory.currentItem = 2;
        FakePlayerRegistry.registerRuntime(oldRuntime);
        attachedSessions.clear();

        GameProfile rebuiltProfile = new GameProfile(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), "ignored");
        BotRuntimeView rebuiltView = factory.rebuildRestoredWithProfile(server, oldRuntime, rebuiltProfile);

        assertNotNull(rebuiltView);
        assertNotSame(oldRuntime, rebuiltView);
        assertSame(rebuiltView, FakePlayerRegistry.getRuntimeView("WaveDRebuildBot"));
        assertEquals(BotRuntimeType.NEXTGEN, rebuiltView.runtimeType());
        assertTrue(rebuiltView.monitor().monitoring());
        assertEquals(48, rebuiltView.monitor().monitorRange());
        assertEquals(240, rebuiltView.monitor().reminderInterval());
        assertTrue(rebuiltView.repel().repelling());
        assertEquals(96, rebuiltView.repel().repelRange());
        assertTrue(rebuiltView.follow().following());
        assertEquals(followTarget, rebuiltView.follow().targetUUID());
        assertEquals(5, rebuiltView.follow().followRange());
        assertEquals(40, rebuiltView.follow().teleportRange());
        TrackingForgePlayer rebuiltPlayer = (TrackingForgePlayer) rebuiltView.entity().asPlayer();
        assertEquals(ownerUuid, rebuiltPlayer.getOwnerUUID());
        assertEquals(0.75F, rebuiltPlayer.experience);
        assertEquals(12, rebuiltPlayer.experienceLevel);
        assertEquals(345, rebuiltPlayer.experienceTotal);
        assertEquals(2, rebuiltPlayer.inventory.currentItem);
        assertEquals("WaveDRebuildBot", rebuiltPlayer.getCommandSenderName());
        assertEquals(rebuiltProfile.getId(), rebuiltPlayer.getGameProfile().getId());
        assertTrue(rebuiltPlayer.respawned);
        assertTrue(oldPlayer.disconnectedMarked);
        assertTrue(oldPlayer.deadSet);
        assertEquals(1, attachedSessions.size());
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

    private static GTstaffForgePlayer player(String name) {
        GTstaffForgePlayer player = allocate(GTstaffForgePlayer.class);
        setField(Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
        setField(
            EntityPlayer.class,
            player,
            "field_146106_i",
            new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static TrackingForgePlayer trackingPlayer(GameProfile profile) {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);
        setField(Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
        setField(
            EntityPlayer.class,
            player,
            "field_146106_i",
            profile);
        setField(EntityPlayer.class, player, "capabilities", new PlayerCapabilities());
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static TestRealPlayer realPlayer(String name) {
        TestRealPlayer player = allocate(TestRealPlayer.class);
        setField(Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            EntityPlayer.class,
            player,
            "field_146106_i",
            new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        setField(
            net.minecraft.entity.player.EntityPlayerMP.class,
            player,
            "theItemInWorldManager",
            allocate(net.minecraft.server.management.ItemInWorldManager.class));
        setField(net.minecraft.entity.player.EntityPlayer.class, player, "capabilities", new PlayerCapabilities());
        player.capabilities.isFlying = true;
        return player;
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

    private static final class TestMinecraftServer extends MinecraftServer {

        private TestMinecraftServer() {
            super(new java.io.File("."), java.net.Proxy.NO_PROXY);
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
            return false;
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

    private static final class TestRealPlayer extends net.minecraft.entity.player.EntityPlayerMP {

        private TestRealPlayer() {
            super(null, null, null, null);
        }
    }

    private static final class TrackingForgePlayer extends GTstaffForgePlayer {

        private boolean disconnectedMarked;
        private boolean respawned;
        private boolean deadSet;

        private TrackingForgePlayer() {
            super(null, null, null);
        }

        @Override
        public void markDisconnected() {
            disconnectedMarked = true;
        }

        @Override
        public void respawnFake() {
            respawned = true;
            this.isDead = false;
        }

        @Override
        public void setDead() {
            deadSet = true;
            this.isDead = true;
        }
    }
}
