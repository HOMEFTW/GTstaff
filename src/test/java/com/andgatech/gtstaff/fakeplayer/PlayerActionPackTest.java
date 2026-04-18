package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.MovingObjectPosition;

import org.junit.jupiter.api.Test;

class PlayerActionPackTest {

    @Test
    void turnAdjustsRelativeRotationAndClampsPitch() {
        StubPlayer player = stubPlayer();
        player.rotationYaw = 30.0F;
        player.rotationPitch = 80.0F;
        PlayerActionPack pack = new PlayerActionPack(player);

        pack.turn(20.0F, 30.0F);

        assertEquals(50.0F, player.rotationYaw);
        assertEquals(90.0F, player.rotationPitch);
    }

    @Test
    void stopMovementClearsDirectionalInputsAndFlags() {
        StubPlayer player = stubPlayer();
        PlayerActionPack pack = new PlayerActionPack(player);
        pack.setForward(1.0F);
        pack.setStrafing(-1.0F);
        pack.setSneaking(true);
        pack.setSprinting(true);

        pack.stopMovement();
        pack.onUpdate();

        assertEquals(0.0F, player.moveForward);
        assertEquals(0.0F, player.moveStrafing);
        assertFalse(player.sneakingState);
        assertFalse(player.sprintingState);
    }

    @Test
    void setSlotUsesOneBasedHotbarIndex() {
        StubPlayer player = stubPlayer();
        PlayerActionPack pack = new PlayerActionPack(player);

        pack.setSlot(3);

        assertEquals(2, player.inventory.currentItem);
    }

    @Test
    void dropItemActionsUseCorrectDropMode() {
        StubPlayer player = stubPlayer();
        PlayerActionPack pack = new PlayerActionPack(player);

        pack.start(ActionType.DROP_ITEM, Action.once());
        pack.onUpdate();
        pack.start(ActionType.DROP_STACK, Action.once());
        pack.onUpdate();

        assertEquals(2, player.dropCalls);
        assertEquals(Boolean.FALSE, player.dropModes[0]);
        assertEquals(Boolean.TRUE, player.dropModes[1]);
    }

    @Test
    void successfulUseSkipsAttackInSameTick() {
        StubPlayer player = stubPlayer();
        TestablePack pack = new TestablePack(player);
        pack.useResult = true;
        pack.attackResult = true;
        pack.start(ActionType.USE, Action.once());
        pack.start(ActionType.ATTACK, Action.once());

        pack.onUpdate();

        assertEquals(1, pack.useCalls);
        assertEquals(0, pack.attackCalls);
    }

    @Test
    void completedJumpActionClearsJumpingOnNextTick() {
        StubPlayer player = stubPlayer();
        player.onGround = false;
        PlayerActionPack pack = new PlayerActionPack(player);
        pack.start(ActionType.JUMP, Action.once());

        pack.onUpdate();
        assertTrue(player.jumpingState);

        pack.onUpdate();
        assertFalse(player.jumpingState);
    }

    private static StubPlayer stubPlayer() {
        StubPlayer player = allocate(StubPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = allocate(StubContainer.class);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        player.dropModes = new Boolean[4];
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

    private static class TestablePack extends PlayerActionPack {

        private boolean useResult;
        private boolean attackResult;
        private int useCalls;
        private int attackCalls;

        private TestablePack(EntityPlayerMP player) {
            super(player);
        }

        @Override
        protected MovingObjectPosition getTarget() {
            return null;
        }

        @Override
        protected boolean performUse(MovingObjectPosition target) {
            useCalls++;
            return useResult;
        }

        @Override
        protected boolean performAttack(MovingObjectPosition target) {
            attackCalls++;
            return attackResult;
        }
    }

    private static class StubPlayer extends EntityPlayerMP {

        private boolean sneakingState;
        private boolean sprintingState;
        private boolean jumpingState;
        private int dropCalls;
        private Boolean lastDropAll;
        private Boolean[] dropModes;

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void setSneaking(boolean value) {
            sneakingState = value;
        }

        @Override
        public void setSprinting(boolean value) {
            sprintingState = value;
        }

        @Override
        public EntityItem dropOneItem(boolean dropAll) {
            lastDropAll = dropAll;
            dropModes[dropCalls] = dropAll;
            dropCalls++;
            return null;
        }

        @Override
        public void setJumping(boolean value) {
            jumpingState = value;
        }

        @Override
        public void attackTargetEntityWithCurrentItem(Entity targetEntity) {}

        @Override
        public void sendContainerToPlayer(Container container) {}
    }

    private static final class StubContainer extends Container {

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }
}
