package com.andgatech.gtstaff.ui;

public final class FakePlayerBaublesSlotLayout {

    private FakePlayerBaublesSlotLayout() {}

    public static int resolveColumns(int visibleSlots, int maxColumns) {
        for (int columns = 1; columns < maxColumns; columns++) {
            if ((visibleSlots + columns - 1) / columns <= 8) {
                return columns;
            }
        }
        return maxColumns;
    }

    public static boolean canScroll(int visibleSlots, int columns, int visibleRows) {
        return columns > 0 && visibleSlots > columns * visibleRows;
    }

    public static int resolveRowOffset(int visibleSlots, int columns, int visibleRows, float scrollOffset) {
        int totalRows = (visibleSlots + columns - 1) / columns;
        int maxOffset = Math.max(0, totalRows - visibleRows);
        int rowOffset = (int) (Math.max(0F, Math.min(1F, scrollOffset)) * maxOffset + 0.5F);
        return Math.max(0, Math.min(maxOffset, rowOffset));
    }

    public static int resolveDisplayY(int slotIndex, int columns, int rowOffset, int startY, int spacing,
        int hiddenY) {
        int row = slotIndex / columns;
        int scrolledRow = row - rowOffset;
        int y = startY + scrolledRow * spacing;
        return y < startY || y > startY + spacing * 7 ? hiddenY : y;
    }
}
