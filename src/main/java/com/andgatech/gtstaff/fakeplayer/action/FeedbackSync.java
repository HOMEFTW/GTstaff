package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.fakeplayer.PlayerVisualSync;

public class FeedbackSync {

    private final EntityPlayerMP player;

    public FeedbackSync(EntityPlayerMP player) {
        this.player = player;
    }

    public void swing() {
        if (player == null) {
            return;
        }
        player.swingItem();
        if (player instanceof PlayerVisualSync visualSync) {
            visualSync.broadcastSwingAnimation();
        }
    }
}
