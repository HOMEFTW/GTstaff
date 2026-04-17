package com.andgatech.gtstaff.fakeplayer;

public class MachineState {

    private boolean active;
    private boolean powered;
    private boolean maintenanceRequired;
    private boolean outputFull;

    public MachineState() {
    }

    public MachineState(boolean active, boolean powered, boolean maintenanceRequired, boolean outputFull) {
        this.active = active;
        this.powered = powered;
        this.maintenanceRequired = maintenanceRequired;
        this.outputFull = outputFull;
    }

    public MachineState(MachineState other) {
        if (other == null) {
            return;
        }
        this.active = other.active;
        this.powered = other.powered;
        this.maintenanceRequired = other.maintenanceRequired;
        this.outputFull = other.outputFull;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isMaintenanceRequired() {
        return this.maintenanceRequired;
    }

    public void setMaintenanceRequired(boolean maintenanceRequired) {
        this.maintenanceRequired = maintenanceRequired;
    }

    public boolean isOutputFull() {
        return this.outputFull;
    }

    public void setOutputFull(boolean outputFull) {
        this.outputFull = outputFull;
    }

    public boolean hasProblems() {
        return !this.powered || this.maintenanceRequired || this.outputFull;
    }

    public MachineState copy() {
        return new MachineState(this);
    }
}
