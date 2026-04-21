package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FakePlayerInventoryCompatTest {

    @Test
    void hidesUnknownBaublesSlotTypes() {
        assertFalse(FakePlayerInventoryCompat.isVisibleBaublesSlotType("unknown"));
        assertFalse(FakePlayerInventoryCompat.isVisibleBaublesSlotType(""));
        assertTrue(FakePlayerInventoryCompat.isVisibleBaublesSlotType("ring"));
    }
}
