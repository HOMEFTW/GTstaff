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

        assertTrue(service.applyLatestState("Bot_Steve", states(coordinates, new MachineState(true, true, false, false))).isEmpty());

        List<String> alerts = service.applyLatestState("Bot_Steve", states(coordinates, new MachineState(false, false, true, true)));
        assertEquals(3, alerts.size());
        assertTrue(alerts.stream().anyMatch(message -> message.contains("lost power")));
        assertTrue(alerts.stream().anyMatch(message -> message.contains("needs maintenance")));
        assertTrue(alerts.stream().anyMatch(message -> message.contains("output is full")));

        List<String> recovery = service.applyLatestState("Bot_Steve", states(coordinates, new MachineState(true, true, false, false)));
        assertEquals(1, recovery.size());
        assertTrue(recovery.get(0).contains("recovered"));
    }

    @Test
    void createStateFromGregTechMarksPowerLossMaintenanceAndOutputFull() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            false,
            ShutDownReasonRegistry.POWER_LOSS,
            5,
            3,
            CheckRecipeResultRegistry.ITEM_OUTPUT_FULL
        );

        assertFalse(state.isActive());
        assertFalse(state.isPowered());
        assertTrue(state.isMaintenanceRequired());
        assertTrue(state.isOutputFull());
    }

    @Test
    void createStateFromGregTechKeepsHealthyMachineClear() {
        MachineState state = MachineMonitorService.createStateFromGregTech(
            true,
            ShutDownReasonRegistry.NONE,
            5,
            5,
            CheckRecipeResultRegistry.SUCCESSFUL
        );

        assertTrue(state.isActive());
        assertTrue(state.isPowered());
        assertFalse(state.isMaintenanceRequired());
        assertFalse(state.isOutputFull());
    }

    private static Map<ChunkCoordinates, MachineState> states(ChunkCoordinates coordinates, MachineState state) {
        Map<ChunkCoordinates, MachineState> states = new HashMap<ChunkCoordinates, MachineState>();
        states.put(coordinates, state);
        return states;
    }
}
