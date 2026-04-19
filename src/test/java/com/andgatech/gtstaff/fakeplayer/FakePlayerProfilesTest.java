package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

class FakePlayerProfilesTest {

    @AfterEach
    void clearResolver() {
        FakePlayerProfiles.setResolverForTests(null);
    }

    @Test
    void usesSkinPortProfileWhenAvailable() {
        GameProfile filledProfile = new GameProfile(UUID.randomUUID(), "SkinBot");
        filledProfile.getProperties().put("textures", new Property("textures", "value", "signature"));
        FakePlayerProfiles.setResolverForTests(username -> Optional.of(filledProfile));

        GameProfile profile = FakePlayerProfiles.createSpawnProfile("SkinBot");

        assertNotSame(filledProfile, profile);
        assertEquals(filledProfile.getId(), profile.getId());
        assertEquals(filledProfile.getName(), profile.getName());
        assertEquals("value", profile.getProperties().get("textures").iterator().next().getValue());
    }

    @Test
    void fallsBackToOfflineProfileWhenSkinPortProfileIsUnavailable() {
        FakePlayerProfiles.setResolverForTests(username -> Optional.empty());

        GameProfile profile = FakePlayerProfiles.createSpawnProfile("FallbackBot");

        assertEquals("FallbackBot", profile.getName());
        assertEquals(EntityPlayer.func_146094_a(new GameProfile(null, "FallbackBot")), profile.getId());
        assertTrue(profile.getProperties().isEmpty());
    }

    @Test
    void resolveSkinProfileReturnsCopiedProfileWhenAvailable() {
        GameProfile filledProfile = new GameProfile(UUID.randomUUID(), "SkinBot");
        filledProfile.getProperties().put("textures", new Property("textures", "value", "signature"));
        FakePlayerProfiles.setResolverForTests(username -> Optional.of(filledProfile));

        Optional<GameProfile> resolved = FakePlayerProfiles.resolveSkinProfile("SkinBot");

        assertTrue(resolved.isPresent());
        assertNotSame(filledProfile, resolved.get());
        assertEquals("value", resolved.get().getProperties().get("textures").iterator().next().getValue());
    }
}
