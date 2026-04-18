package com.andgatech.gtstaff.fakeplayer;

public class Action {

    public boolean done = false;
    public int limit;
    public int interval;
    public int offset;
    public boolean isContinuous;

    private int tickCount = 0;

    private Action(int limit, int interval, int offset, boolean isContinuous) {
        this.limit = limit;
        this.interval = interval;
        this.offset = offset;
        this.isContinuous = isContinuous;
    }

    public static Action once() {
        return new Action(1, 1, 0, false);
    }

    public static Action continuous() {
        return new Action(-1, 1, 0, true);
    }

    public static Action interval(int ticks) {
        return new Action(-1, ticks, 0, false);
    }

    public boolean tick() {
        if (done) return false;
        tickCount++;
        if (tickCount <= offset) return false;
        if ((tickCount - offset - 1) % interval != 0) return false;
        if (limit > 0) {
            limit--;
            if (limit <= 0) done = true;
        }
        return true;
    }
}
