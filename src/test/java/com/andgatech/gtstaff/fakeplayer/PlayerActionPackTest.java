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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.config.Config;

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
    void setSlotSyncsEquipmentForVisualSyncPlayers() {
        VisualSyncPlayer player = stubPlayer(VisualSyncPlayer.class);
        PlayerActionPack pack = new PlayerActionPack(player);

        pack.setSlot(4);

        assertEquals(1, player.syncEquipmentCalls);
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
    void dropItemActionsSyncHeldItemForVisualSyncPlayers() {
        VisualSyncPlayer player = stubPlayer(VisualSyncPlayer.class);
        PlayerActionPack pack = new PlayerActionPack(player);

        pack.start(ActionType.DROP_STACK, Action.once());
        pack.onUpdate();

        assertEquals(1, player.getDropCalls());
        assertEquals(1, player.syncEquipmentCalls);
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

    @Test
    void clientUseBridgeCanRunAfterDirectItemUseSucceeds() {
        StubPlayer player = stubPlayer();
        player.inventory.mainInventory[0] = new ItemStack(new Item());
        player.inventory.currentItem = 0;
        BridgeAwarePack pack = new BridgeAwarePack(player);
        pack.directItemUseResult = true;
        pack.bridgeUseResult = true;

        assertTrue(pack.performUse(null));
        assertEquals(1, pack.directItemUseCalls);
        assertEquals(1, pack.bridgeUseCalls);
        assertFalse(pack.bridgeSawBlockUsed);
        assertTrue(pack.bridgeSawItemUsed);
    }

    @Test
    void clientUseBridgeSeesBlockInteractionState() {
        StubPlayer player = stubPlayer();
        player.inventory.mainInventory[0] = new ItemStack(new Item());
        player.inventory.currentItem = 0;
        BridgeAwarePack pack = new BridgeAwarePack(player);
        pack.blockActivationResult = true;
        pack.bridgeUseResult = true;

        MovingObjectPosition target = new MovingObjectPosition(1, 2, 3, 1, Vec3.createVectorHelper(1.5D, 2.5D, 3.5D));
        assertTrue(pack.performUse(target));
        assertEquals(1, pack.blockActivationCalls);
        assertEquals(0, pack.directItemUseCalls);
        assertEquals(1, pack.bridgeUseCalls);
        assertTrue(pack.bridgeSawBlockUsed);
        assertFalse(pack.bridgeSawItemUsed);
    }

    @Test
    void intervalUseRepeatsAfterCooldownExpires() {
        StubPlayer player = stubPlayer();
        BridgeAwarePack pack = new BridgeAwarePack(player);
        pack.target = new MovingObjectPosition(1, 2, 3, 1, Vec3.createVectorHelper(1.5D, 2.5D, 3.5D));
        pack.blockActivationResult = true;
        pack.start(ActionType.USE, Action.interval(5));

        for (int tick = 0; tick < 6; tick++) {
            pack.onUpdate();
        }

        assertEquals(2, pack.blockActivationCalls);
    }

    @Test
    void jumpActionTriggersMovementCompatBridge() {
        StubPlayer player = stubPlayer();
        player.onGround = true;
        MovementBridgeAwarePack pack = new MovementBridgeAwarePack(player);
        pack.movementBridgeResult = true;
        pack.start(ActionType.JUMP, Action.once());

        pack.onUpdate();

        assertEquals(1, pack.movementBridgeCalls);
        assertEquals(PlayerActionPack.MovementTrigger.JUMP, pack.lastMovementTrigger);
    }

    @Test
    void enablingSneakTriggersMovementCompatBridgeOnlyOnLeadingEdge() {
        StubPlayer player = stubPlayer();
        MovementBridgeAwarePack pack = new MovementBridgeAwarePack(player);
        pack.movementBridgeResult = true;

        pack.setSneaking(true);
        assertEquals(1, pack.movementBridgeCalls);
        assertEquals(PlayerActionPack.MovementTrigger.SNEAK, pack.lastMovementTrigger);

        pack.setSneaking(true);
        assertEquals(1, pack.movementBridgeCalls);

        pack.setSneaking(false);
        pack.setSneaking(true);
        assertEquals(2, pack.movementBridgeCalls);
    }

    @Test
    void getTargetFindsEntityWhenEyeStartsInsideExpandedHitbox() {
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

        PlayerActionPack pack = new PlayerActionPack(player);
        MovingObjectPosition result = pack.getTarget();

        assertTrue(result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY);
        assertEquals(target, result.entityHit);
    }

    @Test
    void attackWithoutTargetStillSwings() {
        StubPlayer player = stubPlayer();
        VisualFeedbackPack pack = new VisualFeedbackPack(player);

        assertTrue(pack.performAttack(null));
        assertEquals(1, pack.swingCalls);
    }

    @Test
    void attackWithoutTargetBroadcastsSwingForVisualSyncPlayers() {
        VisualSyncPlayer player = stubPlayer(VisualSyncPlayer.class);
        ExposedAttackPack pack = new ExposedAttackPack(player);

        assertTrue(pack.attackWithoutTarget());
        assertEquals(1, player.getSwingItemCalls());
        assertEquals(1, player.swingAnimationCalls);
    }

    @Test
    void attackFallsBackToNearbyEntityWhenPreciseRayMisses() {
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

        DamageTrackingEntity target = new DamageTrackingEntity();
        setField(Entity.class, target, "worldObj", world);
        setField(
            Entity.class,
            target,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0.7D, 0.0D, 1.7D, 1.3D, 1.8D, 2.3D));
        world.entities = java.util.Collections.<Entity>singletonList(target);

        PlayerActionPack pack = new PlayerActionPack(player);
        pack.start(ActionType.ATTACK, Action.once());
        pack.onUpdate();

        assertEquals(1, target.damageCalls);
    }

    @Test
    void useWithoutAnyInteractionStillSwings() {
        StubPlayer player = stubPlayer();
        VisualFeedbackPack pack = new VisualFeedbackPack(player);

        assertTrue(pack.performUse(null));
        assertEquals(1, pack.swingCalls);
    }

    @Test
    void attackFallsBackToDirectDamageWhenVanillaAttackHasNoEffect() {
        StubPlayer player = stubPlayer();
        DamageTrackingEntity target = new DamageTrackingEntity();
        FallbackAttackPack pack = new FallbackAttackPack(player);

        assertTrue(pack.performAttack(new MovingObjectPosition(target, Vec3.createVectorHelper(0.0D, 0.0D, 0.0D))));
        assertEquals(1, target.damageCalls);
        assertTrue(target.lastDamage > 0.0F);
        assertEquals(player, target.lastDamageSource.getEntity());
    }

    @Test
    void attackFallsBackWhenVanillaAttackOnlyMarksVelocityWithoutDamagingLivingTarget() {
        VelocityOnlyAttackPlayer player = stubPlayer(VelocityOnlyAttackPlayer.class);
        RejectingLivingTarget target = new RejectingLivingTarget();
        target.setHealth(20.0F);
        FallbackAttackPack pack = new FallbackAttackPack(player);

        assertTrue(pack.performAttack(new MovingObjectPosition(target, Vec3.createVectorHelper(0.0D, 0.0D, 0.0D))));
        assertTrue(target.getHealth() < 20.0F);
    }

    @Test
    void attackForceDamagesLivingTargetWhenAttackEntityFromIsCanceled() {
        StubPlayer player = stubPlayer();
        RejectingLivingTarget target = new RejectingLivingTarget();
        target.setHealth(20.0F);
        FallbackAttackPack pack = new FallbackAttackPack(player);

        assertTrue(pack.performAttack(new MovingObjectPosition(target, Vec3.createVectorHelper(0.0D, 0.0D, 0.0D))));
        assertTrue(target.getHealth() < 20.0F);
        assertEquals(0, target.damageCalls);
    }

    @Test
    void diagnosticsDisabledByDefault() {
        assertFalse(Config.fakePlayerActionDiagnostics);
    }

    private static StubPlayer stubPlayer() {
        return stubPlayer(StubPlayer.class);
    }

    private static <T extends StubPlayer> T stubPlayer(Class<T> type) {
        T player = allocate(type);
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = allocate(StubContainer.class);
        StubItemInWorldManager manager = allocate(StubItemInWorldManager.class);
        setField(ItemInWorldManager.class, manager, "gameType", WorldSettings.GameType.SURVIVAL);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", manager);
        setField(Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(EntityPlayer.class, player, "eyeHeight", 1.62F);
        setField(StubPlayer.class, player, "dropModes", new Boolean[4]);
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

    private static final class BridgeAwarePack extends PlayerActionPack {

        private MovingObjectPosition target;
        private boolean blockActivationResult;
        private boolean directItemUseResult;
        private boolean bridgeUseResult;
        private int blockActivationCalls;
        private int directItemUseCalls;
        private int bridgeUseCalls;
        private boolean bridgeSawBlockUsed;
        private boolean bridgeSawItemUsed;

        private BridgeAwarePack(EntityPlayerMP player) {
            super(player);
        }

        @Override
        protected MovingObjectPosition getTarget() {
            return target;
        }

        @Override
        protected boolean performBlockActivationUse(MovingObjectPosition target, ItemStack held, float hitX, float hitY,
            float hitZ) {
            blockActivationCalls++;
            return blockActivationResult;
        }

        @Override
        protected boolean performDirectItemUse(ItemStack held) {
            directItemUseCalls++;
            return directItemUseResult;
        }

        @Override
        protected boolean performClientUseBridge(MovingObjectPosition target, ItemStack held, boolean blockUsed,
            boolean itemUsed) {
            bridgeUseCalls++;
            bridgeSawBlockUsed = blockUsed;
            bridgeSawItemUsed = itemUsed;
            return bridgeUseResult;
        }
    }

    private static final class MovementBridgeAwarePack extends PlayerActionPack {

        private boolean movementBridgeResult;
        private int movementBridgeCalls;
        private MovementTrigger lastMovementTrigger;

        private MovementBridgeAwarePack(EntityPlayerMP player) {
            super(player);
        }

        @Override
        protected boolean performMovementCompatBridge(MovementTrigger trigger) {
            movementBridgeCalls++;
            lastMovementTrigger = trigger;
            return movementBridgeResult;
        }
    }

    private static final class VisualFeedbackPack extends PlayerActionPack {

        private int swingCalls;

        private VisualFeedbackPack(EntityPlayerMP player) {
            super(player);
        }

        @Override
        protected void performSwingAnimation() {
            swingCalls++;
        }
    }

    private static final class FallbackAttackPack extends PlayerActionPack {

        private FallbackAttackPack(EntityPlayerMP player) {
            super(player);
        }

        @Override
        protected void performSwingAnimation() {}
    }

    private static final class ExposedAttackPack extends PlayerActionPack {

        private ExposedAttackPack(EntityPlayerMP player) {
            super(player);
        }

        private boolean attackWithoutTarget() {
            return performAttack(null);
        }
    }

    private static class StubPlayer extends EntityPlayerMP {

        private boolean sneakingState;
        private boolean sprintingState;
        private boolean jumpingState;
        private int dropCalls;
        private Boolean lastDropAll;
        private Boolean[] dropModes;
        private int swingItemCalls;

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
        public void swingItem() {
            swingItemCalls++;
        }

        protected int getSwingItemCalls() {
            return swingItemCalls;
        }

        protected int getDropCalls() {
            return dropCalls;
        }

        @Override
        public void attackTargetEntityWithCurrentItem(Entity targetEntity) {}

        @Override
        public void sendContainerToPlayer(Container container) {}
    }

    private static final class VisualSyncPlayer extends StubPlayer implements PlayerVisualSync {

        private int syncEquipmentCalls;
        private int swingAnimationCalls;

        @Override
        public void syncEquipmentToWatchers() {
            syncEquipmentCalls++;
        }

        @Override
        public void broadcastSwingAnimation() {
            swingAnimationCalls++;
        }
    }

    private static final class VelocityOnlyAttackPlayer extends StubPlayer {

        @Override
        public void attackTargetEntityWithCurrentItem(Entity targetEntity) {
            if (targetEntity != null) {
                targetEntity.velocityChanged = true;
            }
        }
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

    private static class StubWorld extends WorldServer {

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

    private static class StubTargetEntity extends Entity {

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

    private static final class DamageTrackingEntity extends Entity {

        private int damageCalls;
        private float lastDamage;
        private DamageSource lastDamageSource;

        private DamageTrackingEntity() {
            super((World) null);
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            damageCalls++;
            lastDamage = amount;
            lastDamageSource = source;
            velocityChanged = true;
            return true;
        }

        @Override
        public boolean canBeCollidedWith() {
            return true;
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}
    }

    private static final class RejectingLivingTarget extends net.minecraft.entity.EntityLivingBase {

        private int damageCalls;

        private RejectingLivingTarget() {
            super((World) null);
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            damageCalls++;
            return false;
        }

        @Override
        protected void entityInit() {
            super.entityInit();
        }

        @Override
        public void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public ItemStack getHeldItem() {
            return null;
        }

        @Override
        public ItemStack getEquipmentInSlot(int slot) {
            return null;
        }

        @Override
        public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

        @Override
        public ItemStack[] getLastActiveItems() {
            return new ItemStack[0];
        }
    }
}
