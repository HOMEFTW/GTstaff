package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.andgatech.gtstaff.integration.FakePlayerClientUseCompat;

public class UseExecutor {

    private final EntityPlayerMP player;
    private final FeedbackSync feedbackSync;

    public UseExecutor(EntityPlayerMP player, FeedbackSync feedbackSync) {
        this.player = player;
        this.feedbackSync = feedbackSync;
    }

    public UseResult execute(MovingObjectPosition target, int itemUseCooldown) {
        if (player == null || player.theItemInWorldManager == null || player.worldObj == null) {
            return new UseResult(false, false, false, false);
        }
        if (itemUseCooldown > 0 || player.isUsingItem()) {
            return new UseResult(false, false, false, false);
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
            return new UseResult(blockUsed, itemUsed, bridgeUsed, false);
        }

        feedbackSync.swing();
        return new UseResult(false, false, false, true);
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
}
