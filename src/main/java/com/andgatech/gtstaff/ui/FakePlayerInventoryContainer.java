package com.andgatech.gtstaff.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.andgatech.gtstaff.fakeplayer.PlayerVisualSync;

public class FakePlayerInventoryContainer extends Container {

    private static final int PLAYER_MAIN_SLOT_COUNT = 27;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_SLOT_COUNT = PLAYER_MAIN_SLOT_COUNT + PLAYER_HOTBAR_SLOT_COUNT;
    private static final int HOTBAR_SLOT_START = FakePlayerInventoryView.ARMOR_SLOT_COUNT;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + FakePlayerInventoryView.HOTBAR_SLOT_COUNT;
    private static final int MAIN_SLOT_START = HOTBAR_SLOT_END;
    private static final int MAIN_SLOT_END = FakePlayerInventoryView.BASE_SLOT_COUNT;
    private static final int BASE_TOP_SECTION_HEIGHT = 17 + 5 * 18;
    private static final int BASE_PLAYER_INVENTORY_TOP = 125;
    private static final int PLAYER_HOTBAR_OFFSET = 58;
    private static final int PLAYER_SECTION_HEIGHT = 96;
    private static final int OFFHAND_SLOT_X = 80;
    private static final int OFFHAND_SLOT_Y = 18;
    private static final int BAUBLES_SLOT_X = 184;
    private static final int BAUBLES_SLOT_Y = 18;
    private static final int BAUBLES_SLOT_SPACING = 18;
    private static final int BAUBLES_VISIBLE_ROWS = 8;
    private static final int BAUBLES_MAX_COLUMNS = 4;
    private static final int BAUBLES_HIDDEN_Y = -2000;

    private final EntityPlayer player;
    private final EntityPlayerMP fakePlayer;
    private final FakePlayerInventoryView fakeInventory;
    private final int fakeSlotCount;
    private final int playerSlotStart;
    private final int playerSlotEnd;
    private final int baublesVisibleSlotCount;
    private final int baublesColumns;
    private final int playerInventoryTop;
    private int selectedHotbarSlot;
    private float baublesScrollOffset;

    private FakePlayerInventoryContainer(EntityPlayer player, EntityPlayerMP fakePlayer,
        FakePlayerInventoryView fakeInventory) {
        this.player = player;
        this.fakePlayer = fakePlayer;
        this.fakeInventory = fakeInventory;
        this.fakeSlotCount = fakeInventory.getSizeInventory();
        this.playerSlotStart = this.fakeSlotCount;
        this.playerSlotEnd = this.playerSlotStart + PLAYER_SLOT_COUNT;
        this.baublesVisibleSlotCount = countExtraSlotsOfKind(FakePlayerInventoryExtraSlot.Kind.BAUBLES);
        this.baublesColumns = this.baublesVisibleSlotCount == 0 ? 1
            : FakePlayerBaublesSlotLayout.resolveColumns(this.baublesVisibleSlotCount, BAUBLES_MAX_COLUMNS);
        this.playerInventoryTop = BASE_PLAYER_INVENTORY_TOP;
        this.selectedHotbarSlot = fakeInventory.getSelectedHotbarSlot();
        addFakeInventorySlots();
        addPlayerInventorySlots(player);
        scrollBaublesTo(0F);
    }

    public static FakePlayerInventoryContainer server(EntityPlayerMP player, EntityPlayerMP fakePlayer) {
        return new FakePlayerInventoryContainer(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer));
    }

    public static FakePlayerInventoryContainer client(EntityPlayer player, FakePlayerInventoryView fakeInventory) {
        return new FakePlayerInventoryContainer(player, null, fakeInventory);
    }

    public boolean isFakeInventorySlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.fakeSlotCount;
    }

    public int getSelectedHotbarSlot() {
        return this.selectedHotbarSlot;
    }

    public int getPlayerInventoryTop() {
        return this.playerInventoryTop;
    }

    public int getTopSectionHeight() {
        return BASE_TOP_SECTION_HEIGHT;
    }

    public int getGuiHeight() {
        return BASE_TOP_SECTION_HEIGHT + PLAYER_SECTION_HEIGHT;
    }

    public FakePlayerInventoryView getFakeInventory() {
        return this.fakeInventory;
    }

    public int getBaublesVisibleSlotCount() {
        return this.baublesVisibleSlotCount;
    }

    public boolean canScrollBaubles() {
        return FakePlayerBaublesSlotLayout
            .canScroll(this.baublesVisibleSlotCount, this.baublesColumns, BAUBLES_VISIBLE_ROWS);
    }

    public int getBaublesHiddenRowCount() {
        return FakePlayerBaublesSlotLayout
            .resolveHiddenRowCount(this.baublesVisibleSlotCount, this.baublesColumns, BAUBLES_VISIBLE_ROWS);
    }

    public float getBaublesScrollOffset() {
        return this.baublesScrollOffset;
    }

    public void scrollBaublesTo(float scrollOffset) {
        this.baublesScrollOffset = clampScrollOffset(scrollOffset);
        int rowOffset = FakePlayerBaublesSlotLayout.resolveRowOffset(
            this.baublesVisibleSlotCount,
            this.baublesColumns,
            BAUBLES_VISIBLE_ROWS,
            this.baublesScrollOffset);
        int baublesDisplayIndex = 0;
        for (int slotIndex = MAIN_SLOT_END; slotIndex < this.fakeSlotCount; slotIndex++) {
            if (!this.fakeInventory.isExtraSlotOfKind(slotIndex, FakePlayerInventoryExtraSlot.Kind.BAUBLES)) {
                continue;
            }
            Slot slot = (Slot) this.inventorySlots.get(slotIndex);
            slot.xDisplayPosition = BAUBLES_SLOT_X + baublesDisplayIndex % this.baublesColumns * BAUBLES_SLOT_SPACING;
            slot.yDisplayPosition = FakePlayerBaublesSlotLayout.resolveDisplayY(
                baublesDisplayIndex,
                this.baublesColumns,
                rowOffset,
                BAUBLES_SLOT_Y,
                BAUBLES_SLOT_SPACING,
                BAUBLES_HIDDEN_Y);
            baublesDisplayIndex++;
        }
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
            syncEquipmentToWatchers();
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
            syncEquipmentToWatchers();
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

        for (int extraSlot = 0; extraSlot < this.fakeInventory.getExtraSlotCount(); extraSlot++) {
            int slotIndex = FakePlayerInventoryView.BASE_SLOT_COUNT + extraSlot;
            addSlotToContainer(new FakePlayerExtraSlot(this.fakeInventory, slotIndex, extraSlotX(slotIndex), extraSlotY(slotIndex)));
        }
    }

    private void addPlayerInventorySlots(EntityPlayer player) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventoryIndex = column + (row + 1) * 9;
                addSlotToContainer(
                    new Slot(player.inventory, inventoryIndex, 8 + column * 18, this.playerInventoryTop + row * 18));
            }
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            addSlotToContainer(new Slot(
                player.inventory,
                hotbarSlot,
                8 + hotbarSlot * 18,
                this.playerInventoryTop + PLAYER_HOTBAR_OFFSET));
        }
    }

    private boolean mergeIntoFakeInventory(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemArmor armor) {
            int armorSlotIndex = armorContainerSlotForType(armor.armorType);
            if (armorSlotIndex >= 0 && mergeItemStack(stack, armorSlotIndex, armorSlotIndex + 1, false)) {
                return true;
            }
        }
        if (mergeIntoExtraSlots(stack, FakePlayerInventoryExtraSlot.Kind.BAUBLES)) {
            return true;
        }
        if (mergeItemStack(stack, HOTBAR_SLOT_START, MAIN_SLOT_END, false)) {
            return true;
        }
        return mergeIntoExtraSlots(stack, FakePlayerInventoryExtraSlot.Kind.OFFHAND);
    }

    private boolean mergeIntoExtraSlots(ItemStack stack, FakePlayerInventoryExtraSlot.Kind kind) {
        for (int slotIndex = MAIN_SLOT_END; slotIndex < this.fakeSlotCount; slotIndex++) {
            if (this.fakeInventory.isExtraSlotOfKind(slotIndex, kind)
                && mergeIntoExtraSlot(stack, (Slot) this.inventorySlots.get(slotIndex))) {
                return true;
            }
        }
        return false;
    }

    private static boolean mergeIntoExtraSlot(ItemStack stack, Slot slot) {
        if (stack == null || slot == null || !slot.isItemValid(stack)) {
            return false;
        }

        ItemStack slotStack = slot.getStack();
        int slotLimit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());

        if (slotStack == null) {
            int movedAmount = Math.min(stack.stackSize, slotLimit);
            ItemStack movedStack = stack.copy();
            movedStack.stackSize = movedAmount;
            slot.putStack(movedStack);
            slot.onSlotChanged();
            stack.stackSize -= movedAmount;
            return movedAmount > 0;
        }

        if (!slotStack.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(slotStack, stack)) {
            return false;
        }

        if (slotStack.stackSize >= slotLimit) {
            return false;
        }

        int movedAmount = Math.min(stack.stackSize, slotLimit - slotStack.stackSize);
        if (movedAmount <= 0) {
            return false;
        }
        slotStack.stackSize += movedAmount;
        slot.onSlotChanged();
        stack.stackSize -= movedAmount;
        return true;
    }

    private int countExtraSlotsOfKind(FakePlayerInventoryExtraSlot.Kind kind) {
        int count = 0;
        for (int slotIndex = MAIN_SLOT_END; slotIndex < this.fakeSlotCount; slotIndex++) {
            if (this.fakeInventory.isExtraSlotOfKind(slotIndex, kind)) {
                count++;
            }
        }
        return count;
    }

    private int extraSlotX(int slotIndex) {
        if (this.fakeInventory.isExtraSlotOfKind(slotIndex, FakePlayerInventoryExtraSlot.Kind.OFFHAND)) {
            return OFFHAND_SLOT_X;
        }
        if (!this.fakeInventory.isExtraSlotOfKind(slotIndex, FakePlayerInventoryExtraSlot.Kind.BAUBLES)) {
            return BAUBLES_SLOT_X;
        }
        int baublesDisplayIndex = baublesDisplayIndex(slotIndex);
        return BAUBLES_SLOT_X + baublesDisplayIndex % this.baublesColumns * BAUBLES_SLOT_SPACING;
    }

    private int extraSlotY(int slotIndex) {
        if (this.fakeInventory.isExtraSlotOfKind(slotIndex, FakePlayerInventoryExtraSlot.Kind.OFFHAND)) {
            return OFFHAND_SLOT_Y;
        }
        if (!this.fakeInventory.isExtraSlotOfKind(slotIndex, FakePlayerInventoryExtraSlot.Kind.BAUBLES)) {
            return BAUBLES_SLOT_Y;
        }
        return BAUBLES_SLOT_Y + baublesDisplayIndex(slotIndex) / this.baublesColumns * BAUBLES_SLOT_SPACING;
    }

    private int baublesDisplayIndex(int slotIndex) {
        int displayIndex = 0;
        for (int currentSlot = MAIN_SLOT_END; currentSlot < slotIndex; currentSlot++) {
            if (this.fakeInventory.isExtraSlotOfKind(currentSlot, FakePlayerInventoryExtraSlot.Kind.BAUBLES)) {
                displayIndex++;
            }
        }
        return displayIndex;
    }

    private void setSelectedHotbarSlot(int slot) {
        this.selectedHotbarSlot = clampHotbarSlot(slot);
        this.fakeInventory.setSelectedHotbarSlot(this.selectedHotbarSlot);
        if (this.fakePlayer != null && this.fakePlayer.inventory != null) {
            this.fakePlayer.inventory.currentItem = this.selectedHotbarSlot;
        }
    }

    private void syncEquipmentToWatchers() {
        if (this.fakePlayer instanceof PlayerVisualSync visualSync) {
            visualSync.syncEquipmentToWatchers();
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

    private static float clampScrollOffset(float scrollOffset) {
        if (scrollOffset < 0F) {
            return 0F;
        }
        if (scrollOffset > 1F) {
            return 1F;
        }
        return scrollOffset;
    }

    static final class FakePlayerExtraSlot extends Slot {

        private final FakePlayerInventoryView inventory;

        private FakePlayerExtraSlot(FakePlayerInventoryView inventory, int slotIndex, int x, int y) {
            super(inventory, slotIndex, x, y);
            this.inventory = inventory;
        }

        @Override
        public int getSlotStackLimit() {
            return this.inventory.getSlotStackLimit(getSlotIndex());
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return this.inventory.isItemValidForSlot(getSlotIndex(), stack);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getBackgroundIconIndex() {
            return FakePlayerBaublesIconCompat.backgroundIconForSlotType(getBaublesSlotTypeForBackground());
        }

        String getBaublesSlotTypeForBackground() {
            return this.inventory.isExtraSlotOfKind(getSlotIndex(), FakePlayerInventoryExtraSlot.Kind.BAUBLES)
                ? this.inventory.getExtraSlotBaublesType(getSlotIndex())
                : "";
        }
    }
}
