package com.andgatech.gtstaff.mixin;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces the body (renderYawOffset) to instantly follow the head (rotationYaw)
 * for remote players, fixing the vanilla issue where the body never fully
 * catches up when a player turns while standing still.
 */
@Mixin(EntityOtherPlayerMP.class)
public abstract class EntityOtherPlayerMP_BodyRotationMixin {

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void gtstaff$syncBodyRotation(CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        self.renderYawOffset = self.rotationYaw;
        self.prevRenderYawOffset = self.rotationYaw;
    }
}
