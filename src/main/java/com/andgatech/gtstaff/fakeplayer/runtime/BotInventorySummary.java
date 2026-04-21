package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.List;

public final class BotInventorySummary {

    private final String botName;
    private final int selectedHotbarSlot;
    private final List<String> hotbarLines;
    private final List<String> mainInventoryLines;
    private final List<String> armorLines;

    public BotInventorySummary(String botName, int selectedHotbarSlot, List<String> hotbarLines,
        List<String> mainInventoryLines, List<String> armorLines) {
        this.botName = botName;
        this.selectedHotbarSlot = selectedHotbarSlot;
        this.hotbarLines = hotbarLines;
        this.mainInventoryLines = mainInventoryLines;
        this.armorLines = armorLines;
    }

    public String botName() {
        return botName;
    }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public List<String> hotbarLines() {
        return hotbarLines;
    }

    public List<String> mainInventoryLines() {
        return mainInventoryLines;
    }

    public List<String> armorLines() {
        return armorLines;
    }
}
