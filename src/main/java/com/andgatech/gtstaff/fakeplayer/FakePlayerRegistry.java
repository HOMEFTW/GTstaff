package com.andgatech.gtstaff.fakeplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

public class FakePlayerRegistry {

    private static final Map<String, FakePlayer> fakePlayers = new HashMap<>();
    private static final Map<UUID, String> ownerToBotName = new HashMap<>();

    public static void register(FakePlayer fakePlayer, UUID ownerUUID) {
        fakePlayers.put(fakePlayer.getCommandSenderName(), fakePlayer);
        ownerToBotName.put(ownerUUID, fakePlayer.getCommandSenderName());
    }

    public static void unregister(String name) {
        fakePlayers.remove(name);
    }

    public static FakePlayer getFakePlayer(String name) {
        return fakePlayers.get(name);
    }

    public static Map<String, FakePlayer> getAll() {
        return fakePlayers;
    }

    public static int getCount() {
        return fakePlayers.size();
    }

    public static int getCountByOwner(UUID ownerUUID) {
        return (int) fakePlayers.values()
            .stream()
            .filter(fp -> fp.getOwnerUUID() != null && fp.getOwnerUUID().equals(ownerUUID))
            .count();
    }
}
