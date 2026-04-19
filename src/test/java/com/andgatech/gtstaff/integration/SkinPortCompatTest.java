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
        SkinPortCompat.setSecureFillerForTests(null);
        SkinPortCompat.setFallbackResolverForTests(null);
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
    void resolveProfileUsesSecurelyFilledProfile() {
        GameProfile resolved = new GameProfile(UUID.randomUUID(), "test-player");
        GameProfile signed = createProfileWithSignedTextures();
        SkinPortCompat.setBridgeForTests(name -> resolved);
        SkinPortCompat.setSecureFillerForTests(profile -> signed);

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertTrue(profile.isPresent());
        assertSame(signed, profile.get());
    }

    @Test
    void rejectsSecureFillResultsWithoutSignedTextures() {
        GameProfile resolved = new GameProfile(UUID.randomUUID(), "test-player");
        GameProfile unsigned = new GameProfile(resolved.getId(), "test-player");
        unsigned.getProperties().put("textures", new Property("textures", "dummy"));
        SkinPortCompat.setBridgeForTests(name -> resolved);
        SkinPortCompat.setSecureFillerForTests(profile -> unsigned);

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertFalse(profile.isPresent());
    }

    @Test
    void fallsBackToServerResolverWhenBridgeIsUnavailable() {
        GameProfile resolved = new GameProfile(UUID.randomUUID(), "test-player");
        GameProfile signed = createProfileWithSignedTextures();
        SkinPortCompat.setBridgeForTests(SkinPortCompat.unavailable());
        SkinPortCompat.setFallbackResolverForTests(name -> resolved);
        SkinPortCompat.setSecureFillerForTests(profile -> signed);

        Optional<GameProfile> profile = SkinPortCompat.resolveProfile("test-player");

        assertTrue(profile.isPresent());
        assertSame(signed, profile.get());
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
    void reflectionBridgeResolvesProfileFromListenableFuture() {
        GameProfile resolved = new GameProfile(UUID.randomUUID(), "test-player");
        FakeMojangService.resolvedProfile = resolved;
        SkinPortCompat.setBridgeForTests(
            SkinPortCompat.createBridge(FakeMojangService.class.getName(), FakeMojangService.class.getClassLoader()));

        GameProfile profile = SkinPortCompat.createBridge(
            FakeMojangService.class.getName(),
            FakeMojangService.class.getClassLoader()).resolveProfile("test-player");

        assertSame(resolved, profile);
    }

    @Test
    void reflectionBridgeFallsBackWhenRequiredMethodsAreMissing() {
        SkinPortCompat.setBridgeForTests(
            SkinPortCompat.createBridge(
                MissingGetProfileService.class.getName(),
                MissingGetProfileService.class.getClassLoader()));

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

    private static GameProfile createProfileWithSignedTextures() {
        GameProfile profile = new GameProfile(null, "test-player");
        profile.getProperties().put("textures", new Property("textures", "dummy", "signature"));
        return profile;
    }

    public static final class FakeMojangService {

        private static GameProfile resolvedProfile;

        public static ListenableFuture<GameProfile> getProfile(String playerName) {
            return Futures.immediateFuture(resolvedProfile);
        }

        private static void reset() {
            resolvedProfile = null;
        }
    }

    public static final class MissingGetProfileService {

        public static ListenableFuture<GameProfile> fillProfile(GameProfile profile) {
            return Futures.immediateFuture(null);
        }
    }

    public static final class InterruptingMojangService {

        public static ListenableFuture<GameProfile> getProfile(String playerName) {
            return new InterruptingFuture();
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
