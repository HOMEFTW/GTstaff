package com.andgatech.gtstaff.integration;

import java.util.Optional;

import com.mojang.authlib.GameProfile;

public final class SkinPortCompat {

    private static volatile Bridge bridge = unavailable();

    private SkinPortCompat() {}

    public interface Bridge {

        GameProfile resolveProfile(String playerName);
    }

    public static Bridge unavailable() {
        return playerName -> null;
    }

    public static Optional<GameProfile> resolveProfile(String playerName) {
        try {
            return Optional.ofNullable(bridge.resolveProfile(playerName));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static void setBridgeForTests(Bridge bridge) {
        SkinPortCompat.bridge = bridge == null ? unavailable() : bridge;
    }
}
