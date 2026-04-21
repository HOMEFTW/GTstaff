package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

class FakePlayerInventoryContainerTest {

    @Test
    void serverContainerAddsFakeSlotsBeforePlayerSlots() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        assertEquals(76, container.inventorySlots.size());
        assertTrue(container.isFakeInventorySlot(0));
        assertTrue(container.isFakeInventorySlot(39));
        assertFalse(container.isFakeInventorySlot(40));
    }

    @Test
    void clickingFakeHotbarSlotUpdatesSelectedMainHandSlot() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.mainInventory[3] = namedStack("Sword", 1);
        TestPlayer player = stubPlayer();
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        container.slotClick(7, 0, 0, player);

        assertEquals(3, fakePlayer.inventory.currentItem);
        assertEquals(3, container.getSelectedHotbarSlot());
    }

    @Test
    void transferStackMovesItemsFromPlayerInventoryIntoFakeInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = namedStack("Cobblestone", 64);
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        ItemStack moved = container.transferStackInSlot(player, 40);

        assertNotNull(moved);
        assertTrue(hasStackNamed(fakePlayer.inventory, "Cobblestone"));
    }

    @Test
    void transferStackMovesItemsFromFakeInventoryIntoPlayerInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.mainInventory[9] = namedStack("Iron Pickaxe", 1);
        TestPlayer player = stubPlayer();
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        ItemStack moved = container.transferStackInSlot(player, 13);

        assertNotNull(moved);
        assertTrue(hasStackNamed(player.inventory, "Iron Pickaxe"));
    }

    @Test
    void serverContainerSupportsNextGenForgePlayerInventory() {
        StubNextGenPlayer fakePlayer = stubNextGenPlayer("NextGenBot");
        fakePlayer.inventory.mainInventory[4] = namedStack("Wrench", 1);
        TestPlayer player = stubPlayer();

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        assertEquals(76, container.inventorySlots.size());
        assertEquals("Wrench", container.getFakeInventory().getStackInSlot(8).getDisplayName());
    }

    @Test
    void containerRestoresEquipmentLayoutWithoutShiftingPlayerInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.server(
            fakePlayer,
            Arrays.asList(
                FakePlayerInventoryExtraSlot.client(
                    FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                    "Baubles",
                    1),
                FakePlayerInventoryExtraSlot.client(
                    FakePlayerInventoryExtraSlot.Kind.OFFHAND,
                    "Offhand",
                    64)));

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        Slot baublesSlot = (Slot) container.inventorySlots.get(40);
        Slot offhandSlot = (Slot) container.inventorySlots.get(41);

        assertEquals(78, container.inventorySlots.size());
        assertTrue(container.isFakeInventorySlot(40));
        assertTrue(container.isFakeInventorySlot(41));
        assertFalse(container.isFakeInventorySlot(42));
        assertEquals(184, baublesSlot.xDisplayPosition);
        assertEquals(18, baublesSlot.yDisplayPosition);
        assertEquals(80, offhandSlot.xDisplayPosition);
        assertEquals(18, offhandSlot.yDisplayPosition);
        assertEquals(125, container.getPlayerInventoryTop());
        assertEquals(203, container.getGuiHeight());
        assertEquals(107, container.getTopSectionHeight());
    }

    @Test
    void fakeArmorSlotsProvideVanillaBackgroundIcons() throws NoSuchMethodException {
        assertEquals(
            FakePlayerArmorSlot.class,
            FakePlayerArmorSlot.class.getMethod("getBackgroundIconIndex")
                .getDeclaringClass());
    }

    @Test
    void extraSlotUsesItsOwnStackLimit() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.client(
            "UiBot",
            Collections.singletonList(FakePlayerInventoryExtraSlot.client(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                1)));
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        Slot baubleSlot = (Slot) container.inventorySlots.get(40);

        assertEquals(1, baubleSlot.getSlotStackLimit());
    }

    @Test
    void baublesExtraSlotKeepsSlotTypeForBackgroundIcon() {
        TestPlayer player = stubPlayer();
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.client(
            "UiBot",
            Collections.singletonList(FakePlayerInventoryExtraSlot.client(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                1,
                "ring")));
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        Slot baubleSlot = (Slot) container.inventorySlots.get(40);

        assertTrue(baubleSlot instanceof FakePlayerInventoryContainer.FakePlayerExtraSlot);
        assertEquals(
            "ring",
            ((FakePlayerInventoryContainer.FakePlayerExtraSlot) baubleSlot).getBaublesSlotTypeForBackground());
    }

    @Test
    void extraSlotRejectsInvalidItemsForManualPlacement() {
        TestPlayer player = stubPlayer();
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.client(
            "UiBot",
            Collections.singletonList(FakePlayerInventoryExtraSlot.client(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                1,
                "ring")));
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        Slot baubleSlot = (Slot) container.inventorySlots.get(40);

        assertFalse(baubleSlot.isItemValid(namedStack("Stone", 1)));
    }

    @Test
    void shiftClickCanMoveItemsIntoValidExtraFakeSlotWhenBaseInventoryIsFull() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fillMainInventory(fakePlayer.inventory);
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = namedStack("Torch", 16);
        TestInventory offhand = new TestInventory(1);
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.server(
            fakePlayer,
            Collections.singletonList(FakePlayerInventoryExtraSlot.fromInventory(
                FakePlayerInventoryExtraSlot.Kind.OFFHAND,
                "Offhand",
                offhand,
                0)));
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        ItemStack moved = container.transferStackInSlot(player, 41);

        assertNotNull(moved);
        assertEquals("Torch", offhand.getStackInSlot(0).getDisplayName());
    }

    @Test
    void shiftClickPrefersBaublesSlotsBeforeOrdinaryFakeInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = namedStack("Ring", 1);
        TestInventory baubles = new TestInventory(1);
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.server(
            fakePlayer,
            Collections.singletonList(FakePlayerInventoryExtraSlot.fromInventory(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                baubles,
                0)));
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        ItemStack moved = container.transferStackInSlot(player, 41);

        assertNotNull(moved);
        assertEquals("Ring", baubles.getStackInSlot(0).getDisplayName());
        assertFalse(hasStackNamed(fakePlayer.inventory, "Ring"));
    }

    @Test
    void shiftClickDoesNotConsumeItemsWhenExtraSlotRejectsThem() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fillMainInventory(fakePlayer.inventory);
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = namedStack("Stone", 16);
        RejectingInventory baubles = new RejectingInventory(1);
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.server(
            fakePlayer,
            Collections.singletonList(FakePlayerInventoryExtraSlot.fromInventory(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                baubles,
                0,
                "ring")));
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);

        ItemStack moved = container.transferStackInSlot(player, 41);

        assertNull(moved);
        assertEquals("Stone", player.inventory.mainInventory[9].getDisplayName());
        assertEquals(16, player.inventory.mainInventory[9].stackSize);
        assertEquals(null, baubles.getStackInSlot(0));
    }

    private static boolean hasStackNamed(InventoryPlayer inventory, String name) {
        for (ItemStack stack : inventory.mainInventory) {
            if (stack != null && name.equals(stack.getDisplayName())) {
                return true;
            }
        }
        for (ItemStack stack : inventory.armorInventory) {
            if (stack != null && name.equals(stack.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    private static void fillMainInventory(InventoryPlayer inventory) {
        for (int slot = 0; slot < inventory.mainInventory.length; slot++) {
            inventory.mainInventory[slot] = namedStack("Filler " + slot, 64);
        }
    }

    private static StubFakePlayer stubFakePlayer(String name) {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = name;
        fakePlayer.inventory = new InventoryPlayer(fakePlayer);
        return fakePlayer;
    }

    private static TestPlayer stubPlayer() {
        TestPlayer player = allocate(TestPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = allocate(StubContainer.class);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        return player;
    }

    private static StubNextGenPlayer stubNextGenPlayer(String name) {
        StubNextGenPlayer fakePlayer = allocate(StubNextGenPlayer.class);
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

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubFakePlayer extends FakePlayer {

        private String name;

        private StubFakePlayer() {
            super(null, null, "stub");
        }

        @Override
        public String getCommandSenderName() {
            return this.name;
        }
    }

    private static final class StubNextGenPlayer extends GTstaffForgePlayer {

        private String name;

        private StubNextGenPlayer() {
            super(null, null, null);
        }

        @Override
        public String getCommandSenderName() {
            return this.name;
        }
    }

    private static class TestPlayer extends EntityPlayerMP {

        private TestPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void sendContainerToPlayer(Container container) {}
    }

    private static final class StubContainer extends Container {

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }

    private static class TestInventory implements IInventory {

        private final ItemStack[] stacks;

        private TestInventory(int size) {
            this.stacks = new ItemStack[size];
        }

        @Override
        public int getSizeInventory() {
            return this.stacks.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.stacks[slot];
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = this.stacks[slot];
            this.stacks[slot] = null;
            return stack;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return decrStackSize(slot, 64);
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            this.stacks[slot] = stack;
        }

        @Override
        public String getInventoryName() {
            return "test";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return stack != null;
        }
    }

    private static final class RejectingInventory extends TestInventory {

        private RejectingInventory(int size) {
            super(size);
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return false;
        }
    }
}
