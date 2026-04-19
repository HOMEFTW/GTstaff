package com.andgatech.gtstaff.integration;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;

public final class SkinPortCompat {

    private static final String MOJANG_SERVICE_CLASS = "lain.mods.skins.impl.MojangService";

    private static volatile Bridge bridge = createBridge(MOJANG_SERVICE_CLASS, SkinPortCompat.class.getClassLoader());

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
            GameProfile profile = bridge.resolveProfile(playerName);
            if (!hasTextures(profile)) {
                return Optional.empty();
            }
            return Optional.of(profile);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Bridge createBridge(String className, ClassLoader classLoader) {
        try {
            Class<?> serviceClass = Class.forName(className, true, classLoader);
            Method getProfileMethod = serviceClass.getMethod("getProfile", String.class);
            Method fillProfileMethod = serviceClass.getMethod("fillProfile", GameProfile.class);
            return playerName -> resolveFilledProfile(getProfileMethod, fillProfileMethod, playerName);
        } catch (ReflectiveOperationException e) {
            return unavailable();
        }
    }

    static void setBridgeForTests(Bridge bridge) {
        SkinPortCompat.bridge = bridge == null ? createBridge(MOJANG_SERVICE_CLASS, SkinPortCompat.class.getClassLoader())
            : bridge;
    }

    private static boolean hasTextures(GameProfile profile) {
        return profile != null && profile.getProperties() != null && !profile.getProperties().get("textures").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static GameProfile resolveFilledProfile(Method getProfileMethod, Method fillProfileMethod, String playerName) {
        try {
            GameProfile resolvedProfile = awaitProfile(getProfileMethod.invoke(null, playerName));
            if (resolvedProfile == null) {
                return null;
            }

            return awaitProfile(fillProfileMethod.invoke(null, resolvedProfile));
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
