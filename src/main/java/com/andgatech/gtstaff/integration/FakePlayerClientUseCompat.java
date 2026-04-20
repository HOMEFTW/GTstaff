package com.andgatech.gtstaff.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public final class FakePlayerClientUseCompat {

    interface ClientUseHandler {

        boolean matches(ItemStack held);

        boolean tryUse(EntityPlayerMP player, ItemStack held, MovingObjectPosition target, boolean blockUsed,
            boolean itemUsed);
    }

    private static final Class<?> TRAVEL_CONTROLLER_CLASS = resolveClass("crazypants.enderio.teleport.TravelController");
    private static final Class<?> TRAVEL_SOURCE_CLASS = resolveClass("crazypants.enderio.api.teleport.TravelSource");
    private static final Class<?> BLOCK_COORD_CLASS = resolveClass("com.enderio.core.common.util.BlockCoord");
    private static final Class<?> VECTOR3D_CLASS = resolveClass("com.enderio.core.common.vecmath.Vector3d");
    private static final Class<?> CONFIG_CLASS = resolveClass("crazypants.enderio.config.Config");
    private static final Class<?> PACKET_TRAVEL_EVENT_CLASS = resolveClass(
        "crazypants.enderio.teleport.packet.PacketTravelEvent");

    private static final Object TRAVEL_CONTROLLER = resolveStaticField(TRAVEL_CONTROLLER_CLASS, "instance");
    private static final Object STAFF_BLINK_SOURCE = resolveEnumConstant(TRAVEL_SOURCE_CLASS, "STAFF_BLINK");

    private static final Method GET_EYE_POSITION = resolveStaticMethod(
        "com.enderio.core.common.util.Util",
        "getEyePositionEio",
        EntityPlayer.class);
    private static final Method GET_LOOK_VECTOR = resolveStaticMethod(
        "com.enderio.core.common.util.Util",
        "getLookVecEio",
        EntityPlayer.class);
    private static final Method RAYTRACE_ALL = resolveStaticMethod(
        "com.enderio.core.common.util.Util",
        "raytraceAll",
        net.minecraft.world.World.class,
        Vec3.class,
        Vec3.class,
        boolean.class);

    private static final Constructor<?> BLOCK_COORD_CONSTRUCTOR = resolveConstructor(BLOCK_COORD_CLASS, int.class, int.class, int.class);
    private static final Constructor<?> VECTOR3D_CONSTRUCTOR = resolveConstructor(VECTOR3D_CLASS, double.class, double.class, double.class);

    private static final Field BLOCK_COORD_X = resolveField(BLOCK_COORD_CLASS, "x");
    private static final Field BLOCK_COORD_Y = resolveField(BLOCK_COORD_CLASS, "y");
    private static final Field BLOCK_COORD_Z = resolveField(BLOCK_COORD_CLASS, "z");

    private static final Field CONFIG_TRAVEL_STAFF_MAX_BLINK_DISTANCE = resolveField(
        CONFIG_CLASS,
        "travelStaffMaxBlinkDistance");
    private static final Field CONFIG_TRAVEL_STAFF_BLINK_THROUGH_CLEAR_BLOCKS_ENABLED = resolveField(
        CONFIG_CLASS,
        "travelStaffBlinkThroughClearBlocksEnabled");

    private static final Method FIND_NEARBY_DESTINATION = resolveInstanceMethod(
        TRAVEL_CONTROLLER_CLASS,
        "findNearbyDestination",
        EntityPlayer.class,
        TRAVEL_SOURCE_CLASS,
        VECTOR3D_CLASS);
    private static final Method FIND_VALID_DESTINATION = resolveInstanceMethod(
        TRAVEL_CONTROLLER_CLASS,
        "findValidDestination",
        EntityPlayer.class,
        TRAVEL_SOURCE_CLASS,
        BLOCK_COORD_CLASS);
    private static final Method GET_REQUIRED_POWER = resolveInstanceMethod(
        TRAVEL_CONTROLLER_CLASS,
        "getRequiredPower",
        EntityPlayer.class,
        TRAVEL_SOURCE_CLASS,
        BLOCK_COORD_CLASS);
    private static final Method IS_BLACKLISTED_BLOCK = resolveInstanceMethod(
        TRAVEL_CONTROLLER_CLASS,
        "isBlackListedBlock",
        EntityPlayer.class,
        MovingObjectPosition.class,
        Block.class);
    private static final Method DO_SERVER_TELEPORT = resolveStaticMethod(
        PACKET_TRAVEL_EVENT_CLASS,
        "doServerTeleport",
        Entity.class,
        int.class,
        int.class,
        int.class,
        int.class,
        boolean.class,
        TRAVEL_SOURCE_CLASS);

    private static List<ClientUseHandler> handlers = defaultHandlers();

    private FakePlayerClientUseCompat() {}

    public static boolean tryUse(EntityPlayerMP player, ItemStack held, MovingObjectPosition target, boolean blockUsed,
        boolean itemUsed) {
        if (player == null || held == null) {
            return false;
        }

        for (ClientUseHandler handler : handlers) {
            if (handler.matches(held) && handler.tryUse(player, held, target, blockUsed, itemUsed)) {
                return true;
            }
        }

        return false;
    }

    static void setHandlersForTesting(List<ClientUseHandler> overrides) {
        handlers = overrides == null ? defaultHandlers() : overrides;
    }

    static void resetHandlersForTesting() {
        handlers = defaultHandlers();
    }

    private static List<ClientUseHandler> defaultHandlers() {
        return Collections.<ClientUseHandler>singletonList(new TstYamatoClientUseHandler());
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Constructor<?> resolveConstructor(Class<?> owner, Class<?>... parameterTypes) {
        if ((owner == null) || containsNull(parameterTypes)) {
            return null;
        }
        try {
            Constructor<?> constructor = owner.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method resolveStaticMethod(String className, String name, Class<?>... parameterTypes) {
        return resolveStaticMethod(resolveClass(className), name, parameterTypes);
    }

    private static Method resolveStaticMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if ((owner == null) || containsNull(parameterTypes)) {
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

    private static Method resolveInstanceMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        return resolveStaticMethod(owner, name, parameterTypes);
    }

    private static Field resolveField(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object resolveStaticField(Class<?> owner, String name) {
        Field field = resolveField(owner, name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolveEnumConstant(Class<?> enumType, String name) {
        if (enumType == null || !enumType.isEnum()) {
            return null;
        }
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static boolean containsNull(Class<?>[] types) {
        for (Class<?> type : types) {
            if (type == null) {
                return true;
            }
        }
        return false;
    }

    private static Point readPoint(Object vector) {
        if (vector == null) {
            return null;
        }
        try {
            Field xField = vector.getClass().getDeclaredField("x");
            Field yField = vector.getClass().getDeclaredField("y");
            Field zField = vector.getClass().getDeclaredField("z");
            xField.setAccessible(true);
            yField.setAccessible(true);
            zField.setAccessible(true);
            return new Point(xField.getDouble(vector), yField.getDouble(vector), zField.getDouble(vector));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Point readBlockCoord(Object blockCoord) {
        if ((blockCoord == null) || (BLOCK_COORD_X == null) || (BLOCK_COORD_Y == null) || (BLOCK_COORD_Z == null)) {
            return null;
        }
        try {
            return new Point(
                ((Number) BLOCK_COORD_X.get(blockCoord)).doubleValue(),
                ((Number) BLOCK_COORD_Y.get(blockCoord)).doubleValue(),
                ((Number) BLOCK_COORD_Z.get(blockCoord)).doubleValue());
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Object newVector(Point point) {
        if (VECTOR3D_CONSTRUCTOR == null) {
            return null;
        }
        try {
            return VECTOR3D_CONSTRUCTOR.newInstance(point.x, point.y, point.z);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object newBlockCoord(Point point) {
        if (BLOCK_COORD_CONSTRUCTOR == null) {
            return null;
        }
        try {
            return BLOCK_COORD_CONSTRUCTOR.newInstance((int) point.x, (int) point.y, (int) point.z);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int readBlinkDistance() {
        if (CONFIG_TRAVEL_STAFF_MAX_BLINK_DISTANCE == null) {
            return 0;
        }
        try {
            return CONFIG_TRAVEL_STAFF_MAX_BLINK_DISTANCE.getInt(null);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    private static boolean readBlinkThroughClearBlocksEnabled() {
        if (CONFIG_TRAVEL_STAFF_BLINK_THROUGH_CLEAR_BLOCKS_ENABLED == null) {
            return false;
        }
        try {
            return CONFIG_TRAVEL_STAFF_BLINK_THROUGH_CLEAR_BLOCKS_ENABLED.getBoolean(null);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static Optional<Object> invokeFindNearbyDestination(EntityPlayer player, Object source, Point candidate) {
        if ((TRAVEL_CONTROLLER == null) || (FIND_NEARBY_DESTINATION == null)) {
            return Optional.empty();
        }
        Object vector = newVector(candidate);
        if (vector == null) {
            return Optional.empty();
        }
        try {
            Object result = FIND_NEARBY_DESTINATION.invoke(TRAVEL_CONTROLLER, player, source, vector);
            if (!(result instanceof Optional<?> optional) || !optional.isPresent()) {
                return Optional.empty();
            }
            return Optional.of(optional.get());
        } catch (IllegalAccessException | InvocationTargetException e) {
            return Optional.empty();
        }
    }

    private static boolean invokeIsBlacklistedBlock(EntityPlayer player, MovingObjectPosition hit, Block block) {
        if ((TRAVEL_CONTROLLER == null) || (IS_BLACKLISTED_BLOCK == null)) {
            return false;
        }
        try {
            return ((Boolean) IS_BLACKLISTED_BLOCK.invoke(TRAVEL_CONTROLLER, player, hit, block)).booleanValue();
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    private static boolean serverTravelToLocation(EntityPlayerMP player, Object source, Object target,
        boolean conserveMotion) {
        if ((TRAVEL_CONTROLLER == null) || (FIND_VALID_DESTINATION == null) || (GET_REQUIRED_POWER == null)
            || (DO_SERVER_TELEPORT == null)) {
            return false;
        }
        try {
            Object result = FIND_VALID_DESTINATION.invoke(TRAVEL_CONTROLLER, player, source, target);
            if (!(result instanceof Optional<?> optional) || !optional.isPresent()) {
                return false;
            }

            Object validated = optional.get();
            int powerUse = ((Number) GET_REQUIRED_POWER.invoke(TRAVEL_CONTROLLER, player, source, validated)).intValue();
            if (powerUse < 0) {
                return false;
            }

            Point destination = readBlockCoord(validated);
            if (destination == null) {
                return false;
            }

            return ((Boolean) DO_SERVER_TELEPORT.invoke(
                null,
                player,
                (int) destination.x,
                (int) destination.y,
                (int) destination.z,
                powerUse,
                conserveMotion,
                source)).booleanValue();
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    private static final class TstYamatoClientUseHandler implements ClientUseHandler {

        private static final String YAMATO_CLASS_NAME = "com.Nxer.TwistSpaceTechnology.common.item.ItemYamato";

        @Override
        public boolean matches(ItemStack held) {
            return held != null && held.getItem() != null && YAMATO_CLASS_NAME.equals(held.getItem().getClass().getName());
        }

        @Override
        public boolean tryUse(EntityPlayerMP player, ItemStack held, MovingObjectPosition target, boolean blockUsed,
            boolean itemUsed) {
            if (player.worldObj == null || player.worldObj.isRemote || blockUsed || player.isSneaking()) {
                return false;
            }

            boolean used = tryServerBlink(player);
            if (used) {
                player.swingItem();
                if (player instanceof FakePlayer fakePlayer) {
                    fakePlayer.broadcastSwingAnimation();
                }
            }
            return used;
        }

        private boolean tryServerBlink(EntityPlayerMP player) {
            if ((GET_EYE_POSITION == null) || (GET_LOOK_VECTOR == null) || (RAYTRACE_ALL == null)
                || (STAFF_BLINK_SOURCE == null)) {
                return false;
            }

            Point eyePos = invokePoint(GET_EYE_POSITION, player);
            Point lookVec = invokePoint(GET_LOOK_VECTOR, player);
            if ((eyePos == null) || (lookVec == null)) {
                return false;
            }

            int blinkDistance = readBlinkDistance();
            Point blinkEnd = eyePos.add(lookVec.scale(blinkDistance));
            Vec3 start = Vec3.createVectorHelper(eyePos.x, eyePos.y, eyePos.z);
            Vec3 end = Vec3.createVectorHelper(blinkEnd.x, blinkEnd.y, blinkEnd.z);

            double playerYOffset = player.yOffset;
            double pitchYOffset = -lookVec.y * playerYOffset;
            double maxDistance = blinkDistance + pitchYOffset;
            boolean stopOnLiquid = !readBlinkThroughClearBlocksEnabled();
            MovingObjectPosition blockHit = player.worldObj.rayTraceBlocks(start, end, stopOnLiquid);
            if (blockHit == null) {
                return tryDirectBlinkPath(player, eyePos, lookVec, playerYOffset, maxDistance);
            }

            try {
                @SuppressWarnings("unchecked")
                List<MovingObjectPosition> hits = (List<MovingObjectPosition>) RAYTRACE_ALL.invoke(
                    null,
                    player.worldObj,
                    start,
                    end,
                    stopOnLiquid);
                for (MovingObjectPosition hit : hits) {
                    if (hit == null) {
                        continue;
                    }

                    Block block = player.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ);
                    if (invokeIsBlacklistedBlock(player, hit, block)) {
                        Point blockCenter = new Point(hit.blockX + 0.5D, hit.blockY + 0.5D, hit.blockZ + 0.5D);
                        maxDistance = Math.min(maxDistance, eyePos.distanceTo(blockCenter) - 1.5D - pitchYOffset);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                return false;
            }

            Point impactOrigin = new Point(blockHit.blockX, blockHit.blockY, blockHit.blockZ);
            Point blockCenter = new Point(blockHit.blockX + 0.5D, blockHit.blockY + 0.5D, blockHit.blockZ + 0.5D);
            double directionStep = 1.5D;
            double distanceAlongLook = eyePos.distanceTo(blockCenter) + directionStep;
            while (distanceAlongLook < maxDistance) {
                Point candidatePoint = impactOrigin.add(lookVec.scale(directionStep)).offsetY(-playerYOffset);
                Optional<Object> candidate = invokeFindNearbyDestination(player, STAFF_BLINK_SOURCE, candidatePoint);
                if (candidate.isPresent() && serverTravelToLocation(player, STAFF_BLINK_SOURCE, candidate.get(), false)) {
                    return true;
                }
                distanceAlongLook += 1.0D;
                directionStep += 1.0D;
            }

            directionStep = -0.5D;
            distanceAlongLook = eyePos.distanceTo(blockCenter) + directionStep;
            while (distanceAlongLook > 1.0D) {
                Point candidatePoint = impactOrigin.add(lookVec.scale(directionStep)).offsetY(-playerYOffset);
                Optional<Object> candidate = invokeFindNearbyDestination(player, STAFF_BLINK_SOURCE, candidatePoint);
                if (candidate.isPresent() && serverTravelToLocation(player, STAFF_BLINK_SOURCE, candidate.get(), false)) {
                    return true;
                }
                directionStep -= 1.0D;
                distanceAlongLook -= 1.0D;
            }

            return false;
        }

        private boolean tryDirectBlinkPath(EntityPlayerMP player, Point eyePos, Point lookVec, double playerYOffset,
            double maxDistance) {
            for (double distance = maxDistance; distance > 1.0D; distance -= 1.0D) {
                Point candidatePoint = eyePos.add(lookVec.scale(distance)).offsetY(-playerYOffset);
                Optional<Object> candidate = invokeFindNearbyDestination(player, STAFF_BLINK_SOURCE, candidatePoint);
                if (candidate.isPresent() && serverTravelToLocation(player, STAFF_BLINK_SOURCE, candidate.get(), true)) {
                    return true;
                }
            }
            return false;
        }

        private Point invokePoint(Method method, EntityPlayer player) {
            try {
                return readPoint(method.invoke(null, player));
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }
    }

    private static final class Point {

        private final double x;
        private final double y;
        private final double z;

        private Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        private Point scale(double factor) {
            return new Point(x * factor, y * factor, z * factor);
        }

        private Point offsetY(double delta) {
            return new Point(x, y + delta, z);
        }

        private double distanceTo(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
