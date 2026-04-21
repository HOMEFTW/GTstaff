package com.andgatech.gtstaff.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.ui.FakePlayerInventoryExtraSlot;

public final class FakePlayerInventoryCompat {

    private static final String BAUBLES_UNKNOWN_TYPE = "unknown";
    private static final String BAUBLES_UNIVERSAL_TYPE = "universal";
    private static final String BAUBLES_API = "baubles.api.BaublesApi";
    private static final String BAUBLES_ITEM = "baubles.api.IBauble";
    private static final String BAUBLES_ITEM_EXPANDED = "baubles.api.expanded.IBaubleExpanded";
    private static final String BAUBLES_EXPANDED_SLOTS = "baubles.api.expanded.BaubleExpandedSlots";
    private static final String BACKHAND_UTILS = "xonin.backhand.api.core.BackhandUtils";
    private static final String BACKHAND = "xonin.backhand.Backhand";

    private static Class<?> baublesItemClass;
    private static Class<?> baublesExpandedItemClass;
    private static Method getBaubles;
    private static Method baublesSlotsCurrentlyUsed;
    private static Method baublesGetSlotType;
    private static Method baublesTypeFromLegacyType;
    private static Method getOffhandItem;
    private static Method setOffhandItem;
    private static Method getOffhandSlot;
    private static Method isOffhandBlacklisted;

    private FakePlayerInventoryCompat() {}

    public static List<FakePlayerInventoryExtraSlot> serverSlots(EntityPlayer fakePlayer) {
        if (fakePlayer == null) {
            return Collections.emptyList();
        }

        List<FakePlayerInventoryExtraSlot> slots = new ArrayList<>();
        addServerBaublesSlots(fakePlayer, slots);
        addServerBackhandSlot(fakePlayer, slots);
        return slots;
    }

    public static List<FakePlayerInventoryExtraSlot> clientSlots(EntityPlayer fakePlayer) {
        List<FakePlayerInventoryExtraSlot> slots = new ArrayList<>();
        int baublesSlots = getBaublesSlotCount(fakePlayer);
        for (int slot = 0; slot < baublesSlots; slot++) {
            String slotType = getBaublesSlotType(slot);
            if (!isVisibleBaublesSlotType(slotType)) {
                continue;
            }
            slots.add(FakePlayerInventoryExtraSlot.client(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                1,
                slotType));
        }
        if (hasBackhandOffhandSlot(fakePlayer)) {
            slots.add(FakePlayerInventoryExtraSlot.client(
                FakePlayerInventoryExtraSlot.Kind.OFFHAND,
                "Offhand",
                64));
        }
        return slots;
    }

    private static void addServerBaublesSlots(EntityPlayer fakePlayer, List<FakePlayerInventoryExtraSlot> slots) {
        IInventory baubles = getBaublesInventory(fakePlayer);
        if (baubles == null) {
            return;
        }
        for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
            String slotType = getBaublesSlotType(slot);
            if (!isVisibleBaublesSlotType(slotType)) {
                continue;
            }
            slots.add(FakePlayerInventoryExtraSlot.fromInventory(
                FakePlayerInventoryExtraSlot.Kind.BAUBLES,
                "Baubles",
                baubles,
                slot,
                slotType));
        }
    }

    static boolean isVisibleBaublesSlotType(String slotType) {
        return slotType != null && !slotType.isEmpty() && !BAUBLES_UNKNOWN_TYPE.equals(slotType);
    }

    public static boolean isClientExtraSlotItemValid(FakePlayerInventoryExtraSlot extraSlot, ItemStack stack) {
        if (extraSlot == null || stack == null) {
            return false;
        }
        return switch (extraSlot.kind()) {
            case BAUBLES -> isClientBaublesItemValid(stack, extraSlot.baublesSlotType());
            case OFFHAND -> isBackhandItemValid(stack);
        };
    }

    private static void addServerBackhandSlot(EntityPlayer fakePlayer, List<FakePlayerInventoryExtraSlot> slots) {
        if (!hasBackhandOffhandSlot(fakePlayer)) {
            return;
        }
        slots.add(FakePlayerInventoryExtraSlot.fromInventory(
            FakePlayerInventoryExtraSlot.Kind.OFFHAND,
            "Offhand",
            new BackhandOffhandInventory(fakePlayer),
            0));
    }

    private static IInventory getBaublesInventory(EntityPlayer player) {
        try {
            Method method = getBaublesMethod();
            if (method == null) {
                return null;
            }
            Object result = method.invoke(null, player);
            return result instanceof IInventory inventory ? inventory : null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to query Baubles inventory for fake player", e);
            return null;
        }
    }

    private static int getBaublesSlotCount(EntityPlayer fakePlayer) {
        IInventory baubles = fakePlayer == null ? null : getBaublesInventory(fakePlayer);
        if (baubles != null) {
            return baubles.getSizeInventory();
        }
        try {
            Method method = getBaublesSlotsCurrentlyUsedMethod();
            return method == null ? 0 : ((Number) method.invoke(null)).intValue();
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to query Baubles slot count", e);
            return 0;
        }
    }

    private static String getBaublesSlotType(int slot) {
        try {
            Method method = getBaublesSlotTypeMethod();
            Object result = method == null ? null : method.invoke(null, slot);
            return result instanceof String slotType ? slotType : "";
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to query Baubles slot type", e);
            return "";
        }
    }

    private static boolean hasBackhandOffhandSlot(EntityPlayer fakePlayer) {
        if (getOffhandSlotMethod() == null) {
            return false;
        }
        if (fakePlayer == null) {
            return true;
        }
        return getBackhandOffhandSlot(fakePlayer) >= 0;
    }

    private static int getBackhandOffhandSlot(EntityPlayer fakePlayer) {
        try {
            Method method = getOffhandSlotMethod();
            if (method == null) {
                return -1;
            }
            return ((Number) method.invoke(null, fakePlayer)).intValue();
        } catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to query Backhand offhand slot", e);
            return -1;
        }
    }

    private static ItemStack getBackhandOffhandItem(EntityPlayer fakePlayer) {
        try {
            Method method = getOffhandItemMethod();
            if (method == null) {
                return null;
            }
            Object result = method.invoke(null, fakePlayer);
            return result instanceof ItemStack stack ? stack : null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to query Backhand offhand item", e);
            return null;
        }
    }

    private static void setBackhandOffhandItem(EntityPlayer fakePlayer, ItemStack stack) {
        try {
            Method method = getSetOffhandItemMethod();
            if (method != null) {
                method.invoke(null, fakePlayer, stack);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to set Backhand offhand item", e);
        }
    }

    private static boolean isBackhandItemValid(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        try {
            Method method = getIsOffhandBlacklistedMethod();
            return method == null || !((Boolean) method.invoke(null, stack));
        } catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to check Backhand offhand blacklist", e);
            return true;
        }
    }

    private static boolean isClientBaublesItemValid(ItemStack stack, String slotType) {
        if (stack == null || !isVisibleBaublesSlotType(slotType)) {
            return false;
        }
        Object item = stack.getItem();
        Class<?> baubleClass = getBaublesItemClass();
        if (item == null || baubleClass == null || !baubleClass.isInstance(item)) {
            return false;
        }
        String[] baubleTypes = getClientBaublesItemTypes(item, stack);
        if (baubleTypes.length == 0) {
            return false;
        }
        for (String baubleType : baubleTypes) {
            if (BAUBLES_UNIVERSAL_TYPE.equals(baubleType) || slotType.equals(baubleType)) {
                return true;
            }
        }
        return false;
    }

    private static String[] getClientBaublesItemTypes(Object item, ItemStack stack) {
        try {
            Class<?> expandedClass = getBaublesExpandedItemClass();
            if (expandedClass != null && expandedClass.isInstance(item)) {
                Object result = item.getClass()
                    .getMethod("getBaubleTypes", ItemStack.class)
                    .invoke(item, stack);
                return result instanceof String[] types ? types : new String[0];
            }

            Object legacyType = item.getClass()
                .getMethod("getBaubleType", ItemStack.class)
                .invoke(item, stack);
            String converted = convertLegacyBaublesType(legacyType);
            return converted.isEmpty() ? new String[0] : new String[] { converted };
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            GTstaff.LOG.debug("Unable to query Baubles item types for fake player inventory", e);
            return new String[0];
        }
    }

    private static String convertLegacyBaublesType(Object legacyType) {
        if (legacyType == null) {
            return "";
        }
        try {
            Method method = getBaublesTypeFromLegacyTypeMethod();
            Object result = method == null ? null : method.invoke(null, legacyType);
            return result instanceof String slotType ? slotType : "";
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to convert legacy Baubles type for fake player inventory", e);
            return "";
        }
    }

    private static Method getBaublesMethod() {
        if (getBaubles != null) {
            return getBaubles;
        }
        getBaubles = findStaticMethod(BAUBLES_API, "getBaubles", EntityPlayer.class);
        return getBaubles;
    }

    private static Method getBaublesSlotsCurrentlyUsedMethod() {
        if (baublesSlotsCurrentlyUsed != null) {
            return baublesSlotsCurrentlyUsed;
        }
        baublesSlotsCurrentlyUsed = findStaticMethod(BAUBLES_EXPANDED_SLOTS, "slotsCurrentlyUsed");
        return baublesSlotsCurrentlyUsed;
    }

    private static Method getBaublesSlotTypeMethod() {
        if (baublesGetSlotType != null) {
            return baublesGetSlotType;
        }
        baublesGetSlotType = findStaticMethod(BAUBLES_EXPANDED_SLOTS, "getSlotType", int.class);
        return baublesGetSlotType;
    }

    private static Method getBaublesTypeFromLegacyTypeMethod() {
        if (baublesTypeFromLegacyType != null) {
            return baublesTypeFromLegacyType;
        }
        Class<?> baubleTypeClass = findClass("baubles.api.BaubleType");
        baublesTypeFromLegacyType = baubleTypeClass == null ? null
            : findStaticMethod(BAUBLES_EXPANDED_SLOTS, "getTypeFromBaubleType", baubleTypeClass);
        return baublesTypeFromLegacyType;
    }

    private static Method getOffhandItemMethod() {
        if (getOffhandItem != null) {
            return getOffhandItem;
        }
        getOffhandItem = findStaticMethod(BACKHAND_UTILS, "getOffhandItem", EntityPlayer.class);
        return getOffhandItem;
    }

    private static Method getSetOffhandItemMethod() {
        if (setOffhandItem != null) {
            return setOffhandItem;
        }
        setOffhandItem = findStaticMethod(BACKHAND_UTILS, "setPlayerOffhandItem", EntityPlayer.class, ItemStack.class);
        return setOffhandItem;
    }

    private static Method getOffhandSlotMethod() {
        if (getOffhandSlot != null) {
            return getOffhandSlot;
        }
        getOffhandSlot = findStaticMethod(BACKHAND_UTILS, "getOffhandSlot", EntityPlayer.class);
        return getOffhandSlot;
    }

    private static Method getIsOffhandBlacklistedMethod() {
        if (isOffhandBlacklisted != null) {
            return isOffhandBlacklisted;
        }
        isOffhandBlacklisted = findStaticMethod(BACKHAND, "isOffhandBlacklisted", ItemStack.class);
        return isOffhandBlacklisted;
    }

    private static Method findStaticMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Class<?> type = Class.forName(className);
            return type.getMethod(methodName, parameterTypes);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    private static Class<?> getBaublesItemClass() {
        if (baublesItemClass != null) {
            return baublesItemClass;
        }
        baublesItemClass = findClass(BAUBLES_ITEM);
        return baublesItemClass;
    }

    private static Class<?> getBaublesExpandedItemClass() {
        if (baublesExpandedItemClass != null) {
            return baublesExpandedItemClass;
        }
        baublesExpandedItemClass = findClass(BAUBLES_ITEM_EXPANDED);
        return baublesExpandedItemClass;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static final class BackhandOffhandInventory implements IInventory {

        private final EntityPlayer player;

        private BackhandOffhandInventory(EntityPlayer player) {
            this.player = player;
        }

        @Override
        public int getSizeInventory() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? getBackhandOffhandItem(this.player) : null;
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = getStackInSlot(slot);
            if (slot != 0 || stack == null || amount <= 0) {
                return null;
            }
            if (stack.stackSize <= amount) {
                setInventorySlotContents(slot, null);
                return stack;
            }
            ItemStack removed = stack.splitStack(amount);
            if (stack.stackSize <= 0) {
                setInventorySlotContents(slot, null);
            } else {
                markDirty();
            }
            return removed;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            ItemStack stack = getStackInSlot(slot);
            setInventorySlotContents(slot, null);
            return stack;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            if (slot == 0) {
                setBackhandOffhandItem(this.player, stack);
                markDirty();
            }
        }

        @Override
        public String getInventoryName() {
            return "Offhand";
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
        public void markDirty() {
            if (this.player != null && this.player.inventory != null) {
                this.player.inventory.markDirty();
            }
        }

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
            return slot == 0 && isBackhandItemValid(stack);
        }
    }
}
