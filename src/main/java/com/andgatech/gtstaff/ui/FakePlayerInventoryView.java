package com.andgatech.gtstaff.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.integration.FakePlayerInventoryCompat;

public final class FakePlayerInventoryView implements IInventory {

    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final int MAIN_SLOT_COUNT = 27;
    public static final int BASE_SLOT_COUNT = ARMOR_SLOT_COUNT + HOTBAR_SLOT_COUNT + MAIN_SLOT_COUNT;
    public static final int SLOT_COUNT = BASE_SLOT_COUNT;

    private final EntityPlayer fakePlayer;
    private final ItemStack[] clientSlots;
    private final List<FakePlayerInventoryExtraSlot> extraSlots;
    private final String inventoryName;
    private int clientSelectedHotbarSlot;

    private FakePlayerInventoryView(EntityPlayer fakePlayer, String inventoryName,
        List<FakePlayerInventoryExtraSlot> extraSlots) {
        this.fakePlayer = fakePlayer;
        this.extraSlots = Collections.unmodifiableList(new ArrayList<>(extraSlots));
        this.clientSlots = fakePlayer == null ? new ItemStack[BASE_SLOT_COUNT] : null;
        this.inventoryName = inventoryName == null || inventoryName.trim()
            .isEmpty() ? "Fake Player" : inventoryName.trim();
    }

    public static FakePlayerInventoryView server(EntityPlayer fakePlayer) {
        if (fakePlayer == null) {
            throw new IllegalArgumentException("fakePlayer");
        }
        return new FakePlayerInventoryView(
            fakePlayer,
            fakePlayer.getCommandSenderName(),
            FakePlayerInventoryCompat.serverSlots(fakePlayer));
    }

    static FakePlayerInventoryView server(EntityPlayer fakePlayer, List<FakePlayerInventoryExtraSlot> extraSlots) {
        if (fakePlayer == null) {
            throw new IllegalArgumentException("fakePlayer");
        }
        return new FakePlayerInventoryView(fakePlayer, fakePlayer.getCommandSenderName(), safeExtraSlots(extraSlots));
    }

    public static FakePlayerInventoryView client(String inventoryName) {
        return client(inventoryName, (EntityPlayer) null);
    }

    public static FakePlayerInventoryView client(String inventoryName, EntityPlayer fakePlayer) {
        return new FakePlayerInventoryView(null, inventoryName, FakePlayerInventoryCompat.clientSlots(fakePlayer));
    }

    static FakePlayerInventoryView client(String inventoryName, List<FakePlayerInventoryExtraSlot> extraSlots) {
        return new FakePlayerInventoryView(null, inventoryName, safeExtraSlots(extraSlots));
    }

    public boolean isExtraSlotOfKind(int slot, FakePlayerInventoryExtraSlot.Kind kind) {
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        return extraSlot != null && extraSlot.kind() == kind;
    }

    public String getExtraSlotBaublesType(int slot) {
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        return extraSlot == null ? "" : extraSlot.baublesSlotType();
    }

    public int getExtraSlotCount() {
        return this.extraSlots.size();
    }

    public int getSlotStackLimit(int slot) {
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        return extraSlot == null ? getInventoryStackLimit() : extraSlot.getStackLimit();
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
        return BASE_SLOT_COUNT + this.extraSlots.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isValidSlot(slot)) {
            return null;
        }
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        if (extraSlot != null) {
            return extraSlot.getStack();
        }
        if (this.fakePlayer != null) {
            return getServerStack(slot);
        }
        return this.clientSlots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        if (extraSlot != null) {
            ItemStack removed = extraSlot.decrStackSize(amount);
            if (removed != null) {
                extraSlot.markDirty();
                markDirty();
            }
            return removed;
        }

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
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        if (extraSlot != null) {
            return extraSlot.getStackOnClosing();
        }
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

        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        if (extraSlot != null) {
            if (stack != null && !extraSlot.isItemValid(stack)) {
                return;
            }
            extraSlot.setStack(stack);
            markDirty();
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
        for (FakePlayerInventoryExtraSlot extraSlot : this.extraSlots) {
            if (!extraSlot.isUseableByPlayer(player)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        FakePlayerInventoryExtraSlot extraSlot = getExtraSlot(slot);
        if (extraSlot != null) {
            return extraSlot.isItemValid(stack);
        }
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

    private FakePlayerInventoryExtraSlot getExtraSlot(int slot) {
        if (slot < BASE_SLOT_COUNT || slot >= getSizeInventory()) {
            return null;
        }
        return this.extraSlots.get(slot - BASE_SLOT_COUNT);
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        return Math.min(HOTBAR_SLOT_COUNT - 1, slot);
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < getSizeInventory();
    }

    private static List<FakePlayerInventoryExtraSlot> safeExtraSlots(List<FakePlayerInventoryExtraSlot> extraSlots) {
        return extraSlots == null ? Collections.emptyList() : extraSlots;
    }
}
