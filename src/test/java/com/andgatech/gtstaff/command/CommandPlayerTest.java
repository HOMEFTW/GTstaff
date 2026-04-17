package com.andgatech.gtstaff.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

import org.junit.jupiter.api.Test;

class CommandPlayerTest {

    @Test
    void listRoutesToListHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "list" });

        assertEquals("list", command.lastHandler);
    }

    @Test
    void spawnRoutesToSpawnHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "spawn" });

        assertEquals("spawn", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void monitorRoutesToMonitorHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "monitor", "on" });

        assertEquals("monitor", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
    }

    @Test
    void attackRoutesToManipulationHandler() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        command.processCommand(sender(), new String[] { "Bot_Steve", "attack", "continuous" });

        assertEquals("manipulation", command.lastHandler);
        assertEquals("Bot_Steve", command.lastBotName);
        assertEquals("attack", command.lastAction);
    }

    @Test
    void missingArgumentsThrowsUsage() {
        TrackingCommandPlayer command = new TrackingCommandPlayer();

        assertThrows(WrongUsageException.class, () -> command.processCommand(sender(), new String[0]));
        assertThrows(WrongUsageException.class, () -> command.processCommand(sender(), new String[] { "Bot_Steve" }));
    }

    private static ICommandSender sender() {
        return (ICommandSender) Proxy.newProxyInstance(
            CommandPlayerTest.class.getClassLoader(),
            new Class<?>[] { ICommandSender.class },
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == float.class) return 0F;
                if (returnType == double.class) return 0D;
                return null;
            }
        );
    }

    private static final class TrackingCommandPlayer extends CommandPlayer {
        private String lastHandler;
        private String lastBotName;
        private String lastAction;

        @Override
        protected void handleList(ICommandSender sender) {
            lastHandler = "list";
        }

        @Override
        protected void handleSpawn(ICommandSender sender, String botName, String[] args) {
            lastHandler = "spawn";
            lastBotName = botName;
        }

        @Override
        protected void handleKill(ICommandSender sender, String botName) {
            lastHandler = "kill";
            lastBotName = botName;
        }

        @Override
        protected void handleShadow(ICommandSender sender, String botName) {
            lastHandler = "shadow";
            lastBotName = botName;
        }

        @Override
        protected void handleMonitor(ICommandSender sender, String botName, String[] args) {
            lastHandler = "monitor";
            lastBotName = botName;
        }

        @Override
        protected void handleManipulation(ICommandSender sender, String botName, String action, String[] args) {
            lastHandler = "manipulation";
            lastBotName = botName;
            lastAction = action;
        }
    }
}
