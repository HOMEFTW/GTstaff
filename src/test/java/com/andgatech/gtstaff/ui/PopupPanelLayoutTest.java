package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.sizer.Flex;

class PopupPanelLayoutTest {

    @Test
    void centerInParentUsesParentAreaInsteadOfOffsettingToTheRight() {
        ModularPanel parent = ModularPanel.defaultPanel("parent", 220, 170);
        ModularPanel child = new ModularPanel("child");

        PopupPanelLayout.centerInParent(child, parent, 190, 140);

        Flex flex = child.flex();
        assertFalse((Boolean) getField(flex, "relativeToParent"));
        assertSame(parent.getArea(), getField(flex, "relativeTo"));
    }

    private static Object getField(Object target, String name) {
        try {
            Field field = target.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
