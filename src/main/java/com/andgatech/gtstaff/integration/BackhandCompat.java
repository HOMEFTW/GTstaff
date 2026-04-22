package com.andgatech.gtstaff.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

public final class BackhandCompat {

    private static final String BACKHAND_PACKET_HANDLER_CLASS_NAME = "xonin.backhand.packet.BackhandPacketHandler";
    private static final String OFFHAND_SYNC_PACKET_CLASS_NAME = "xonin.backhand.packet.OffhandSyncItemPacket";
    private static final String GET_OFFHAND_METHOD = "backhand$getOffhandItem";
    private static final String SET_OFFHAND_METHOD = "backhand$setOffhandItem";
    private static final String BACKHAND_CLASS_NAME = "xonin.backhand.Backhand";
    private static final Consumer<EntityPlayer> DEFAULT_OFFHAND_SYNC_ACTION = BackhandCompat::syncOffhandToWorldPlayersReflectively;
    private static final BiConsumer<EntityPlayer, EntityPlayer> DEFAULT_OFFHAND_SYNC_TO_PLAYER_ACTION = BackhandCompat::syncOffhandToPlayerReflectively;
    private static volatile Consumer<EntityPlayer> offhandSyncAction = DEFAULT_OFFHAND_SYNC_ACTION;
    private static volatile BiConsumer<EntityPlayer, EntityPlayer> offhandSyncToPlayerAction = DEFAULT_OFFHAND_SYNC_TO_PLAYER_ACTION;

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

    public static void syncOffhandToPlayer(EntityPlayer player, EntityPlayer recipient) {
        if (player == null || recipient == null || recipient == player) {
            return;
        }
        offhandSyncToPlayerAction.accept(player, recipient);
    }

    static void setOffhandSyncActionForTests(Consumer<EntityPlayer> testAction) {
        offhandSyncAction = testAction == null ? DEFAULT_OFFHAND_SYNC_ACTION : testAction;
    }

    static void setOffhandSyncToPlayerActionForTests(BiConsumer<EntityPlayer, EntityPlayer> testAction) {
        offhandSyncToPlayerAction = testAction == null ? DEFAULT_OFFHAND_SYNC_TO_PLAYER_ACTION : testAction;
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

    private static void syncOffhandToWorldPlayersReflectively(EntityPlayer player) {
        if (!(player.worldObj instanceof WorldServer world) || world.playerEntities == null) {
            return;
        }
        for (Object watcherObj : world.playerEntities) {
            if (watcherObj instanceof EntityPlayer watcher) {
                syncOffhandToPlayer(player, watcher);
            }
        }
    }

    private static void syncOffhandToPlayerReflectively(EntityPlayer player, EntityPlayer recipient) {
        if (!(recipient instanceof EntityPlayerMP)) {
            return;
        }
        try {
            Object packet = createOffhandSyncPacket(player);
            if (packet == null) {
                return;
            }
            Class<?> packetHandlerClass = Class.forName(BACKHAND_PACKET_HANDLER_CLASS_NAME);
            Method sendMethod = findMethod(packetHandlerClass, "sendPacketToPlayer", 2);
            if (sendMethod == null) {
                return;
            }
            sendMethod.setAccessible(true);
            sendMethod.invoke(null, packet, recipient);
        } catch (ClassNotFoundException ignored) {
            // Backhand absent: nothing to sync.
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to sync Backhand offhand to player", e);
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

    private static Object createOffhandSyncPacket(EntityPlayer player)
        throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException,
        InvocationTargetException {
        return Class.forName(OFFHAND_SYNC_PACKET_CLASS_NAME).getConstructor(EntityPlayer.class)
            .newInstance(player);
    }
}
