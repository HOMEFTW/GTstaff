# Fake Player Follow Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 GTstaff 假人系统实现跟随玩家功能——近距离物理移动追踪、超距传送拉回、跨维度延迟传送，支持命令和 UI 触发。

**Architecture:** 新增 `FollowService` 类挂载在 `FakePlayer` 上，每 tick 在 `onUpdate()` 中被调用。FollowService 计算方向向量并设置 moveForward/moveStrafing，复用 vanilla 移动引擎。跨维度和超距通过直接设置位置实现。命令层新增 `follow` 子命令，UI 层在 Other 页签添加按钮和滑块。

**Tech Stack:** Java 8, Forge 1.7.10, Sponge Mixin, ModularUI2, JUnit 5

---

## File Structure

### New files

- `src/main/java/com/andgatech/gtstaff/fakeplayer/FollowService.java` — 跟随核心逻辑：方向计算、距离判断、飞行同步、跨维度传送
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FollowServiceTest.java` — FollowService 单元测试（方向计算、Y 轴控制、距离判断）

### Modified files

- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java` — 添加 `FollowService` 字段，`onUpdate()` 中调用 `followService.tick()`
- `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java` — 新增 `follow` 子命令分支
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java` — 持久化新增 followTarget / followRange / teleportRange
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java` — 新增 startFollow / stopFollow / setFollowRange / setTeleportRange 方法，BotDetails 新增 following 状态
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java` — Other 页签新增跟随相关 UI 控件

---

## Task 1: FollowService Core Logic And Tests

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/FollowService.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FollowServiceTest.java`
- Test: `./gradlew test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`

- [ ] **Step 1: Write failing tests for direction calculation**

```java
package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FollowServiceTest {

    @Test
    void calculateMoveForward_towardsSouthYawZero() {
        // Target is south (+Z), fake faces south (yaw=0)
        // atan2(-dx, dz) = atan2(0, 1) = 0, yawDiff = 0
        // moveForward = cos(0) = 1.0, moveStrafing = sin(0) = 0
        float[] result = FollowService.calculateMovement(0.0, 10.0, 0.0, 10.0, 20.0);
        assertEquals(1.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMoveForward_towardsEastYawZero() {
        // Target is east (+X), fake faces south (yaw=0)
        // targetYaw = atan2(-10, 0) = -90 (in degrees), yawDiff = -90 - 0 = -90
        // moveForward = cos(-90°) ≈ 0, moveStrafing = sin(-90°) = -1.0
        float[] result = FollowService.calculateMovement(0.0, 10.0, 20.0, 10.0, 10.0);
        assertEquals(0.0F, result[0], 0.01F, "moveForward");
        assertEquals(-1.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMoveForward_towardsNorthYaw180() {
        // Target is north (-Z), fake faces north (yaw=180)
        // targetYaw = atan2(0, -10) = 180, yawDiff = 180 - 180 = 0
        // moveForward = cos(0) = 1.0
        float[] result = FollowService.calculateMovement(180.0F, 10.0, 10.0, 10.0, 0.0);
        assertEquals(1.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMovement_diagonal() {
        // Target is at +5X +5Z from fake, fake faces south (yaw=0)
        // targetYaw = atan2(-5, 5) = -45°, yawDiff = -45
        // moveForward = cos(-45°) ≈ 0.707, moveStrafing = sin(-45°) ≈ -0.707
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 15.0, 15.0);
        float expectedComponent = (float) (Math.sqrt(2) / 2.0);
        assertEquals(expectedComponent, result[0], 0.01F, "moveForward");
        assertEquals(-expectedComponent, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void calculateMovement_samePosition_returnsZero() {
        float[] result = FollowService.calculateMovement(0.0F, 10.0, 10.0, 10.0, 10.0);
        assertEquals(0.0F, result[0], 0.01F, "moveForward");
        assertEquals(0.0F, result[1], 0.01F, "moveStrafing");
    }

    @Test
    void normalizeYawDiff_wrapsCorrectly() {
        assertEquals(0.0F, FollowService.normalizeYawDiff(0.0F), 0.01F);
        assertEquals(90.0F, FollowService.normalizeYawDiff(90.0F), 0.01F);
        assertEquals(-90.0F, FollowService.normalizeYawDiff(270.0F), 0.01F);
        assertEquals(180.0F, FollowService.normalizeYawDiff(-180.0F), 0.01F);
        assertEquals(-170.0F, FollowService.normalizeYawDiff(190.0F), 0.01F);
    }

    @Test
    void shouldJump_targetAboveThreshold() {
        assertTrue(FollowService.shouldJump(5.0, 8.0, true));
    }

    @Test
    void shouldNotJump_targetWithinThreshold() {
        assertFalse(FollowService.shouldJump(5.0, 5.3, true));
    }

    @Test
    void shouldDescend_targetBelowThreshold() {
        assertTrue(FollowService.shouldDescend(5.0, 3.0, true));
    }

    @Test
    void shouldNotDescend_onGround() {
        assertFalse(FollowService.shouldDescend(5.0, 3.0, false));
    }

    @Test
    void defaultFollowRange() {
        assertEquals(3, FollowService.DEFAULT_FOLLOW_RANGE);
    }

    @Test
    void defaultTeleportRange() {
        assertEquals(32, FollowService.DEFAULT_TELEPORT_RANGE);
    }

    @Test
    void crossDimensionDelay() {
        assertEquals(100, FollowService.CROSS_DIM_DELAY_TICKS);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew --offline test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`
Expected: FAIL — `FollowService` class does not exist yet.

- [ ] **Step 3: Implement FollowService with static utility methods**

```java
package com.andgatech.gtstaff.fakeplayer;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

public class FollowService {

    public static final int DEFAULT_FOLLOW_RANGE = 3;
    public static final int DEFAULT_TELEPORT_RANGE = 32;
    public static final int CROSS_DIM_DELAY_TICKS = 100;
    private static final float Y_THRESHOLD = 0.5F;

    private UUID followTargetUUID;
    private int followRange = DEFAULT_FOLLOW_RANGE;
    private int teleportRange = DEFAULT_TELEPORT_RANGE;
    private int crossDimTicksRemaining = 0;
    private int previousTargetDimension = Integer.MIN_VALUE;
    private boolean pendingCrossDimMessage = false;

    private final FakePlayer fakePlayer;

    public FollowService(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    public void tick() {
        if (followTargetUUID == null) return;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) { stop(); return; }

        EntityPlayerMP target = server.getConfigurationManager().func_152612_a(followTargetUUID.toString());
        if (target == null || target.isDead) { stop(); return; }

        // Cross-dimension handling
        if (fakePlayer.dimension != target.dimension) {
            handleCrossDimension(target);
            return;
        }

        // Reset cross-dim state when in same dimension
        crossDimTicksRemaining = 0;
        pendingCrossDimMessage = false;

        // Calculate distance
        double dx = target.posX - fakePlayer.posX;
        double dy = target.posY - fakePlayer.posY;
        double dz = target.posZ - fakePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Teleport if too far
        if (distance > teleportRange) {
            teleportNearTarget(target);
            return;
        }

        // Stop moving if close enough
        if (distance < followRange) {
            fakePlayer.moveForward = 0.0F;
            fakePlayer.moveStrafing = 0.0F;
            fakePlayer.setJumping(false);
            return;
        }

        // Sync flying state
        fakePlayer.capabilities.isFlying = target.capabilities.isFlying;
        if (target.capabilities.isFlying) {
            fakePlayer.capabilities.allowFlying = true;
        }

        // Calculate movement direction
        float[] movement = calculateMovement(fakePlayer.rotationYaw, fakePlayer.posX, fakePlayer.posZ, target.posX, target.posZ);
        fakePlayer.moveForward = movement[0];
        fakePlayer.moveStrafing = movement[1];

        // Face the target
        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        fakePlayer.rotationYaw = targetYaw;
        fakePlayer.rotationYawHead = targetYaw;
        fakePlayer.renderYawOffset = targetYaw;

        // Y-axis control
        boolean isAirborne = !fakePlayer.onGround || fakePlayer.capabilities.isFlying;
        if (isAirborne) {
            if (shouldJump(fakePlayer.posY, target.posY, fakePlayer.capabilities.isFlying)) {
                fakePlayer.setJumping(true);
            } else if (fakePlayer.capabilities.isFlying && shouldDescend(fakePlayer.posY, target.posY, fakePlayer.capabilities.isFlying)) {
                // Descend in flight by moving down — set sneaking to signal descent
                // In vanilla, sneaking while flying slows descent; we use position adjustment instead
                fakePlayer.motionY = Math.max(fakePlayer.motionY - 0.1D, -0.5D);
            } else {
                fakePlayer.setJumping(false);
            }
        }
    }

    private void handleCrossDimension(EntityPlayerMP target) {
        // If target changed dimension during countdown, reset
        if (previousTargetDimension != Integer.MIN_VALUE && target.dimension != previousTargetDimension) {
            crossDimTicksRemaining = CROSS_DIM_DELAY_TICKS;
            pendingCrossDimMessage = true;
        }
        previousTargetDimension = target.dimension;

        if (crossDimTicksRemaining == 0 && !pendingCrossDimMessage) {
            // Start countdown
            crossDimTicksRemaining = CROSS_DIM_DELAY_TICKS;
            pendingCrossDimMessage = true;
        }

        if (pendingCrossDimMessage) {
            // Notify target player
            String botName = FakePlayer.colorizeName(fakePlayer.getCommandSenderName());
            target.addChatMessage(new ChatComponentText("[GTstaff] " + botName + " 将在 " + (crossDimTicksRemaining / 20) + " 秒后传送至你的维度"));
            pendingCrossDimMessage = false;
        }

        if (crossDimTicksRemaining > 0) {
            crossDimTicksRemaining--;
            if (crossDimTicksRemaining == 0) {
                executeCrossDimensionTeleport(target);
                previousTargetDimension = Integer.MIN_VALUE;
            }
        }
    }

    private void executeCrossDimensionTeleport(EntityPlayerMP target) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        int targetDim = target.dimension;
        WorldServer oldWorld = server.worldServerForDimension(fakePlayer.dimension);
        WorldServer newWorld = server.worldServerForDimension(targetDim);
        if (newWorld == null) return;

        // Remove from old world
        if (oldWorld != null) {
            oldWorld.removePlayer(fakePlayer);
        }

        // Update dimension and position
        fakePlayer.dimension = targetDim;
        fakePlayer.setPositionAndUpdate(target.posX, target.posY, target.posZ);
        fakePlayer.theItemInWorldManager.setWorld(newWorld);

        // Add to new world
        newWorld.spawnEntityInWorld(fakePlayer);

        // Sync position
        if (fakePlayer.playerNetServerHandler != null) {
            fakePlayer.playerNetServerHandler.setPlayerLocation(target.posX, target.posY, target.posZ, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        }
    }

    private void teleportNearTarget(EntityPlayerMP target) {
        // Teleport behind the target (opposite of target's facing direction), offset by 2 blocks
        float offsetX = -MathHelper.sin(target.rotationYaw * (float) Math.PI / 180.0F) * 2.0F;
        float offsetZ = MathHelper.cos(target.rotationYaw * (float) Math.PI / 180.0F) * 2.0F;
        double newX = target.posX + offsetX;
        double newY = target.posY;
        double newZ = target.posZ + offsetZ;

        fakePlayer.setPositionAndUpdate(newX, newY, newZ);
        if (fakePlayer.playerNetServerHandler != null) {
            fakePlayer.playerNetServerHandler.setPlayerLocation(newX, newY, newZ, fakePlayer.rotationYaw, fakePlayer.rotationPitch);
        }
    }

    // --- Static utility methods (testable without Minecraft) ---

    /**
     * Calculates moveForward and moveStrafing to move from (fromX, fromZ) towards (toX, toZ),
     * relative to the fake player's current yaw.
     *
     * @return float[2] where [0] = moveForward, [1] = moveStrafing
     */
    public static float[] calculateMovement(float fakeYaw, double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.01D) {
            return new float[] { 0.0F, 0.0F };
        }

        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        float yawDiff = normalizeYawDiff(targetYaw - fakeYaw);
        double yawRad = Math.toRadians(yawDiff);

        float moveForward = (float) Math.cos(yawRad);
        float moveStrafing = (float) Math.sin(yawRad);

        // Normalize to max magnitude 1.0
        float maxAbs = Math.max(Math.abs(moveForward), Math.abs(moveStrafing));
        if (maxAbs > 1.0F) {
            moveForward /= maxAbs;
            moveStrafing /= maxAbs;
        }

        return new float[] { moveForward, moveStrafing };
    }

    public static float normalizeYawDiff(float diff) {
        diff = diff % 360.0F;
        if (diff > 180.0F) {
            diff -= 360.0F;
        } else if (diff < -180.0F) {
            diff += 360.0F;
        }
        return diff;
    }

    public static boolean shouldJump(double fakeY, double targetY, boolean isFlying) {
        return (isFlying || !isOnGround(fakeY)) && targetY - fakeY > Y_THRESHOLD;
    }

    public static boolean shouldDescend(double fakeY, double targetY, boolean isFlying) {
        return isFlying && fakeY - targetY > Y_THRESHOLD;
    }

    private static boolean isOnGround(double y) {
        // Simple heuristic — in production, onGround field is authoritative
        return y == Math.floor(y);
    }

    // --- Getters / Setters ---

    public boolean isFollowing() {
        return followTargetUUID != null;
    }

    public UUID getFollowTargetUUID() {
        return followTargetUUID;
    }

    public int getFollowRange() {
        return followRange;
    }

    public void setFollowRange(int range) {
        this.followRange = Math.max(1, range);
    }

    public int getTeleportRange() {
        return teleportRange;
    }

    public void setTeleportRange(int range) {
        this.teleportRange = Math.max(followRange + 1, range);
    }

    public void startFollowing(UUID targetUUID) {
        this.followTargetUUID = targetUUID;
        this.crossDimTicksRemaining = 0;
        this.pendingCrossDimMessage = false;
        this.previousTargetDimension = Integer.MIN_VALUE;
    }

    public void stop() {
        this.followTargetUUID = null;
        this.crossDimTicksRemaining = 0;
        this.pendingCrossDimMessage = false;
        this.previousTargetDimension = Integer.MIN_VALUE;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew --offline test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`
Expected: All 13 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FollowService.java src/test/java/com/andgatech/gtstaff/fakeplayer/FollowServiceTest.java
git commit -m "feat: add FollowService with direction calculation and tests"
```

---

## Task 2: Integrate FollowService Into FakePlayer

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Test: `./gradlew --offline compileJava`

- [ ] **Step 1: Add FollowService field and accessor to FakePlayer**

In `FakePlayer.java`, after the `machineMonitorService` field (line 24), add:

```java
private final FollowService followService = new FollowService(this);
```

- [ ] **Step 2: Call followService.tick() in onUpdate()**

In `FakePlayer.onUpdate()`, after the action pack tick (line 163) and before `runLivingUpdate` (line 168), insert:

```java
this.followService.tick();
```

The resulting `onUpdate()` method should be:

```java
@Override
public void onUpdate() {
    if (this.disconnected) {
        return;
    }

    if (this.isDead && this.deathTime > 0) {
        this.respawnFake();
    }

    super.onUpdate();
    ((IFakePlayerHolder) this).getActionPack()
        .onUpdate();
    this.followService.tick();
    runLivingUpdate(this::onLivingUpdate);
    this.machineMonitorService.tick(this);
}
```

- [ ] **Step 3: Add follow-related accessor methods to FakePlayer**

Add these methods after the monster repelling accessors (after line 256):

```java
public FollowService getFollowService() {
    return this.followService;
}

public boolean isFollowing() {
    return this.followService.isFollowing();
}
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew --offline compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java
git commit -m "feat: integrate FollowService into FakePlayer.onUpdate()"
```

---

## Task 3: Add Follow Command

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
- Test: `./gradlew --offline test`

- [ ] **Step 1: Add follow branch to command switch**

In `CommandPlayer.processCommand()`, in the `switch (action)` block (after the `"monitor"` case around line 80), add a new case:

```java
case "follow":
    handleFollow(sender, botName, trailingArgs);
    return;
```

- [ ] **Step 2: Update command usage string**

Update `getCommandUsage` (line 41) to include `follow`:

```java
return "/player <name> spawn|kill|shadow|attack|use|jump|drop|dropStack|move|look|turn|sneak|unsneak|sprint|unsprint|mount|dismount|hotbar|stop|monitor|follow [player|stop|range <n>|tprange <n>] ...";
```

- [ ] **Step 3: Implement handleFollow method**

Add this method after `handleMonitor` (after line 262):

```java
protected void handleFollow(ICommandSender sender, String botName, String[] args) {
    FakePlayer target = requireFakePlayer(botName);
    if (PermissionHelper.cantManipulate(sender, target)) {
        throw new CommandException("You do not have permission to control that bot");
    }

    FollowService followService = target.getFollowService();

    if (args.length == 0) {
        // Default: follow the sender
        if (!(sender instanceof EntityPlayerMP player)) {
            throw new CommandException("Only players can be followed");
        }
        followService.startFollowing(player.getUniqueID());
        notifySender(sender, FakePlayer.colorizeName(target.getCommandSenderName()) + " 开始跟随你");
        return;
    }

    String subCommand = args[0].toLowerCase(Locale.ROOT);
    switch (subCommand) {
        case "stop":
            followService.stop();
            target.moveForward = 0.0F;
            target.moveStrafing = 0.0F;
            target.setJumping(false);
            notifySender(sender, FakePlayer.colorizeName(target.getCommandSenderName()) + " 停止跟随");
            return;
        case "range":
            if (args.length != 2) {
                throw new WrongUsageException("/player <name> follow range <blocks>");
            }
            int followRange = parseIntWithMin(sender, args[1], 1);
            followService.setFollowRange(followRange);
            notifySender(sender, target.getCommandSenderName() + " 跟随距离设置为 " + followRange + " 格");
            return;
        case "tprange":
            if (args.length != 2) {
                throw new WrongUsageException("/player <name> follow tprange <blocks>");
            }
            int tpRange = parseIntWithMin(sender, args[1], 2);
            followService.setTeleportRange(tpRange);
            notifySender(sender, target.getCommandSenderName() + " 传送距离设置为 " + tpRange + " 格");
            return;
        default:
            // Follow a named player
            EntityPlayerMP followTarget = getPlayer(sender, args[0]);
            followService.startFollowing(followTarget.getUniqueID());
            notifySender(sender, FakePlayer.colorizeName(target.getCommandSenderName()) + " 开始跟随 " + followTarget.getCommandSenderName());
            return;
    }
}
```

Add the import at the top of `CommandPlayer.java`:

```java
import com.andgatech.gtstaff.fakeplayer.FollowService;
```

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew --offline test && ./gradlew --offline compileJava`
Expected: All tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java
git commit -m "feat: add /player follow command"
```

---

## Task 4: Persistence For Follow State

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Test: `./gradlew --offline test`

- [ ] **Step 1: Add follow-related NBT keys to FakePlayerRegistry**

After the existing key constants (line 36), add:

```java
private static final String FOLLOW_TARGET_KEY = "FollowTarget";
private static final String FOLLOW_RANGE_KEY = "FollowRange";
private static final String TELEPORT_RANGE_KEY = "TeleportRange";
```

- [ ] **Step 2: Add follow fields to PersistedBotData**

Add three new fields to the `PersistedBotData` class (after `monsterRepelRange` field, around line 64):

```java
private final UUID followTarget;
private final int followRange;
private final int teleportRange;
```

Update the constructor to accept these three parameters (add after `monsterRepelRange` parameter):

```java
UUID followTarget, int followRange, int teleportRange) {
    // ... existing assignments ...
    this.followTarget = followTarget;
    this.followRange = followRange;
    this.teleportRange = teleportRange;
}
```

Add getters:

```java
public UUID getFollowTarget() {
    return this.followTarget;
}

public int getFollowRange() {
    return this.followRange;
}

public int getTeleportRange() {
    return this.teleportRange;
}
```

- [ ] **Step 3: Update snapshot() to capture follow state**

In the `snapshot()` method, after the existing fields, get follow state from the FakePlayer:

```java
UUID followTarget = fakePlayer.isFollowing() ? fakePlayer.getFollowService().getFollowTargetUUID() : null;
int followRange = fakePlayer.getFollowService().getFollowRange();
int teleportRange = fakePlayer.getFollowService().getTeleportRange();
```

Add these as the last three arguments to the `PersistedBotData` constructor call:

```java
return new PersistedBotData(
    // ... existing 15 arguments ...
    followTarget,
    followRange,
    teleportRange);
```

- [ ] **Step 4: Update save() to write follow NBT**

In the `save()` method, after the `MONSTER_REPEL_RANGE_KEY` write (around line 243), add:

```java
if (data.getFollowTarget() != null) {
    bot.setString(FOLLOW_TARGET_KEY, data.getFollowTarget().toString());
}
bot.setInteger(FOLLOW_RANGE_KEY, data.getFollowRange());
bot.setInteger(TELEPORT_RANGE_KEY, data.getTeleportRange());
```

- [ ] **Step 5: Update load() to read follow NBT**

In the `load()` method, after reading `monsterRepelRange` (around line 295), add:

```java
UUID followTarget = null;
if (bot.hasKey(FOLLOW_TARGET_KEY)) {
    followTarget = UUID.fromString(bot.getString(FOLLOW_TARGET_KEY));
}
int followRange = bot.hasKey(FOLLOW_RANGE_KEY) ? bot.getInteger(FOLLOW_RANGE_KEY) : FollowService.DEFAULT_FOLLOW_RANGE;
int teleportRange = bot.hasKey(TELEPORT_RANGE_KEY) ? bot.getInteger(TELEPORT_RANGE_KEY) : FollowService.DEFAULT_TELEPORT_RANGE;
```

Add these as the last three arguments to the `PersistedBotData` constructor call in `load()`.

- [ ] **Step 6: Update restorePersisted to restore follow state**

In `restorePersisted(BotRestorer restorer)`, after setting monster repel range (around line 346), add:

```java
if (data.getFollowTarget() != null) {
    fakePlayer.getFollowService().setFollowRange(data.getFollowRange());
    fakePlayer.getFollowService().setTeleportRange(data.getTeleportRange());
    fakePlayer.getFollowService().startFollowing(data.getFollowTarget());
}
```

- [ ] **Step 7: Run tests and compile**

Run: `./gradlew --offline test && ./gradlew --offline compileJava`
Expected: All tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java
git commit -m "feat: persist follow state to registry"
```

---

## Task 5: Service Layer For UI

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Test: `./gradlew --offline test`

- [ ] **Step 1: Add following fields to BotDetails**

In the `BotDetails` class, add two new fields:

```java
public final boolean following;
public final int followRange;
public final int teleportRange;
```

Update the `BotDetails` constructor to accept and assign these three parameters. Add them after `monsterRepelRange`.

Update the `describeBot()` method — when the bot is online, add follow data:

```java
boolean following = fakePlayer.isFollowing();
int followRange = fakePlayer.getFollowService().getFollowRange();
int teleportRange = fakePlayer.getFollowService().getTeleportRange();
```

Pass these to the `BotDetails` constructor.

For the offline case (the fallback `BotDetails`), use `false, FollowService.DEFAULT_FOLLOW_RANGE, FollowService.DEFAULT_TELEPORT_RANGE`.

Add the import:

```java
import com.andgatech.gtstaff.fakeplayer.FollowService;
```

- [ ] **Step 2: Add follow service methods to FakePlayerManagerService**

Add these methods after `setMonsterRepelRange` (around line 340):

```java
public String startFollow(ICommandSender sender, String botName) {
    if (!(sender instanceof EntityPlayerMP player)) {
        throw new CommandException("Only players can be followed");
    }
    String normalizedBotName = requireBotName(botName);
    FakePlayer fakePlayer = findBot(normalizedBotName);
    if (fakePlayer == null) {
        throw new CommandException(buildOfflineBotMessage(normalizedBotName));
    }
    fakePlayer.getFollowService().startFollowing(player.getUniqueID());
    return FakePlayer.colorizeName(normalizedBotName) + " 开始跟随你";
}

public String stopFollow(ICommandSender sender, String botName) {
    String normalizedBotName = requireBotName(botName);
    FakePlayer fakePlayer = findBot(normalizedBotName);
    if (fakePlayer == null) {
        throw new CommandException(buildOfflineBotMessage(normalizedBotName));
    }
    fakePlayer.getFollowService().stop();
    fakePlayer.moveForward = 0.0F;
    fakePlayer.moveStrafing = 0.0F;
    fakePlayer.setJumping(false);
    return FakePlayer.colorizeName(normalizedBotName) + " 停止跟随";
}

public String setFollowRange(ICommandSender sender, String botName, int range) {
    String normalizedBotName = requireBotName(botName);
    FakePlayer fakePlayer = findBot(normalizedBotName);
    if (fakePlayer == null) {
        throw new CommandException(buildOfflineBotMessage(normalizedBotName));
    }
    fakePlayer.getFollowService().setFollowRange(range);
    return normalizedBotName + " 跟随距离设置为 " + range + " 格";
}

public String setTeleportRange(ICommandSender sender, String botName, int range) {
    String normalizedBotName = requireBotName(botName);
    FakePlayer fakePlayer = findBot(normalizedBotName);
    if (fakePlayer == null) {
        throw new CommandException(buildOfflineBotMessage(normalizedBotName));
    }
    fakePlayer.getFollowService().setTeleportRange(range);
    return normalizedBotName + " 传送距离设置为 " + range + " 格";
}
```

- [ ] **Step 3: Run tests and compile**

Run: `./gradlew --offline test && ./gradlew --offline compileJava`
Expected: All tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java
git commit -m "feat: add follow service methods to FakePlayerManagerService"
```

---

## Task 6: UI Follow Controls In Other Tab

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java`
- Test: `./gradlew --offline compileJava`

- [ ] **Step 1: Add follow section to buildOtherPage**

In `FakePlayerManagerUI.Holder.buildOtherPage()`, after the monster repel range buttons (after line 484) and before the description text widget, add the follow UI section:

```java
// ---- Follow Section ----
col.child(
    new TextWidget("假人跟随").top(2)
        .left(150)
        .size(80, 14));

col.child(
    new ButtonWidget<>().size(60, 18)
        .overlay(IKey.dynamic(() -> {
            if (!hasSelectedBot(service, state)) return "跟随我";
            FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
            return d.following ? "跟随中..." : "跟随我";
        }))
        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
            if (!hasSelectedBot(service, state)) {
                state.statusMessage = "请先选择一个假人。";
                return;
            }
            try {
                state.statusMessage = service.startFollow(player, state.selectedBotName);
            } catch (CommandException e) {
                state.statusMessage = e.getMessage();
            }
        }))
        .setEnabledIf(w -> hasSelectedBot(service, state))
        .top(18)
        .left(150));

col.child(
    new ButtonWidget<>().size(60, 18)
        .overlay(IKey.str("停止跟随"))
        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
            if (!hasSelectedBot(service, state)) {
                state.statusMessage = "请先选择一个假人。";
                return;
            }
            try {
                state.statusMessage = service.stopFollow(player, state.selectedBotName);
            } catch (CommandException e) {
                state.statusMessage = e.getMessage();
            }
        }))
        .setEnabledIf(w -> hasSelectedBot(service, state))
        .top(18)
        .left(214));

// Follow range buttons
col.child(
    new TextWidget("跟随距离:").top(40)
        .left(150)
        .size(50, 14));

String[] followRangeLabels = { "1", "3", "5", "8", "10" };
int[] followRangeValues = { 1, 3, 5, 8, 10 };
for (int i = 0; i < followRangeLabels.length; i++) {
    final int idx = i;
    final int range = followRangeValues[i];
    final String label = followRangeLabels[i];
    col.child(
        new ButtonWidget<>().size(22, 14)
            .overlay(IKey.dynamic(() -> {
                if (!hasSelectedBot(service, state)) return label;
                FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                return d.followRange == range ? "[" + label + "]" : label;
            }))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                if (!hasSelectedBot(service, state)) {
                    state.statusMessage = "请先选择一个假人。";
                    return;
                }
                try {
                    state.statusMessage = service.setFollowRange(player, state.selectedBotName, range);
                } catch (CommandException e) {
                    state.statusMessage = e.getMessage();
                }
            }))
            .setEnabledIf(w -> hasSelectedBot(service, state))
            .top(56)
            .left(150 + idx * 24));
}

// Teleport range buttons
col.child(
    new TextWidget("传送距离:").top(72)
        .left(150)
        .size(50, 14));

String[] tpRangeLabels = { "16", "32", "64", "96", "128" };
int[] tpRangeValues = { 16, 32, 64, 96, 128 };
for (int i = 0; i < tpRangeLabels.length; i++) {
    final int idx = i;
    final int range = tpRangeValues[i];
    final String label = tpRangeLabels[i];
    col.child(
        new ButtonWidget<>().size(24, 14)
            .overlay(IKey.dynamic(() -> {
                if (!hasSelectedBot(service, state)) return label;
                FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                return d.teleportRange == range ? "[" + label + "]" : label;
            }))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                if (!hasSelectedBot(service, state)) {
                    state.statusMessage = "请先选择一个假人。";
                    return;
                }
                try {
                    state.statusMessage = service.setTeleportRange(player, state.selectedBotName, range);
                } catch (CommandException e) {
                    state.statusMessage = e.getMessage();
                }
            }))
            .setEnabledIf(w -> hasSelectedBot(service, state))
            .top(88)
            .left(150 + idx * 26));
}

// Follow status text
col.child(
    new TextWidget(IKey.dynamic(() -> {
        if (!hasSelectedBot(service, state)) return "选择假人以查看跟随状态。";
        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
        if (!d.following) return "未跟随。\n点击"跟随我"让假人跟随你。";
        return "跟随中。\n跟随距离: " + d.followRange + " 格\n传送距离: " + d.teleportRange + " 格";
    })).top(106)
        .left(150)
        .size(140, 40));
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew --offline compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java
git commit -m "feat: add follow controls to Other tab in UI"
```

---

## Task 7: Update Context And Final Verification

**Files:**
- Modify: `D:\Code\GTstaff\context.md`
- Test: `./gradlew --offline test`
- Test: `./gradlew --offline compileJava`

- [ ] **Step 1: Update context.md**

在 `context.md` 的"已实现内容"部分，在"敌对生物驱逐"之后、"持久化"之前，添加：

```markdown
### 假人跟随
- `FollowService`：挂载在 `FakePlayer` 上，每 tick 在 `actionPack.onUpdate()` 之后、`runLivingUpdate()` 之前执行
- `FollowService.tick()`：检查目标在线 → 维度检查 → 距离判断 → 飞行同步 → 方向计算 → Y 轴控制
- 方向计算：`calculateMovement(fakeYaw, fromX, fromZ, toX, toZ)` 将目标方向转换为 moveForward / moveStrafing（基于 yaw 差的 cos/sin 分量）
- Y 轴控制：空中时 `setJumping(true)` 上升、`motionY -= 0.1` 下降，阈值 0.5 格
- 超距传送：距离 > teleportRange 时传送到玩家背后 2 格
- 跨维度传送：维度不同时等待 100 tick（5 秒），聊天栏通知玩家，计时结束后跨维度传送
- 飞行同步：跟随时自动将 `fakePlayer.capabilities.isFlying` 同步为目标玩家的飞行状态
- 参数：followRange（默认 3 格）、teleportRange（默认 32 格），可通过命令和 UI 调节
- 命令：`/player <name> follow [player|stop|range <n>|tprange <n>]`
- UI：Other 页签新增"跟随我"/"停止跟随"按钮 + 跟随距离和传送距离按钮组
- 持久化：followTarget（UUID）、followRange、teleportRange 写入 `data/gtstaff_registry.dat`；重启后自动恢复跟随
```

在"命令"部分的 `CommandPlayer` 支持列表末尾添加 `follow`。

在"架构备注"部分添加：

```markdown
- `FollowService.tick()` 在 `actionPack.onUpdate()` 之后执行，覆盖 actionPack 设置的 moveForward/moveStrafing；跟随优先级高于手动 move 命令
- 跨维度传送通过手动将假人从旧世界移除、在新世界 spawn 实现，不走 `transferPlayerToDimension`
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew --offline test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Run final compile**

Run: `./gradlew --offline compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add context.md
git commit -m "docs: update context with follow feature"
```

---

## Self-Review

### Spec coverage
- FollowService 核心逻辑（方向计算、距离判断、飞行同步、Y 轴控制）→ Task 1
- FakePlayer.onUpdate() 集成 → Task 2
- follow 命令 → Task 3
- 持久化 → Task 4
- UI 服务层 → Task 5
- UI 控件 → Task 6
- 跨维度延迟传送 → Task 1（FollowService 内部）
- 文档更新 → Task 7

### Placeholder scan
- 无 TODO / TBD / 占位语
- 所有代码步骤都有完整实现代码
- 所有命令都有明确的参数和用法

### Type consistency
- `FollowService` 的 `startFollowing(UUID)` / `stop()` / `getFollowRange()` / `setFollowRange(int)` / `getTeleportRange()` / `setTeleportRange(int)` 在 Task 1 定义，Task 3-6 使用一致
- `BotDetails` 新增 `following` / `followRange` / `teleportRange` 字段在 Task 5 添加，Task 6 UI 引用一致
- `FakePlayer.getFollowService()` 在 Task 2 添加，Task 3-5 引用一致
- `PersistedBotData` 新增字段在 Task 4 添加，save/load/restore 路径完整
