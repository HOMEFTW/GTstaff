package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.ui.FakePlayerInventoryGuiIds;

final class LegacyInventoryRuntime implements BotInventoryRuntime {

    private final FakePlayer fakePlayer;

    LegacyInventoryRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public int selectedHotbarSlot() {
        return fakePlayer.inventory == null ? 0 : MathHelper.clamp_int(fakePlayer.inventory.currentItem, 0, 8);
    }

    @Override
    public BotInventorySummary summary() {
        InventoryPlayer inventory = fakePlayer.inventory;
        List<String> hotbarLines = new ArrayList<String>();
        List<String> mainInventoryLines = new ArrayList<String>();
        List<String> armorLines = new ArrayList<String>();
        int selectedHotbarSlot = selectedHotbarSlot();

        if (inventory != null) {
            for (int index = 0; index < 9; index++) {
                hotbarLines.add(formatInventorySlot(index, inventory.mainInventory[index], index == selectedHotbarSlot));
            }

            for (int index = 9; index < inventory.mainInventory.length; index++) {
                mainInventoryLines.add(formatInventorySlot(index, inventory.mainInventory[index], false));
            }

            armorLines
                .add(formatArmorSlot("Helmet", inventory.armorInventory.length > 3 ? inventory.armorInventory[3] : null));
            armorLines.add(
                formatArmorSlot("Chestplate", inventory.armorInventory.length > 2 ? inventory.armorInventory[2] : null));
            armorLines
                .add(formatArmorSlot("Leggings", inventory.armorInventory.length > 1 ? inventory.armorInventory[1] : null));
            armorLines.add(formatArmorSlot("Boots", inventory.armorInventory.length > 0 ? inventory.armorInventory[0] : null));
        }

        return new BotInventorySummary(
            fakePlayer.getCommandSenderName(),
            selectedHotbarSlot,
            hotbarLines,
            mainInventoryLines,
            armorLines);
    }

    @Override
    public String openInventoryManager(EntityPlayerMP player) throws CommandException {
        if (player == null) {
            throw new CommandException("Inventory manager can only be opened by a player");
        }

        player.openGui(
            GTstaff.instance,
            FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY,
            player.worldObj,
            fakePlayer.getEntityId(),
            0,
            0);
        return "Opening inventory manager for " + fakePlayer.getCommandSenderName() + ".";
    }

    private String formatInventorySlot(int slotIndex, ItemStack stack, boolean selected) {
        String marker = selected ? "[*]" : "[ ]";
        return marker + " " + (slotIndex + 1) + ": " + formatStack(stack);
    }

    private String formatArmorSlot(String label, ItemStack stack) {
        return label + ": " + formatStack(stack);
    }

    private String formatStack(ItemStack stack) {
        if (stack == null) {
            return "(empty)";
        }
        return stack.getDisplayName() + " x" + stack.stackSize;
    }
}
