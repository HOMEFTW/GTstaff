package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotRuntimeView extends BotHandle {

    boolean online();

    BotActionRuntime action();

    BotFollowRuntime follow();

    BotMonitorRuntime monitor();

    BotRepelRuntime repel();

    BotInventoryRuntime inventory();
}
