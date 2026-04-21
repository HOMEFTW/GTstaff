package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.mojang.authlib.GameProfile;

class BotLifecycleManagerTest {

    @Test
    void mixedModeRestoresPersistedLegacySnapshotsAsLegacyButUsesNextGenForFreshSpawn() {
        RecordingLifecycleManager manager = new RecordingLifecycleManager(BotRuntimeMode.MIXED);

        manager.spawn("WaveDSpawnBot", null, null, 0.0F, 0.0F, 0, null, false, UUID.randomUUID());
        manager.restore(null, persisted("WaveDRestoreBot", BotRuntimeType.LEGACY, UUID.randomUUID()));

        assertEquals("spawn:nextgen", manager.events.get(0));
        assertEquals("restore:legacy", manager.events.get(1));
    }

    @Test
    void mixedModeUsesNextGenForShadowCreation() {
        RecordingLifecycleManager manager = new RecordingLifecycleManager(BotRuntimeMode.MIXED);
        UUID ownerUuid = UUID.randomUUID();

        manager.shadow(null, shadowPlayer("WaveDShadowBot", ownerUuid));

        assertEquals("shadow:nextgen", manager.events.get(0));
    }

    @Test
    void killUnregistersRuntimeOnlyBot() {
        BotLifecycleManager manager = new BotLifecycleManager();
        BotRuntimeView runtime = runtime("WaveDKillBot", UUID.randomUUID());
        FakePlayerRegistry.registerRuntime(runtime);

        manager.kill("WaveDKillBot");

        assertEquals(null, FakePlayerRegistry.getRuntimeView("WaveDKillBot"));
    }

    @Test
    void killMarksNextGenForgePlayerDisconnectedBeforeRemoval() {
        BotLifecycleManager manager = new BotLifecycleManager();
        TrackingDisconnectForgePlayer player = allocate(TrackingDisconnectForgePlayer.class);
        primeForSetDead(player);
        BotRuntimeView runtime = runtime("WaveDDisconnectBot", UUID.randomUUID(), player);
        FakePlayerRegistry.registerRuntime(runtime);

        manager.kill("WaveDDisconnectBot");

        assertTrue(player.disconnectedMarked);
        assertEquals(null, FakePlayerRegistry.getRuntimeView("WaveDDisconnectBot"));
    }

    @Test
    void restoreNextGenReappliesPersistedServiceState() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        WorldServer world = allocate(WorldServer.class);
        UUID ownerUuid = UUID.randomUUID();
        BotLifecycleManager manager = new BotLifecycleManager(new NextGenBotFactory(
            dimension -> world,
            (minecraftServer, resolvedWorld, profile) -> player(profile.getName()),
            (botSession, minecraftServer) -> {},
            () -> false)) {

                @Override
                protected BotRuntimeMode runtimeMode() {
                    return BotRuntimeMode.NEXTGEN;
                }
            };
        UUID followTarget = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        BotRuntimeView runtime = manager.restore(
            server,
            persisted("WaveDRestoreBot", BotRuntimeType.NEXTGEN, ownerUuid, followTarget));

        assertNotNull(runtime);
        assertEquals(BotRuntimeType.NEXTGEN, runtime.runtimeType());
        assertTrue(runtime.monitor().monitoring());
        assertEquals(48, runtime.monitor().monitorRange());
        assertEquals(240, runtime.monitor().reminderInterval());
        assertTrue(runtime.repel().repelling());
        assertEquals(96, runtime.repel().repelRange());
        assertTrue(runtime.follow().following());
        assertEquals(followTarget, runtime.follow().targetUUID());
        assertEquals(5, runtime.follow().followRange());
        assertEquals(40, runtime.follow().teleportRange());
    }

    private static FakePlayerRegistry.PersistedBotData persisted(String name, BotRuntimeType runtimeType, UUID ownerUuid) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name);
        tag.setString("Owner", ownerUuid.toString());
        tag.setInteger("Dimension", 0);
        tag.setDouble("PosX", 1.0D);
        tag.setDouble("PosY", 64.0D);
        tag.setDouble("PosZ", 2.0D);
        tag.setFloat("Yaw", 90.0F);
        tag.setFloat("Pitch", 0.0F);
        tag.setInteger("GameType", 0);
        tag.setString("RuntimeType", runtimeType.name());
        tag.setInteger("SnapshotVersion", 2);
        return FakePlayerRegistry.PersistedBotData.fromTag(tag);
    }

    private static FakePlayerRegistry.PersistedBotData persisted(String name, BotRuntimeType runtimeType, UUID ownerUuid,
        UUID followTarget) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name);
        tag.setString("Owner", ownerUuid.toString());
        tag.setInteger("Dimension", 0);
        tag.setDouble("PosX", 1.0D);
        tag.setDouble("PosY", 64.0D);
        tag.setDouble("PosZ", 2.0D);
        tag.setFloat("Yaw", 90.0F);
        tag.setFloat("Pitch", 0.0F);
        tag.setInteger("GameType", 0);
        tag.setBoolean("Monitoring", true);
        tag.setInteger("MonitorRange", 48);
        tag.setInteger("ReminderInterval", 240);
        tag.setBoolean("MonsterRepelling", true);
        tag.setInteger("MonsterRepelRange", 96);
        tag.setString("FollowTarget", followTarget.toString());
        tag.setInteger("FollowRange", 5);
        tag.setInteger("TeleportRange", 40);
        tag.setString("RuntimeType", runtimeType.name());
        tag.setInteger("SnapshotVersion", 2);
        return FakePlayerRegistry.PersistedBotData.fromTag(tag);
    }

    private static net.minecraft.entity.player.EntityPlayerMP shadowPlayer(String name, UUID ownerUuid) {
        TestShadowPlayer player = allocate(TestShadowPlayer.class);
        player.name = name;
        player.uniqueId = ownerUuid;
        return player;
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
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static void primeForSetDead(net.minecraft.entity.player.EntityPlayerMP player) {
        Container container = new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        };
        player.inventory = new InventoryPlayer(player);
        setField(EntityPlayer.class, player, "inventoryContainer", container);
        setField(EntityPlayer.class, player, "openContainer", container);
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

    private static BotRuntimeView runtime(String name, UUID ownerUuid) {
        return runtime(name, ownerUuid, null);
    }

    private static BotRuntimeView runtime(String name, UUID ownerUuid, net.minecraft.entity.player.EntityPlayerMP player) {
        return new BotRuntimeView() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public UUID ownerUUID() {
                return ownerUuid;
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
        };
    }

    private static final class RecordingLifecycleManager extends BotLifecycleManager {

        private final BotRuntimeMode mode;
        private final List<String> events = new ArrayList<String>();

        private RecordingLifecycleManager(BotRuntimeMode mode) {
            this.mode = mode;
        }

        @Override
        protected BotRuntimeMode runtimeMode() {
            return mode;
        }

        @Override
        protected BotRuntimeView spawnLegacy(String botName, net.minecraft.server.MinecraftServer server,
            net.minecraft.util.ChunkCoordinates position, float yaw, float pitch, int dimension,
            net.minecraft.world.WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
            events.add("spawn:legacy");
            return stub(botName, BotRuntimeType.LEGACY, ownerUUID);
        }

        @Override
        protected BotRuntimeView spawnNextGen(String botName, net.minecraft.server.MinecraftServer server,
            net.minecraft.util.ChunkCoordinates position, float yaw, float pitch, int dimension,
            net.minecraft.world.WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
            events.add("spawn:nextgen");
            return stub(botName, BotRuntimeType.NEXTGEN, ownerUUID);
        }

        @Override
        protected BotRuntimeView restoreLegacy(net.minecraft.server.MinecraftServer server,
            com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry.PersistedBotData data) {
            events.add("restore:legacy");
            return stub(data.getName(), BotRuntimeType.LEGACY, data.getOwnerUUID());
        }

        @Override
        protected BotRuntimeView restoreNextGen(net.minecraft.server.MinecraftServer server,
            com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry.PersistedBotData data) {
            events.add("restore:nextgen");
            return stub(data.getName(), BotRuntimeType.NEXTGEN, data.getOwnerUUID());
        }

        @Override
        protected BotRuntimeView shadowLegacy(net.minecraft.server.MinecraftServer server,
            net.minecraft.entity.player.EntityPlayerMP sourcePlayer) {
            events.add("shadow:legacy");
            return stub(sourcePlayer.getCommandSenderName(), BotRuntimeType.LEGACY, sourcePlayer.getUniqueID());
        }

        @Override
        protected BotRuntimeView shadowNextGen(net.minecraft.server.MinecraftServer server,
            net.minecraft.entity.player.EntityPlayerMP sourcePlayer) {
            events.add("shadow:nextgen");
            return stub(sourcePlayer.getCommandSenderName(), BotRuntimeType.NEXTGEN, sourcePlayer.getUniqueID());
        }

        private BotRuntimeView stub(String name, BotRuntimeType runtimeType, UUID ownerUUID) {
            return new BotRuntimeView() {

                @Override
                public String name() {
                    return name;
                }

                @Override
                public UUID ownerUUID() {
                    return ownerUUID;
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
            };
        }
    }

    private static final class TestShadowPlayer extends net.minecraft.entity.player.EntityPlayerMP {

        private String name;
        private UUID uniqueId;

        private TestShadowPlayer() {
            super(null, null, null, null);
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }

        @Override
        public UUID getUniqueID() {
            return uniqueId;
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

    private static final class TrackingDisconnectForgePlayer extends GTstaffForgePlayer {

        private boolean disconnectedMarked;

        private TrackingDisconnectForgePlayer() {
            super(null, null, null);
        }

        @Override
        public void markDisconnected() {
            disconnectedMarked = true;
        }
    }
}
