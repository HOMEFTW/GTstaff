package com.andgatech.gtstaff.fakeplayer;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;

import com.andgatech.gtstaff.integration.SkinPortCompat;
import com.mojang.authlib.GameProfile;

public final class FakePlayerProfiles {

    @FunctionalInterface
    interface ProfileResolver {

        Optional<GameProfile> resolve(String username);
    }

    private static final ProfileResolver DEFAULT_RESOLVER = SkinPortCompat::resolveProfile;

    private static volatile ProfileResolver resolver = DEFAULT_RESOLVER;

    private FakePlayerProfiles() {}

    public static GameProfile createSpawnProfile(String username) {
        String safeUsername = username == null ? "" : username;
        Optional<GameProfile> resolvedProfile = resolveSkinProfile(safeUsername);
        if (resolvedProfile.isPresent()) {
            return resolvedProfile.get();
        }
        return new GameProfile(EntityPlayer.func_146094_a(new GameProfile(null, safeUsername)), safeUsername);
    }

    public static Optional<GameProfile> resolveSkinProfile(String username) {
        String safeUsername = username == null ? "" : username;
        Optional<GameProfile> resolvedProfile = resolver.resolve(safeUsername);
        if (!resolvedProfile.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(copyOf(resolvedProfile.get(), safeUsername));
    }

    static void setResolverForTests(ProfileResolver testResolver) {
        resolver = testResolver == null ? DEFAULT_RESOLVER : testResolver;
    }

    public static GameProfile copyOf(GameProfile profile) {
        return copyOf(profile, profile == null ? null : profile.getName());
    }

    public static GameProfile copyOf(GameProfile profile, String name) {
        if (profile == null) {
            return null;
        }
        GameProfile copy = new GameProfile(profile.getId(), name == null ? profile.getName() : name);
        copy.getProperties().putAll(profile.getProperties());
        return copy;
    }
}
