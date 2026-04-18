package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;
import com.mojang.authlib.GameProfile;

@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMPMixin implements IFakePlayerHolder {

    @Unique
    private PlayerActionPack actionPack;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtstaff$initActionPack(MinecraftServer server, WorldServer world, GameProfile profile,
        ItemInWorldManager itemInWorldManager, CallbackInfo ci) {
        this.actionPack = new PlayerActionPack((EntityPlayerMP) (Object) this);
    }

    @Override
    public PlayerActionPack getActionPack() {
        return this.actionPack;
    }
}
