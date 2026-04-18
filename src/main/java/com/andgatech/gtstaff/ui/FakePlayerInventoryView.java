package com.andgatech.gtstaff.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public final class FakePlayerInventoryView implements IInventory {

    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final int MAIN_SLOT_COUNT = 27;
    public static final int SLOT_COUNT = ARMOR_SLOT_COUNT + HOTBAR_SLOT_COUNT + MAIN_SLOT_COUNT;

    private final FakePlayer fakePlayer;
    private final ItemStack[] clientSlots;
    private final String inventoryName;
    private int clientSelectedHotbarSlot;

    private FakePlayerInventoryView(FakePlayer fakePlayer, String inventoryName) {
        this.fakePlayer = fakePlayer;
        this.clientSlots = fakePlayer == null ? new ItemStack[SLOT_COUNT] : null;
        this.inventoryName = inventoryName == null || inventoryName.trim()
            .isEmpty() ? "Fake Player" : inventoryName.trim();
    }

    public static FakePlayerInventoryView server(FakePlayer fakePlayer) {
        if (fakePlayer == null) {
            throw new IllegalArgumentException("fakePlayer");
        }
        return new FakePlayerInventoryView(fakePlayer, fakePlayer.getCommandSenderName());
    }

    public static FakePlayerInventoryView client(String inventoryName) {
        return new FakePlayerInventoryView(null, inventoryName);
    }

    public int getSelectedHotbarSlot() {
        if (this.fakePlayer != null && this.fakePlayer.inventory != null) {
            return clampHotbarSlot(this.fakePlayer.inventory.currentItem);
        }
        return clampHotbarSlot(this.clientSelectedHotbarSlot);
    }

    public void setSelectedHotbarSlot(int slot) {
        int clampedSlot = clampHotbarSlot(slot);
        if (this.fakePlayer != null && this.fakePlayer.inventory != null) {
            this.fakePlayer.inventory.currentItem = clampedSlot;
            return;
        }
        this.clientSelectedHotbarSlot = clampedSlot;
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isValidSlot(slot)) {
            return null;
        }
        if (this.fakePlayer != null) {
            return getServerStack(slot);
        }
        return this.clientSlots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null || amount <= 0) {
            return null;
        }

        if (stack.stackSize <= amount) {
            ItemStack removed = stack;
            setInventorySlotContents(slot, null);
            return removed;
        }

        ItemStack removed = stack.splitStack(amount);
        if (stack.stackSize <= 0) {
            setInventorySlotContents(slot, null);
        } else if (this.fakePlayer == null) {
            this.clientSlots[slot] = stack;
        }
        markDirty();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = getStackInSlot(slot);
        if (stack != null) {
            setInventorySlotContents(slot, null);
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }

        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }

        if (this.fakePlayer != null) {
            setServerStack(slot, stack);
            markDirty();
            return;
        }

        this.clientSlots[slot] = stack;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return this.inventoryName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
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
        return isValidSlot(slot);
    }

    private ItemStack getServerStack(int slot) {
        InventoryPlayer inventory = this.fakePlayer.inventory;
        if (inventory == null) {
            return null;
        }
        if (slot < ARMOR_SLOT_COUNT) {
            return inventory.armorInventory[3 - slot];
        }
        if (slot < ARMOR_SLOT_COUNT + HOTBAR_SLOT_COUNT) {
            return inventory.mainInventory[slot - ARMOR_SLOT_COUNT];
        }
        return inventory.mainInventory[slot - (ARMOR_SLOT_COUNT + HOTBAR_SLOT_COUNT) + 9];
    }

    private void setServerStack(int slot, ItemStack stack) {
        InventoryPlayer inventory = this.fakePlayer.inventory;
        if (inventory == null) {
            return;
        }
        if (slot < ARMOR_SLOT_COUNT) {
            inventory.armorInventory[3 - slot] = stack;
            return;
        }
        if (slot < ARMOR_SLOT_COUNT + HOTBAR_SLOT_COUNT) {
            inventory.mainInventory[slot - ARMOR_SLOT_COUNT] = stack;
            return;
        }
        inventory.mainInventory[slot - (ARMOR_SLOT_COUNT + HOTBAR_SLOT_COUNT) + 9] = stack;
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        return Math.min(HOTBAR_SLOT_COUNT - 1, slot);
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }
}
