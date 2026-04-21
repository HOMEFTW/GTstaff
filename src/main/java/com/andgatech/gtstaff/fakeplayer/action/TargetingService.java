package com.andgatech.gtstaff.fakeplayer.action;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class TargetingService {

    private static final double SURVIVAL_REACH = 4.5D;
    private static final double CREATIVE_REACH = 5.0D;
    private static final double ATTACK_FALLBACK_FACING_DOT = 0.7071067811865476D;

    private final EntityPlayerMP player;

    public TargetingService(EntityPlayerMP player) {
        this.player = player;
    }

    @SuppressWarnings("unchecked")
    public TargetingResult resolve() {
        double reach = reachDistance();
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
            return new TargetingResult(new MovingObjectPosition(closestEntity, closestEntityHit));
        }
        return new TargetingResult(blockHit);
    }

    public TargetingResult resolveForAttack() {
        TargetingResult precise = resolve();
        if (precise.hitEntity()) {
            return precise;
        }

        Entity nearbyEntity = nearestAttackableEntity(reachDistance());
        if (nearbyEntity == null) {
            return precise;
        }
        return new TargetingResult(new MovingObjectPosition(nearbyEntity, centerOf(nearbyEntity)));
    }

    private double reachDistance() {
        return player.theItemInWorldManager != null && player.theItemInWorldManager.isCreative() ? CREATIVE_REACH
            : SURVIVAL_REACH;
    }

    @SuppressWarnings("unchecked")
    private Entity nearestAttackableEntity(double reach) {
        if (player == null || player.worldObj == null || player.boundingBox == null) {
            return null;
        }

        Entity closestEntity = null;
        double closestDistanceSq = reach * reach;
        Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 lookVec = player.getLookVec();
        List<Entity> entities = player.worldObj
            .getEntitiesWithinAABBExcludingEntity(player, player.boundingBox.expand(reach, 1.0D, reach));
        for (Entity entity : entities) {
            if (entity == null || entity.isDead || !entity.canBeCollidedWith()) {
                continue;
            }
            Vec3 targetCenter = centerOf(entity);
            if (!isFacingTarget(eyePos, lookVec, targetCenter)) {
                continue;
            }
            double distanceSq = distanceSq(eyePos, targetCenter);
            if (distanceSq <= closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closestEntity = entity;
            }
        }
        return closestEntity;
    }

    private boolean isFacingTarget(Vec3 eyePos, Vec3 lookVec, Vec3 targetCenter) {
        if (eyePos == null || lookVec == null || targetCenter == null) {
            return false;
        }
        double dx = targetCenter.xCoord - eyePos.xCoord;
        double dy = targetCenter.yCoord - eyePos.yCoord;
        double dz = targetCenter.zCoord - eyePos.zCoord;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0001D) {
            return true;
        }
        double dot = (dx / length) * lookVec.xCoord + (dy / length) * lookVec.yCoord + (dz / length) * lookVec.zCoord;
        return dot >= ATTACK_FALLBACK_FACING_DOT;
    }

    private double distanceSq(Vec3 first, Vec3 second) {
        double dx = first.xCoord - second.xCoord;
        double dy = first.yCoord - second.yCoord;
        double dz = first.zCoord - second.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    private Vec3 centerOf(Entity entity) {
        if (entity.boundingBox == null) {
            return Vec3.createVectorHelper(entity.posX, entity.posY + entity.height * 0.5D, entity.posZ);
        }
        return Vec3.createVectorHelper(
            (entity.boundingBox.minX + entity.boundingBox.maxX) * 0.5D,
            (entity.boundingBox.minY + entity.boundingBox.maxY) * 0.5D,
            (entity.boundingBox.minZ + entity.boundingBox.maxZ) * 0.5D);
    }
}
