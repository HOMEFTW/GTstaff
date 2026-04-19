package com.andgatech.gtstaff.integration;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import net.minecraft.server.MinecraftServer;

import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public final class SkinPortCompat {

    private static final String MOJANG_SERVICE_CLASS = "lain.mods.skins.impl.MojangService";

    @FunctionalInterface
    interface SecureFiller {

        GameProfile fill(GameProfile profile);
    }

    @FunctionalInterface
    interface FallbackResolver {

        GameProfile resolveProfile(String playerName);
    }

    private static final SecureFiller DEFAULT_SECURE_FILLER = SkinPortCompat::fillSecureProfile;
    private static final FallbackResolver DEFAULT_FALLBACK_RESOLVER = SkinPortCompat::resolveProfileFromServer;

    private static volatile Bridge bridge = createBridge(MOJANG_SERVICE_CLASS, SkinPortCompat.class.getClassLoader());
    private static volatile SecureFiller secureFiller = DEFAULT_SECURE_FILLER;
    private static volatile FallbackResolver fallbackResolver = DEFAULT_FALLBACK_RESOLVER;

    private SkinPortCompat() {}

    public interface Bridge {

        GameProfile resolveProfile(String playerName);
    }

    public static Bridge unavailable() {
        return playerName -> null;
    }

    public static Optional<GameProfile> resolveProfile(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            GameProfile resolvedProfile = resolveBaseProfile(playerName);
            if (resolvedProfile == null || resolvedProfile.getId() == null) {
                return Optional.empty();
            }

            GameProfile secureProfile = secureFiller.fill(copyWithoutProperties(resolvedProfile));
            if (!hasSignedTextures(secureProfile)) {
                return Optional.empty();
            }
            return Optional.of(secureProfile);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Bridge createBridge(String className, ClassLoader classLoader) {
        try {
            Class<?> serviceClass = Class.forName(className, true, classLoader);
            Method getProfileMethod = serviceClass.getMethod("getProfile", String.class);
            return playerName -> resolveProfile(getProfileMethod, playerName);
        } catch (ReflectiveOperationException e) {
            return unavailable();
        }
    }

    static void setBridgeForTests(Bridge bridge) {
        SkinPortCompat.bridge = bridge == null ? createBridge(MOJANG_SERVICE_CLASS, SkinPortCompat.class.getClassLoader())
            : bridge;
    }

    static void setSecureFillerForTests(SecureFiller filler) {
        secureFiller = filler == null ? DEFAULT_SECURE_FILLER : filler;
    }

    static void setFallbackResolverForTests(FallbackResolver resolver) {
        fallbackResolver = resolver == null ? DEFAULT_FALLBACK_RESOLVER : resolver;
    }

    private static GameProfile resolveBaseProfile(String playerName) {
        GameProfile profile = bridge.resolveProfile(playerName);
        if (profile != null && profile.getId() != null) {
            return profile;
        }
        return fallbackResolver.resolveProfile(playerName);
    }

    private static boolean hasSignedTextures(GameProfile profile) {
        if (profile == null || profile.getProperties() == null || profile.getProperties().get("textures").isEmpty()) {
            return false;
        }
        for (Property property : profile.getProperties().get("textures")) {
            if (property != null && property.hasSignature() && property.getValue() != null
                && !property.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static GameProfile copyWithoutProperties(GameProfile profile) {
        if (profile == null) {
            return null;
        }
        return new GameProfile(profile.getId(), profile.getName());
    }

    private static GameProfile fillSecureProfile(GameProfile profile) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || profile == null || profile.getId() == null) {
            return null;
        }
        return server.func_147130_as().fillProfileProperties(copyWithoutProperties(profile), true);
    }

    private static GameProfile resolveProfileFromServer(String playerName) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.func_152358_ax() == null) {
            return null;
        }
        return server.func_152358_ax().func_152655_a(playerName);
    }

    @SuppressWarnings("unchecked")
    private static GameProfile resolveProfile(Method getProfileMethod, String playerName) {
        try {
            return awaitProfile(getProfileMethod.invoke(null, playerName));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static GameProfile awaitProfile(Object futureObject) throws ExecutionException {
        if (!(futureObject instanceof ListenableFuture)) {
            return null;
        }

        try {
            return ((ListenableFuture<GameProfile>) futureObject).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
