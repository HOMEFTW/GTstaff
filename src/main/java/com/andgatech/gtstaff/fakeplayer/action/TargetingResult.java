package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

public final class TargetingResult {

    private final MovingObjectPosition hit;

    public TargetingResult(MovingObjectPosition hit) {
        this.hit = hit;
    }

    public MovingObjectPosition hit() {
        return hit;
    }

    public boolean hitEntity() {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null;
    }

    public Entity entity() {
        return hitEntity() ? hit.entityHit : null;
    }
}
