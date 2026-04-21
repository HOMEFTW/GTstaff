package com.andgatech.gtstaff.ui;

final class FakePlayerBaublesSlotLayout {

    private FakePlayerBaublesSlotLayout() {}

    static int resolveColumns(int visibleSlots, int maxColumns) {
        for (int columns = 1; columns < maxColumns; columns++) {
            if ((visibleSlots + columns - 1) / columns <= 8) {
                return columns;
            }
        }
        return maxColumns;
    }

    static boolean canScroll(int visibleSlots, int columns, int visibleRows) {
        return columns > 0 && visibleSlots > columns * visibleRows;
    }

    static int resolveHiddenRowCount(int visibleSlots, int columns, int visibleRows) {
        if (columns <= 0) {
            return 0;
        }
        int totalRows = (visibleSlots + columns - 1) / columns;
        return Math.max(0, totalRows - visibleRows);
    }

    static int resolveRowOffset(int visibleSlots, int columns, int visibleRows, float scrollOffset) {
        int maxOffset = resolveHiddenRowCount(visibleSlots, columns, visibleRows);
        int rowOffset = (int) (clamp(scrollOffset) * maxOffset + 0.5F);
        return Math.max(0, Math.min(maxOffset, rowOffset));
    }

    static int resolveDisplayY(int slotIndex, int columns, int rowOffset, int startY, int spacing, int hiddenY) {
        int row = slotIndex / columns;
        int scrolledRow = row - rowOffset;
        int y = startY + scrolledRow * spacing;
        if (scrolledRow < 0 || scrolledRow >= 8) {
            return hiddenY;
        }
        return y;
    }

    private static float clamp(float value) {
        if (value < 0F) {
            return 0F;
        }
        if (value > 1F) {
            return 1F;
        }
        return value;
    }
}
