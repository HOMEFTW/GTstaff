package com.andgatech.gtstaff.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public class FakePlayerInventoryContainer extends Container {

    private static final int FAKE_SLOT_COUNT = FakePlayerInventoryView.SLOT_COUNT;
    private static final int PLAYER_MAIN_SLOT_COUNT = 27;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_SLOT_COUNT = PLAYER_MAIN_SLOT_COUNT + PLAYER_HOTBAR_SLOT_COUNT;
    private static final int PLAYER_SLOT_START = FAKE_SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;
    private static final int HOTBAR_SLOT_START = FakePlayerInventoryView.ARMOR_SLOT_COUNT;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + FakePlayerInventoryView.HOTBAR_SLOT_COUNT;
    private static final int MAIN_SLOT_START = HOTBAR_SLOT_END;
    private static final int MAIN_SLOT_END = FAKE_SLOT_COUNT;

    private final EntityPlayer player;
    private final FakePlayer fakePlayer;
    private final FakePlayerInventoryView fakeInventory;
    private int selectedHotbarSlot;

    private FakePlayerInventoryContainer(EntityPlayer player, FakePlayer fakePlayer,
        FakePlayerInventoryView fakeInventory) {
        this.player = player;
        this.fakePlayer = fakePlayer;
        this.fakeInventory = fakeInventory;
        this.selectedHotbarSlot = fakeInventory.getSelectedHotbarSlot();
        addFakeInventorySlots();
        addPlayerInventorySlots(player);
    }

    public static FakePlayerInventoryContainer server(EntityPlayerMP player, FakePlayer fakePlayer) {
        return new FakePlayerInventoryContainer(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer));
    }

    public static FakePlayerInventoryContainer client(EntityPlayer player, FakePlayerInventoryView fakeInventory) {
        return new FakePlayerInventoryContainer(player, null, fakeInventory);
    }

    public boolean isFakeInventorySlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < FAKE_SLOT_COUNT;
    }

    public int getSelectedHotbarSlot() {
        return this.selectedHotbarSlot;
    }

    public FakePlayerInventoryView getFakeInventory() {
        return this.fakeInventory;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafting) {
        super.addCraftingToCrafters(crafting);
        crafting.sendProgressBarUpdate(this, 0, this.selectedHotbarSlot);
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int modifier, EntityPlayer player) {
        ItemStack result = super.slotClick(slotId, button, modifier, player);
        if (slotId >= HOTBAR_SLOT_START && slotId < HOTBAR_SLOT_END) {
            setSelectedHotbarSlot(slotId - HOTBAR_SLOT_START);
        }
        if (this.fakePlayer != null && isFakeInventorySlot(slotId)) {
            this.fakePlayer.syncEquipmentToWatchers();
        }
        return result;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = slotIndex >= 0 && slotIndex < this.inventorySlots.size() ? (Slot) this.inventorySlots.get(slotIndex)
            : null;
        if (slot == null || !slot.getHasStack()) {
            return null;
        }

        ItemStack stackInSlot = slot.getStack();
        ItemStack originalStack = stackInSlot.copy();

        if (isFakeInventorySlot(slotIndex)) {
            if (!mergeItemStack(stackInSlot, PLAYER_SLOT_START, PLAYER_SLOT_END, false)) {
                return null;
            }
        } else {
            if (!mergeIntoFakeInventory(stackInSlot)) {
                return null;
            }
        }

        if (stackInSlot.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }

        return originalStack;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (this.fakePlayer != null) {
            this.fakePlayer.syncEquipmentToWatchers();
        }
        int currentSelectedSlot = this.fakeInventory.getSelectedHotbarSlot();
        if (this.selectedHotbarSlot == currentSelectedSlot) {
            return;
        }
        this.selectedHotbarSlot = currentSelectedSlot;
        for (Object crafter : this.crafters) {
            ((ICrafting) crafter).sendProgressBarUpdate(this, 0, this.selectedHotbarSlot);
        }
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            setSelectedHotbarSlot(value);
            return;
        }
        super.updateProgressBar(id, value);
    }

    private void addFakeInventorySlots() {
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 0, 8, 18, 0));
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 1, 26, 18, 1));
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 2, 44, 18, 2));
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 3, 62, 18, 3));

        for (int hotbarSlot = 0; hotbarSlot < FakePlayerInventoryView.HOTBAR_SLOT_COUNT; hotbarSlot++) {
            addSlotToContainer(new Slot(this.fakeInventory, HOTBAR_SLOT_START + hotbarSlot, 8 + hotbarSlot * 18, 36));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = MAIN_SLOT_START + row * 9 + column;
                addSlotToContainer(new Slot(this.fakeInventory, slotIndex, 8 + column * 18, 54 + row * 18));
            }
        }
    }

    private void addPlayerInventorySlots(EntityPlayer player) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventoryIndex = column + (row + 1) * 9;
                addSlotToContainer(new Slot(player.inventory, inventoryIndex, 8 + column * 18, 125 + row * 18));
            }
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            addSlotToContainer(new Slot(player.inventory, hotbarSlot, 8 + hotbarSlot * 18, 183));
        }
    }

    private boolean mergeIntoFakeInventory(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemArmor armor) {
            int armorSlotIndex = armorContainerSlotForType(armor.armorType);
            if (armorSlotIndex >= 0 && mergeItemStack(stack, armorSlotIndex, armorSlotIndex + 1, false)) {
                return true;
            }
        }
        return mergeItemStack(stack, HOTBAR_SLOT_START, MAIN_SLOT_END, false);
    }

    private void setSelectedHotbarSlot(int slot) {
        this.selectedHotbarSlot = clampHotbarSlot(slot);
        this.fakeInventory.setSelectedHotbarSlot(this.selectedHotbarSlot);
        if (this.fakePlayer != null && this.fakePlayer.inventory != null) {
            this.fakePlayer.inventory.currentItem = this.selectedHotbarSlot;
        }
    }

    private static int armorContainerSlotForType(int armorType) {
        switch (armorType) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            default:
                return -1;
        }
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        return Math.min(FakePlayerInventoryView.HOTBAR_SLOT_COUNT - 1, slot);
    }
}
