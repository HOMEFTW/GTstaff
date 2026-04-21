package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

class FakePlayerInventoryViewTest {

    @Test
    void mapsArmorHotbarAndMainInventoryIntoFixedContainerOrder() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.armorInventory[3] = namedStack("Helmet", 1);
        fakePlayer.inventory.mainInventory[0] = namedStack("Sword", 1);
        fakePlayer.inventory.mainInventory[9] = namedStack("Pickaxe", 1);
        fakePlayer.inventory.currentItem = 4;

        FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

        assertEquals(
            "Helmet",
            view.getStackInSlot(0)
                .getDisplayName());
        assertEquals(
            "Sword",
            view.getStackInSlot(4)
                .getDisplayName());
        assertEquals(
            "Pickaxe",
            view.getStackInSlot(13)
                .getDisplayName());
        assertEquals(4, view.getSelectedHotbarSlot());
    }

    @Test
    void setInventorySlotContentsWritesBackIntoFakePlayerInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

        view.setInventorySlotContents(1, namedStack("Chestplate", 1));
        view.setInventorySlotContents(8, namedStack("Torch", 16));
        view.setInventorySlotContents(20, namedStack("Wrench", 1));

        assertEquals("Chestplate", fakePlayer.inventory.armorInventory[2].getDisplayName());
        assertEquals("Torch", fakePlayer.inventory.mainInventory[4].getDisplayName());
        assertEquals("Wrench", fakePlayer.inventory.mainInventory[16].getDisplayName());
    }

    @Test
    void removeStackFromMappedSlotClearsUnderlyingInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.mainInventory[0] = namedStack("Sword", 1);
        FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

        ItemStack removed = view.decrStackSize(4, 1);

        assertNotNull(removed);
        assertEquals("Sword", removed.getDisplayName());
        assertNull(fakePlayer.inventory.mainInventory[0]);
    }

    @Test
    void appendsExtraInventorySlotsAfterVanillaPlayerInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestInventory baubles = new TestInventory(2);
        baubles.setInventorySlotContents(0, namedStack("Ring", 1));
        FakePlayerInventoryView view = FakePlayerInventoryView.server(
            fakePlayer,
            Collections.singletonList(FakePlayerInventoryExtraSlot.fromInventory(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                baubles,
                0)));

        assertEquals(FakePlayerInventoryView.BASE_SLOT_COUNT + 1, view.getSizeInventory());
        assertEquals(
            "Ring",
            view.getStackInSlot(FakePlayerInventoryView.BASE_SLOT_COUNT)
                .getDisplayName());

        view.setInventorySlotContents(FakePlayerInventoryView.BASE_SLOT_COUNT, namedStack("Amulet", 1));

        assertEquals("Amulet", baubles.getStackInSlot(0).getDisplayName());
        assertTrue(view.isExtraSlotOfKind(FakePlayerInventoryView.BASE_SLOT_COUNT, FakePlayerInventoryExtraSlot.Kind.BAUBLES));
    }

    @Test
    void clientBaublesExtraSlotRejectsPlainItem() {
        FakePlayerInventoryView view = FakePlayerInventoryView.client(
            "UiBot",
            Collections.singletonList(FakePlayerInventoryExtraSlot.client(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                1,
                "ring")));

        assertFalse(view.isItemValidForSlot(FakePlayerInventoryView.BASE_SLOT_COUNT, namedStack("Stone", 1)));
    }

    @Test
    void serverExtraSlotWriteThroughRespectsUnderlyingValidation() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        RejectingInventory baubles = new RejectingInventory();
        FakePlayerInventoryView view = FakePlayerInventoryView.server(
            fakePlayer,
            Collections.singletonList(FakePlayerInventoryExtraSlot.fromInventory(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                baubles,
                0,
                "ring")));

        view.setInventorySlotContents(FakePlayerInventoryView.BASE_SLOT_COUNT, namedStack("Stone", 1));

        assertNull(baubles.getStackInSlot(0));
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
            return 1;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
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

        private RejectingInventory() {
            super(1);
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return false;
        }
    }
}
