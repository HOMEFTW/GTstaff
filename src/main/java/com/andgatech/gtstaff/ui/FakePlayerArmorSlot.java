package com.andgatech.gtstaff.ui;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

public class FakePlayerArmorSlot extends Slot {

    private final int armorType;

    public FakePlayerArmorSlot(IInventory inventory, int slotIndex, int x, int y, int armorType) {
        super(inventory, slotIndex, x, y);
        this.armorType = armorType;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmor armor && armor.armorType == this.armorType;
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }
}
