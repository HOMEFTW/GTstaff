package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.Locale;

public enum BotRuntimeMode {
    LEGACY,
    NEXTGEN,
    MIXED;

    public static BotRuntimeMode fromConfig(String rawValue) {
        if (rawValue == null) {
            return NEXTGEN;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if ("legacy".equals(normalized)) {
            return LEGACY;
        }
        if ("mixed".equals(normalized)) {
            return MIXED;
        }
        if ("nextgen".equals(normalized)) {
            return NEXTGEN;
        }
        return NEXTGEN;
    }

    public boolean prefersNextGen() {
        return this == NEXTGEN || this == MIXED;
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
