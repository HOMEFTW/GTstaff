package com.andgatech.gtstaff.fakeplayer;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

import com.andgatech.gtstaff.fakeplayer.runtime.BotLifecycleManager;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.integration.ServerUtilitiesCompat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class FakePlayerRestoreScheduler {

    public static final FakePlayerRestoreScheduler INSTANCE = new FakePlayerRestoreScheduler();

    private static Function<MinecraftServer, List<BotRuntimeView>> restoreAction = server -> FakePlayerRegistry
        .restorePersistedRuntimes(data -> new BotLifecycleManager().restore(server, data));
    private static BiConsumer<MinecraftServer, BotRuntimeView> skinScheduleAction = FakePlayerSkinRestoreScheduler::schedule;
    private static MinecraftServer pendingServer;
    private static boolean registered;

    private FakePlayerRestoreScheduler() {}

    public static void register() {
        if (registered) {
            return;
        }

        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        registered = true;
    }

    public static void schedule(MinecraftServer server) {
        pendingServer = server;
    }

    public static void cancel() {
        pendingServer = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        runPendingRestore();
    }

    static void runPendingRestore() {
        MinecraftServer server = pendingServer;
        if (!isReady(server)) {
            return;
        }

        pendingServer = null;
        List<BotRuntimeView> restoredBots = restoreAction.apply(server);
        if (restoredBots == null) {
            restoredBots = Collections.emptyList();
        }
        for (BotRuntimeView runtime : restoredBots) {
            if (runtime != null) {
                skinScheduleAction.accept(server, runtime);
            }
        }
    }

    private static boolean isReady(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        if (server.isDedicatedServer()) {
            return true;
        }

        ServerConfigurationManager configurationManager = server.getConfigurationManager();
        if (configurationManager == null) {
            return false;
        }

        for (Object entry : configurationManager.playerEntityList) {
            if (entry instanceof EntityPlayerMP player && !ServerUtilitiesCompat.isFakePlayer(player)) {
                return true;
            }
        }
        return false;
    }

    static void setRestoreActionForTesting(Function<MinecraftServer, List<BotRuntimeView>> action) {
        restoreAction = action == null ? server -> FakePlayerRegistry.restorePersistedRuntimes(
            data -> new BotLifecycleManager().restore(server, data)) : action;
    }

    static void setSkinScheduleActionForTesting(BiConsumer<MinecraftServer, BotRuntimeView> action) {
        skinScheduleAction = action == null ? FakePlayerSkinRestoreScheduler::schedule : action;
    }

    static void resetForTesting() {
        pendingServer = null;
        restoreAction = server -> FakePlayerRegistry.restorePersistedRuntimes(data -> new BotLifecycleManager()
            .restore(server, data));
        skinScheduleAction = FakePlayerSkinRestoreScheduler::schedule;
        registered = false;
    }
}
