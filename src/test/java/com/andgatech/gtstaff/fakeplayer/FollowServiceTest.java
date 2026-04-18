package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FollowServiceTest {

    @Test
    void calculateMoveForward_towardsSouthYawZero() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 10.0, 20.0);
        assertEquals(1.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMoveForward_towardsEastYawZero() {
        // Target is east (+X=20), fake at X=10 facing south (yaw=0)
        // targetYaw = atan2(-10, 0) = -90, yawDiff = -90
        // moveForward = cos(-90°) ≈ 0, moveStrafing = sin(-90°) = -1.0
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 20.0, 10.0);
        assertEquals(0.0F, result[0], 0.01F, "moveForward");
        assertEquals(-1.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMoveForward_towardsNorthYaw180() {
        float[] result = FollowService.calculateMovement(180.0F, 10.0, 10.0, 10.0, 0.0);
        assertEquals(1.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMovement_diagonal() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 15.0, 15.0);
        float expectedComponent = (float) (Math.sqrt(2) / 2.0);
        assertEquals(expectedComponent, result[0], 0.01F, "moveForward");
        assertEquals(-expectedComponent, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMovement_samePosition_returnsZero() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 10.0, 10.0);
        assertEquals(0.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void normalizeYawDiff_wrapsCorrectly() {
        assertEquals(0.0F, FollowService.normalizeYawDiff(0.0F), 0.01F);
        assertEquals(90.0F, FollowService.normalizeYawDiff(90.0F), 0.01F);
        assertEquals(-90.0F, FollowService.normalizeYawDiff(270.0F), 0.01F);
        assertEquals(-180.0F, FollowService.normalizeYawDiff(-180.0F), 0.01F);
        assertEquals(-170.0F, FollowService.normalizeYawDiff(190.0F), 0.01F);
    }

    @Test
    void shouldJump_targetAboveThreshold() {
        assertTrue(FollowService.shouldJump(5.0, 8.0, true));
    }

    @Test
    void shouldNotJump_targetWithinThreshold() {
        assertFalse(FollowService.shouldJump(5.0, 5.3, true));
    }

    @Test
    void shouldDescend_targetBelowThreshold() {
        assertTrue(FollowService.shouldDescend(5.0, 3.0, true));
    }

    @Test
    void shouldNotDescend_notFlying() {
        assertFalse(FollowService.shouldDescend(5.0, 3.0, false));
    }

    @Test
    void defaultFollowRange() {
        assertEquals(3, FollowService.DEFAULT_FOLLOW_RANGE);
    }

    @Test
    void defaultTeleportRange() {
        assertEquals(32, FollowService.DEFAULT_TELEPORT_RANGE);
    }

    @Test
    void crossDimensionDelay() {
        assertEquals(100, FollowService.CROSS_DIM_DELAY_TICKS);
    }
}
