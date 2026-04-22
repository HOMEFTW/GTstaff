package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;
import com.andgatech.gtstaff.fakeplayer.PlayerVisualSync;
import com.andgatech.gtstaff.integration.BackhandCompat;
import com.mojang.authlib.GameProfile;

public class GTstaffForgePlayer extends FakePlayer implements IFakePlayerHolder, PlayerVisualSync {

    private NextGenBotRuntime runtime;
    private PlayerActionPack actionPack;
    private UUID ownerUUID;
    private boolean disconnected;

    public GTstaffForgePlayer(MinecraftServer server, WorldServer world, GameProfile profile) {
        super(world, profile);
        this.actionPack = new PlayerActionPack(this);
    }

    public void bindRuntime(NextGenBotRuntime runtime) {
        this.runtime = runtime;
    }

    public NextGenBotRuntime runtime() {
        return runtime;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void markDisconnected() {
        this.disconnected = true;
    }

    @Override
    public boolean isEntityInvulnerable() {
        return disconnected;
    }

    @Override
    public boolean canAttackPlayer(EntityPlayer player) {
        return !disconnected && player != this;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (disconnected) {
            return false;
        }
        if (this.capabilities == null || !this.capabilities.disableDamage) {
            return attackEntityFromWithVanillaRules(source, amount);
        }

        boolean previousDisableDamage = this.capabilities.disableDamage;
        this.capabilities.disableDamage = false;
        try {
            return attackEntityFromWithVanillaRules(source, amount);
        } finally {
            this.capabilities.disableDamage = previousDisableDamage;
        }
    }

    protected boolean attackEntityFromWithVanillaRules(DamageSource source, float amount) {
        return super.attackEntityFrom(source, amount);
    }

    public void respawnFake() {
        this.isDead = false;
        this.deathTime = 0;
        this.hurtTime = 0;
        this.hurtResistantTime = 0;
        this.recentlyHit = 0;
        this.fallDistance = 0.0F;
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.foodStats = new FoodStats();
        this.setHealth(this.getMaxHealth());
        this.clearItemInUse();
    }

    @Override
    public void onUpdate() {
        if (disconnected) {
            return;
        }
        if (this.isDead && this.deathTime > 0) {
            this.respawnFake();
        }
        runBaseUpdate();
        getActionPack().onUpdate();
        runRuntimeServicesPhase();
        runLivingUpdatePhase();
    }

    @Override
    public void onDeath(DamageSource source) {
        if (disconnected) {
            return;
        }
        super.onDeath(source);
        this.respawnFake();
    }

    protected void runBaseUpdate() {
        super.onUpdate();
    }

    protected void runRuntimeServicesPhase() {
        if (runtime != null) {
            runtime.tickRuntimeServices();
        }
    }

    protected void runLivingUpdatePhase() {
        this.onLivingUpdate();
    }

    @Override
    public PlayerActionPack getActionPack() {
        if (actionPack == null) {
            actionPack = new PlayerActionPack(this);
        }
        return actionPack;
    }

    @Override
    public void syncEquipmentToWatchers() {
        if (!(this.worldObj instanceof WorldServer world) || this.worldObj.isRemote || this.inventory == null) {
            return;
        }
        int entityId = this.getEntityId();
        for (Object watcherObj : world.playerEntities) {
            if (!(watcherObj instanceof net.minecraft.entity.player.EntityPlayerMP watcher) || watcher == this
                || watcher.playerNetServerHandler == null) {
                continue;
            }
            watcher.playerNetServerHandler
                .sendPacket(new S04PacketEntityEquipment(entityId, 0, this.inventory.getCurrentItem()));
            for (int index = 0; index < 4; index++) {
                watcher.playerNetServerHandler
                    .sendPacket(new S04PacketEntityEquipment(entityId, index + 1, this.inventory.armorInventory[index]));
            }
        }
        BackhandCompat.syncOffhandToWatchers(this);
    }

    @Override
    public void broadcastSwingAnimation() {
        if (!(this.worldObj instanceof WorldServer world) || this.worldObj.isRemote) {
            return;
        }
        S0BPacketAnimation packet = new S0BPacketAnimation(this, 0);
        for (Object watcherObj : world.playerEntities) {
            if (watcherObj instanceof net.minecraft.entity.player.EntityPlayerMP watcher && watcher != this
                && watcher.playerNetServerHandler != null) {
                watcher.playerNetServerHandler.sendPacket(packet);
            }
        }
    }
}
