package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SkinPortCompatTest {

    @AfterEach
    void clearBridge() {
        SkinPortCompat.setBridgeForTests(SkinPortCompat.unavailable());
    }

    @Test
    void unavailableBridgeReturnsEmptyProfile() {
        SkinPortCompat.setBridgeForTests(SkinPortCompat.unavailable());

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    @Test
    void availableBridgeReturningNullReturnsEmptyProfile() {
        SkinPortCompat.setBridgeForTests(name -> null);

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    @Test
    void availableBridgeReturningProfileWithTexturesReturnsProfile() {
        GameProfile expected = createProfileWithTextures();
        SkinPortCompat.setBridgeForTests(name -> expected);

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertTrue(profile.isPresent());
        assertSame(expected, profile.get());
    }

    @Test
    void availableBridgeThrowingExceptionReturnsEmptyProfile() {
        SkinPortCompat.setBridgeForTests(name -> {
            throw new IllegalStateException("bridge unavailable");
        });

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    private static GameProfile createProfileWithTextures() {
        GameProfile profile = new GameProfile(null, "test-player");
        profile.getProperties().put("textures", new Property("textures", "dummy"));
        return profile;
    }
}
