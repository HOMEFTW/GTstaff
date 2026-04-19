package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;

class FakePlayerManagerServiceTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void submitSpawnBuildsPlayerCommandArguments() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);
        FakePlayerManagerService.SpawnDraft draft = new FakePlayerManagerService.SpawnDraft();
        draft.botName = "UiBot";
        draft.x = 10;
        draft.y = 64;
        draft.z = -3;
        draft.dimension = 7;
        draft.gameMode = "creative";

        String status = service.submitSpawn(sender(), draft);

        assertEquals("Spawned fake player UiBot.", status);
        assertArrayEquals(
            new String[] { "UiBot", "spawn", "at", "10", "64", "-3", "in", "7", "as", "creative" },
            runner.lastArgs);
    }

    @Test
    void submitSpawnRejectsBlankBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService(
            (sender, args) -> { throw new AssertionError("runner should not be invoked"); });
        FakePlayerManagerService.SpawnDraft draft = new FakePlayerManagerService.SpawnDraft();
        draft.botName = "   ";

        CommandException exception = assertThrows(CommandException.class, () -> service.submitSpawn(sender(), draft));

        assertEquals("Bot name cannot be empty", exception.getMessage());
    }

    @Test
    void submitSpawnParsesRawUiFieldValues() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);

        String status = service.submitSpawn(sender(), "UiBot", "10", "64", "-3", "7", "creative");

        assertEquals("Spawned fake player UiBot.", status);
        assertArrayEquals(
            new String[] { "UiBot", "spawn", "at", "10", "64", "-3", "in", "7", "as", "creative" },
            runner.lastArgs);
    }

    @Test
    void submitSpawnRejectsInvalidRawCoordinate() {
        FakePlayerManagerService service = new FakePlayerManagerService(
            (sender, args) -> { throw new AssertionError("runner should not be invoked"); });

        CommandException exception = assertThrows(
            CommandException.class,
            () -> service.submitSpawn(sender(), "UiBot", "oops", "64", "-3", "7", "creative"));

        assertEquals("Invalid X coordinate: oops", exception.getMessage());
    }

    @Test
    void submitLookBuildsPresetDirectionCommandArguments() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);
        FakePlayerManagerService.LookDraft draft = new FakePlayerManagerService.LookDraft();
        draft.botName = "UiBot";
        draft.mode = "west";

        String status = service.submitLook(sender(), draft);

        assertEquals("Updated look direction for UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "look", "west" }, runner.lastArgs);
    }

    @Test
    void submitLookBuildsLookAtCommandArguments() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);
        FakePlayerManagerService.LookDraft draft = new FakePlayerManagerService.LookDraft();
        draft.botName = "UiBot";
        draft.mode = "at";
        draft.x = 12;
        draft.y = 90;
        draft.z = -4;

        String status = service.submitLook(sender(), draft);

        assertEquals("Updated look direction for UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "look", "at", "12", "90", "-4" }, runner.lastArgs);
    }

    @Test
    void submitLookRejectsBlankBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService(
            (sender, args) -> { throw new AssertionError("runner should not be invoked"); });
        FakePlayerManagerService.LookDraft draft = new FakePlayerManagerService.LookDraft();
        draft.botName = "   ";

        CommandException exception = assertThrows(CommandException.class, () -> service.submitLook(sender(), draft));

        assertEquals("Bot name cannot be empty", exception.getMessage());
    }

    @Test
    void createLookDraftPrefillsSingleOnlineBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerRegistry.register(stubFakePlayer("SoloBot"), null);

        FakePlayerManagerService.LookDraft draft = service.createLookDraft(null);

        assertEquals("SoloBot", draft.botName);
    }

    @Test
    void readInventoryBuildsReadOnlySnapshotFromRegisteredFakePlayer() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerManagerService.InventoryDraft draft = new FakePlayerManagerService.InventoryDraft();
        draft.botName = "UiBot";

        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.currentItem = 2;
        fakePlayer.inventory.mainInventory[2] = namedStack("Apple", 5);
        fakePlayer.inventory.mainInventory[9] = namedStack("Iron Pickaxe", 1);
        fakePlayer.inventory.armorInventory[3] = namedStack("Diamond Helmet", 1);
        FakePlayerRegistry.register(fakePlayer, null);

        FakePlayerManagerService.InventorySnapshot snapshot = service.readInventory(draft);

        assertEquals("UiBot", snapshot.botName);
        assertEquals(2, snapshot.selectedHotbarSlot);
        assertTrue(snapshot.hotbarLines.contains("[*] 3: Apple x5"));
        assertTrue(snapshot.mainInventoryLines.contains("[ ] 10: Iron Pickaxe x1"));
        assertTrue(snapshot.armorLines.contains("Helmet: Diamond Helmet x1"));
    }

    @Test
    void createInventoryDraftPrefillsSingleOnlineBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerRegistry.register(stubFakePlayer("SoloBot"), null);

        FakePlayerManagerService.InventoryDraft draft = service.createInventoryDraft(null);

        assertEquals("SoloBot", draft.botName);
    }

    @Test
    void readInventoryRejectsBlankBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerManagerService.InventoryDraft draft = new FakePlayerManagerService.InventoryDraft();
        draft.botName = "   ";

        CommandException exception = assertThrows(CommandException.class, () -> service.readInventory(draft));

        assertEquals("Bot name cannot be empty", exception.getMessage());
    }

    @Test
    void readInventoryRejectsUnknownFakePlayer() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerManagerService.InventoryDraft draft = new FakePlayerManagerService.InventoryDraft();
        draft.botName = "MissingBot";

        CommandException exception = assertThrows(CommandException.class, () -> service.readInventory(draft));

        assertEquals("Fake player MissingBot is not online", exception.getMessage());
    }

    @Test
    void readInventoryRejectsUnknownFakePlayerWithOnlineBotHint() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerRegistry.register(stubFakePlayer("AlphaBot"), null);
        FakePlayerRegistry.register(stubFakePlayer("BetaBot"), null);
        FakePlayerManagerService.InventoryDraft draft = new FakePlayerManagerService.InventoryDraft();
        draft.botName = "MissingBot";

        CommandException exception = assertThrows(CommandException.class, () -> service.readInventory(draft));

        assertEquals("Fake player MissingBot is not online. Online bots: AlphaBot, BetaBot", exception.getMessage());
    }

    @Test
    void listBotNamesReturnsCaseInsensitiveSortedNames() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerRegistry.register(stubFakePlayer("beta"), null);
        FakePlayerRegistry.register(stubFakePlayer("Alpha"), null);
        FakePlayerRegistry.register(stubFakePlayer("gamma"), null);

        assertEquals(Arrays.asList("Alpha", "beta", "gamma"), service.listBotNames());
    }

    @Test
    void defaultSelectedBotNameUsesAlphabeticallyFirstBot() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        FakePlayerRegistry.register(stubFakePlayer("Zulu"), null);
        FakePlayerRegistry.register(stubFakePlayer("alpha"), null);

        assertEquals("alpha", service.defaultSelectedBotName());
    }

    @Test
    void describeBotReturnsSelectedHotbarAndPosition() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.currentItem = 5;
        fakePlayer.posX = 12.8D;
        fakePlayer.posY = 70.2D;
        fakePlayer.posZ = -4.4D;
        fakePlayer.dimension = 9;
        FakePlayerRegistry.register(fakePlayer, null);

        FakePlayerManagerService.BotDetails details = service.describeBot("UiBot");

        assertEquals("UiBot", details.botName);
        assertEquals(12, details.blockX);
        assertEquals(70, details.blockY);
        assertEquals(-5, details.blockZ);
        assertEquals(9, details.dimension);
        assertEquals(5, details.selectedHotbarSlot);
    }

    @Test
    void openInventoryManagerRejectsUnauthorizedPlayer() {
        boolean originalAllowNonOpControlOwnBot = Config.allowNonOpControlOwnBot;
        try {
            Config.allowNonOpControlOwnBot = true;
            FakePlayerManagerService service = new FakePlayerManagerService();
            StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
            fakePlayer.setOwnerUUID(UUID.randomUUID());
            FakePlayerRegistry.register(fakePlayer, null);
            RecordingPlayer player = stubPlayer(UUID.randomUUID(), false);

            CommandException exception = assertThrows(
                CommandException.class,
                () -> service.openInventoryManager(player, "UiBot"));

            assertEquals("You do not have permission to manage UiBot", exception.getMessage());
        } finally {
            Config.allowNonOpControlOwnBot = originalAllowNonOpControlOwnBot;
        }
    }

    @Test
    void openInventoryManagerOpensForgeGuiForAuthorizedPlayer() {
        boolean originalAllowNonOpControlOwnBot = Config.allowNonOpControlOwnBot;
        try {
            Config.allowNonOpControlOwnBot = true;
            FakePlayerManagerService service = new FakePlayerManagerService();
            RecordingPlayer player = stubPlayer(UUID.randomUUID(), true);
            StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
            fakePlayer.entityId = 1243;
            fakePlayer.setOwnerUUID(UUID.randomUUID());
            FakePlayerRegistry.register(fakePlayer, null);

            String status = service.openInventoryManager(player, "UiBot");

            assertEquals("Opening inventory manager for UiBot.", status);
            assertEquals(1243, player.lastOpenX);
        } finally {
            Config.allowNonOpControlOwnBot = originalAllowNonOpControlOwnBot;
        }
    }

    private static ICommandSender sender() {
        return (ICommandSender) Proxy.newProxyInstance(
            FakePlayerManagerServiceTest.class.getClassLoader(),
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

    private static StubFakePlayer stubFakePlayer(String name) {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = name;
        fakePlayer.inventory = new InventoryPlayer(fakePlayer);
        return fakePlayer;
    }

    private static RecordingPlayer stubPlayer(UUID uniqueId, boolean op) {
        RecordingPlayer player = allocate(RecordingPlayer.class);
        player.uniqueId = uniqueId;
        player.op = op;
        player.inventory = new InventoryPlayer(player);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        return player;
    }

    private static ItemStack namedStack(String displayName, int stackSize) {
        ItemStack stack = new ItemStack(new Item(), stackSize);
        stack.setStackDisplayName(displayName);
        return stack;
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

    @Test
    void executeActionRunsPlayerCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);

        String status = service.executeAction(sender(), "UiBot", "attack");

        assertEquals("Executed attack on UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "attack" }, runner.lastArgs);
    }

    @Test
    void executeActionRejectsBlankBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService(
            (sender, args) -> { throw new AssertionError("runner should not be invoked"); });

        CommandException exception = assertThrows(
            CommandException.class,
            () -> service.executeAction(sender(), "  ", "attack"));

        assertEquals("Bot name cannot be empty", exception.getMessage());
    }

    @Test
    void executeActionRejectsBlankAction() {
        FakePlayerManagerService service = new FakePlayerManagerService(
            (sender, args) -> { throw new AssertionError("runner should not be invoked"); });

        CommandException exception = assertThrows(
            CommandException.class,
            () -> service.executeAction(sender(), "UiBot", "  "));

        assertEquals("Action cannot be empty", exception.getMessage());
    }

    @Test
    void killBotRunsKillCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);

        String status = service.killBot(sender(), "UiBot");

        assertEquals("Killed UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "kill" }, runner.lastArgs);
    }

    @Test
    void purgeBotRunsPurgeCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);

        String status = service.purgeBot(sender(), "UiBot");

        assertEquals("Purged UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "purge" }, runner.lastArgs);
    }

    @Test
    void shadowBotRunsShadowCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);

        String status = service.shadowBot(sender(), "UiBot");

        assertEquals("Created shadow of UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "shadow" }, runner.lastArgs);
    }

    @Test
    void toggleMonitorRunsMonitorOnCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);
        FakePlayerRegistry.register(stubFakePlayer("UiBot"), null);

        String status = service.toggleMonitor(sender(), "UiBot", true);

        assertEquals("Monitor enabled for UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "monitor", "on" }, runner.lastArgs);
    }

    @Test
    void toggleMonitorRunsMonitorOffCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);
        FakePlayerRegistry.register(stubFakePlayer("UiBot"), null);

        String status = service.toggleMonitor(sender(), "UiBot", false);

        assertEquals("Monitor disabled for UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "monitor", "off" }, runner.lastArgs);
    }

    @Test
    void setMonitorRangeRunsCommand() {
        RecordingRunner runner = new RecordingRunner();
        FakePlayerManagerService service = new FakePlayerManagerService(runner);
        FakePlayerRegistry.register(stubFakePlayer("UiBot"), null);

        String status = service.setMonitorRange(sender(), "UiBot", 32);

        assertEquals("Monitor range set to 32 for UiBot.", status);
        assertArrayEquals(new String[] { "UiBot", "monitor", "range", "32" }, runner.lastArgs);
    }

    @Test
    void scanMachinesReturnsSummaryForOnlineBot() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        FakePlayerRegistry.register(fakePlayer, null);

        String summary = service.scanMachines("UiBot");

        assertTrue(summary.contains("监控: 关"));
        assertTrue(summary.contains("未发现GT多方块机器"));
    }

    @Test
    void scanMachinesReturnsOfflineMessageForMissingBot() {
        FakePlayerManagerService service = new FakePlayerManagerService();

        String summary = service.scanMachines("MissingBot");

        assertEquals("假人 MissingBot 不在线。", summary);
    }

    @Test
    void getInventorySummaryTextReturnsCompactSnapshot() {
        FakePlayerManagerService service = new FakePlayerManagerService();
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.currentItem = 0;
        fakePlayer.inventory.mainInventory[0] = namedStack("Iron Pickaxe", 1);
        fakePlayer.inventory.armorInventory[3] = namedStack("Diamond Helmet", 1);
        FakePlayerRegistry.register(fakePlayer, null);

        String summary = service.getInventorySummaryText("UiBot");

        assertTrue(summary.contains("Iron Pickaxe"));
        assertTrue(summary.contains("Diamond Helmet"));
    }

    @Test
    void getInventorySummaryTextReturnsOfflineMessageForMissingBot() {
        FakePlayerManagerService service = new FakePlayerManagerService();

        String summary = service.getInventorySummaryText("MissingBot");

        assertEquals("假人 MissingBot 不在线。", summary);
    }

    private static final class RecordingRunner implements FakePlayerManagerService.CommandRunner {

        private String[] lastArgs;

        @Override
        public void run(ICommandSender sender, String[] args) {
            this.lastArgs = args;
        }
    }

    private static final class StubFakePlayer extends FakePlayer {

        private String name;
        private boolean monitoring;
        private int monitorRange = 16;
        private int entityId = 99;
        private MachineMonitorService machineMonitorService;

        private StubFakePlayer() {
            super(null, null, "stub");
        }

        @Override
        public String getCommandSenderName() {
            return this.name;
        }

        @Override
        public boolean isMonitoring() {
            return this.monitoring;
        }

        @Override
        public void setMonitoring(boolean monitoring) {
            this.monitoring = monitoring;
        }

        @Override
        public int getMonitorRange() {
            return this.monitorRange;
        }

        @Override
        public void setMonitorRange(int monitorRange) {
            this.monitorRange = monitorRange;
        }

        @Override
        public int getEntityId() {
            return this.entityId;
        }

        @Override
        public MachineMonitorService getMachineMonitorService() {
            if (this.machineMonitorService == null) {
                this.machineMonitorService = new MachineMonitorService();
            }
            return this.machineMonitorService;
        }
    }

    private static final class RecordingPlayer extends EntityPlayerMP {

        private UUID uniqueId;
        private boolean op;
        private int lastOpenX = Integer.MIN_VALUE;

        private RecordingPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
            return this.op;
        }

        @Override
        public UUID getUniqueID() {
            return this.uniqueId;
        }

        @Override
        public void openGui(Object mod, int modGuiId, net.minecraft.world.World world, int x, int y, int z) {
            this.lastOpenX = x;
        }
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }
}
