package com.andgatech.gtstaff.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeMode;

class ConfigTest {

    private final int originalFakePlayerPermissionLevel = Config.fakePlayerPermissionLevel;
    private final boolean originalAllowNonOpControlOwnBot = Config.allowNonOpControlOwnBot;
    private final int originalMaxBotsPerPlayer = Config.maxBotsPerPlayer;
    private final int originalMaxBotsTotal = Config.maxBotsTotal;
    private final int originalDefaultMonitorRange = Config.defaultMonitorRange;
    private final boolean originalFakePlayerActionDiagnostics = Config.fakePlayerActionDiagnostics;

    @AfterEach
    void resetConfigState() {
        Config.fakePlayerPermissionLevel = originalFakePlayerPermissionLevel;
        Config.allowNonOpControlOwnBot = originalAllowNonOpControlOwnBot;
        Config.maxBotsPerPlayer = originalMaxBotsPerPlayer;
        Config.maxBotsTotal = originalMaxBotsTotal;
        Config.defaultMonitorRange = originalDefaultMonitorRange;
        Config.fakePlayerRuntimeMode = "nextgen";
        Config.fakePlayerActionDiagnostics = originalFakePlayerActionDiagnostics;
    }

    @Test
    void fakePlayerRuntimeModeDefaultsToNextGen() {
        try {
            String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/andgatech/gtstaff/config/Config.java")));
            assertTrue(source.contains("public static String fakePlayerRuntimeMode = \"nextgen\";"));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void invalidRuntimeModeValuesNormalizeToNextGen() {
        assertEquals("nextgen", BotRuntimeMode.fromConfig("broken-mode").configValue());
        assertEquals("nextgen", BotRuntimeMode.fromConfig(null).configValue());
    }
}
