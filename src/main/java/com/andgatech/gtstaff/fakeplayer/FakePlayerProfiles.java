package com.andgatech.gtstaff.fakeplayer;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;

import com.andgatech.gtstaff.integration.SkinPortCompat;
import com.mojang.authlib.GameProfile;

final class FakePlayerProfiles {

    @FunctionalInterface
    interface ProfileResolver {

        Optional<GameProfile> resolve(String username);
    }

    private static final ProfileResolver DEFAULT_RESOLVER = SkinPortCompat::resolveProfile;

    private static volatile ProfileResolver resolver = DEFAULT_RESOLVER;

    private FakePlayerProfiles() {}

    static GameProfile createSpawnProfile(String username) {
        String safeUsername = username == null ? "" : username;
        Optional<GameProfile> resolvedProfile = resolver.resolve(safeUsername);
        if (resolvedProfile.isPresent()) {
            return copyOf(resolvedProfile.get());
        }
        return new GameProfile(EntityPlayer.func_146094_a(new GameProfile(null, safeUsername)), safeUsername);
    }

    static void setResolverForTests(ProfileResolver testResolver) {
        resolver = testResolver == null ? DEFAULT_RESOLVER : testResolver;
    }

    private static GameProfile copyOf(GameProfile profile) {
        GameProfile copy = new GameProfile(profile.getId(), profile.getName());
        copy.getProperties().putAll(profile.getProperties());
        return copy;
    }
}
