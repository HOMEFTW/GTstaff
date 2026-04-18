package com.andgatech.gtstaff.ui;

import com.cleanroommc.modularui.screen.ModularPanel;

final class PopupPanelLayout {

    private PopupPanelLayout() {}

    static <T extends ModularPanel> T centerInParent(T panel, ModularPanel parent, int width, int height) {
        panel.relative(parent);
        panel.center();
        panel.size(width, height);
        return panel;
    }
}
