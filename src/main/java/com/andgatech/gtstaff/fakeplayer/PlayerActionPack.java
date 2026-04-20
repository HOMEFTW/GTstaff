package com.andgatech.gtstaff.fakeplayer;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.andgatech.gtstaff.integration.FakePlayerClientUseCompat;
import com.andgatech.gtstaff.integration.FakePlayerMovementCompat;

public class PlayerActionPack {

    public static enum MovementTrigger {
        JUMP,
        SNEAK
    }

    private final EntityPlayerMP player;
    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);

    private ChunkCoordinates currentBlock;
    private int blockHitDelay;
    private float curBlockDamageMP;
    private int itemUseCooldown;

    private float moveForward;
    private float moveStrafing;
    private boolean sneaking;
    private boolean sprinting;

    public PlayerActionPack(EntityPlayerMP player) {
        this.player = player;
    }

    public void onUpdate() {
        Iterator<Map.Entry<ActionType, Action>> iterator = actions.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<ActionType, Action> entry = iterator.next();
            if (entry.getValue().done) {
                cleanup(entry.getKey());
                iterator.remove();
            }
        }

        Map<ActionType, Boolean> attempts = new EnumMap<>(ActionType.class);
        for (Map.Entry<ActionType, Action> entry : actions.entrySet()) {
            ActionType type = entry.getKey();

            if (Boolean.TRUE.equals(attempts.get(ActionType.USE)) && type == ActionType.ATTACK) {
                continue;
            }

            if (entry.getValue()
                .tick()) {
                attempts.put(type, executeAction(type));
            }
        }

        player.moveStrafing = moveStrafing * (sneaking ? 0.3F : 1.0F);
        player.moveForward = moveForward * (sneaking ? 0.3F : 1.0F);
        player.setSneaking(sneaking);
        player.setSprinting(sprinting);
    }

    public void start(ActionType type, Action action) {
        stop(type);
        actions.put(type, action);
    }

    public void stop(ActionType type) {
        if (actions.remove(type) != null) {
            cleanup(type);
        }
    }

    public void stopAll() {
        for (ActionType type : actions.keySet()) {
            cleanup(type);
        }
        actions.clear();
        stopMovement();
    }

    public void look(float yaw, float pitch) {
        player.rotationYaw = yaw % 360.0F;
        player.rotationPitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
        player.prevRotationYaw = player.rotationYaw;
        player.prevRotationPitch = player.rotationPitch;
        player.renderYawOffset = player.rotationYaw;
        player.prevRenderYawOffset = player.rotationYaw;
        player.rotationYawHead = player.rotationYaw;
        player.prevRotationYawHead = player.rotationYaw;
    }

    public void turn(float yaw, float pitch) {
        look(player.rotationYaw + yaw, player.rotationPitch + pitch);
    }

    public void stopMovement() {
        moveForward = 0.0F;
        moveStrafing = 0.0F;
        sneaking = false;
        sprinting = false;
    }

    public void setSlot(int slot) {
        player.inventory.currentItem = MathHelper.clamp_int(slot, 1, 9) - 1;
        if (player instanceof FakePlayer fake) fake.syncEquipmentToWatchers();
    }

    public void setForward(float value) {
        moveForward = value;
    }

    public void setStrafing(float value) {
        moveStrafing = value;
    }

    public void setSneaking(boolean value) {
        boolean changedToSneaking = value && !sneaking;
        sneaking = value;
        if (value) {
            sprinting = false;
        }
        if (changedToSneaking) {
            performMovementCompatBridge(MovementTrigger.SNEAK);
        }
    }

    public void setSprinting(boolean value) {
        sprinting = value;
        if (value) {
            sneaking = false;
        }
    }

    @SuppressWarnings("unchecked")
    protected MovingObjectPosition getTarget() {
        double reach = isCreativeMode() ? 5.0D : 4.5D;

        Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 lookVec = player.getLookVec();
        Vec3 endPos = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);

        MovingObjectPosition blockHit = player.worldObj.func_147447_a(eyePos, endPos, false, false, true);

        double blockDist = blockHit != null ? eyePos.distanceTo(blockHit.hitVec) : Double.MAX_VALUE;

        Entity closestEntity = null;
        Vec3 closestEntityHit = null;
        double closestEntityDist = blockDist;

        List<Entity> entities = player.worldObj
            .getEntitiesWithinAABBExcludingEntity(
                player,
                player.boundingBox.addCoord(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach)
                    .expand(1.0D, 1.0D, 1.0D));
        for (Entity entity : entities) {
            if (!entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB expandedBB = entity.boundingBox.expand(border, border, border);
            MovingObjectPosition intercept = expandedBB.calculateIntercept(eyePos, endPos);

            if (expandedBB.isVecInside(eyePos)) {
                if (0.0D <= closestEntityDist) {
                    closestEntityDist = 0.0D;
                    closestEntityHit = intercept == null ? eyePos : intercept.hitVec;
                    closestEntity = entity;
                }
                continue;
            }

            if (intercept != null) {
                double dist = eyePos.distanceTo(intercept.hitVec);
                if (dist < closestEntityDist || closestEntityDist == 0.0D) {
                    if (entity == player.ridingEntity && !entity.canRiderInteract()) {
                        if (closestEntityDist == 0.0D) {
                            closestEntityHit = intercept.hitVec;
                            closestEntity = entity;
                        }
                    } else {
                        closestEntityDist = dist;
                        closestEntityHit = intercept.hitVec;
                        closestEntity = entity;
                    }
                }
            }
        }

        if (closestEntity != null && (closestEntityDist < blockDist || blockHit == null)) {
            return new MovingObjectPosition(closestEntity, closestEntityHit);
        }

        return blockHit;
    }

    protected boolean performUse(MovingObjectPosition target) {
        if (player.theItemInWorldManager == null || player.worldObj == null) {
            return false;
        }

        if (itemUseCooldown > 0) {
            itemUseCooldown--;
            return true;
        }

        if (player.isUsingItem()) {
            return true;
        }

        ItemStack held = player.getCurrentEquippedItem();
        boolean blockUsed = false;
        boolean itemUsed = false;

        if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            float hitX = 0.5F;
            float hitY = 0.5F;
            float hitZ = 0.5F;
            Vec3 hitVec = target.hitVec;

            if (hitVec != null) {
                hitX = (float) (hitVec.xCoord - target.blockX);
                hitY = (float) (hitVec.yCoord - target.blockY);
                hitZ = (float) (hitVec.zCoord - target.blockZ);
            }

            blockUsed = performBlockActivationUse(target, held, hitX, hitY, hitZ);
        }

        if (!blockUsed && held != null) {
            itemUsed = performDirectItemUse(held);
        }

        boolean bridgeUsed = performClientUseBridge(target, held, blockUsed, itemUsed);
        if (blockUsed || itemUsed || bridgeUsed) {
            itemUseCooldown = 3;
            return true;
        }

        performSwingAnimation();
        itemUseCooldown = 3;
        return true;
    }

    protected boolean performBlockActivationUse(MovingObjectPosition target, ItemStack held, float hitX, float hitY,
        float hitZ) {
        return player.theItemInWorldManager.activateBlockOrUseItem(
            player,
            player.worldObj,
            held,
            target.blockX,
            target.blockY,
            target.blockZ,
            target.sideHit,
            hitX,
            hitY,
            hitZ);
    }

    protected boolean performDirectItemUse(ItemStack held) {
        return player.theItemInWorldManager.tryUseItem(player, player.worldObj, held);
    }

    protected boolean performClientUseBridge(MovingObjectPosition target, ItemStack held, boolean blockUsed,
        boolean itemUsed) {
        return FakePlayerClientUseCompat.tryUse(player, held, target, blockUsed, itemUsed);
    }

    protected boolean performMovementCompatBridge(MovementTrigger trigger) {
        return FakePlayerMovementCompat.tryTrigger(player, trigger);
    }

    protected void performSwingAnimation() {
        player.swingItem();
        if (player instanceof FakePlayer fake) fake.broadcastSwingAnimation();
    }

    protected void performEntityAttack(Entity targetEntity) {
        EntityAttackState attackState = EntityAttackState.capture(targetEntity);
        player.attackTargetEntityWithCurrentItem(targetEntity);
        if (!attackState.hasObservableAttackEffect(targetEntity)) {
            performEntityAttackFallback(targetEntity);
        }
    }

    protected boolean performEntityAttackFallback(Entity targetEntity) {
        float fallbackDamage = resolveFallbackAttackDamage();
        if (fallbackDamage <= 0.0F) {
            return false;
        }
        DamageSource damageSource = DamageSource.causePlayerDamage(player);
        if (targetEntity instanceof EntityLivingBase livingTarget) {
            return forceLivingEntityDamage(livingTarget, damageSource, fallbackDamage, livingTarget.getHealth());
        }
        return targetEntity.attackEntityFrom(damageSource, fallbackDamage);
    }

    protected float resolveFallbackAttackDamage() {
        if (player == null || player.getEntityAttribute(SharedMonsterAttributes.attackDamage) == null) {
            return 1.0F;
        }
        return Math.max(1.0F, (float) player.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue());
    }

    protected boolean forceLivingEntityDamage(EntityLivingBase targetEntity, DamageSource damageSource, float damage,
        float previousHealth) {
        if (targetEntity == null || damage <= 0.0F || previousHealth <= 0.0F) {
            return false;
        }

        targetEntity.prevHealth = previousHealth;
        targetEntity.hurtResistantTime = targetEntity.maxHurtResistantTime;
        targetEntity.maxHurtTime = 10;
        targetEntity.hurtTime = targetEntity.maxHurtTime;
        targetEntity.velocityChanged = true;

        float updatedHealth = Math.max(0.0F, previousHealth - damage);
        targetEntity.setHealth(updatedHealth);
        if (updatedHealth <= 0.0F && !targetEntity.isDead) {
            targetEntity.onDeath(damageSource);
        }
        return updatedHealth < previousHealth;
    }

    protected boolean performAttack(MovingObjectPosition target) {
        if (target == null) {
            performSwingAnimation();
            return true;
        }

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity targetEntity = target.entityHit;
            if (targetEntity == null) {
                performSwingAnimation();
                return true;
            }

            performEntityAttack(targetEntity);
            performSwingAnimation();
            return true;
        }

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return attackBlock(target);
        }

        performSwingAnimation();
        return true;
    }

    private boolean executeAction(ActionType type) {
        switch (type) {
            case USE:
                return performUse(getTarget());
            case ATTACK:
                return performAttack(getTarget());
            case JUMP:
                if (performMovementCompatBridge(MovementTrigger.JUMP)) {
                    return true;
                }
                if (player.onGround) {
                    player.jump();
                } else {
                    player.setJumping(true);
                }
                return true;
            case DROP_ITEM:
                player.dropOneItem(false);
                return true;
            case DROP_STACK:
                player.dropOneItem(true);
                return true;
            default:
                return false;
        }
    }

    private boolean attackBlock(MovingObjectPosition target) {
        if (player.theItemInWorldManager == null || player.worldObj == null) {
            return false;
        }

        if (blockHitDelay > 0) {
            blockHitDelay--;
            return false;
        }

        int x = target.blockX;
        int y = target.blockY;
        int z = target.blockZ;
        Block block = player.worldObj.getBlock(x, y, z);

        if (block == null || block.isAir(player.worldObj, x, y, z)) {
            clearBlockBreakingState();
            return false;
        }

        if (isCreativeMode()) {
            player.theItemInWorldManager.onBlockClicked(x, y, z, target.sideHit);
            performSwingAnimation();
            blockHitDelay = 5;
            clearBlockBreakingState();
            return true;
        }

        ChunkCoordinates hitBlock = new ChunkCoordinates(x, y, z);
        float hardness = block.getPlayerRelativeBlockHardness(player, player.worldObj, x, y, z);

        if (!sameBlock(hitBlock)) {
            clearBlockBreakingState();
            player.theItemInWorldManager.onBlockClicked(x, y, z, target.sideHit);
            currentBlock = hitBlock;
            curBlockDamageMP = hardness;
        } else {
            curBlockDamageMP += hardness;
        }

        performSwingAnimation();

        if (curBlockDamageMP >= 1.0F) {
            boolean harvested = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
            blockHitDelay = 5;
            clearBlockBreakingState();
            return harvested;
        }

        player.worldObj
            .destroyBlockInWorldPartially(player.getEntityId(), x, y, z, Math.min(9, (int) (curBlockDamageMP * 10.0F)));
        return false;
    }

    private boolean sameBlock(ChunkCoordinates block) {
        return currentBlock != null && currentBlock.posX == block.posX
            && currentBlock.posY == block.posY
            && currentBlock.posZ == block.posZ;
    }

    private void cleanup(ActionType type) {
        switch (type) {
            case USE:
                itemUseCooldown = 0;
                player.clearItemInUse();
                break;
            case ATTACK:
                clearBlockBreakingState();
                blockHitDelay = 0;
                break;
            case JUMP:
                player.setJumping(false);
                break;
            default:
                break;
        }
    }

    private void clearBlockBreakingState() {
        if (currentBlock != null && player.worldObj != null) {
            player.worldObj.destroyBlockInWorldPartially(
                player.getEntityId(),
                currentBlock.posX,
                currentBlock.posY,
                currentBlock.posZ,
                -1);
        }
        currentBlock = null;
        curBlockDamageMP = 0.0F;
    }

    private boolean isCreativeMode() {
        return player.theItemInWorldManager != null && player.theItemInWorldManager.isCreative();
    }

    private static final class EntityAttackState {

        private final boolean dead;
        private final boolean velocityChanged;
        private final boolean burning;
        private final float health;
        private final int hurtTime;
        private final int hurtResistantTime;
        private final int deathTime;
        private final boolean living;

        private EntityAttackState(boolean dead, boolean velocityChanged, boolean burning, float health, int hurtTime,
            int hurtResistantTime, int deathTime, boolean living) {
            this.dead = dead;
            this.velocityChanged = velocityChanged;
            this.burning = burning;
            this.health = health;
            this.hurtTime = hurtTime;
            this.hurtResistantTime = hurtResistantTime;
            this.deathTime = deathTime;
            this.living = living;
        }

        private static EntityAttackState capture(Entity targetEntity) {
            if (targetEntity instanceof EntityLivingBase livingTarget) {
                return new EntityAttackState(
                    livingTarget.isDead,
                    livingTarget.velocityChanged,
                    livingTarget.isBurning(),
                    livingTarget.getHealth(),
                    livingTarget.hurtTime,
                    livingTarget.hurtResistantTime,
                    livingTarget.deathTime,
                    true);
            }
            return new EntityAttackState(
                targetEntity != null && targetEntity.isDead,
                targetEntity != null && targetEntity.velocityChanged,
                targetEntity != null && targetEntity.isBurning(),
                0.0F,
                0,
                0,
                0,
                false);
        }

        private boolean hasObservableAttackEffect(Entity targetEntity) {
            if (targetEntity == null) {
                return false;
            }
            if (!living) {
                return targetEntity.isDead != dead || targetEntity.velocityChanged != velocityChanged
                    || targetEntity.isBurning() != burning;
            }
            if (!(targetEntity instanceof EntityLivingBase livingTarget)) {
                return true;
            }
            return livingTarget.isDead != dead || livingTarget.getHealth() < health || livingTarget.deathTime > deathTime;
        }
    }
}
