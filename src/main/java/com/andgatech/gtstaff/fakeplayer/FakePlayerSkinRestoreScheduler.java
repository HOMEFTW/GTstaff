package com.andgatech.gtstaff.fakeplayer;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.mojang.authlib.GameProfile;

public final class FakePlayerSkinRestoreScheduler {

    @FunctionalInterface
    interface Resolver {

        Optional<GameProfile> resolve(String botName);
    }

    @FunctionalInterface
    interface RebuildAction {

        FakePlayer rebuild(MinecraftServer server, FakePlayer oldBot, GameProfile profile);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GTstaff-FakePlayerSkinRestore");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final Consumer<Runnable> DEFAULT_ASYNC_EXECUTOR = EXECUTOR::execute;
    private static final Consumer<Runnable> DEFAULT_MAIN_THREAD_EXECUTOR = ServerThreadUtil::addScheduledTask;
    private static final Resolver DEFAULT_RESOLVER = FakePlayerProfiles::resolveSkinProfile;
    private static final RebuildAction DEFAULT_REBUILD_ACTION = FakePlayer::rebuildRestoredWithProfile;

    private static final AtomicInteger generation = new AtomicInteger();

    private static volatile Consumer<Runnable> asyncExecutor = DEFAULT_ASYNC_EXECUTOR;
    private static volatile Consumer<Runnable> mainThreadExecutor = DEFAULT_MAIN_THREAD_EXECUTOR;
    private static volatile Resolver resolver = DEFAULT_RESOLVER;
    private static volatile RebuildAction rebuildAction = DEFAULT_REBUILD_ACTION;

    private FakePlayerSkinRestoreScheduler() {}

    public static void schedule(MinecraftServer server, FakePlayer fakePlayer) {
        if (server == null || fakePlayer == null) {
            return;
        }

        String botName = fakePlayer.getCommandSenderName();
        if (botName == null || botName.trim().isEmpty()) {
            return;
        }

        int scheduledGeneration = generation.get();
        asyncExecutor.accept(() -> resolveAndQueueRebuild(server, fakePlayer, botName, scheduledGeneration));
    }

    public static void cancelAll() {
        generation.incrementAndGet();
    }

    private static void resolveAndQueueRebuild(MinecraftServer server, FakePlayer fakePlayer, String botName,
        int scheduledGeneration) {
        if (generation.get() != scheduledGeneration) {
            return;
        }

        Optional<GameProfile> resolved = resolver.resolve(botName);
        if (!resolved.isPresent()) {
            return;
        }

        GameProfile profile = resolved.get();
        mainThreadExecutor.accept(() -> {
            if (generation.get() != scheduledGeneration) {
                return;
            }
            rebuildAction.rebuild(server, fakePlayer, profile);
        });
    }

    static void setAsyncExecutorForTests(Consumer<Runnable> executor) {
        asyncExecutor = executor == null ? DEFAULT_ASYNC_EXECUTOR : executor;
    }

    static void setMainThreadExecutorForTests(Consumer<Runnable> executor) {
        mainThreadExecutor = executor == null ? DEFAULT_MAIN_THREAD_EXECUTOR : executor;
    }

    static void setResolverForTests(Resolver testResolver) {
        resolver = testResolver == null ? DEFAULT_RESOLVER : testResolver;
    }

    static void setRebuildActionForTests(RebuildAction action) {
        rebuildAction = action == null ? DEFAULT_REBUILD_ACTION : action;
    }

    static void resetForTests() {
        cancelAll();
        asyncExecutor = DEFAULT_ASYNC_EXECUTOR;
        mainThreadExecutor = DEFAULT_MAIN_THREAD_EXECUTOR;
        resolver = DEFAULT_RESOLVER;
        rebuildAction = DEFAULT_REBUILD_ACTION;
    }
}
