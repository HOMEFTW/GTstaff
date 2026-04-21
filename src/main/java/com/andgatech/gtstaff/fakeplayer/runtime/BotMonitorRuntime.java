package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotMonitorRuntime {

    boolean monitoring();

    int monitorRange();

    int reminderInterval();

    void setMonitoring(boolean monitoring);

    void setMonitorRange(int range);

    void setReminderInterval(int ticks);

    String overviewMessage(String botName);

    default String scanNow(String botName) {
        return overviewMessage(botName);
    }
}
