package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.BotSession;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;
import com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntime;
import com.mojang.authlib.GameProfile;

final class RespawnMixinHooks {

    @FunctionalInterface
    interface RespawnFactory {

        EntityPlayerMP create(MinecraftServer minecraftServer, WorldServer worldServer, GameProfile gameProfile,
            ItemInWorldManager itemInWorldManager, EntityPlayerMP sourcePlayer);
    }

    private static final RespawnFactory DEFAULT_NEXTGEN_RESPAWN_FACTORY =
        (minecraftServer, worldServer, gameProfile, itemInWorldManager, sourcePlayer) -> {
            GTstaffForgePlayer nextGenPlayer = (GTstaffForgePlayer) sourcePlayer;
            GTstaffForgePlayer respawned = new GTstaffForgePlayer(minecraftServer, worldServer, gameProfile);
            respawned.setOwnerUUID(nextGenPlayer.getOwnerUUID());
            new NextGenBotRuntime(respawned, new BotSession(respawned), nextGenPlayer.getOwnerUUID());
            return respawned;
        };

    private static RespawnFactory nextGenRespawnFactory = DEFAULT_NEXTGEN_RESPAWN_FACTORY;

    private RespawnMixinHooks() {}

    static EntityPlayerMP createRespawnPlayer(MinecraftServer minecraftServer, WorldServer worldServer,
        GameProfile gameProfile, ItemInWorldManager itemInWorldManager, EntityPlayerMP player) {
        if (player instanceof GTstaffForgePlayer) {
            return nextGenRespawnFactory.create(minecraftServer, worldServer, gameProfile, itemInWorldManager, player);
        }
        if (player instanceof FakePlayer) {
            return new FakePlayer(minecraftServer, worldServer, gameProfile);
        }
        return new EntityPlayerMP(minecraftServer, worldServer, gameProfile, itemInWorldManager);
    }

    static void setNextGenRespawnFactoryForTesting(RespawnFactory factory) {
        nextGenRespawnFactory = factory == null ? DEFAULT_NEXTGEN_RESPAWN_FACTORY : factory;
    }

    static void copyFakePlayerState(EntityPlayer player, EntityPlayerMP respawnedPlayer) {
        if (player instanceof FakePlayer && respawnedPlayer instanceof FakePlayer) {
            FakePlayer source = (FakePlayer) player;
            FakePlayer target = (FakePlayer) respawnedPlayer;

            target.setOwnerUUID(source.getOwnerUUID());
            target.setMonitoring(source.isMonitoring());
            target.setMonitorRange(source.getMonitorRange());
            FakePlayerRegistry.register(target, source.getOwnerUUID());
            return;
        }

        if (!(player instanceof GTstaffForgePlayer) || !(respawnedPlayer instanceof GTstaffForgePlayer)) {
            return;
        }

        GTstaffForgePlayer source = (GTstaffForgePlayer) player;
        GTstaffForgePlayer target = (GTstaffForgePlayer) respawnedPlayer;
        target.setOwnerUUID(source.getOwnerUUID());

        NextGenBotRuntime targetRuntime = target.runtime();
        if (targetRuntime == null) {
            targetRuntime = new NextGenBotRuntime(target, new BotSession(target), source.getOwnerUUID());
        }

        copyNextGenRuntimeState(source.runtime(), targetRuntime);
        FakePlayerRegistry.registerRuntime(targetRuntime);
    }

    private static void copyNextGenRuntimeState(BotRuntimeView sourceRuntime, BotRuntimeView targetRuntime) {
        if (sourceRuntime == null || targetRuntime == null) {
            return;
        }

        BotMonitorRuntime sourceMonitor = sourceRuntime.monitor();
        BotMonitorRuntime targetMonitor = targetRuntime.monitor();
        if (sourceMonitor != null && targetMonitor != null) {
            targetMonitor.setMonitoring(sourceMonitor.monitoring());
            targetMonitor.setMonitorRange(sourceMonitor.monitorRange());
            targetMonitor.setReminderInterval(sourceMonitor.reminderInterval());
        }

        BotRepelRuntime sourceRepel = sourceRuntime.repel();
        BotRepelRuntime targetRepel = targetRuntime.repel();
        if (sourceRepel != null && targetRepel != null) {
            targetRepel.setRepelling(sourceRepel.repelling());
            targetRepel.setRepelRange(sourceRepel.repelRange());
        }

        BotFollowRuntime sourceFollow = sourceRuntime.follow();
        BotFollowRuntime targetFollow = targetRuntime.follow();
        if (sourceFollow != null && targetFollow != null) {
            targetFollow.setFollowRange(sourceFollow.followRange());
            targetFollow.setTeleportRange(sourceFollow.teleportRange());
            if (sourceFollow.targetUUID() != null) {
                targetFollow.startFollowing(sourceFollow.targetUUID());
            } else {
                targetFollow.stop();
            }
        }
    }
}
