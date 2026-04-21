package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldSettings;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.mojang.authlib.GameProfile;

public class BotLifecycleManager {

    private final NextGenBotFactory nextGenFactory;

    public BotLifecycleManager() {
        this(new NextGenBotFactory());
    }

    BotLifecycleManager(NextGenBotFactory nextGenFactory) {
        this.nextGenFactory = nextGenFactory;
    }

    public BotRuntimeView spawn(String botName, MinecraftServer server, ChunkCoordinates position, float yaw, float pitch,
        int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        BotRuntimeView runtime = runtimeMode().prefersNextGen()
            ? spawnNextGen(botName, server, position, yaw, pitch, dimension, gameType, flying, ownerUUID)
            : spawnLegacy(botName, server, position, yaw, pitch, dimension, gameType, flying, ownerUUID);
        if (runtime != null) {
            FakePlayerRegistry.registerRuntime(runtime);
        }
        return runtime;
    }

    public BotRuntimeView restore(MinecraftServer server, FakePlayerRegistry.PersistedBotData data) {
        if (data == null) {
            return null;
        }
        return restoreAsLegacy(data) ? restoreLegacy(server, data) : restoreNextGen(server, data);
    }

    public BotRuntimeView shadow(MinecraftServer server, EntityPlayerMP sourcePlayer) {
        if (sourcePlayer == null) {
            return null;
        }
        BotRuntimeView runtime = runtimeMode().prefersNextGen() ? shadowNextGen(server, sourcePlayer)
            : shadowLegacy(server, sourcePlayer);
        if (runtime != null) {
            FakePlayerRegistry.registerRuntime(runtime);
        }
        return runtime;
    }

    public boolean kill(String botName) {
        BotRuntimeView runtime = FakePlayerRegistry.getRuntimeView(botName);
        if (runtime == null) {
            return false;
        }
        EntityPlayerMP player = runtime.entity() == null ? null : runtime.entity().asPlayer();
        if (player instanceof FakePlayer fakePlayer) {
            if (fakePlayer.playerNetServerHandler instanceof com.andgatech.gtstaff.fakeplayer.FakeNetHandlerPlayServer) {
                fakePlayer.playerNetServerHandler.kickPlayerFromServer("You logged in from another location");
            } else {
                FakePlayerRegistry.unregister(fakePlayer.getCommandSenderName());
            }
            return true;
        }
        if (player instanceof GTstaffForgePlayer forgePlayer) {
            forgePlayer.markDisconnected();
        }
        if (player != null && player.mcServer != null && player.mcServer.getConfigurationManager() != null) {
            player.mcServer.getConfigurationManager()
                .playerLoggedOut(player);
        }
        if (player != null) {
            player.setDead();
        }
        FakePlayerRegistry.unregister(botName);
        return true;
    }

    public BotRuntimeView rebuildRestoredWithProfile(MinecraftServer server, BotRuntimeView runtime, GameProfile profile) {
        if (server == null || runtime == null || profile == null) {
            return runtime;
        }
        EntityPlayerMP player = runtime.entity() == null ? null : runtime.entity().asPlayer();
        if (player instanceof FakePlayer fakePlayer) {
            FakePlayer rebuilt = FakePlayer.rebuildRestoredWithProfile(server, fakePlayer, profile);
            return rebuilt == null ? runtime : rebuilt.asRuntimeView();
        }
        if (player instanceof GTstaffForgePlayer || runtime.runtimeType() == BotRuntimeType.NEXTGEN) {
            return nextGenFactory.rebuildRestoredWithProfile(server, runtime, profile);
        }
        return runtime;
    }

    protected BotRuntimeMode runtimeMode() {
        return BotRuntimeMode.fromConfig(Config.fakePlayerRuntimeMode);
    }

    protected BotRuntimeView spawnLegacy(String botName, MinecraftServer server, ChunkCoordinates position, float yaw,
        float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        FakePlayer fakePlayer = FakePlayer.createFake(botName, server, position, yaw, pitch, dimension, gameType, flying);
        if (fakePlayer == null) {
            return null;
        }
        if (ownerUUID != null) {
            fakePlayer.setOwnerUUID(ownerUUID);
            FakePlayerRegistry.register(fakePlayer, ownerUUID);
        }
        return fakePlayer.asRuntimeView();
    }

    protected BotRuntimeView spawnNextGen(String botName, MinecraftServer server, ChunkCoordinates position, float yaw,
        float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        return nextGenFactory.spawn(botName, server, position, yaw, pitch, dimension, gameType, flying, ownerUUID);
    }

    protected BotRuntimeView restoreLegacy(MinecraftServer server, FakePlayerRegistry.PersistedBotData data) {
        FakePlayer fakePlayer = FakePlayer.restorePersisted(server, data);
        if (fakePlayer == null) {
            return null;
        }
        fakePlayer.setOwnerUUID(data.getOwnerUUID());
        fakePlayer.setMonitoring(data.isMonitoring());
        fakePlayer.setMonitorRange(data.getMonitorRange());
        fakePlayer.setReminderInterval(data.getReminderInterval());
        fakePlayer.setMonsterRepelling(data.isMonsterRepelling());
        fakePlayer.setMonsterRepelRange(data.getMonsterRepelRange());
        if (data.getFollowTarget() != null) {
            fakePlayer.getFollowService()
                .setFollowRange(data.getFollowRange());
            fakePlayer.getFollowService()
                .setTeleportRange(data.getTeleportRange());
            fakePlayer.getFollowService()
                .startFollowing(data.getFollowTarget());
        }
        return fakePlayer.asRuntimeView();
    }

    protected BotRuntimeView restoreNextGen(MinecraftServer server, FakePlayerRegistry.PersistedBotData data) {
        BotRuntimeView runtime = nextGenFactory.restore(server, data);
        if (runtime == null) {
            return null;
        }
        if (runtime.monitor() != null) {
            runtime.monitor()
                .setMonitoring(data.isMonitoring());
            runtime.monitor()
                .setMonitorRange(data.getMonitorRange());
            runtime.monitor()
                .setReminderInterval(data.getReminderInterval());
        }
        if (runtime.repel() != null) {
            runtime.repel()
                .setRepelling(data.isMonsterRepelling());
            runtime.repel()
                .setRepelRange(data.getMonsterRepelRange());
        }
        if (runtime.follow() != null) {
            runtime.follow()
                .setFollowRange(data.getFollowRange());
            runtime.follow()
                .setTeleportRange(data.getTeleportRange());
            if (data.getFollowTarget() != null) {
                runtime.follow()
                    .startFollowing(data.getFollowTarget());
            }
        }
        return runtime;
    }

    protected BotRuntimeView shadowLegacy(MinecraftServer server, EntityPlayerMP sourcePlayer) {
        FakePlayer fakePlayer = FakePlayer.createShadow(server, sourcePlayer);
        return fakePlayer == null ? null : fakePlayer.asRuntimeView();
    }

    protected BotRuntimeView shadowNextGen(MinecraftServer server, EntityPlayerMP sourcePlayer) {
        return nextGenFactory.shadow(server, sourcePlayer);
    }

    private boolean restoreAsLegacy(FakePlayerRegistry.PersistedBotData data) {
        BotRuntimeMode mode = runtimeMode();
        return mode == BotRuntimeMode.LEGACY
            || (mode == BotRuntimeMode.MIXED && data.getRuntimeType() == BotRuntimeType.LEGACY);
    }
}
