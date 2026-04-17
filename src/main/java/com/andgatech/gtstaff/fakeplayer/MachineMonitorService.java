package com.andgatech.gtstaff.fakeplayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

public class MachineMonitorService {

    private static final int SCAN_INTERVAL = 60;

    private final Map<ChunkCoordinates, MachineState> machineStates = new HashMap<ChunkCoordinates, MachineState>();
    private boolean monitoring;
    private int monitorRange = 16;

    public boolean isMonitoring() {
        return this.monitoring;
    }

    public void setMonitoring(boolean monitoring) {
        this.monitoring = monitoring;
    }

    public int getMonitorRange() {
        return this.monitorRange;
    }

    public void setMonitorRange(int monitorRange) {
        this.monitorRange = Math.max(1, monitorRange);
    }

    public Map<ChunkCoordinates, MachineState> getMachineStates() {
        return this.machineStates;
    }

    public MachineState getMachineState(ChunkCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }

        return this.machineStates.get(coordinates);
    }

    public void recordMachineState(ChunkCoordinates coordinates, MachineState state) {
        if (coordinates == null || state == null) {
            return;
        }

        this.machineStates.put(copyCoordinates(coordinates), state.copy());
    }

    public void removeMachineState(ChunkCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }

        this.machineStates.remove(coordinates);
    }

    public void clear() {
        this.machineStates.clear();
    }

    public boolean shouldScan(int ticksExisted) {
        return this.monitoring && ticksExisted > 0 && ticksExisted % SCAN_INTERVAL == 0;
    }

    public List<String> applyLatestState(String botName, Map<ChunkCoordinates, MachineState> latestStates) {
        Map<ChunkCoordinates, MachineState> safeStates = latestStates == null ? Collections.<ChunkCoordinates, MachineState>emptyMap() : latestStates;
        List<String> messages = new ArrayList<String>();

        for (Map.Entry<ChunkCoordinates, MachineState> entry : safeStates.entrySet()) {
            ChunkCoordinates coordinates = entry.getKey();
            MachineState nextState = entry.getValue();
            if (coordinates == null || nextState == null) {
                continue;
            }

            MachineState previousState = this.machineStates.get(coordinates);
            if (previousState != null) {
                if (previousState.isPowered() && !nextState.isPowered()) {
                    messages.add(buildMessage(botName, coordinates, "lost power"));
                }
                if (!previousState.isMaintenanceRequired() && nextState.isMaintenanceRequired()) {
                    messages.add(buildMessage(botName, coordinates, "needs maintenance"));
                }
                if (!previousState.isOutputFull() && nextState.isOutputFull()) {
                    messages.add(buildMessage(botName, coordinates, "output is full"));
                }
                if (previousState.hasProblems() && !nextState.hasProblems()) {
                    messages.add(buildMessage(botName, coordinates, "recovered"));
                }
            }
        }

        this.machineStates.clear();
        for (Map.Entry<ChunkCoordinates, MachineState> entry : safeStates.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                this.machineStates.put(copyCoordinates(entry.getKey()), entry.getValue().copy());
            }
        }
        return messages;
    }

    public void tick(FakePlayer fakePlayer) {
        if (fakePlayer == null || !shouldScan(fakePlayer.ticksExisted)) {
            return;
        }

        Map<ChunkCoordinates, MachineState> latestStates = scanMachines(fakePlayer);
        for (String message : applyLatestState(fakePlayer.getCommandSenderName(), latestStates)) {
            emitMessage(fakePlayer, message);
        }
    }

    protected Map<ChunkCoordinates, MachineState> scanMachines(FakePlayer fakePlayer) {
        Map<ChunkCoordinates, MachineState> latestStates = new HashMap<ChunkCoordinates, MachineState>();
        if (fakePlayer == null || fakePlayer.worldObj == null || fakePlayer.worldObj.loadedTileEntityList == null) {
            return latestStates;
        }

        double rangeSquared = (double) this.monitorRange * (double) this.monitorRange;
        for (Object entry : new ArrayList<Object>(fakePlayer.worldObj.loadedTileEntityList)) {
            if (!(entry instanceof TileEntity tileEntity)) {
                continue;
            }
            if (!isWithinRange(fakePlayer, tileEntity, rangeSquared)) {
                continue;
            }
            if (!(tileEntity instanceof IGregTechTileEntity gregTechTileEntity)) {
                continue;
            }
            if (!(gregTechTileEntity.getMetaTileEntity() instanceof MTEMultiBlockBase machine)) {
                continue;
            }

            latestStates.put(
                new ChunkCoordinates(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord),
                createStateFromGregTech(
                    gregTechTileEntity.isActive(),
                    gregTechTileEntity.getLastShutDownReason(),
                    machine.getIdealStatus(),
                    machine.getRepairStatus(),
                    machine.getCheckRecipeResult()
                )
            );
        }
        return latestStates;
    }

    protected void emitMessage(FakePlayer fakePlayer, String message) {
        if (fakePlayer == null || fakePlayer.mcServer == null || fakePlayer.getOwnerUUID() == null) {
            return;
        }

        ServerConfigurationManager configurationManager = fakePlayer.mcServer.getConfigurationManager();
        if (configurationManager == null || configurationManager.playerEntityList == null) {
            return;
        }

        for (Object entry : configurationManager.playerEntityList) {
            if (!(entry instanceof EntityPlayerMP player)) {
                continue;
            }
            if (fakePlayer.getOwnerUUID().equals(player.getUniqueID())) {
                player.addChatMessage(new ChatComponentText(message));
                return;
            }
        }
    }

    private String buildMessage(String botName, ChunkCoordinates coordinates, String event) {
        return "[GTstaff] " + botName + ": machine at (" + coordinates.posX + ", " + coordinates.posY + ", "
            + coordinates.posZ + ") " + event;
    }

    private boolean isWithinRange(FakePlayer fakePlayer, TileEntity tileEntity, double rangeSquared) {
        double dx = tileEntity.xCoord + 0.5D - fakePlayer.posX;
        double dy = tileEntity.yCoord + 0.5D - fakePlayer.posY;
        double dz = tileEntity.zCoord + 0.5D - fakePlayer.posZ;
        return dx * dx + dy * dy + dz * dz <= rangeSquared;
    }

    private ChunkCoordinates copyCoordinates(ChunkCoordinates coordinates) {
        return new ChunkCoordinates(coordinates);
    }

    public static MachineState createStateFromGregTech(boolean active, ShutDownReason shutDownReason, int idealStatus,
        int repairStatus, CheckRecipeResult checkRecipeResult) {
        boolean powered = !isPowerLoss(shutDownReason);
        boolean maintenanceRequired = idealStatus > repairStatus;
        boolean outputFull = isOutputFull(checkRecipeResult);
        return new MachineState(active, powered, maintenanceRequired, outputFull);
    }

    private static boolean isPowerLoss(ShutDownReason shutDownReason) {
        if (shutDownReason == null) {
            return false;
        }

        if (shutDownReason == ShutDownReasonRegistry.POWER_LOSS) {
            return true;
        }

        String powerLossKey = getReasonKey(ShutDownReasonRegistry.POWER_LOSS);
        String currentKey = getReasonKey(shutDownReason);
        return powerLossKey != null && powerLossKey.equals(currentKey);
    }

    private static String getReasonKey(ShutDownReason shutDownReason) {
        try {
            Method method = shutDownReason.getClass().getMethod("getKey");
            Object value = method.invoke(shutDownReason);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isOutputFull(CheckRecipeResult checkRecipeResult) {
        if (checkRecipeResult == null) {
            return false;
        }

        return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL == checkRecipeResult
            || CheckRecipeResultRegistry.FLUID_OUTPUT_FULL == checkRecipeResult
            || CheckRecipeResultRegistry.ITEM_OUTPUT_FULL.equals(checkRecipeResult)
            || CheckRecipeResultRegistry.FLUID_OUTPUT_FULL.equals(checkRecipeResult);
    }
}
