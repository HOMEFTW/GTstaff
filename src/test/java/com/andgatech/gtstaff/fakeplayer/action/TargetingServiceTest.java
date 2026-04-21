package com.andgatech.gtstaff.fakeplayer.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.Test;

class TargetingServiceTest {

    @Test
    void findsEntityWhenEyeStartsInsideExpandedHitbox() {
        StubPlayer player = stubPlayer();
        StubWorld world = allocate(StubWorld.class);
        setField(Entity.class, player, "worldObj", world);
        player.posX = 0.0D;
        player.posY = 0.0D;
        player.posZ = 0.0D;
        player.rotationYaw = 0.0F;
        player.rotationPitch = 0.0F;
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D));

        StubTargetEntity target = allocate(StubTargetEntity.class);
        setField(Entity.class, target, "worldObj", world);
        setField(
            Entity.class,
            target,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(-0.1D, 1.2D, -0.1D, 0.1D, 1.8D, 0.1D));
        world.entities = java.util.Collections.<Entity>singletonList(target);

        TargetingResult result = new TargetingService(player).resolve();

        assertTrue(result.hitEntity());
        assertEquals(target, result.entity());
    }

    @Test
    void attackFallbackIgnoresEntityOutsideFacingCone() {
        StubPlayer player = stubPlayer();
        StubWorld world = allocate(StubWorld.class);
        setField(Entity.class, player, "worldObj", world);
        player.posX = 0.0D;
        player.posY = 0.0D;
        player.posZ = 0.0D;
        player.rotationYaw = 0.0F;
        player.rotationPitch = 0.0F;
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D));

        StubTargetEntity sideTarget = allocate(StubTargetEntity.class);
        setField(Entity.class, sideTarget, "worldObj", world);
        sideTarget.posX = 2.5D;
        sideTarget.posY = 0.0D;
        sideTarget.posZ = 1.0D;
        setField(
            Entity.class,
            sideTarget,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(2.2D, 0.0D, 0.7D, 2.8D, 1.8D, 1.3D));
        world.entities = java.util.Collections.<Entity>singletonList(sideTarget);

        TargetingResult result = new TargetingService(player).resolveForAttack();

        assertFalse(result.hitEntity());
    }

    @Test
    void attackFallbackIncludesEntityInsideFortyFiveDegreeCone() {
        StubPlayer player = stubPlayer();
        StubWorld world = allocate(StubWorld.class);
        setField(Entity.class, player, "worldObj", world);
        player.posX = 0.0D;
        player.posY = 0.0D;
        player.posZ = 0.0D;
        player.rotationYaw = 0.0F;
        player.rotationPitch = 0.0F;
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D));

        StubTargetEntity angledTarget = allocate(StubTargetEntity.class);
        setField(Entity.class, angledTarget, "worldObj", world);
        angledTarget.posX = 2.0D;
        angledTarget.posY = 0.0D;
        angledTarget.posZ = 3.0D;
        setField(
            Entity.class,
            angledTarget,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(1.7D, 0.0D, 2.7D, 2.3D, 1.8D, 3.3D));
        world.entities = java.util.Collections.<Entity>singletonList(angledTarget);

        TargetingResult result = new TargetingService(player).resolveForAttack();

        assertTrue(result.hitEntity());
        assertEquals(angledTarget, result.entity());
    }

    private static StubPlayer stubPlayer() {
        StubPlayer player = allocate(StubPlayer.class);
        setField(EntityPlayer.class, player, "eyeHeight", 1.62F);
        StubItemInWorldManager manager = allocate(StubItemInWorldManager.class);
        setField(ItemInWorldManager.class, manager, "gameType", WorldSettings.GameType.SURVIVAL);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", manager);
        return player;
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return type.cast(unsafe.allocateInstance(type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubPlayer extends EntityPlayerMP {

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void sendContainerToPlayer(Container container) {}
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }

    private static final class StubWorld extends WorldServer {

        private java.util.List<Entity> entities = java.util.Collections.emptyList();

        private StubWorld() {
            super(null, null, null, 0, null, null);
        }

        @Override
        public MovingObjectPosition func_147447_a(Vec3 start, Vec3 end, boolean stopOnLiquid,
            boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
            return null;
        }

        @Override
        public java.util.List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB bounds) {
            return entities;
        }
    }

    private static final class StubTargetEntity extends Entity {

        private StubTargetEntity() {
            super((World) null);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public boolean canBeCollidedWith() {
            return true;
        }
    }
}
