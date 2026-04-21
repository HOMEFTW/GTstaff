package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

public interface BotInventoryRuntime {

    int selectedHotbarSlot();

    BotInventorySummary summary();

    String openInventoryManager(EntityPlayerMP player) throws CommandException;
}
