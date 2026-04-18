package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.Test;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

class MachineMonitorServiceTest {

    @Test
    void shouldScanRequiresMonitoringAndSixtyTickBoundary() {
        MachineMonitorService service = new MachineMonitorService();

        assertFalse(service.shouldScan(60));

        service.setMonitoring(true);

        assertFalse(service.shouldScan(59));
        assertTrue(service.shouldScan(60));
        assertTrue(service.shouldScan(120));
    }

    @Test
    void applyLatestStateReportsAlertsAndRecovery() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coordinates = new ChunkCoordinates(12, 64, -8);

        assertTrue(
            service.applyLatestState("Bot_Steve", states(coordinates, new MachineState(true, true, false, false)))
                .isEmpty());

        List<String> alerts = service
            .applyLatestState("Bot_Steve", states(coordinates, new MachineState(false, false, true, true)));
        assertEquals(3, alerts.size());
        assertTrue(
            alerts.stream()
                .anyMatch(message -> message.contains("断电")));
        assertTrue(
            alerts.stream()
                .anyMatch(message -> message.contains("需要维护")));
        assertTrue(
            alerts.stream()
                .anyMatch(message -> message.contains("输出已满")));

        List<String> recovery = service
            .applyLatestState("Bot_Steve", states(coordinates, new MachineState(true, true, false, false)));
        assertEquals(1, recovery.size());
        assertTrue(
            recovery.get(0)
                .contains("已恢复正常"));
    }

    @Test
    void applyLatestStateDetectsStructureIncomplete() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coordinates = new ChunkCoordinates(10, 20, 30);

        // baseline: structure complete
        service.applyLatestState("Bot", states(coordinates, new MachineState(true, true, false, false, false)));

        // structure becomes incomplete
        List<String> alerts = service
            .applyLatestState("Bot", states(coordinates, new MachineState(false, true, false, false, true)));
        assertEquals(1, alerts.size());
        assertTrue(
            alerts.get(0)
                .contains("结构不完整"));

        // recovers
        List<String> recovery = service
            .applyLatestState("Bot", states(coordinates, new MachineState(true, true, false, false, false)));
        assertEquals(1, recovery.size());
        assertTrue(
            recovery.get(0)
                .contains("已恢复正常"));
    }

    @Test
    void createStateFromGregTechMarksPowerLossMaintenanceAndOutputFull() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.POWER_LOSS,
            5,
            3,
            CheckRecipeResultRegistry.ITEM_OUTPUT_FULL,
            false);

        assertFalse(state.isActive());
        assertFalse(state.isPowered());
        assertTrue(state.isMaintenanceRequired());
        assertTrue(state.isOutputFull());
        assertFalse(state.isStructureIncomplete());
    }

    @Test
    void createStateFromGregTechKeepsHealthyMachineClear() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            true,
            ShutDownReasonRegistry.NONE,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            false);

        assertTrue(state.isActive());
        assertTrue(state.isPowered());
        assertFalse(state.isMaintenanceRequired());
        assertFalse(state.isOutputFull());
        assertFalse(state.isStructureIncomplete());
        assertFalse(state.isPollutionFail());
        assertFalse(state.isNoRepair());
        assertFalse(state.isNoTurbine());
        assertFalse(state.isNoMachinePart());
        assertFalse(state.isInsufficientDynamo());
        assertFalse(state.isOutOfResource());
        assertFalse(state.isInsufficientPower());
        assertFalse(state.isInsufficientVoltage());
    }

    @Test
    void createStateFromGregTechDetectsStructureIncomplete() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.STRUCTURE_INCOMPLETE,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            true);

        assertFalse(state.isActive());
        assertTrue(state.isPowered());
        assertFalse(state.isMaintenanceRequired());
        assertFalse(state.isOutputFull());
        assertTrue(state.isStructureIncomplete());
        assertTrue(state.hasProblems());
    }

    @Test
    void buildOverviewMessageShowsNoMachinesWhenEmpty() {
        MachineMonitorService service = new MachineMonitorService();
        String message = service.buildOverviewMessage("Bot");
        assertTrue(message.contains("未发现GT多方块机器"));
    }

    @Test
    void buildOverviewMessageSummarizesMachineStates() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coord1 = new ChunkCoordinates(1, 2, 3);
        ChunkCoordinates coord2 = new ChunkCoordinates(10, 20, 30);

        Map<ChunkCoordinates, MachineState> both = new HashMap<ChunkCoordinates, MachineState>();
        both.put(coord1, new MachineState(true, true, false, false, false));
        both.put(coord2, new MachineState(false, true, false, false, true));
        service.applyLatestState("Bot", both);

        String message = service.buildOverviewMessage("Bot");
        assertTrue(message.contains("2 台机器"));
        assertTrue(message.contains("(1,2,3) 运行中"));
        assertTrue(message.contains("(10,20,30) 结构不完整"));
    }

    @Test
    void shouldRemindTriggersAtDefaultInterval() {
        MachineMonitorService service = new MachineMonitorService();
        assertFalse(service.shouldRemind(600));

        service.setMonitoring(true);
        assertFalse(service.shouldRemind(599));
        assertTrue(service.shouldRemind(600));
        assertTrue(service.shouldRemind(1200));
    }

    @Test
    void shouldRemindUsesConfigurableInterval() {
        MachineMonitorService service = new MachineMonitorService();
        service.setMonitoring(true);

        service.setReminderInterval(600);
        assertFalse(service.shouldRemind(200));
        assertTrue(service.shouldRemind(600));
        assertTrue(service.shouldRemind(1200));

        service.setReminderInterval(1200);
        assertFalse(service.shouldRemind(600));
        assertTrue(service.shouldRemind(1200));
    }

    @Test
    void hasProblemMachinesReturnsFalseWhenAllHealthy() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coord = new ChunkCoordinates(5, 10, 15);
        service.applyLatestState("Bot", states(coord, new MachineState(true, true, false, false, false)));

        assertFalse(service.hasProblemMachines());
    }

    @Test
    void hasProblemMachinesReturnsTrueWhenMachineHasProblem() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coord = new ChunkCoordinates(5, 10, 15);
        service.applyLatestState("Bot", states(coord, new MachineState(false, false, true, false, false)));

        assertTrue(service.hasProblemMachines());
    }

    @Test
    void buildProblemSummaryOnlyListsProblemMachines() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates healthy = new ChunkCoordinates(1, 2, 3);
        ChunkCoordinates broken = new ChunkCoordinates(10, 20, 30);

        Map<ChunkCoordinates, MachineState> both = new HashMap<ChunkCoordinates, MachineState>();
        both.put(healthy, new MachineState(true, true, false, false, false));
        both.put(broken, new MachineState(false, false, true, true, true));
        service.applyLatestState("Bot", both);

        String summary = service.buildProblemSummary("Bot");
        assertTrue(summary.contains("警告 1 台机器"));
        assertTrue(summary.contains("(10,20,30)"));
        assertTrue(summary.contains("结构不完整"));
        assertTrue(summary.contains("断电"));
        assertFalse(summary.contains("(1,2,3)"));
    }

    @Test
    void buildProblemSummaryReturnsEmptyWhenNoProblems() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coord = new ChunkCoordinates(1, 2, 3);
        service.applyLatestState("Bot", states(coord, new MachineState(true, true, false, false, false)));

        String summary = service.buildProblemSummary("Bot");
        assertEquals("", summary);
    }

    @Test
    void createStateFromGregTechDetectsPollutionFail() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.POLLUTION_FAIL,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            false);

        assertTrue(state.isPollutionFail());
        assertTrue(state.hasProblems());
    }

    @Test
    void createStateFromGregTechDetectsNoRepair() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.NO_REPAIR,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            false);

        assertTrue(state.isNoRepair());
        assertTrue(state.hasProblems());
    }

    @Test
    void createStateFromGregTechDetectsNoTurbine() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.NO_TURBINE,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            false);

        assertTrue(state.isNoTurbine());
        assertTrue(state.hasProblems());
    }

    @Test
    void createStateFromGregTechDetectsNoMachinePart() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.NO_MACHINE_PART,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            false);

        assertTrue(state.isNoMachinePart());
        assertTrue(state.hasProblems());
    }

    @Test
    void createStateFromGregTechDetectsInsufficientDynamo() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.INSUFFICIENT_DYNAMO,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL,
            false);

        assertTrue(state.isInsufficientDynamo());
        assertTrue(state.hasProblems());
    }

    @Test
    void applyLatestStateDetectsNewFaultTransitions() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coord = new ChunkCoordinates(5, 10, 15);

        // baseline: healthy
        service.applyLatestState("Bot", states(coord, new MachineState(true, true, false, false, false)));

        // trigger pollution fail, no turbine, wrong part, insufficient dynamo
        MachineState broken = new MachineState(false, true, false, false, false);
        broken.setPollutionFail(true);
        broken.setNoTurbine(true);
        broken.setNoMachinePart(true);
        broken.setInsufficientDynamo(true);
        broken.setNoRepair(true);
        broken.setOutOfResource(true);
        broken.setInsufficientPower(true);
        broken.setInsufficientVoltage(true);
        List<String> alerts = service.applyLatestState("Bot", states(coord, broken));

        assertEquals(8, alerts.size());
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("污染排放失败")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("缺少涡轮")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("机器部件错误")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("发电机不足")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("无法修复")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("资源耗尽")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("电力不足")));
        assertTrue(
            alerts.stream()
                .anyMatch(m -> m.contains("电压不足")));
    }

    @Test
    void summarizeStateShowsCorrectLabelsForNewFaults() {
        MachineMonitorService service = new MachineMonitorService();
        ChunkCoordinates coord = new ChunkCoordinates(1, 2, 3);

        MachineState state = new MachineState(false, true, false, false, false);
        state.setPollutionFail(true);
        state.setNoTurbine(true);
        state.setNoMachinePart(true);
        state.setInsufficientDynamo(true);
        state.setNoRepair(true);
        state.setOutOfResource(true);
        state.setInsufficientPower(true);
        state.setInsufficientVoltage(true);
        Map<ChunkCoordinates, MachineState> map = new HashMap<ChunkCoordinates, MachineState>();
        map.put(coord, state);
        service.applyLatestState("Bot", map);

        String summary = service.buildProblemSummary("Bot");
        assertTrue(summary.contains("污染堵塞"));
        assertTrue(summary.contains("缺涡轮"));
        assertTrue(summary.contains("部件错误"));
        assertTrue(summary.contains("发电机不足"));
        assertTrue(summary.contains("无法修复"));
        assertTrue(summary.contains("资源耗尽"));
        assertTrue(summary.contains("电力不足"));
        assertTrue(summary.contains("电压不足"));
    }

    private static Map<ChunkCoordinates, MachineState> states(ChunkCoordinates coordinates, MachineState state) {
        Map<ChunkCoordinates, MachineState> states = new HashMap<ChunkCoordinates, MachineState>();
        states.put(coordinates, state);
        return states;
    }
}
