package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

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
}
