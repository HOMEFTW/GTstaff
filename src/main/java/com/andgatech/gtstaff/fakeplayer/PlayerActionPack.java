package com.andgatech.gtstaff.fakeplayer;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class PlayerActionPack {

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
        Iterator<Map.Entry<ActionType, Action>> iterator = actions.entrySet().iterator();
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

            if (entry.getValue().tick()) {
                attempts.put(type, executeAction(type));
            }
        }

        player.moveStrafing = moveStrafing * (sneaking ? 0.3F : 1.0F);
        player.moveForward = moveForward * (sneaking ? 0.3F : 1.0F);
        player.setSneaking(sneaking);
        player.setSprinting(sprinting);
    }

    public void start(ActionType type, Action action) {
        if (actions.containsKey(type)) {
            cleanup(type);
        }
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
    }

    public void setForward(float value) {
        moveForward = value;
    }

    public void setStrafing(float value) {
        moveStrafing = value;
    }

    public void setSneaking(boolean value) {
        sneaking = value;
        if (value) {
            sprinting = false;
        }
    }

    public void setSprinting(boolean value) {
        sprinting = value;
        if (value) {
            sneaking = false;
        }
    }

    protected MovingObjectPosition getTarget() {
        double reach = isCreativeMode() ? 5.0D : 4.5D;
        return player.rayTrace(reach, 1.0F);
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

            if (player.theItemInWorldManager.activateBlockOrUseItem(player, player.worldObj, held, target.blockX, target.blockY, target.blockZ, target.sideHit, hitX, hitY, hitZ)) {
                itemUseCooldown = 3;
                return true;
            }
        }

        if (held != null && player.theItemInWorldManager.tryUseItem(player, player.worldObj, held)) {
            itemUseCooldown = 3;
            return true;
        }

        return false;
    }

    protected boolean performAttack(MovingObjectPosition target) {
        if (target == null) {
            return false;
        }

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity targetEntity = target.entityHit;
            if (targetEntity == null) {
                return false;
            }

            player.attackTargetEntityWithCurrentItem(targetEntity);
            player.swingItem();
            return true;
        }

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return attackBlock(target);
        }

        return false;
    }

    private boolean executeAction(ActionType type) {
        switch (type) {
            case USE:
                return performUse(getTarget());
            case ATTACK:
                return performAttack(getTarget());
            case JUMP:
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
            player.swingItem();
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

        player.swingItem();

        if (curBlockDamageMP >= 1.0F) {
            boolean harvested = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
            blockHitDelay = 5;
            clearBlockBreakingState();
            return harvested;
        }

        player.worldObj.destroyBlockInWorldPartially(player.getEntityId(), x, y, z, Math.min(9, (int) (curBlockDamageMP * 10.0F)));
        return false;
    }

    private boolean sameBlock(ChunkCoordinates block) {
        return currentBlock != null && currentBlock.posX == block.posX && currentBlock.posY == block.posY && currentBlock.posZ == block.posZ;
    }

    private void cleanup(ActionType type) {
        switch (type) {
            case USE:
                itemUseCooldown = 0;
                player.clearItemInUse();
                break;
            case ATTACK:
                clearBlockBreakingState();
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
            player.worldObj.destroyBlockInWorldPartially(player.getEntityId(), currentBlock.posX, currentBlock.posY, currentBlock.posZ, -1);
        }
        currentBlock = null;
        curBlockDamageMP = 0.0F;
    }

    private boolean isCreativeMode() {
        return player.theItemInWorldManager != null && player.theItemInWorldManager.isCreative();
    }
}
