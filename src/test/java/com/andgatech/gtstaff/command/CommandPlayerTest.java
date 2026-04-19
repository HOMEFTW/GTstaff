package com.andgatech.gtstaff.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.mojang.authlib.GameProfile;

class CommandPlayerTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void listRoutesToListHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "list" });

        assertEquals("list", command.lastHandler);
    }

    @Test
    void spawnRoutesToSpawnHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "spawn" });

        assertEquals("spawn", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void purgeRoutesToPurgeHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "purge" });

        assertEquals("purge", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void monitorRoutesToMonitorHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "monitor", "on" });

        assertEquals("monitor", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void repelRoutesToRepelHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "repel", "on" });

        assertEquals("repel", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void inventoryRoutesToInventoryHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "inventory", "summary" });

        assertEquals("inventory", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void attackRoutesToManipulationHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "attack", "continuous" });

        assertEquals("manipulation", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
        assertEquals("attack", command.lastAction);
    }

    @Test
    void usageMentionsMissingCommandsThatNowHaveHandlers() {
        String usage = new CommandPlayer().getCommandUsage(sender());

        assertTrue(usage.contains("repel"));
        assertTrue(usage.contains("inventory"));
        assertTrue(usage.contains("stopattack"));
        assertTrue(usage.contains("stopuse"));
    }

    @Test
    void monitorIntervalUpdatesReminderInterval() {
        CommandPlayer command = new CommandPlayer();
        StubFakePlayer bot = fakePlayer("Bot_Steve");
        FakePlayerRegistry.register(bot, null);

        command.processCommand(sender(), new String[] { "Bot_Steve", "monitor", "interval", "120" });

        assertEquals(120, bot.getReminderInterval());
    }

    @Test
    void repelCommandUpdatesMonsterRepelStateAndRange() {
        CommandPlayer command = new CommandPlayer();
        StubFakePlayer bot = fakePlayer("Bot_Steve");
        FakePlayerRegistry.register(bot, null);

        command.processCommand(sender(), new String[] { "Bot_Steve", "repel", "on", "range", "128" });

        assertTrue(bot.isMonsterRepelling());
        assertEquals(128, bot.getMonsterRepelRange());
    }

    @Test
    void resolveSaveRootPrefersCurrentWorldSaveDirectory() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();
        File fallbackRoot = new File("server-root");
        File worldSaveRoot = new File("world-root");
        command.fallbackSaveRoot = fallbackRoot;
        command.currentSaveRoot = worldSaveRoot;

        File resolved = command.resolveSaveRoot(null);

        assertSame(worldSaveRoot, resolved);
    }

    @Test
    void resolveSaveRootPrefersOverworldSaveDirectory() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();
        File overworldSaveRoot = new File("overworld-root");
        File worldSaveRoot = new File("world-root");
        File fallbackRoot = new File("server-root");
        command.overworldSaveRoot = overworldSaveRoot;
        command.currentSaveRoot = worldSaveRoot;
        command.fallbackSaveRoot = fallbackRoot;

        File resolved = command.resolveSaveRoot(null);

        assertSame(overworldSaveRoot, resolved);
    }

    @Test
    void cleanupRootsIncludeResolvedRootAndFallbackRootWhenDifferent() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();
        File resolvedRoot = new File("world-root");
        File fallbackRoot = new File("game-root");
        command.currentSaveRoot = resolvedRoot;
        command.fallbackSaveRoot = fallbackRoot;

        assertIterableEquals(Arrays.asList(resolvedRoot, fallbackRoot), command.getSaveRootsForCleanup(null));
    }

    @Test
    void missingArgumentsThrowsUsage() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        assertThrows(WrongUsageException.class, () -> command.processCommand(sender(), new String[0]));
        assertThrows(WrongUsageException.class, () -> command.processCommand(sender(), new String[] { "Bot_Steve" }));
    }

    private static StubFakePlayer fakePlayer(String name) {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = name;
        fakePlayer.profileId = UUID.nameUUIDFromBytes(name.getBytes());
        fakePlayer.monitorRange = 16;
        fakePlayer.monsterRepelRange = 64;
        fakePlayer.reminderInterval = 600;
        return fakePlayer;
    }

    private static ICommandSender sender() {
        return (ICommandSender) Proxy.newProxyInstance(
            CommandPlayerTest.class.getClassLoader(),
            new Class<?>[] { ICommandSender.class },
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == float.class) return 0F;
                if (returnType == double.class) return 0D;
                return null;
            });
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

    private static final class TrackingCommandPlayer extends CommandPlayer {

        private String lastHandler;
        private String lastBotName;
        private String lastAction;
        private File overworldSaveRoot;
        private File currentSaveRoot;
        private File fallbackSaveRoot;

        @Override
        protected void handleList(ICommandSender sender) {
            lastHandler = "list";
        }

        @Override
        protected void handleSpawn(ICommandSender sender, String botName, String[] args) {
            lastHandler = "spawn";
            lastBotName = botName;
        }

        @Override
        protected void handleKill(ICommandSender sender, String botName) {
            lastHandler = "kill";
            lastBotName = botName;
        }

        @Override
        protected void handlePurge(ICommandSender sender, String botName) {
            lastHandler = "purge";
            lastBotName = botName;
        }

        @Override
        protected void handleShadow(ICommandSender sender, String botName) {
            lastHandler = "shadow";
            lastBotName = botName;
        }

        @Override
        protected void handleMonitor(ICommandSender sender, String botName, String[] args) {
            lastHandler = "monitor";
            lastBotName = botName;
        }

        @Override
        protected void handleRepel(ICommandSender sender, String botName, String[] args) {
            lastHandler = "repel";
            lastBotName = botName;
        }

        @Override
        protected void handleInventory(ICommandSender sender, String botName, String[] args) {
            lastHandler = "inventory";
            lastBotName = botName;
        }

        @Override
        protected void handleManipulation(ICommandSender sender, String botName, String action, String[] args) {
            lastHandler = "manipulation";
            lastBotName = botName;
            lastAction = action;
        }

        @Override
        protected File getCurrentSaveRootDirectory() {
            return currentSaveRoot;
        }

        @Override
        protected File getOverworldSaveRoot(MinecraftServer server) {
            return overworldSaveRoot;
        }

        @Override
        protected File getFallbackSaveRoot(MinecraftServer server) {
            return fallbackSaveRoot;
        }
    }

    private static final class StubFakePlayer extends FakePlayer {

        private String name;
        private UUID profileId;
        private boolean monitoring;
        private int monitorRange;
        private int reminderInterval;
        private boolean monsterRepelling;
        private int monsterRepelRange;

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
    }
}
