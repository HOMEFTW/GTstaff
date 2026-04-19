package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.andgatech.gtstaff.integration.ServerUtilitiesCompat;

@Pseudo
@Mixin(targets = "serverutils.lib.util.ServerUtils")
public abstract class ServerUtils_ServerUtilitiesMixin {

    @Inject(
        method = "isFake(Lnet/minecraft/entity/player/EntityPlayerMP;)Z",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private static void gtstaff$recognizeGtstaffFakePlayers(EntityPlayerMP player,
        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && ServerUtilitiesCompat.isFakePlayer(player)) {
            cir.setReturnValue(true);
        }
    }
}
