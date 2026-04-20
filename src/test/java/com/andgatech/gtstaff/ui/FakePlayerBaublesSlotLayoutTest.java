package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FakePlayerBaublesSlotLayoutTest {

    @Test
    void computesColumnsAndScrollRowsForTallBaublesPanel() {
        assertEquals(3, FakePlayerBaublesSlotLayout.resolveColumns(20, 4));
        assertTrue(FakePlayerBaublesSlotLayout.canScroll(40, 4, 8));
        assertEquals(1, FakePlayerBaublesSlotLayout.resolveRowOffset(40, 4, 8, 0.5F));
    }

    @Test
    void hidesSlotsOutsideVisibleBaublesWindow() {
        int hiddenY = FakePlayerBaublesSlotLayout.resolveDisplayY(35, 4, 0, 12, 18, -2000);
        assertEquals(-2000, hiddenY);
    }
}
