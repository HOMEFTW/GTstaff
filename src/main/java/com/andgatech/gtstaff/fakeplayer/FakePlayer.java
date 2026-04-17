package com.andgatech.gtstaff.fakeplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

public class FakePlayer extends EntityPlayerMP {

    private UUID ownerUUID;
    private boolean monitoring = false;
    private int monitorRange = 16;
    private final Map<BlockPos, MachineState> monitoredMachines = new HashMap<>();

    public FakePlayer(MinecraftServer server, WorldServer world, String username) {
        super(server, world, null);
    }

    public static FakePlayer createFake(String username, MinecraftServer server, BlockPos pos, float yaw, float pitch, int dimension, WorldSettings.GameType gamemode, boolean flying) {
        // TODO: implement full creation logic
        return null;
    }

    public static FakePlayer createShadow(MinecraftServer server, EntityPlayerMP player) {
        // TODO: implement shadow creation
        return null;
    }

    public void respawnFake() {
        // TODO: implement respawn logic
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
    }

    @Override
    public void kill() {
        super.kill();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public void setMonitoring(boolean monitoring) {
        this.monitoring = monitoring;
    }

    public int getMonitorRange() {
        return monitorRange;
    }

    public void setMonitorRange(int monitorRange) {
        this.monitorRange = monitorRange;
    }

    static class MachineState {
        boolean wasActive;
        boolean hadPower;
        boolean neededMaintenance;
        boolean wasOutputFull;
    }
}
