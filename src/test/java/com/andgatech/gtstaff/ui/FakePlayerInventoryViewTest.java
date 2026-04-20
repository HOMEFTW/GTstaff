package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.integration.BackhandCompat;

class FakePlayerInventoryViewTest {

    @Test
    void mapsArmorOffhandHotbarAndMainInventoryIntoFixedContainerOrder() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.armorInventory[3] = namedStack("Helmet", 1);
        BackhandCompat.setOffhandItem(fakePlayer, namedStack("Torch", 16));
        fakePlayer.inventory.mainInventory[0] = namedStack("Sword", 1);
        fakePlayer.inventory.mainInventory[9] = namedStack("Pickaxe", 1);
        fakePlayer.inventory.currentItem = 4;

        FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

        assertEquals(
            "Helmet",
            view.getStackInSlot(0)
                .getDisplayName());
        assertEquals(
            "Torch",
            view.getStackInSlot(4)
                .getDisplayName());
        assertEquals(
            "Sword",
            view.getStackInSlot(5)
                .getDisplayName());
        assertEquals(
            "Pickaxe",
            view.getStackInSlot(14)
                .getDisplayName());
        assertEquals(4, view.getSelectedHotbarSlot());
    }

    @Test
    void setInventorySlotContentsWritesBackIntoFakePlayerInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

        view.setInventorySlotContents(1, namedStack("Chestplate", 1));
        view.setInventorySlotContents(4, namedStack("Shield", 1));
        view.setInventorySlotContents(9, namedStack("Torch", 16));
        view.setInventorySlotContents(21, namedStack("Wrench", 1));

        assertEquals("Chestplate", fakePlayer.inventory.armorInventory[2].getDisplayName());
        assertEquals("Shield", BackhandCompat.getOffhandItem(fakePlayer).getDisplayName());
        assertEquals("Torch", fakePlayer.inventory.mainInventory[4].getDisplayName());
        assertEquals("Wrench", fakePlayer.inventory.mainInventory[16].getDisplayName());
    }

    @Test
    void removeStackFromMappedSlotClearsUnderlyingInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.mainInventory[0] = namedStack("Sword", 1);
        FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

        ItemStack removed = view.decrStackSize(5, 1);

        assertNotNull(removed);
        assertEquals("Sword", removed.getDisplayName());
        assertNull(fakePlayer.inventory.mainInventory[0]);
    }

    private static StubFakePlayer stubFakePlayer(String name) {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = name;
        fakePlayer.inventory = new TestInventoryPlayer(fakePlayer);
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

    private static final class TestInventoryPlayer extends InventoryPlayer {

        private ItemStack offhandItem;

        private TestInventoryPlayer(StubFakePlayer player) {
            super(player);
        }

        public ItemStack backhand$getOffhandItem() {
            return this.offhandItem;
        }

        public void backhand$setOffhandItem(ItemStack stack) {
            this.offhandItem = stack;
        }
    }
}
