package com.andgatech.gtstaff.integration;

import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

public final class ServerUtilitiesCompat {

    private ServerUtilitiesCompat() {}

    public static boolean isFakePlayer(EntityPlayerMP player) {
        return player instanceof FakePlayer || player instanceof GTstaffForgePlayer;
    }
}
