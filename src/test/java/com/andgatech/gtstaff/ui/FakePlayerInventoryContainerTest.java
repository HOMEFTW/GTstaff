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
import com.andgatech.gtstaff.integration.BackhandCompat;

import baubles.api.BaubleType;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import baubles.common.container.InventoryBaubles;
import baubles.common.BaublesConfig;

class FakePlayerInventoryContainerTest {

    @Test
    void serverContainerAddsFakeSlotsBeforePlayerSlots() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        InventoryBaubles baubles = new InventoryBaubles(fakePlayer);

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);
        int visibleBaublesSlotCount = countVisibleBaublesSlots(baubles);

        assertEquals(77 + visibleBaublesSlotCount, container.inventorySlots.size());
        assertTrue(container.isFakeInventorySlot(0));
        assertTrue(container.isFakeInventorySlot(40));
        assertFalse(container.isFakeInventorySlot(41));
        assertEquals(41 + visibleBaublesSlotCount, container.getPlayerInventoryStartIndex());
    }

    @Test
    void serverContainerAddsOffhandSlotBetweenArmorAndHotbar() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        assertTrue(container.isFakeInventorySlot(3));
        assertTrue(container.isOffhandSlot(4));
        assertFalse(container.isOffhandSlot(5));
    }

    @Test
    void clickingFakeHotbarSlotUpdatesSelectedMainHandSlot() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.mainInventory[3] = namedStack("Sword", 1);
        TestPlayer player = stubPlayer();
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        container.slotClick(8, 0, 0, player);

        assertEquals(3, fakePlayer.inventory.currentItem);
        assertEquals(3, container.getSelectedHotbarSlot());
    }

    @Test
    void transferStackMovesItemsFromPlayerInventoryIntoFakeInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        BackhandCompat.setOffhandItem(fakePlayer, namedStack("Lantern", 1));
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = namedStack("Cobblestone", 64);
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        ItemStack moved = container.transferStackInSlot(player, container.getPlayerInventoryStartIndex());

        assertNotNull(moved);
        assertTrue(hasStackNamed(fakePlayer.inventory, "Cobblestone"));
    }

    @Test
    void transferStackMovesItemsFromFakeInventoryIntoPlayerInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        fakePlayer.inventory.mainInventory[9] = namedStack("Iron Pickaxe", 1);
        TestPlayer player = stubPlayer();
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        ItemStack moved = container.transferStackInSlot(player, 14);

        assertNotNull(moved);
        assertTrue(hasStackNamed(player.inventory, "Iron Pickaxe"));
    }

    @Test
    void serverContainerAddsBaublesSlotsBetweenFakeAndPlayerInventories() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        InventoryBaubles baubles = new InventoryBaubles(fakePlayer);

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer
            .forTest(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer), baubles);

        int visibleBaublesSlotCount = countVisibleBaublesSlots(baubles);

        assertEquals(77 + visibleBaublesSlotCount, container.inventorySlots.size());
        assertTrue(container.isFakeInventorySlot(40));
        assertTrue(container.isBaublesSlot(41));
        assertFalse(container.isBaublesSlot(41 + visibleBaublesSlotCount));
    }

    @Test
    void clientContainerStillAddsConfiguredBaublesSlotsWithoutFakePlayerEntity() {
        TestPlayer player = stubPlayer();
        InventoryBaubles baubles = new InventoryBaubles(player);

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer
            .client(player, FakePlayerInventoryView.client("UiBot"));

        int visibleBaublesSlotCount = countVisibleBaublesSlots(baubles);

        assertEquals(77 + visibleBaublesSlotCount, container.inventorySlots.size());
        assertTrue(container.isBaublesSlot(41));
        assertEquals(41 + visibleBaublesSlotCount, container.getPlayerInventoryStartIndex());
        assertTrue(container.isOffhandSlot(4));
    }

    @Test
    void transferStackPrefersCompatibleBaublesSlotBeforeFakeMainInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = universalBaubleStack("Ring");
        InventoryBaubles baubles = new InventoryBaubles(fakePlayer);

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer
            .forTest(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer), baubles);

        ItemStack moved = container.transferStackInSlot(player, container.getPlayerInventoryStartIndex());

        assertNotNull(moved);
        assertNotNull(baubles.getStackInSlot(0));
        assertEquals("Ring", baubles.getStackInSlot(0).getDisplayName());
        assertFalse(hasStackNamed(fakePlayer.inventory, "Ring"));
    }

    @Test
    void transferStackPrefersCompatibleOffhandSlotBeforeBaublesAndFakeMainInventory() {
        StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
        TestPlayer player = stubPlayer();
        player.inventory.mainInventory[9] = namedStack("Torch", 16);

        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

        ItemStack moved = container.transferStackInSlot(player, container.getPlayerInventoryStartIndex());

        assertNotNull(moved);
        assertNotNull(container.getFakeInventory().getStackInSlot(FakePlayerInventoryView.OFFHAND_SLOT_INDEX));
        assertEquals(
            "Torch",
            container.getFakeInventory()
                .getStackInSlot(FakePlayerInventoryView.OFFHAND_SLOT_INDEX)
                .getDisplayName());
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
        fakePlayer.inventory = new TestInventoryPlayer(fakePlayer);
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

    private static ItemStack universalBaubleStack(String displayName) {
        ItemStack stack = new ItemStack(new UniversalBaubleItem(), 1);
        stack.setStackDisplayName(displayName);
        return stack;
    }

    private static int countVisibleBaublesSlots(InventoryBaubles baubles) {
        int visible = 0;
        for (int slotIndex = 0; slotIndex < baubles.getSizeInventory(); slotIndex++) {
            String slotType = BaubleExpandedSlots.getSlotType(slotIndex);
            if (BaublesConfig.showUnusedSlots || !BaubleExpandedSlots.unknownType.equals(slotType)) {
                visible++;
            }
        }
        return visible;
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

    private static final class UniversalBaubleItem extends Item implements IBaubleExpanded {

        @Override
        public BaubleType getBaubleType(ItemStack itemstack) {
            return BaubleType.RING;
        }

        @Override
        public void onWornTick(ItemStack itemstack, net.minecraft.entity.EntityLivingBase player) {}

        @Override
        public void onEquipped(ItemStack itemstack, net.minecraft.entity.EntityLivingBase player) {}

        @Override
        public void onUnequipped(ItemStack itemstack, net.minecraft.entity.EntityLivingBase player) {}

        @Override
        public boolean canEquip(ItemStack itemstack, net.minecraft.entity.EntityLivingBase player) {
            return true;
        }

        @Override
        public boolean canUnequip(ItemStack itemstack, net.minecraft.entity.EntityLivingBase player) {
            return true;
        }

        @Override
        public String[] getBaubleTypes(ItemStack itemstack) {
            return new String[] { BaubleExpandedSlots.universalType };
        }
    }
}
