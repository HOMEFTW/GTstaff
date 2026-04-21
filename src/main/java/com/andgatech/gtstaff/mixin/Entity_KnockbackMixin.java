package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.andgatech.gtstaff.integration.ServerUtilitiesCompat;

@Mixin(EntityLivingBase.class)
public abstract class Entity_KnockbackMixin {

    @Inject(method = "knockBack", at = @At("RETURN"))
    private void gtstaff$markVelocityChanged(net.minecraft.entity.Entity entity, float strength, double xRatio,
        double zRatio, CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.entity.player.EntityPlayerMP player
            && ServerUtilitiesCompat.isFakePlayer(player)) {
            ((Entity) (Object) this).velocityChanged = true;
        }
    }
}
