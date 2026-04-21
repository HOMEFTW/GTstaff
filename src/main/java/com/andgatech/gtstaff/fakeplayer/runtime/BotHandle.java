package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

public interface BotHandle {

    String name();

    UUID ownerUUID();

    int dimension();

    BotRuntimeType runtimeType();

    BotEntityBridge entity();
}
