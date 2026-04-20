package com.andgatech.gtstaff.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public final class BackhandCompat {

    private static final String BACKHAND_CLASS_NAME = "xonin.backhand.Backhand";
    private static final String BACKHAND_PACKET_HANDLER_CLASS_NAME = "xonin.backhand.packet.BackhandPacketHandler";
    private static final String OFFHAND_SYNC_PACKET_CLASS_NAME = "xonin.backhand.packet.OffhandSyncItemPacket";
    private static final String GET_OFFHAND_METHOD = "backhand$getOffhandItem";
    private static final String SET_OFFHAND_METHOD = "backhand$setOffhandItem";
    private static final Consumer<EntityPlayer> DEFAULT_OFFHAND_SYNC_ACTION = BackhandCompat::syncOffhandToTrackingReflectively;
    private static volatile Consumer<EntityPlayer> offhandSyncAction = DEFAULT_OFFHAND_SYNC_ACTION;

    private BackhandCompat() {}

    public static ItemStack getOffhandItem(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return null;
        }
        Object result = invokeInventoryMethod(player.inventory, GET_OFFHAND_METHOD, new Class<?>[0]);
        return result instanceof ItemStack ? (ItemStack) result : null;
    }

    public static void setOffhandItem(EntityPlayer player, ItemStack stack) {
        if (player == null || player.inventory == null) {
            return;
        }
        invokeInventoryMethod(player.inventory, SET_OFFHAND_METHOD, new Class<?>[] { ItemStack.class }, stack);
    }

    public static boolean isOffhandItemValid(ItemStack stack) {
        if (stack == null) {
            return true;
        }

        try {
            Class<?> backhandClass = Class.forName(BACKHAND_CLASS_NAME);
            Method blacklistMethod = backhandClass.getMethod("isOffhandBlacklisted", ItemStack.class);
            Object result = blacklistMethod.invoke(null, stack);
            return !(result instanceof Boolean) || !((Boolean) result).booleanValue();
        } catch (ClassNotFoundException ignored) {
            return true;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to query Backhand offhand blacklist", e);
        }
    }

    public static void syncOffhandToWatchers(EntityPlayer player) {
        if (player == null) {
            return;
        }
        offhandSyncAction.accept(player);
    }

    static void setOffhandSyncActionForTests(Consumer<EntityPlayer> testAction) {
        offhandSyncAction = testAction == null ? DEFAULT_OFFHAND_SYNC_ACTION : testAction;
    }

    private static Object invokeInventoryMethod(InventoryPlayer inventory, String methodName, Class<?>[] parameterTypes,
        Object... arguments) {
        Method method = findMethod(inventory.getClass(), methodName, parameterTypes);
        if (method == null) {
            return null;
        }

        try {
            method.setAccessible(true);
            return method.invoke(inventory, arguments);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke Backhand inventory bridge: " + methodName, e);
        }
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>[] parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void syncOffhandToTrackingReflectively(EntityPlayer player) {
        try {
            Class<?> packetClass = Class.forName(OFFHAND_SYNC_PACKET_CLASS_NAME);
            Object packet = packetClass.getConstructor(EntityPlayer.class)
                .newInstance(player);
            Class<?> packetHandlerClass = Class.forName(BACKHAND_PACKET_HANDLER_CLASS_NAME);
            Method sendMethod = findMethod(packetHandlerClass, "sendPacketToAllTracking", 2);
            if (sendMethod == null) {
                return;
            }
            sendMethod.setAccessible(true);
            sendMethod.invoke(null, player, packet);
        } catch (ClassNotFoundException ignored) {
            // Backhand absent: nothing to sync.
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to sync Backhand offhand to tracking players", e);
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName()
                .equals(methodName) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }
}
