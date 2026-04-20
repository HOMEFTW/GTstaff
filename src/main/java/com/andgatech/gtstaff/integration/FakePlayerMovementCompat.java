package com.andgatech.gtstaff.integration;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

public final class FakePlayerMovementCompat {

    interface MovementHandler {

        boolean tryTrigger(EntityPlayerMP player, PlayerActionPack.MovementTrigger trigger);
    }

    private static final Class<?> ELEVATOR_HANDLER_CLASS = resolveClass("openblocks.common.ElevatorActionHandler");
    private static final Class<?> ELEVATOR_BLOCK_CLASS = resolveClass("openblocks.api.IElevatorBlock");

    private static final Method ELEVATOR_ACTIVATE = resolveStaticMethod(
        ELEVATOR_HANDLER_CLASS,
        "activate",
        EntityPlayer.class,
        World.class,
        int.class,
        int.class,
        int.class,
        ForgeDirection.class);

    private static List<MovementHandler> handlers = defaultHandlers();

    private FakePlayerMovementCompat() {}

    public static boolean tryTrigger(EntityPlayerMP player, PlayerActionPack.MovementTrigger trigger) {
        if (player == null || trigger == null) {
            return false;
        }

        for (MovementHandler handler : handlers) {
            if (handler.tryTrigger(player, trigger)) {
                return true;
            }
        }

        return false;
    }

    static void setHandlersForTesting(List<MovementHandler> overrides) {
        handlers = overrides == null ? defaultHandlers() : overrides;
    }

    static void resetHandlersForTesting() {
        handlers = defaultHandlers();
    }

    private static List<MovementHandler> defaultHandlers() {
        return Collections.<MovementHandler>singletonList(new OpenBlocksElevatorHandler());
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method resolveStaticMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static final class OpenBlocksElevatorHandler implements MovementHandler {

        @Override
        public boolean tryTrigger(EntityPlayerMP player, PlayerActionPack.MovementTrigger trigger) {
            if (ELEVATOR_BLOCK_CLASS == null || ELEVATOR_ACTIVATE == null || player.worldObj == null
                || player.boundingBox == null || player.ridingEntity != null) {
                return false;
            }

            ForgeDirection direction = directionFor(trigger);
            if (direction == null) {
                return false;
            }

            int x = MathHelper.floor_double(player.posX);
            int y = MathHelper.floor_double(player.boundingBox.minY) - 1;
            int z = MathHelper.floor_double(player.posZ);
            Block block = player.worldObj.getBlock(x, y, z);

            if (trigger == PlayerActionPack.MovementTrigger.JUMP && block == Blocks.air) {
                y--;
                block = player.worldObj.getBlock(x, y, z);
            }

            if (!ELEVATOR_BLOCK_CLASS.isInstance(block)) {
                return false;
            }

            try {
                ELEVATOR_ACTIVATE.invoke(null, player, player.worldObj, x, y, z, direction);
                return true;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        private ForgeDirection directionFor(PlayerActionPack.MovementTrigger trigger) {
            switch (trigger) {
                case JUMP:
                    return ForgeDirection.UP;
                case SNEAK:
                    return ForgeDirection.DOWN;
                default:
                    return null;
            }
        }
    }
}
