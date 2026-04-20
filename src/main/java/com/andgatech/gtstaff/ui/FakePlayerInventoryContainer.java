package com.andgatech.gtstaff.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

import baubles.api.IBauble;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.common.BaublesConfig;
import baubles.common.container.InventoryBaubles;
import baubles.common.container.SlotBauble;
import baubles.common.lib.PlayerHandler;

public class FakePlayerInventoryContainer extends Container {

    private static final int FAKE_SLOT_COUNT = FakePlayerInventoryView.SLOT_COUNT;
    private static final int PLAYER_MAIN_SLOT_COUNT = 27;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_SLOT_COUNT = PLAYER_MAIN_SLOT_COUNT + PLAYER_HOTBAR_SLOT_COUNT;
    private static final int HOTBAR_SLOT_START = FakePlayerInventoryView.HOTBAR_SLOT_START;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + FakePlayerInventoryView.HOTBAR_SLOT_COUNT;
    private static final int MAIN_SLOT_START = FakePlayerInventoryView.MAIN_SLOT_START;
    private static final int MAIN_SLOT_END = FAKE_SLOT_COUNT;
    private static final int OFFHAND_SLOT_X = 80;
    private static final int OFFHAND_SLOT_Y = 18;
    private static final int BAUBLES_SLOT_X = 184;
    private static final int BAUBLES_SLOT_Y = 18;
    private static final int BAUBLES_SLOT_SPACING = 18;
    private static final int BAUBLES_VISIBLE_ROWS = 8;
    private static final int BAUBLES_MAX_COLUMNS = 4;
    private static final int BAUBLES_HIDDEN_Y = -2000;

    private final EntityPlayer player;
    private final FakePlayer fakePlayer;
    private final FakePlayerInventoryView fakeInventory;
    private final InventoryBaubles baublesInventory;
    private final int baublesVisibleSlotCount;
    private final int baublesColumns;
    private final int baublesSlotStart;
    private final int baublesSlotEnd;
    private final int playerSlotStart;
    private final int playerSlotEnd;
    private int selectedHotbarSlot;

    private FakePlayerInventoryContainer(EntityPlayer player, FakePlayer fakePlayer,
        FakePlayerInventoryView fakeInventory, InventoryBaubles baublesInventory) {
        this.player = player;
        this.fakePlayer = fakePlayer;
        this.fakeInventory = fakeInventory;
        this.baublesInventory = baublesInventory;
        this.baublesVisibleSlotCount = resolveVisibleBaublesSlotCount(baublesInventory);
        this.baublesColumns = this.baublesVisibleSlotCount <= 0 ? 1
            : FakePlayerBaublesSlotLayout.resolveColumns(this.baublesVisibleSlotCount, BAUBLES_MAX_COLUMNS);
        this.selectedHotbarSlot = fakeInventory.getSelectedHotbarSlot();
        if (this.baublesInventory != null) {
            this.baublesInventory.setEventHandler(this);
        }
        addFakeInventorySlots();
        this.baublesSlotStart = this.inventorySlots.size();
        addBaublesSlots();
        this.baublesSlotEnd = this.inventorySlots.size();
        this.playerSlotStart = this.inventorySlots.size();
        addPlayerInventorySlots(player);
        this.playerSlotEnd = this.inventorySlots.size();
        scrollBaublesTo(0F);
    }

    public static FakePlayerInventoryContainer server(EntityPlayerMP player, FakePlayer fakePlayer) {
        return new FakePlayerInventoryContainer(
            player,
            fakePlayer,
            FakePlayerInventoryView.server(fakePlayer),
            resolveBaublesInventory(fakePlayer));
    }

    public static FakePlayerInventoryContainer client(EntityPlayer player, FakePlayerInventoryView fakeInventory) {
        return new FakePlayerInventoryContainer(player, null, fakeInventory, resolveClientBaublesInventory(player, null));
    }

    static FakePlayerInventoryContainer client(EntityPlayer player, FakePlayerInventoryView fakeInventory,
        InventoryBaubles baublesInventory) {
        return new FakePlayerInventoryContainer(
            player,
            null,
            fakeInventory,
            resolveClientBaublesInventory(player, baublesInventory));
    }

    static FakePlayerInventoryContainer forTest(EntityPlayer player, FakePlayer fakePlayer,
        FakePlayerInventoryView fakeInventory, InventoryBaubles baublesInventory) {
        return new FakePlayerInventoryContainer(player, fakePlayer, fakeInventory, baublesInventory);
    }

    public boolean isFakeInventorySlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < FAKE_SLOT_COUNT;
    }

    public boolean isBaublesSlot(int slotIndex) {
        return slotIndex >= this.baublesSlotStart && slotIndex < this.baublesSlotEnd;
    }

    public boolean isOffhandSlot(int slotIndex) {
        return slotIndex == FakePlayerInventoryView.OFFHAND_SLOT_INDEX;
    }

    public int getPlayerInventoryStartIndex() {
        return this.playerSlotStart;
    }

    public int getBaublesVisibleSlotCount() {
        return this.baublesVisibleSlotCount;
    }

    public int getBaublesColumns() {
        return this.baublesColumns;
    }

    public int getBaublesHiddenRowCount() {
        int totalRows = (this.baublesVisibleSlotCount + this.baublesColumns - 1) / this.baublesColumns;
        return Math.max(0, totalRows - BAUBLES_VISIBLE_ROWS);
    }

    public boolean canScrollBaubles() {
        return FakePlayerBaublesSlotLayout
            .canScroll(this.baublesVisibleSlotCount, this.baublesColumns, BAUBLES_VISIBLE_ROWS);
    }

    public void scrollBaublesTo(float scrollOffset) {
        if (this.baublesSlotStart >= this.baublesSlotEnd) {
            return;
        }

        int rowOffset = FakePlayerBaublesSlotLayout.resolveRowOffset(
            this.baublesVisibleSlotCount,
            this.baublesColumns,
            BAUBLES_VISIBLE_ROWS,
            scrollOffset);

        for (int visibleSlotIndex = 0; visibleSlotIndex < this.baublesSlotEnd - this.baublesSlotStart; visibleSlotIndex++) {
            Slot slot = (Slot) this.inventorySlots.get(this.baublesSlotStart + visibleSlotIndex);
            slot.xDisplayPosition = BAUBLES_SLOT_X + (visibleSlotIndex % this.baublesColumns) * BAUBLES_SLOT_SPACING;
            slot.yDisplayPosition = FakePlayerBaublesSlotLayout.resolveDisplayY(
                visibleSlotIndex,
                this.baublesColumns,
                rowOffset,
                BAUBLES_SLOT_Y,
                BAUBLES_SLOT_SPACING,
                BAUBLES_HIDDEN_Y);
        }
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
        if (this.fakePlayer != null && (isFakeInventorySlot(slotId) || isBaublesSlot(slotId))) {
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

        if (isFakeInventorySlot(slotIndex) || isBaublesSlot(slotIndex)) {
            if (!mergeItemStack(stackInSlot, this.playerSlotStart, this.playerSlotEnd, false)) {
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
        addSlotToContainer(
            new FakePlayerOffhandSlot(
                this.fakeInventory,
                FakePlayerInventoryView.OFFHAND_SLOT_INDEX,
                OFFHAND_SLOT_X,
                OFFHAND_SLOT_Y));

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

    private void addBaublesSlots() {
        if (this.baublesInventory == null) {
            return;
        }

        int visibleSlotIndex = 0;
        for (int slotIndex = 0; slotIndex < this.baublesInventory.getSizeInventory(); slotIndex++) {
            String slotType = BaubleExpandedSlots.getSlotType(slotIndex);
            if (!shouldShowBaublesSlot(slotType)) {
                continue;
            }
            addSlotToContainer(new SlotBauble(
                this.baublesInventory,
                slotType,
                slotIndex,
                BAUBLES_SLOT_X + (visibleSlotIndex % this.baublesColumns) * BAUBLES_SLOT_SPACING,
                BAUBLES_SLOT_Y + (visibleSlotIndex / this.baublesColumns) * BAUBLES_SLOT_SPACING));
            visibleSlotIndex++;
        }
    }

    private boolean mergeIntoFakeInventory(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemArmor armor) {
            int armorSlotIndex = armorContainerSlotForType(armor.armorType);
            if (armorSlotIndex >= 0 && mergeItemStack(stack, armorSlotIndex, armorSlotIndex + 1, false)) {
                return true;
            }
        }
        if (mergeIntoBaublesInventory(stack)) {
            return true;
        }
        if (mergeItemStack(
            stack,
            FakePlayerInventoryView.OFFHAND_SLOT_INDEX,
            FakePlayerInventoryView.OFFHAND_SLOT_INDEX + 1,
            false)) {
            return true;
        }
        return mergeItemStack(stack, HOTBAR_SLOT_START, MAIN_SLOT_END, false);
    }

    private boolean mergeIntoBaublesInventory(ItemStack stack) {
        if (stack == null || this.baublesSlotStart >= this.baublesSlotEnd || !(stack.getItem() instanceof IBauble bauble)) {
            return false;
        }
        EntityPlayer baublesWearer = this.fakePlayer != null ? this.fakePlayer : this.player;
        if (!bauble.canEquip(stack, baublesWearer)) {
            return false;
        }
        return mergeItemStack(stack, this.baublesSlotStart, this.baublesSlotEnd, false);
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

    private static InventoryBaubles resolveBaublesInventory(EntityPlayer player) {
        if (player == null || player.worldObj == null) {
            return new InventoryBaubles(player);
        }
        InventoryBaubles baublesInventory = PlayerHandler.getPlayerBaubles(player);
        return baublesInventory != null ? baublesInventory : new InventoryBaubles(player);
    }

    private static InventoryBaubles resolveClientBaublesInventory(EntityPlayer player, InventoryBaubles baublesInventory) {
        if (baublesInventory != null) {
            return baublesInventory;
        }
        return new InventoryBaubles(player);
    }

    private static boolean shouldShowBaublesSlot(String slotType) {
        return BaublesConfig.showUnusedSlots || !BaubleExpandedSlots.unknownType.equals(slotType);
    }

    private static int resolveVisibleBaublesSlotCount(InventoryBaubles baublesInventory) {
        if (baublesInventory == null) {
            return 0;
        }

        int visibleSlotCount = 0;
        for (int slotIndex = 0; slotIndex < baublesInventory.getSizeInventory(); slotIndex++) {
            if (shouldShowBaublesSlot(BaubleExpandedSlots.getSlotType(slotIndex))) {
                visibleSlotCount++;
            }
        }
        return visibleSlotCount;
    }
}
