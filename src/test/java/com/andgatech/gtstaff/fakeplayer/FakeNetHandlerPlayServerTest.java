package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
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
import com.mojang.authlib.GameProfile;

class FakeNetHandlerPlayServerTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void duplicateLoginKickTearsDownNextGenPlayer() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        TrackingServerConfigurationManager configurationManager = new TrackingServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);
        TrackingNextGenPlayer player = allocate(TrackingNextGenPlayer.class);
        player.name = "KickBot";
        player.profile = new GameProfile(UUID.nameUUIDFromBytes("KickBot".getBytes()), "KickBot");
        setField(EntityPlayerMP.class, player, "mcServer", server);
        FakePlayerRegistry.registerRuntime(runtime(player));

        FakeNetHandlerPlayServer.handleFakePlayerKick(player, "You logged in from another location");

        assertTrue(player.disconnectedMarked);
        assertEquals(1, player.setDeadCalls);
        assertEquals(1, configurationManager.playerLoggedOutCalls);
        assertEquals(null, FakePlayerRegistry.getRuntimeView("KickBot"));
    }

    @Test
    void unrelatedKickReasonDoesNotTearDownNextGenPlayer() {
        TestMinecraftServer server = allocate(TestMinecraftServer.class);
        TrackingServerConfigurationManager configurationManager = new TrackingServerConfigurationManager(server);
        setField(MinecraftServer.class, server, "serverConfigManager", configurationManager);
        TrackingNextGenPlayer player = allocate(TrackingNextGenPlayer.class);
        player.name = "KickBot";
        player.profile = new GameProfile(UUID.nameUUIDFromBytes("KickBot".getBytes()), "KickBot");
        setField(EntityPlayerMP.class, player, "mcServer", server);
        FakePlayerRegistry.registerRuntime(runtime(player));

        FakeNetHandlerPlayServer.handleFakePlayerKick(player, "Other reason");

        assertEquals(0, player.setDeadCalls);
        assertEquals(0, configurationManager.playerLoggedOutCalls);
        assertTrue(FakePlayerRegistry.getRuntimeView("KickBot") != null);
    }

    private static BotRuntimeView runtime(TrackingNextGenPlayer player) {
        return new BotRuntimeView() {

            @Override
            public String name() {
                return player.getCommandSenderName();
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

    private static final class TrackingNextGenPlayer extends GTstaffForgePlayer {

        private String name;
        private GameProfile profile;
        private boolean disconnectedMarked;
        private int setDeadCalls;

        private TrackingNextGenPlayer() {
            super(null, null, null);
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
        public void markDisconnected() {
            disconnectedMarked = true;
        }

        @Override
        public void setDead() {
            setDeadCalls++;
        }
    }

    private static final class TrackingServerConfigurationManager extends ServerConfigurationManager {

        private int playerLoggedOutCalls;

        private TrackingServerConfigurationManager(MinecraftServer server) {
            super(server);
        }

        @Override
        public void playerLoggedOut(EntityPlayerMP player) {
            playerLoggedOutCalls++;
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
}
