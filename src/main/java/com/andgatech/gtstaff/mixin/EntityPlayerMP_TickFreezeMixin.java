package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMP_TickFreezeMixin {

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void gtstaff$tickFreezeHook(CallbackInfo ci) {
        // 1.7.10 has no built-in tick-freeze path; keep this as a safe extension point.
    }
}
