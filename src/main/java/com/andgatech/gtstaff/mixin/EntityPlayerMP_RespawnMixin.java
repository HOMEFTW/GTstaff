package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMP_RespawnMixin {

    @Inject(method = "clonePlayer", at = @At("RETURN"))
    private void gtstaff$copyFakePlayerState(EntityPlayer player, boolean conqueredEnd, CallbackInfo ci) {
        RespawnMixinHooks.copyFakePlayerState(player, (EntityPlayerMP) (Object) this);
    }
}
