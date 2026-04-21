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
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

import com.andgatech.gtstaff.fakeplayer.action.ActionDiagnostics;
import com.andgatech.gtstaff.fakeplayer.action.AttackExecutor;
import com.andgatech.gtstaff.fakeplayer.action.AttackResult;
import com.andgatech.gtstaff.fakeplayer.action.FeedbackSync;
import com.andgatech.gtstaff.fakeplayer.action.TargetingService;
import com.andgatech.gtstaff.fakeplayer.action.UseExecutor;
import com.andgatech.gtstaff.fakeplayer.action.UseResult;
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
        if (itemUseCooldown > 0) {
            itemUseCooldown--;
        }

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
        syncEquipmentToWatchers();
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
        return new TargetingService(player).resolve().hit();
    }

    protected MovingObjectPosition getAttackTarget() {
        return new TargetingService(player).resolveForAttack().hit();
    }

    protected boolean performUse(MovingObjectPosition target) {
        UseResult result = new UseExecutor(player, new FeedbackSync(player) {

            @Override
            public void swing() {
                performSwingAnimation();
            }
        }) {

            @Override
            protected boolean performBlockActivationUse(MovingObjectPosition target, ItemStack held, float hitX,
                float hitY, float hitZ) {
                return PlayerActionPack.this.performBlockActivationUse(target, held, hitX, hitY, hitZ);
            }

            @Override
            protected boolean performDirectItemUse(ItemStack held) {
                return PlayerActionPack.this.performDirectItemUse(held);
            }

            @Override
            protected boolean performClientUseBridge(MovingObjectPosition target, ItemStack held, boolean blockUsed,
                boolean itemUsed) {
                return PlayerActionPack.this.performClientUseBridge(target, held, blockUsed, itemUsed);
            }
        }.execute(target, itemUseCooldown);

        ActionDiagnostics.logUse(player, result);
        if (result.accepted()) {
            itemUseCooldown = 3;
            return true;
        }
        return false;
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
        if (player instanceof PlayerVisualSync visualSync) {
            visualSync.broadcastSwingAnimation();
        }
    }

    protected boolean performEntityAttack(Entity targetEntity) {
        EntityAttackState attackState = EntityAttackState.capture(targetEntity);
        player.attackTargetEntityWithCurrentItem(targetEntity);
        if (!attackState.hasObservableAttackEffect(targetEntity)) {
            return performEntityAttackFallback(targetEntity);
        }
        return false;
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
        AttackResult result = new AttackExecutor() {

            @Override
            protected boolean performEntityAttack(Entity targetEntity) {
                return PlayerActionPack.this.performEntityAttack(targetEntity);
            }

            @Override
            protected boolean performBlockAttack(MovingObjectPosition target) {
                return PlayerActionPack.this.attackBlock(target);
            }

            @Override
            protected void swing() {
                performSwingAnimation();
            }
        }.execute(target);

        ActionDiagnostics.logAttack(player, result);
        return result.accepted();
    }

    private boolean executeAction(ActionType type) {
        switch (type) {
            case USE:
                return performUse(getTarget());
            case ATTACK:
                return performAttack(getAttackTarget());
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
                return performDrop(false);
            case DROP_STACK:
                return performDrop(true);
            default:
                return false;
        }
    }

    private boolean performDrop(boolean dropAll) {
        player.dropOneItem(dropAll);
        syncEquipmentToWatchers();
        return true;
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

    private void syncEquipmentToWatchers() {
        if (player instanceof PlayerVisualSync visualSync) {
            visualSync.syncEquipmentToWatchers();
        }
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
