package com.andgatech.gtstaff.ui;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.util.IIcon;

import com.andgatech.gtstaff.GTstaff;

final class FakePlayerBaublesIconCompat {

    private static final String BAUBLES_CLASS = "baubles.common.Baubles";
    private static final String ITEM_DEBUGGER_FIELD = "itemDebugger";
    private static final String BACKGROUND_ICON_METHOD = "getBackgroundIconForSlotType";

    private static Field itemDebuggerField;
    private static Method backgroundIconMethod;

    private FakePlayerBaublesIconCompat() {}

    static IIcon backgroundIconForSlotType(String slotType) {
        if (slotType == null || slotType.isEmpty()) {
            return null;
        }
        try {
            Object itemDebugger = itemDebugger();
            if (itemDebugger == null) {
                return null;
            }
            Method method = backgroundIconMethod(itemDebugger);
            Object icon = method == null ? null : method.invoke(itemDebugger, slotType);
            return icon instanceof IIcon iIcon ? iIcon : null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            GTstaff.LOG.debug("Unable to resolve Baubles background icon for fake player inventory", e);
            return null;
        }
    }

    private static Object itemDebugger() {
        try {
            Field field = itemDebuggerField();
            return field == null ? null : field.get(null);
        } catch (IllegalAccessException e) {
            GTstaff.LOG.debug("Unable to access Baubles itemDebugger", e);
            return null;
        }
    }

    private static Field itemDebuggerField() {
        if (itemDebuggerField != null) {
            return itemDebuggerField;
        }
        try {
            Class<?> baubles = Class.forName(BAUBLES_CLASS);
            itemDebuggerField = baubles.getField(ITEM_DEBUGGER_FIELD);
            return itemDebuggerField;
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            return null;
        }
    }

    private static Method backgroundIconMethod(Object itemDebugger) {
        if (backgroundIconMethod != null) {
            return backgroundIconMethod;
        }
        try {
            backgroundIconMethod = itemDebugger.getClass()
                .getMethod(BACKGROUND_ICON_METHOD, String.class);
            return backgroundIconMethod;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
