package com.andgatech.gtstaff.fakeplayer;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

public class PlayerActionPack {

    private final EntityPlayerMP player;
    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);

    private float moveForward = 0;
    private float moveStrafing = 0;
    private boolean sneaking = false;
    private boolean sprinting = false;

    public PlayerActionPack(EntityPlayerMP player) {
        this.player = player;
    }

    public void onUpdate() {
        // 1. Clean up completed actions
        actions.entrySet().removeIf(e -> e.getValue().done);

        // 2. Execute active actions
        for (Map.Entry<ActionType, Action> entry : actions.entrySet()) {
            if (entry.getValue().tick()) {
                executeAction(entry.getKey());
            }
        }

        // 3. Apply movement
        player.moveStrafing = moveStrafing * (sneaking ? 0.3f : 1.0f);
        player.moveForward = moveForward * (sneaking ? 0.3f : 1.0f);
        player.setSneaking(sneaking);
        player.setSprinting(sprinting);
    }

    private void executeAction(ActionType type) {
        // TODO: implement action execution (USE, ATTACK, JUMP, DROP_ITEM, DROP_STACK)
    }

    public void start(ActionType type, Action action) {
        actions.put(type, action);
    }

    public void stop(ActionType type) {
        actions.remove(type);
    }

    public void stopAll() {
        actions.clear();
        moveForward = 0;
        moveStrafing = 0;
        sneaking = false;
        sprinting = false;
    }

    public void look(float yaw, float pitch) {
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
    }

    public void setForward(float value) {
        this.moveForward = value;
    }

    public void setStrafing(float value) {
        this.moveStrafing = value;
    }

    public void setSneaking(boolean value) {
        this.sneaking = value;
    }

    public void setSprinting(boolean value) {
        this.sprinting = value;
    }
}
