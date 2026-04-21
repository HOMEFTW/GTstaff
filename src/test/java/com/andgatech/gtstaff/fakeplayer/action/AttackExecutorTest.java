package com.andgatech.gtstaff.fakeplayer.action;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.junit.jupiter.api.Test;

class AttackExecutorTest {

    @Test
    void marksFallbackWhenEntityAttackReportsFallbackUsage() {
        AttackExecutor executor = new AttackExecutor() {

            @Override
            protected boolean performEntityAttack(Entity targetEntity) {
                return true;
            }
        };

        AttackResult result = executor.execute(
            new MovingObjectPosition(new TrackingEntity(), Vec3.createVectorHelper(0.0D, 0.0D, 0.0D)));

        assertTrue(result.accepted());
        assertTrue(result.usedFallback());
        assertTrue(result.swung());
    }

    private static final class TrackingEntity extends Entity {

        private TrackingEntity() {
            super((net.minecraft.world.World) null);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}
    }
}
