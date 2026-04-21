package com.andgatech.gtstaff.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.integration.FakePlayerInventoryCompat;

public final class FakePlayerInventoryExtraSlot {

    public enum Kind {
        BAUBLES,
        OFFHAND
    }

    private final Kind kind;
    private final String label;
    private final IInventory inventory;
    private final int inventorySlot;
    private final int clientStackLimit;
    private final String baublesSlotType;
    private ItemStack clientStack;

    private FakePlayerInventoryExtraSlot(Kind kind, String label, IInventory inventory, int inventorySlot,
        int clientStackLimit, String baublesSlotType) {
        this.kind = kind;
        this.label = label == null ? "" : label;
        this.inventory = inventory;
        this.inventorySlot = inventorySlot;
        this.clientStackLimit = clientStackLimit <= 0 ? 64 : clientStackLimit;
        this.baublesSlotType = baublesSlotType == null ? "" : baublesSlotType;
    }

    public static FakePlayerInventoryExtraSlot fromInventory(Kind kind, String label, IInventory inventory,
        int inventorySlot) {
        return fromInventory(kind, label, inventory, inventorySlot, "");
    }

    public static FakePlayerInventoryExtraSlot fromInventory(Kind kind, String label, IInventory inventory,
        int inventorySlot, String baublesSlotType) {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        if (inventory == null) {
            throw new IllegalArgumentException("inventory");
        }
        if (inventorySlot < 0 || inventorySlot >= inventory.getSizeInventory()) {
            throw new IllegalArgumentException("inventorySlot");
        }
        return new FakePlayerInventoryExtraSlot(
            kind,
            label,
            inventory,
            inventorySlot,
            inventory.getInventoryStackLimit(),
            baublesSlotType);
    }

    public static FakePlayerInventoryExtraSlot client(Kind kind, String label, int stackLimit) {
        return client(kind, label, stackLimit, "");
    }

    public static FakePlayerInventoryExtraSlot client(Kind kind, String label, int stackLimit, String baublesSlotType) {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        return new FakePlayerInventoryExtraSlot(kind, label, null, -1, stackLimit, baublesSlotType);
    }

    public Kind kind() {
        return this.kind;
    }

    public String label() {
        return this.label;
    }

    public String baublesSlotType() {
        return this.baublesSlotType;
    }

    public ItemStack getStack() {
        if (this.inventory != null) {
            return this.inventory.getStackInSlot(this.inventorySlot);
        }
        return this.clientStack;
    }

    public ItemStack decrStackSize(int amount) {
        if (amount <= 0) {
            return null;
        }
        if (this.inventory != null) {
            return this.inventory.decrStackSize(this.inventorySlot, amount);
        }

        ItemStack stack = this.clientStack;
        if (stack == null) {
            return null;
        }
        if (stack.stackSize <= amount) {
            this.clientStack = null;
            return stack;
        }
        ItemStack removed = stack.splitStack(amount);
        if (stack.stackSize <= 0) {
            this.clientStack = null;
        }
        return removed;
    }

    public ItemStack getStackOnClosing() {
        if (this.inventory != null) {
            return this.inventory.getStackInSlotOnClosing(this.inventorySlot);
        }
        ItemStack stack = this.clientStack;
        this.clientStack = null;
        return stack;
    }

    public void setStack(ItemStack stack) {
        int stackLimit = getStackLimit();
        if (stack != null && stack.stackSize > stackLimit) {
            stack.stackSize = stackLimit;
        }
        if (this.inventory != null) {
            if (stack != null && !this.inventory.isItemValidForSlot(this.inventorySlot, stack)) {
                return;
            }
            this.inventory.setInventorySlotContents(this.inventorySlot, stack);
            return;
        }
        this.clientStack = stack;
    }

    public int getStackLimit() {
        if (this.inventory != null) {
            return this.inventory.getInventoryStackLimit();
        }
        return this.clientStackLimit;
    }

    public void markDirty() {
        if (this.inventory != null) {
            this.inventory.markDirty();
        }
    }

    public boolean isItemValid(ItemStack stack) {
        if (this.inventory == null) {
            return FakePlayerInventoryCompat.isClientExtraSlotItemValid(this, stack);
        }
        return this.inventory.isItemValidForSlot(this.inventorySlot, stack);
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.inventory == null || this.inventory.isUseableByPlayer(player);
    }
}
