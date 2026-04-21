package com.andgatech.gtstaff.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeMode;

public class Config {

    public static final String GENERAL = "General";
    public static final String FAKE_PLAYER = "FakePlayer";

    // region FakePlayer Settings
    public static int fakePlayerPermissionLevel = 2;
    public static boolean allowNonOpControlOwnBot = true;
    public static int maxBotsPerPlayer = 10;
    public static int maxBotsTotal = 20;
    public static int defaultMonitorRange = 16;
    public static String fakePlayerRuntimeMode = "nextgen";
    public static boolean fakePlayerActionDiagnostics = false;
    // endregion

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        fakePlayerPermissionLevel = configuration.getInt(
            "fakePlayerPermissionLevel",
            FAKE_PLAYER,
            fakePlayerPermissionLevel,
            0,
            4,
            "OP level required to create/destroy fake players.");
        allowNonOpControlOwnBot = configuration.getBoolean(
            "allowNonOpControlOwnBot",
            FAKE_PLAYER,
            allowNonOpControlOwnBot,
            "Whether non-OP players can control their own fake players.");
        maxBotsPerPlayer = configuration
            .getInt("maxBotsPerPlayer", FAKE_PLAYER, maxBotsPerPlayer, 1, 100, "Maximum fake players per player.");
        maxBotsTotal = configuration
            .getInt("maxBotsTotal", FAKE_PLAYER, maxBotsTotal, 1, 200, "Maximum total fake players on the server.");
        defaultMonitorRange = configuration.getInt(
            "defaultMonitorRange",
            FAKE_PLAYER,
            defaultMonitorRange,
            1,
            64,
            "Default GT machine monitoring range.");
        Property fakePlayerRuntimeModeProperty = configuration.get(
            "fakePlayerRuntimeMode",
            FAKE_PLAYER,
            fakePlayerRuntimeMode,
            "Default fake-player runtime mode: legacy, nextgen, mixed.");
        fakePlayerRuntimeMode = BotRuntimeMode.fromConfig(fakePlayerRuntimeModeProperty.getString())
            .configValue();
        fakePlayerRuntimeModeProperty.set(fakePlayerRuntimeMode);
        fakePlayerActionDiagnostics = configuration.getBoolean(
            "fakePlayerActionDiagnostics",
            FAKE_PLAYER,
            fakePlayerActionDiagnostics,
            "Enable lightweight fake-player attack/use diagnostics in the server log.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
