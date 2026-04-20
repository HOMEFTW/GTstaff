package com.andgatech.gtstaff.ui;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.integration.BackhandCompat;

public final class FakePlayerOffhandSlot extends Slot {

    public FakePlayerOffhandSlot(IInventory inventory, int slotIndex, int xDisplayPosition, int yDisplayPosition) {
        super(inventory, slotIndex, xDisplayPosition, yDisplayPosition);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return BackhandCompat.isOffhandItemValid(stack);
    }
}
