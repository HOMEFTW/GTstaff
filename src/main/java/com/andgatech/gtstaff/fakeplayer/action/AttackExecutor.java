package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

public class AttackExecutor {

    public AttackResult execute(MovingObjectPosition target) {
        if (target == null) {
            swing();
            return new AttackResult(true, false, true);
        }

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity targetEntity = target.entityHit;
            if (targetEntity == null) {
                swing();
                return new AttackResult(true, false, true);
            }

            boolean usedFallback = performEntityAttack(targetEntity);
            swing();
            return new AttackResult(true, usedFallback, true);
        }

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new AttackResult(performBlockAttack(target), false, false);
        }

        swing();
        return new AttackResult(true, false, true);
    }

    protected boolean performEntityAttack(Entity targetEntity) {
        return false;
    }

    protected boolean performBlockAttack(MovingObjectPosition target) {
        return false;
    }

    protected void swing() {}
}
