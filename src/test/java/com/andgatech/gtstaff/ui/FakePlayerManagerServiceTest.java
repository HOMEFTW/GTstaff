package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

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
            runner.lastArgs
        );
    }

    @Test
    void submitSpawnRejectsBlankBotName() {
        FakePlayerManagerService service = new FakePlayerManagerService((sender, args) -> {
            throw new AssertionError("runner should not be invoked");
        });
        FakePlayerManagerService.SpawnDraft draft = new FakePlayerManagerService.SpawnDraft();
        draft.botName = "   ";

        CommandException exception = assertThrows(CommandException.class, () -> service.submitSpawn(sender(), draft));

        assertEquals("Bot name cannot be empty", exception.getMessage());
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
        FakePlayerManagerService service = new FakePlayerManagerService((sender, args) -> {
            throw new AssertionError("runner should not be invoked");
        });
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
            }
        );
    }

    private static StubFakePlayer stubFakePlayer(String name) {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = name;
        fakePlayer.inventory = new InventoryPlayer(fakePlayer);
        return fakePlayer;
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
    }
}
