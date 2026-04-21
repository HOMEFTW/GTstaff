package com.andgatech.gtstaff.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventoryRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventorySummary;
import com.andgatech.gtstaff.fakeplayer.runtime.BotLifecycleManager;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.mojang.authlib.GameProfile;

class CommandPlayerTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
        Config.fakePlayerRuntimeMode = "legacy";
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

    @Test
    void listStillUsesLegacyBotsWhenRuntimeModeDefaultsToLegacy() {
        Config.fakePlayerRuntimeMode = "legacy";
        FakePlayerRegistry.clear();
        FakePlayerRegistry.register(fakePlayer("WaveABot"), UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));

        TestCommandSender sender = new TestCommandSender();
        new CommandPlayer().processCommand(sender, new String[] { "list" });

        assertTrue(sender.messages().stream().anyMatch(line -> line.contains("WaveABot")));
    }

    @Test
    void inventorySummaryUsesRuntimeHandleWhenNoLegacyFakePlayerIsRegistered() {
        FakePlayerRegistry.registerRuntime(
            stubRuntime(
                "RuntimeBot",
                new BotInventorySummary(
                    "RuntimeBot",
                    0,
                    Collections.singletonList("[*] 1: Torch x16"),
                    Collections.singletonList("[ ] 10: Wrench x1"),
                    Collections.singletonList("Helmet: Nano Helmet x1"))));
        TestCommandSender sender = new TestCommandSender();

        new CommandPlayer().processCommand(sender, new String[] { "RuntimeBot", "inventory", "summary" });

        assertTrue(sender.messages().stream().anyMatch(line -> line.contains("Torch x16")));
        assertTrue(sender.messages().stream().anyMatch(line -> line.contains("Wrench x1")));
    }

    @Test
    void monitorIntervalUsesRuntimeHandleWhenNoLegacyFakePlayerIsRegistered() {
        StubRuntimeView runtime = stubRuntime("RuntimeBot");
        FakePlayerRegistry.registerRuntime(runtime);

        new CommandPlayer().processCommand(sender(), new String[] { "RuntimeBot", "monitor", "interval", "120" });

        assertEquals(120, runtime.monitorRuntime.reminderInterval());
    }

    @Test
    void spawnRoutesThroughLifecycleManager() {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        LifecycleCommandPlayer command = new LifecycleCommandPlayer(lifecycleManager);

        command.processCommand(
            new TestCommandSender(),
            new String[] { "WaveDSpawnBot", "spawn", "at", "10", "64", "-3", "in", "7", "as", "creative" });

        assertEquals("WaveDSpawnBot", lifecycleManager.spawnedBotName);
        assertEquals(7, lifecycleManager.spawnedDimension);
        assertEquals(WorldSettings.GameType.CREATIVE, lifecycleManager.spawnedGameType);
    }

    @Test
    void attackUsesRuntimeActionWhenLegacyFakePlayerIsMissing() {
        StubRuntimeView runtime = stubRuntime("RuntimeBot");
        FakePlayerRegistry.registerRuntime(runtime);

        new CommandPlayer().processCommand(sender(), new String[] { "RuntimeBot", "attack", "continuous" });

        assertEquals(ActionType.ATTACK, runtime.lastStartedType);
        assertTrue(runtime.lastStartedAction != null && runtime.lastStartedAction.isContinuous);
    }

    @Test
    void nonOwnerCannotManipulateRuntimeOnlyBot() {
        boolean originalAllowNonOpControlOwnBot = Config.allowNonOpControlOwnBot;
        try {
            Config.allowNonOpControlOwnBot = true;
            StubRuntimeView runtime = stubRuntime("RuntimeBot", UUID.randomUUID(), null);
            FakePlayerRegistry.registerRuntime(runtime);

            CommandException exception = assertThrows(
                CommandException.class,
                () -> new CommandPlayer().processCommand(playerSender(UUID.randomUUID(), false), new String[] {
                    "RuntimeBot",
                    "attack",
                    "once" }));

            assertEquals("You do not have permission to control that bot", exception.getMessage());
        } finally {
            Config.allowNonOpControlOwnBot = originalAllowNonOpControlOwnBot;
        }
    }

    @Test
    void ownerCanManipulateOwnRuntimeOnlyBotWhenPolicyAllowsIt() {
        boolean originalAllowNonOpControlOwnBot = Config.allowNonOpControlOwnBot;
        try {
            Config.allowNonOpControlOwnBot = true;
            UUID owner = UUID.randomUUID();
            StubRuntimeView runtime = stubRuntime("RuntimeBot", owner, null);
            FakePlayerRegistry.registerRuntime(runtime);

            new CommandPlayer().processCommand(playerSender(owner, false), new String[] { "RuntimeBot", "attack", "once" });

            assertEquals(ActionType.ATTACK, runtime.lastStartedType);
        } finally {
            Config.allowNonOpControlOwnBot = originalAllowNonOpControlOwnBot;
        }
    }

    @Test
    void shadowRoutesThroughLifecycleManager() {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        ShadowCommandPlayer command = new ShadowCommandPlayer(lifecycleManager, realPlayer("WaveDShadowBot"));

        command.processCommand(new TestCommandSender(), new String[] { "WaveDShadowBot", "shadow" });

        assertEquals("WaveDShadowBot", lifecycleManager.shadowedBotName);
    }

    @Test
    void shadowRejectsNextGenFakePlayerTarget() {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        ShadowCommandPlayer command = new ShadowCommandPlayer(lifecycleManager, nextGenPlayer("WaveDShadowBot"));

        CommandException exception = assertThrows(
            CommandException.class,
            () -> command.processCommand(new TestCommandSender(), new String[] { "WaveDShadowBot", "shadow" }));

        assertEquals("Target is already a fake player", exception.getMessage());
    }

    @Test
    void killUsesLifecycleManagerWhenLegacyFakePlayerIsMissing() {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        FakePlayerRegistry.registerRuntime(stubRuntime("WaveDKillBot"));

        new LifecycleCommandPlayer(lifecycleManager).processCommand(sender(), new String[] { "WaveDKillBot", "kill" });

        assertEquals("WaveDKillBot", lifecycleManager.killedBotName);
    }

    @Test
    void purgeUsesLifecycleManagerWhenRuntimeOnlyBotIsOnline() {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        FakePlayerRegistry.registerRuntime(stubRuntime("WaveDPurgeBot"));
        TestCommandSender sender = new TestCommandSender();

        new PurgeCommandPlayer(lifecycleManager).processCommand(sender, new String[] { "WaveDPurgeBot", "purge" });

        assertEquals("WaveDPurgeBot", lifecycleManager.killedBotName);
        assertEquals(null, FakePlayerRegistry.getRuntimeView("WaveDPurgeBot"));
        assertTrue(sender.messages().stream().anyMatch(line -> line.contains("Purged fake player WaveDPurgeBot")));
    }

    @Test
    void purgeUsesLifecycleManagerWhenLegacyFakePlayerIsOnline() {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        FakePlayerRegistry.register(fakePlayer("WaveDLegacyPurgeBot"), UUID.randomUUID());
        TestCommandSender sender = new TestCommandSender();

        new PurgeCommandPlayer(lifecycleManager).processCommand(sender, new String[] { "WaveDLegacyPurgeBot", "purge" });

        assertEquals("WaveDLegacyPurgeBot", lifecycleManager.killedBotName);
        assertEquals(null, FakePlayerRegistry.getRuntimeView("WaveDLegacyPurgeBot"));
        assertTrue(sender.messages().stream().anyMatch(line -> line.contains("Purged fake player WaveDLegacyPurgeBot")));
    }

    @Test
    void purgeUsesOnlineRuntimeProfileIdForNextgenCleanup(@TempDir File tempDir) throws IOException {
        RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
        UUID profileId = UUID.randomUUID();
        UUID offlineProfileId = net.minecraft.entity.player.EntityPlayer.func_146094_a(new GameProfile(null, "WaveDProfileBot"));
        File playerdataDir = new File(tempDir, "playerdata");
        File statsDir = new File(tempDir, "stats");
        assertTrue(playerdataDir.mkdirs());
        assertTrue(statsDir.mkdirs());
        File actualProfileDat = writeFile(new File(playerdataDir, profileId + ".dat"));
        File actualStatsJson = writeFile(new File(statsDir, profileId + ".json"));
        File offlineProfileDat = writeFile(new File(playerdataDir, offlineProfileId + ".dat"));
        File offlineStatsJson = writeFile(new File(statsDir, offlineProfileId + ".json"));
        FakePlayerRegistry.registerRuntime(
            stubRuntime("WaveDProfileBot", null, runtimePlayer("WaveDProfileBot", profileId)));

        new PurgeCommandPlayer(lifecycleManager, tempDir)
            .processCommand(new TestCommandSender(), new String[] { "WaveDProfileBot", "purge" });

        assertEquals("WaveDProfileBot", lifecycleManager.killedBotName);
        assertFalse(actualProfileDat.exists());
        assertFalse(actualStatsJson.exists());
        assertTrue(offlineProfileDat.exists());
        assertTrue(offlineStatsJson.exists());
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

    private static StubRuntimeView stubRuntime(String name) {
        return stubRuntime(name, null, null);
    }

    private static StubRuntimeView stubRuntime(String name, UUID ownerUuid,
        net.minecraft.entity.player.EntityPlayerMP entity) {
        return stubRuntime(
            name,
            ownerUuid,
            entity,
            new BotInventorySummary(
                name,
                0,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList()));
    }

    private static StubRuntimeView stubRuntime(String name, BotInventorySummary summary) {
        return stubRuntime(name, null, null, summary);
    }

    private static StubRuntimeView stubRuntime(String name, UUID ownerUuid,
        net.minecraft.entity.player.EntityPlayerMP entity, BotInventorySummary summary) {
        return new StubRuntimeView(name, summary, ownerUuid, entity);
    }

    private static PermissionedPlayer playerSender(UUID uniqueId, boolean op) {
        PermissionedPlayer player = allocate(PermissionedPlayer.class);
        player.uniqueId = uniqueId;
        player.op = op;
        player.messages = new ArrayList<String>();
        return player;
    }

    private static TestRealPlayer realPlayer(String name) {
        TestRealPlayer player = allocate(TestRealPlayer.class);
        player.name = name;
        player.uniqueId = UUID.nameUUIDFromBytes(name.getBytes());
        setField(
            net.minecraft.entity.player.EntityPlayerMP.class,
            player,
            "theItemInWorldManager",
            allocate(net.minecraft.server.management.ItemInWorldManager.class));
        return player;
    }

    private static TestNextGenPlayer nextGenPlayer(String name) {
        TestNextGenPlayer player = allocate(TestNextGenPlayer.class);
        player.name = name;
        player.profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);
        return player;
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

    private static File writeFile(File file) throws IOException {
        Files.write(file.toPath(), "test".getBytes(StandardCharsets.UTF_8));
        return file;
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

    private static final class TestCommandSender implements ICommandSender {

        private List<String> messages = new ArrayList<String>();

        @Override
        public String getCommandSenderName() {
            return "tester";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return null;
        }

        @Override
        public void addChatMessage(IChatComponent component) {
            messages.add(component == null ? null : component.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return true;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }

        @Override
        public World getEntityWorld() {
            return null;
        }

        private List<String> messages() {
            return messages;
        }
    }

    private static final class TestRealPlayer extends net.minecraft.entity.player.EntityPlayerMP {

        private UUID uniqueId;
        private String name;

        private TestRealPlayer() {
            super(null, null, null, null);
        }

        @Override
        public UUID getUniqueID() {
            return uniqueId;
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }
    }

    private static RuntimeEntityPlayer runtimePlayer(String name, UUID profileId) {
        RuntimeEntityPlayer player = allocate(RuntimeEntityPlayer.class);
        player.name = name;
        player.profile = new GameProfile(profileId, name);
        return player;
    }

    private static final class RuntimeEntityPlayer extends net.minecraft.entity.player.EntityPlayerMP {

        private String name;
        private GameProfile profile;

        private RuntimeEntityPlayer() {
            super(null, null, null, null);
        }

        @Override
        public String getCommandSenderName() {
            return name;
        }

        @Override
        public GameProfile getGameProfile() {
            return profile;
        }
    }

    private static final class TestNextGenPlayer
        extends com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer {

        private String name;
        private GameProfile profile;

        private TestNextGenPlayer() {
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
    }

    private static final class PermissionedPlayer extends net.minecraft.entity.player.EntityPlayerMP {

        private UUID uniqueId;
        private boolean op;
        private List<String> messages;

        private PermissionedPlayer() {
            super(null, null, null, null);
        }

        @Override
        public UUID getUniqueID() {
            return uniqueId;
        }

        @Override
        public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
            return op;
        }

        @Override
        public void addChatMessage(IChatComponent component) {
            messages.add(component == null ? null : component.getUnformattedText());
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

    private static final class StubRuntimeView implements BotRuntimeView {

        private final String name;
        private final BotInventorySummary summary;
        private final UUID ownerUuid;
        private final net.minecraft.entity.player.EntityPlayerMP entity;
        private final StubMonitorRuntime monitorRuntime = new StubMonitorRuntime();
        private ActionType lastStartedType;
        private Action lastStartedAction;

        private StubRuntimeView(String name, BotInventorySummary summary, UUID ownerUuid,
            net.minecraft.entity.player.EntityPlayerMP entity) {
            this.name = name;
            this.summary = summary;
            this.ownerUuid = ownerUuid;
            this.entity = entity;
        }

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
            return () -> entity;
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public BotActionRuntime action() {
            return new BotActionRuntime() {

                @Override
                public void start(com.andgatech.gtstaff.fakeplayer.ActionType type,
                    com.andgatech.gtstaff.fakeplayer.Action action) {
                    lastStartedType = type;
                    lastStartedAction = action;
                }

                @Override
                public void stop(com.andgatech.gtstaff.fakeplayer.ActionType type) {}

                @Override
                public void stopAll() {}

                @Override
                public void setSlot(int slot) {}

                @Override
                public void setForward(float value) {}

                @Override
                public void setStrafing(float value) {}

                @Override
                public void stopMovement() {}

                @Override
                public void look(float yaw, float pitch) {}

                @Override
                public void turn(float yaw, float pitch) {}

                @Override
                public void setSneaking(boolean value) {}

                @Override
                public void setSprinting(boolean value) {}

                @Override
                public void dismount() {}

                @Override
                public boolean supportsMount() {
                    return true;
                }
            };
        }

        @Override
        public BotFollowRuntime follow() {
            return new BotFollowRuntime() {

                @Override
                public boolean following() {
                    return false;
                }

                @Override
                public UUID targetUUID() {
                    return null;
                }

                @Override
                public int followRange() {
                    return 3;
                }

                @Override
                public int teleportRange() {
                    return 32;
                }

                @Override
                public void startFollowing(UUID targetUUID) {}

                @Override
                public void stop() {}

                @Override
                public void setFollowRange(int range) {}

                @Override
                public void setTeleportRange(int range) {}
            };
        }

        @Override
        public BotMonitorRuntime monitor() {
            return monitorRuntime;
        }

        @Override
        public BotRepelRuntime repel() {
            return new BotRepelRuntime() {

                @Override
                public boolean repelling() {
                    return false;
                }

                @Override
                public int repelRange() {
                    return 64;
                }

                @Override
                public void setRepelling(boolean repelling) {}

                @Override
                public void setRepelRange(int range) {}
            };
        }

        @Override
        public BotInventoryRuntime inventory() {
            return new BotInventoryRuntime() {

                @Override
                public int selectedHotbarSlot() {
                    return summary.selectedHotbarSlot();
                }

                @Override
                public BotInventorySummary summary() {
                    return summary;
                }

                @Override
                public String openInventoryManager(net.minecraft.entity.player.EntityPlayerMP player)
                    throws CommandException {
                    return "Opening inventory manager for " + name + ".";
                }
            };
        }
    }

    private static final class StubMonitorRuntime implements BotMonitorRuntime {

        private boolean monitoring;
        private int monitorRange = 16;
        private int reminderInterval = 600;

        @Override
        public boolean monitoring() {
            return monitoring;
        }

        @Override
        public int monitorRange() {
            return monitorRange;
        }

        @Override
        public int reminderInterval() {
            return reminderInterval;
        }

        @Override
        public void setMonitoring(boolean monitoring) {
            this.monitoring = monitoring;
        }

        @Override
        public void setMonitorRange(int range) {
            this.monitorRange = range;
        }

        @Override
        public void setReminderInterval(int ticks) {
            this.reminderInterval = ticks;
        }

        @Override
        public String overviewMessage(String botName) {
            return "";
        }
    }

    private static final class LifecycleCommandPlayer extends CommandPlayer {

        private final TestMinecraftServer server = allocate(TestMinecraftServer.class);
        private final WorldServer world = allocate(WorldServer.class);

        private LifecycleCommandPlayer(BotLifecycleManager lifecycleManager) {
            super(lifecycleManager);
        }

        @Override
        protected MinecraftServer requireServer() {
            return server;
        }

        @Override
        protected WorldServer resolveWorld(MinecraftServer server, int dimension) {
            return world;
        }
    }

    private static final class ShadowCommandPlayer extends CommandPlayer {

        private final net.minecraft.entity.player.EntityPlayerMP realPlayer;

        private ShadowCommandPlayer(BotLifecycleManager lifecycleManager,
            net.minecraft.entity.player.EntityPlayerMP realPlayer) {
            super(lifecycleManager);
            this.realPlayer = realPlayer;
        }

        @Override
        protected MinecraftServer requireServer() {
            return allocate(TestMinecraftServer.class);
        }

        @Override
        protected net.minecraft.entity.player.EntityPlayerMP resolvePlayer(ICommandSender sender, String name) {
            return realPlayer;
        }
    }

    private static final class PurgeCommandPlayer extends CommandPlayer {

        private final TestMinecraftServer server = allocate(TestMinecraftServer.class);
        private final File cleanupRoot;

        private PurgeCommandPlayer(BotLifecycleManager lifecycleManager) {
            this(lifecycleManager, null);
        }

        private PurgeCommandPlayer(BotLifecycleManager lifecycleManager, File cleanupRoot) {
            super(lifecycleManager);
            this.cleanupRoot = cleanupRoot;
        }

        @Override
        protected MinecraftServer requireServer() {
            return server;
        }

        @Override
        protected List<File> getSaveRootsForCleanup(MinecraftServer server) {
            return cleanupRoot == null ? Collections.<File>emptyList() : Collections.singletonList(cleanupRoot);
        }
    }

    private static final class RecordingLifecycleManager extends BotLifecycleManager {

        private String spawnedBotName;
        private int spawnedDimension;
        private WorldSettings.GameType spawnedGameType;
        private String shadowedBotName;
        private String killedBotName;

        @Override
        public BotRuntimeView spawn(String botName, MinecraftServer server, ChunkCoordinates position, float yaw,
            float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
            this.spawnedBotName = botName;
            this.spawnedDimension = dimension;
            this.spawnedGameType = gameType;
            return stubRuntime(botName);
        }

        @Override
        public BotRuntimeView shadow(MinecraftServer server, net.minecraft.entity.player.EntityPlayerMP sourcePlayer) {
            this.shadowedBotName = sourcePlayer == null ? null : sourcePlayer.getCommandSenderName();
            return stubRuntime(this.shadowedBotName);
        }

        @Override
        public boolean kill(String botName) {
            this.killedBotName = botName;
            FakePlayerRegistry.unregister(botName);
            return true;
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
