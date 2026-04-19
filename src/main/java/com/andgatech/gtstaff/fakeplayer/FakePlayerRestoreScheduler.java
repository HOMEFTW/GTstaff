package com.andgatech.gtstaff.fakeplayer;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class FakePlayerRestoreScheduler {

    public static final FakePlayerRestoreScheduler INSTANCE = new FakePlayerRestoreScheduler();

    private static Function<MinecraftServer, List<FakePlayer>> restoreAction = FakePlayerRegistry::restorePersisted;
    private static BiConsumer<MinecraftServer, FakePlayer> skinScheduleAction = FakePlayerSkinRestoreScheduler::schedule;
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
        List<FakePlayer> restoredBots = restoreAction.apply(server);
        if (restoredBots == null) {
            restoredBots = Collections.emptyList();
        }
        for (FakePlayer fakePlayer : restoredBots) {
            if (fakePlayer != null) {
                skinScheduleAction.accept(server, fakePlayer);
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
            if (entry instanceof EntityPlayerMP && !(entry instanceof FakePlayer)) {
                return true;
            }
        }
        return false;
    }

    static void setRestoreActionForTesting(Function<MinecraftServer, List<FakePlayer>> action) {
        restoreAction = action == null ? FakePlayerRegistry::restorePersisted : action;
    }

    static void setSkinScheduleActionForTesting(BiConsumer<MinecraftServer, FakePlayer> action) {
        skinScheduleAction = action == null ? FakePlayerSkinRestoreScheduler::schedule : action;
    }

    static void resetForTesting() {
        pendingServer = null;
        restoreAction = FakePlayerRegistry::restorePersisted;
        skinScheduleAction = FakePlayerSkinRestoreScheduler::schedule;
        registered = false;
    }
}
