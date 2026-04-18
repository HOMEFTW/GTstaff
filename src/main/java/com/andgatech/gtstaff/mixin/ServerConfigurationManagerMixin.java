package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.mojang.authlib.GameProfile;

@Mixin(ServerConfigurationManager.class)
public abstract class ServerConfigurationManagerMixin {

    @Redirect(
        method = "respawnPlayer",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/WorldServer;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/management/ItemInWorldManager;)Lnet/minecraft/entity/player/EntityPlayerMP;"))
    private EntityPlayerMP gtstaff$respawnAsFakePlayer(MinecraftServer minecraftServer, WorldServer worldServer,
        GameProfile gameProfile, ItemInWorldManager itemInWorldManager, EntityPlayerMP player, int dimension,
        boolean conqueredEnd) {
        if (player instanceof FakePlayer) {
            return new FakePlayer(minecraftServer, worldServer, gameProfile);
        }

        return new EntityPlayerMP(minecraftServer, worldServer, gameProfile, itemInWorldManager);
    }
}
