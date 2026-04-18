package com.andgatech.gtstaff.fakeplayer;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class FakePlayerRestoreScheduler {

    public static final FakePlayerRestoreScheduler INSTANCE = new FakePlayerRestoreScheduler();

    private static Consumer<MinecraftServer> restoreAction = FakePlayerRegistry::restorePersisted;
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
        restoreAction.accept(server);
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

    static void setRestoreActionForTesting(Consumer<MinecraftServer> action) {
        restoreAction = action == null ? FakePlayerRegistry::restorePersisted : action;
    }

    static void resetForTesting() {
        pendingServer = null;
        restoreAction = FakePlayerRegistry::restorePersisted;
        registered = false;
    }
}
