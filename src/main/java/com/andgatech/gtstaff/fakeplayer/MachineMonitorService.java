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
import net.minecraft.util.EnumChatFormatting;

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
    private int reminderInterval = 600;

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

    public boolean shouldRemind(int ticksExisted) {
        return this.monitoring && ticksExisted > 0
            && this.reminderInterval > 0
            && ticksExisted % this.reminderInterval == 0;
    }

    public int getReminderInterval() {
        return this.reminderInterval;
    }

    public void setReminderInterval(int reminderInterval) {
        this.reminderInterval = Math.max(60, reminderInterval);
    }

    public List<String> applyLatestState(String botName, Map<ChunkCoordinates, MachineState> latestStates) {
        Map<ChunkCoordinates, MachineState> safeStates = latestStates == null
            ? Collections.<ChunkCoordinates, MachineState>emptyMap()
            : latestStates;
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
                    messages.add(buildMessage(botName, coordinates, "断电"));
                }
                if (!previousState.isMaintenanceRequired() && nextState.isMaintenanceRequired()) {
                    messages.add(buildMessage(botName, coordinates, "需要维护"));
                }
                if (!previousState.isOutputFull() && nextState.isOutputFull()) {
                    messages.add(buildMessage(botName, coordinates, "输出已满"));
                }
                if (!previousState.isStructureIncomplete() && nextState.isStructureIncomplete()) {
                    messages.add(buildMessage(botName, coordinates, "结构不完整"));
                }
                if (!previousState.isPollutionFail() && nextState.isPollutionFail()) {
                    messages.add(buildMessage(botName, coordinates, "污染排放失败(排气口堵塞)"));
                }
                if (!previousState.isNoRepair() && nextState.isNoRepair()) {
                    messages.add(buildMessage(botName, coordinates, "无法修复(维护耗尽)"));
                }
                if (!previousState.isNoTurbine() && nextState.isNoTurbine()) {
                    messages.add(buildMessage(botName, coordinates, "缺少涡轮"));
                }
                if (!previousState.isNoMachinePart() && nextState.isNoMachinePart()) {
                    messages.add(buildMessage(botName, coordinates, "机器部件错误/缺失"));
                }
                if (!previousState.isInsufficientDynamo() && nextState.isInsufficientDynamo()) {
                    messages.add(buildMessage(botName, coordinates, "发电机不足"));
                }
                if (!previousState.isOutOfResource() && nextState.isOutOfResource()) {
                    messages.add(buildMessage(botName, coordinates, "资源耗尽(流体/材料)"));
                }
                if (!previousState.isInsufficientPower() && nextState.isInsufficientPower()) {
                    messages.add(buildMessage(botName, coordinates, "电力不足"));
                }
                if (!previousState.isInsufficientVoltage() && nextState.isInsufficientVoltage()) {
                    messages.add(buildMessage(botName, coordinates, "电压不足(等级不够)"));
                }
                if (previousState.hasProblems() && !nextState.hasProblems()) {
                    messages.add(buildMessage(botName, coordinates, "已恢复正常"));
                }
            }
        }

        this.machineStates.clear();
        for (Map.Entry<ChunkCoordinates, MachineState> entry : safeStates.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                this.machineStates.put(
                    copyCoordinates(entry.getKey()),
                    entry.getValue()
                        .copy());
            }
        }
        return messages;
    }

    public void tick(FakePlayer fakePlayer) {
        if (fakePlayer == null || !this.monitoring || fakePlayer.ticksExisted <= 0) {
            return;
        }

        if (fakePlayer.ticksExisted % SCAN_INTERVAL == 0) {
            boolean wasEmpty = this.machineStates.isEmpty();
            Map<ChunkCoordinates, MachineState> latestStates = scanMachines(fakePlayer);
            if (wasEmpty && !latestStates.isEmpty()) {
                for (String line : buildOverviewLines(fakePlayer.getCommandSenderName())) {
                    emitMessage(fakePlayer, line);
                }
            }
            for (String message : applyLatestState(fakePlayer.getCommandSenderName(), latestStates)) {
                emitMessage(fakePlayer, message);
            }
        }

        if (fakePlayer.ticksExisted % this.reminderInterval == 0 && hasProblemMachines()) {
            for (String line : buildProblemSummaryLines(fakePlayer.getCommandSenderName())) {
                emitMessage(fakePlayer, line);
            }
        }
    }

    public boolean hasProblemMachines() {
        for (MachineState state : this.machineStates.values()) {
            if (state != null && state.hasProblems()) {
                return true;
            }
        }
        return false;
    }

    public List<String> buildProblemSummaryLines(String botName) {
        List<String> lines = new ArrayList<String>();
        List<String> problemLines = new ArrayList<String>();
        for (Map.Entry<ChunkCoordinates, MachineState> entry : this.machineStates.entrySet()) {
            MachineState state = entry.getValue();
            if (state == null || !state.hasProblems()) {
                continue;
            }
            ChunkCoordinates c = entry.getKey();
            List<String> issues = new ArrayList<String>();
            if (state.isStructureIncomplete()) issues.add("结构不完整");
            if (!state.isPowered()) issues.add("断电");
            if (state.isPollutionFail()) issues.add("污染堵塞");
            if (state.isNoRepair()) issues.add("无法修复");
            if (state.isNoTurbine()) issues.add("缺涡轮");
            if (state.isNoMachinePart()) issues.add("部件错误");
            if (state.isInsufficientDynamo()) issues.add("发电机不足");
            if (state.isOutOfResource()) issues.add("资源耗尽");
            if (state.isInsufficientPower()) issues.add("电力不足");
            if (state.isInsufficientVoltage()) issues.add("电压不足");
            if (state.isMaintenanceRequired()) issues.add("需维护");
            if (state.isOutputFull()) issues.add("输出满");
            problemLines.add(String.format("(%d,%d,%d) %s", c.posX, c.posY, c.posZ, String.join(", ", issues)));
        }
        if (!problemLines.isEmpty()) {
            lines.add("[GTstaff] " + botName + " 警告 " + problemLines.size() + " 台机器:");
            lines.addAll(problemLines);
        }
        return lines;
    }

    public String buildProblemSummary(String botName) {
        List<String> lines = buildProblemSummaryLines(botName);
        return lines.isEmpty() ? "" : String.join("\n", lines);
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
                    machine.getCheckRecipeResult(),
                    !machine.mMachine));
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

        EnumChatFormatting color = fakePlayer.getChatColor();
        ChatComponentText component = new ChatComponentText(message);
        if (color != null) {
            component.getChatStyle()
                .setColor(color);
        }

        for (Object entry : configurationManager.playerEntityList) {
            if (!(entry instanceof EntityPlayerMP player)) {
                continue;
            }
            if (fakePlayer.getOwnerUUID()
                .equals(player.getUniqueID())) {
                player.addChatMessage(component);
                return;
            }
        }
    }

    private String buildMessage(String botName, ChunkCoordinates coordinates, String event) {
        return "[GTstaff] " + botName
            + ": 机器("
            + coordinates.posX
            + ", "
            + coordinates.posY
            + ", "
            + coordinates.posZ
            + ") "
            + event;
    }

    private boolean isWithinRange(FakePlayer fakePlayer, TileEntity tileEntity, double rangeSquared) {
        double dx = tileEntity.xCoord + 0.5D - fakePlayer.posX;
        double dy = tileEntity.yCoord + 0.5D - fakePlayer.posY;
        double dz = tileEntity.zCoord + 0.5D - fakePlayer.posZ;
        return dx * dx + dy * dy + dz * dz <= rangeSquared;
    }

    public List<String> buildOverviewLines(String botName) {
        List<String> lines = new ArrayList<String>();
        if (this.machineStates.isEmpty()) {
            lines.add("[" + botName + "] 附近未发现GT多方块机器。");
            return lines;
        }
        lines.add("[" + botName + "] " + this.machineStates.size() + " 台机器:");
        for (Map.Entry<ChunkCoordinates, MachineState> entry : this.machineStates.entrySet()) {
            ChunkCoordinates c = entry.getKey();
            MachineState s = entry.getValue();
            lines.add(String.format("  (%d,%d,%d) %s", c.posX, c.posY, c.posZ, summarizeState(s)));
        }
        return lines;
    }

    public String buildOverviewMessage(String botName) {
        return String.join("\n", buildOverviewLines(botName));
    }

    private static String summarizeState(MachineState s) {
        if (s.isStructureIncomplete()) return "结构不完整";
        if (s.isPollutionFail()) return "污染堵塞";
        if (!s.isActive() && !s.isPowered()) return "停机(断电)";
        if (s.isNoTurbine()) return "缺涡轮";
        if (s.isNoMachinePart()) return "部件错误";
        if (s.isInsufficientDynamo()) return "发电机不足";
        if (s.isOutOfResource()) return "资源耗尽";
        if (s.isNoRepair()) return "无法修复";
        if (s.isOutputFull()) return "输出满";
        if (s.isInsufficientPower()) return "电力不足";
        if (s.isInsufficientVoltage()) return "电压不足";
        if (s.isMaintenanceRequired()) return "需维护";
        if (s.isActive()) return "运行中";
        return "空闲";
    }

    private ChunkCoordinates copyCoordinates(ChunkCoordinates coordinates) {
        return new ChunkCoordinates(coordinates);
    }

    public static MachineState createStateFromGregTech(boolean active, ShutDownReason shutDownReason, int idealStatus,
        int repairStatus, CheckRecipeResult checkRecipeResult, boolean structureIncomplete) {
        MachineState state = new MachineState();
        state.setActive(active);
        state.setPowered(!isPowerLoss(shutDownReason));
        state.setMaintenanceRequired(idealStatus > repairStatus);
        state.setOutputFull(isOutputFull(checkRecipeResult));
        state.setStructureIncomplete(structureIncomplete);
        state.setPollutionFail(matchesReason(shutDownReason, ShutDownReasonRegistry.POLLUTION_FAIL));
        state.setNoRepair(matchesReason(shutDownReason, ShutDownReasonRegistry.NO_REPAIR));
        state.setNoTurbine(matchesReason(shutDownReason, ShutDownReasonRegistry.NO_TURBINE));
        state.setNoMachinePart(matchesReason(shutDownReason, ShutDownReasonRegistry.NO_MACHINE_PART));
        state.setInsufficientDynamo(matchesReason(shutDownReason, ShutDownReasonRegistry.INSUFFICIENT_DYNAMO));
        state.setOutOfResource(isOutOfResource(shutDownReason));
        state.setInsufficientPower(matchesRecipeResult(checkRecipeResult, "insufficient_power"));
        state.setInsufficientVoltage(matchesRecipeResult(checkRecipeResult, "insufficient_voltage"));
        return state;
    }

    private static boolean matchesReason(ShutDownReason reason, ShutDownReason expected) {
        if (reason == null || expected == null) {
            return false;
        }
        if (reason == expected) {
            return true;
        }
        String reasonKey = getReasonKey(reason);
        String expectedKey = getReasonKey(expected);
        return reasonKey != null && reasonKey.equals(expectedKey);
    }

    private static boolean isOutOfResource(ShutDownReason reason) {
        if (reason == null) {
            return false;
        }
        String key = getReasonKey(reason);
        return "out_of_fluid".equals(key) || "out_of_stuff".equals(key);
    }

    private static boolean matchesRecipeResult(CheckRecipeResult result, String id) {
        if (result == null || result.wasSuccessful()) {
            return false;
        }
        return id.equals(result.getID());
    }

    private static boolean isPowerLoss(ShutDownReason shutDownReason) {
        return matchesReason(shutDownReason, ShutDownReasonRegistry.POWER_LOSS);
    }

    private static String getReasonKey(ShutDownReason shutDownReason) {
        try {
            Method method = shutDownReason.getClass()
                .getMethod("getKey");
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
