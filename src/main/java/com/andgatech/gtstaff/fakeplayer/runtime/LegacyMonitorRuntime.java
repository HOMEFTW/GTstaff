package com.andgatech.gtstaff.fakeplayer.runtime;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;

final class LegacyMonitorRuntime implements BotMonitorRuntime {

    private final FakePlayer fakePlayer;

    LegacyMonitorRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public boolean monitoring() {
        return fakePlayer.isMonitoring();
    }

    @Override
    public int monitorRange() {
        return fakePlayer.getMonitorRange();
    }

    @Override
    public int reminderInterval() {
        return fakePlayer.getReminderInterval();
    }

    @Override
    public void setMonitoring(boolean monitoring) {
        fakePlayer.setMonitoring(monitoring);
    }

    @Override
    public void setMonitorRange(int range) {
        fakePlayer.setMonitorRange(range);
    }

    @Override
    public void setReminderInterval(int ticks) {
        fakePlayer.setReminderInterval(ticks);
    }

    @Override
    public String overviewMessage(String botName) {
        MachineMonitorService service = fakePlayer.getMachineMonitorService();
        return service == null ? "" : service.buildOverviewMessage(botName);
    }

    @Override
    public String scanNow(String botName) {
        MachineMonitorService service = fakePlayer.getMachineMonitorService();
        if (service == null) {
            return "";
        }
        service.scanNow(botName, fakePlayer);
        return service.buildOverviewMessage(botName);
    }
}
