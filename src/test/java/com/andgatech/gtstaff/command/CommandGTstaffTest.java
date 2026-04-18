package com.andgatech.gtstaff.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

import org.junit.jupiter.api.Test;

class CommandGTstaffTest {

    @Test
    void emptyArgsOpenUiHandler() {
        TrackingCommandGTstaff command = new TrackingCommandGTstaff();

        command.processCommand(sender(), new String[0]);

        assertEquals("ui", command.lastHandler);
    }

    @Test
    void uiArgOpenUiHandler() {
        TrackingCommandGTstaff command = new TrackingCommandGTstaff();

        command.processCommand(sender(), new String[] { "ui" });

        assertEquals("ui", command.lastHandler);
    }

    @Test
    void unknownArgThrowsUsage() {
        TrackingCommandGTstaff command = new TrackingCommandGTstaff();

        assertThrows(WrongUsageException.class, () -> command.processCommand(sender(), new String[] { "nope" }));
    }

    private static ICommandSender sender() {
        return (ICommandSender) Proxy.newProxyInstance(
            CommandGTstaffTest.class.getClassLoader(),
            new Class<?>[] { ICommandSender.class },
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == float.class) return 0F;
                if (returnType == double.class) return 0D;
                return null;
            });
    }

    private static final class TrackingCommandGTstaff extends CommandGTstaff {

        private String lastHandler;

        @Override
        protected void openUi(ICommandSender sender) {
            lastHandler = "ui";
        }
    }
}
