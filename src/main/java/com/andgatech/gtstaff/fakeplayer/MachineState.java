package com.andgatech.gtstaff.fakeplayer;

public class MachineState {

    private boolean active;
    private boolean powered;
    private boolean maintenanceRequired;
    private boolean outputFull;
    private boolean structureIncomplete;
    private boolean pollutionFail;
    private boolean noRepair;
    private boolean noTurbine;
    private boolean noMachinePart;
    private boolean insufficientDynamo;
    private boolean outOfResource;
    private boolean insufficientPower;
    private boolean insufficientVoltage;

    public MachineState() {}

    public MachineState(boolean active, boolean powered, boolean maintenanceRequired, boolean outputFull) {
        this(active, powered, maintenanceRequired, outputFull, false);
    }

    public MachineState(boolean active, boolean powered, boolean maintenanceRequired, boolean outputFull,
        boolean structureIncomplete) {
        this.active = active;
        this.powered = powered;
        this.maintenanceRequired = maintenanceRequired;
        this.outputFull = outputFull;
        this.structureIncomplete = structureIncomplete;
    }

    public MachineState(MachineState other) {
        if (other == null) {
            return;
        }
        this.active = other.active;
        this.powered = other.powered;
        this.maintenanceRequired = other.maintenanceRequired;
        this.outputFull = other.outputFull;
        this.structureIncomplete = other.structureIncomplete;
        this.pollutionFail = other.pollutionFail;
        this.noRepair = other.noRepair;
        this.noTurbine = other.noTurbine;
        this.noMachinePart = other.noMachinePart;
        this.insufficientDynamo = other.insufficientDynamo;
        this.outOfResource = other.outOfResource;
        this.insufficientPower = other.insufficientPower;
        this.insufficientVoltage = other.insufficientVoltage;
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

    public boolean isStructureIncomplete() {
        return this.structureIncomplete;
    }

    public void setStructureIncomplete(boolean structureIncomplete) {
        this.structureIncomplete = structureIncomplete;
    }

    public boolean isPollutionFail() {
        return this.pollutionFail;
    }

    public void setPollutionFail(boolean pollutionFail) {
        this.pollutionFail = pollutionFail;
    }

    public boolean isNoRepair() {
        return this.noRepair;
    }

    public void setNoRepair(boolean noRepair) {
        this.noRepair = noRepair;
    }

    public boolean isNoTurbine() {
        return this.noTurbine;
    }

    public void setNoTurbine(boolean noTurbine) {
        this.noTurbine = noTurbine;
    }

    public boolean isNoMachinePart() {
        return this.noMachinePart;
    }

    public void setNoMachinePart(boolean noMachinePart) {
        this.noMachinePart = noMachinePart;
    }

    public boolean isInsufficientDynamo() {
        return this.insufficientDynamo;
    }

    public void setInsufficientDynamo(boolean insufficientDynamo) {
        this.insufficientDynamo = insufficientDynamo;
    }

    public boolean isOutOfResource() {
        return this.outOfResource;
    }

    public void setOutOfResource(boolean outOfResource) {
        this.outOfResource = outOfResource;
    }

    public boolean isInsufficientPower() {
        return this.insufficientPower;
    }

    public void setInsufficientPower(boolean insufficientPower) {
        this.insufficientPower = insufficientPower;
    }

    public boolean isInsufficientVoltage() {
        return this.insufficientVoltage;
    }

    public void setInsufficientVoltage(boolean insufficientVoltage) {
        this.insufficientVoltage = insufficientVoltage;
    }

    public boolean hasProblems() {
        return !this.powered || this.maintenanceRequired
            || this.outputFull
            || this.structureIncomplete
            || this.pollutionFail
            || this.noRepair
            || this.noTurbine
            || this.noMachinePart
            || this.insufficientDynamo
            || this.outOfResource
            || this.insufficientPower
            || this.insufficientVoltage;
    }

    public MachineState copy() {
        return new MachineState(this);
    }
}
