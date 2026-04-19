package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SkinPortCompatTest {

    @AfterEach
    void clearBridge() {
        SkinPortCompat.setBridgeForTests(null);
        FakeMojangService.reset();
        Thread.interrupted();
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
    void rejectsProfilesWithoutTextureProperties() {
        GameProfile profileWithoutTextures = new GameProfile(UUID.randomUUID(), "test-player");
        SkinPortCompat.setBridgeForTests(name -> profileWithoutTextures);

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    @Test
    void availableBridgeThrowingExceptionReturnsEmptyProfile() {
        SkinPortCompat.setBridgeForTests(name -> {
            throw new IllegalStateException("bridge unavailable");
        });

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    @Test
    void reflectionBridgeResolvesFilledProfileFromListenableFuture() {
        GameProfile resolved = new GameProfile(UUID.randomUUID(), "test-player");
        GameProfile filled = createProfileWithTextures();
        FakeMojangService.resolvedProfile = resolved;
        FakeMojangService.filledProfile = filled;
        SkinPortCompat.setBridgeForTests(
            SkinPortCompat.createBridge(FakeMojangService.class.getName(), FakeMojangService.class.getClassLoader()));

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertTrue(profile.isPresent());
        assertSame(filled, profile.get());
        assertSame(resolved, FakeMojangService.receivedProfile);
    }

    @Test
    void reflectionBridgeFallsBackWhenRequiredMethodsAreMissing() {
        SkinPortCompat.setBridgeForTests(
            SkinPortCompat.createBridge(
                MissingFillProfileService.class.getName(),
                MissingFillProfileService.class.getClassLoader()));

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    @Test
    void reflectionBridgeRestoresInterruptFlagWhenFutureIsInterrupted() {
        try {
            SkinPortCompat.setBridgeForTests(
                SkinPortCompat.createBridge(
                    InterruptingMojangService.class.getName(),
                    InterruptingMojangService.class.getClassLoader()));

            Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

            assertFalse(profile.isPresent());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static GameProfile createProfileWithTextures() {
        GameProfile profile = new GameProfile(null, "test-player");
        profile.getProperties().put("textures", new Property("textures", "dummy"));
        return profile;
    }

    public static final class FakeMojangService {

        private static GameProfile resolvedProfile;
        private static GameProfile filledProfile;
        private static GameProfile receivedProfile;

        public static ListenableFuture<GameProfile> getProfile(String playerName) {
            return Futures.immediateFuture(resolvedProfile);
        }

        public static ListenableFuture<GameProfile> fillProfile(GameProfile profile) {
            receivedProfile = profile;
            return Futures.immediateFuture(filledProfile);
        }

        private static void reset() {
            resolvedProfile = null;
            filledProfile = null;
            receivedProfile = null;
        }
    }

    public static final class MissingFillProfileService {

        public static ListenableFuture<GameProfile> getProfile(String playerName) {
            return Futures.immediateFuture(null);
        }
    }

    public static final class InterruptingMojangService {

        public static ListenableFuture<GameProfile> getProfile(String playerName) {
            return new InterruptingFuture();
        }

        public static ListenableFuture<GameProfile> fillProfile(GameProfile profile) {
            return Futures.immediateFuture(profile);
        }
    }

    private static final class InterruptingFuture implements ListenableFuture<GameProfile> {

        @Override
        public void addListener(Runnable listener, java.util.concurrent.Executor executor) {}

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public GameProfile get() throws InterruptedException, ExecutionException {
            throw new InterruptedException("interrupted");
        }

        @Override
        public GameProfile get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
            throw new InterruptedException("interrupted");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }
}
