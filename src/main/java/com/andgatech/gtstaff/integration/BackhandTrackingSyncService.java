package com.andgatech.gtstaff.integration;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public enum BackhandTrackingSyncService {

    INSTANCE;

    @SubscribeEvent
    public void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event == null) {
            return;
        }
        syncTrackedFakePlayer(event.entityPlayer, event.target);
    }

    void syncTrackedFakePlayer(EntityPlayer watcher, Entity target) {
        if (!(target instanceof GTstaffForgePlayer nextGenFakePlayer) || watcher == null || watcher == target) {
            return;
        }
        BackhandCompat.syncOffhandToPlayer(nextGenFakePlayer, watcher);
    }
}
