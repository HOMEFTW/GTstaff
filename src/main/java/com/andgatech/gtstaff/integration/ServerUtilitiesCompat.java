package com.andgatech.gtstaff.integration;

import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public final class ServerUtilitiesCompat {

    private ServerUtilitiesCompat() {}

    public static boolean isFakePlayer(EntityPlayerMP player) {
        return player instanceof FakePlayer;
    }
}
