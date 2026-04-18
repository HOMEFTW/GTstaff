package com.andgatech.gtstaff.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMP_RespawnMixin {

    @Inject(method = "clonePlayer", at = @At("RETURN"))
    private void gtstaff$copyFakePlayerState(EntityPlayer player, boolean conqueredEnd, CallbackInfo ci) {
        if (!(player instanceof FakePlayer) || !((Object) this instanceof FakePlayer)) {
            return;
        }

        FakePlayer source = (FakePlayer) player;
        FakePlayer target = (FakePlayer) (Object) this;

        target.setOwnerUUID(source.getOwnerUUID());
        target.setMonitoring(source.isMonitoring());
        target.setMonitorRange(source.getMonitorRange());
        FakePlayerRegistry.register(target, source.getOwnerUUID());
    }
}
