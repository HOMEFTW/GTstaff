package com.andgatech.gtstaff.fakeplayer.runtime;

import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;

final class NextGenMonitorRuntime implements BotMonitorRuntime {

    private final GTstaffForgePlayer player;
    private final MachineMonitorService service;

    NextGenMonitorRuntime(GTstaffForgePlayer player) {
        this(player, new MachineMonitorService());
    }

    NextGenMonitorRuntime(GTstaffForgePlayer player, MachineMonitorService service) {
        this.player = player;
        this.service = service == null ? new MachineMonitorService() : service;
    }

    @Override
    public boolean monitoring() {
        return service.isMonitoring();
    }

    @Override
    public int monitorRange() {
        return service.getMonitorRange();
    }

    @Override
    public int reminderInterval() {
        return service.getReminderInterval();
    }

    @Override
    public void setMonitoring(boolean monitoring) {
        service.setMonitoring(monitoring);
    }

    @Override
    public void setMonitorRange(int range) {
        service.setMonitorRange(range);
    }

    @Override
    public void setReminderInterval(int ticks) {
        service.setReminderInterval(ticks);
    }

    @Override
    public String overviewMessage(String botName) {
        return service.buildOverviewMessage(botName);
    }

    @Override
    public String scanNow(String botName) {
        service.scanNow(botName, player);
        return service.buildOverviewMessage(botName);
    }

    void tick() {
        if (player == null) {
            return;
        }
        service.tick(player.getCommandSenderName(), player, player.getOwnerUUID());
    }
}
