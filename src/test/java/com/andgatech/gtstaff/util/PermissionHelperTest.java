package com.andgatech.gtstaff.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

class PermissionHelperTest {

    private final int originalFakePlayerPermissionLevel = Config.fakePlayerPermissionLevel;
    private final boolean originalAllowNonOpControlOwnBot = Config.allowNonOpControlOwnBot;
    private final int originalMaxBotsPerPlayer = Config.maxBotsPerPlayer;
    private final int originalMaxBotsTotal = Config.maxBotsTotal;

    @AfterEach
    void resetState() {
        Config.fakePlayerPermissionLevel = originalFakePlayerPermissionLevel;
        Config.allowNonOpControlOwnBot = originalAllowNonOpControlOwnBot;
        Config.maxBotsPerPlayer = originalMaxBotsPerPlayer;
        Config.maxBotsTotal = originalMaxBotsTotal;
        FakePlayerRegistry.clear();
    }

    @Test
    void consoleSenderBypassesPlayerChecks() {
        ICommandSender sender = consoleSender();

        assertFalse(PermissionHelper.cantSpawn(sender, "console-bot", null).isPresent());
        assertFalse(PermissionHelper.cantManipulate(sender, bot(UUID.randomUUID(), "console-bot-a")));
        assertFalse(PermissionHelper.cantRemove(sender, bot(UUID.randomUUID(), "console-bot-b")));
    }

    @Test
    void ownerCanManipulateOwnBotWhenPolicyAllowsIt() {
        Config.allowNonOpControlOwnBot = true;

        UUID owner = UUID.randomUUID();
        TestFakePlayer sender = player(owner, false);
        FakePlayer target = bot(owner, "owner-control-bot");

        assertFalse(PermissionHelper.cantManipulate(sender, target));
    }

    @Test
    void ownerCannotManipulateOwnBotWhenPolicyDisallowsIt() {
        Config.allowNonOpControlOwnBot = false;

        UUID owner = UUID.randomUUID();
        TestFakePlayer sender = player(owner, false);
        FakePlayer target = bot(owner, "owner-blocked-bot");

        assertTrue(PermissionHelper.cantManipulate(sender, target));
    }

    @Test
    void operatorCanRemoveAnyBot() {
        TestFakePlayer sender = player(UUID.randomUUID(), true);

        assertFalse(PermissionHelper.cantRemove(sender, bot(UUID.randomUUID(), "op-bot")));
    }

    @Test
    void cantSpawnRespectsPerOwnerLimit() {
        Config.maxBotsPerPlayer = 2;

        UUID owner = UUID.randomUUID();
        TestFakePlayer sender = player(owner, false);
        FakePlayerRegistry.register(bot(owner, "bot-a"), owner);
        FakePlayerRegistry.register(bot(owner, "bot-b"), owner);

        assertEquals("Player bot limit reached", PermissionHelper.cantSpawn(sender, "bot-c", null).orElse(null));
    }

    @Test
    void cantSpawnRejectsOnlineDuplicateNames() {
        TestMinecraftServer server = serverWithOnlinePlayer("Bot_Online");

        assertEquals("Player already online", PermissionHelper.cantSpawn(consoleSender(), "Bot_Online", server).orElse(null));
    }

    @Test
    void cantSpawnRejectsTotalLimit() {
        Config.maxBotsTotal = 1;
        FakePlayerRegistry.register(bot(UUID.randomUUID(), "existing-bot"), UUID.randomUUID());

        assertEquals("Server bot limit reached", PermissionHelper.cantSpawn(consoleSender(), "new-bot", null).orElse(null));
    }

    private static FakePlayer bot(UUID owner, String name) {
        FakePlayer bot = allocate(StubFakePlayer.class);
        ((StubFakePlayer) bot).name = name;
        ((StubFakePlayer) bot).profileId = UUID.nameUUIDFromBytes(name.getBytes());
        ((StubFakePlayer) bot).monitorRange = 16;
        bot.setOwnerUUID(owner);
        return bot;
    }

    private static TestFakePlayer player(UUID uniqueId, boolean op) {
        TestFakePlayer player = allocate(TestFakePlayer.class);
        player.uniqueId = uniqueId;
        player.op = op;
        return player;
    }

    private static ICommandSender consoleSender() {
        return (ICommandSender) Proxy.newProxyInstance(
            PermissionHelperTest.class.getClassLoader(),
            new Class<?>[] { ICommandSender.class },
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == float.class) return 0F;
                if (returnType == double.class) return 0D;
                return null;
            }
        );
    }

    private static TestMinecraftServer serverWithOnlinePlayer(String name) {
        TestMinecraftServer server = new TestMinecraftServer();
        setField(MinecraftServer.class, server, "serverConfigManager", new TestServerConfigurationManager(server, name));
        return server;
    }

    private static final class TestFakePlayer extends FakePlayer {

        private UUID uniqueId;
        private boolean op;

        private TestFakePlayer(UUID uniqueId, boolean op) {
            super(null, null, "test-player");
            this.uniqueId = uniqueId;
            this.op = op;
        }

        @Override
        public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
            return op;
        }

        @Override
        public UUID getUniqueID() {
            return uniqueId;
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

    private static final class TestServerConfigurationManager extends ServerConfigurationManager {
        private final String onlineName;

        private TestServerConfigurationManager(MinecraftServer server, String onlineName) {
            super(server);
            this.onlineName = onlineName;
        }

        @Override
        public TestFakePlayer func_152612_a(String username) {
            return onlineName != null && onlineName.equalsIgnoreCase(username) ? player(UUID.randomUUID(), false) : null;
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
