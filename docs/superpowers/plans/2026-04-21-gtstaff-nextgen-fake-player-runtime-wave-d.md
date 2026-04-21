# GTstaff NextGen Fake Player Runtime Wave D Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `nextgen` the default fake-player runtime without feature regression by moving `spawn / shadow / restore / manipulation commands` onto runtime-neutral infrastructure while preserving `legacy` and `mixed` rollback paths.

**Architecture:** Wave D adds one last runtime-facing slice for manipulation (`BotActionRuntime`), then introduces a lifecycle manager that chooses `legacy` vs `nextgen` factories for spawn, shadow, restore, and rollback. `CommandPlayer`, restore scheduling, and registry recovery switch to that lifecycle layer, `Config.fakePlayerRuntimeMode` defaults to `nextgen`, and the code keeps explicit `legacy` and `mixed` escape hatches instead of deleting the old implementation.

**Tech Stack:** Java 8, Forge 1.7.10, GTstaff fake-player runtime adapters, `PlayerActionPack`, Forge `FakePlayer`, JUnit 5

---

## Scope Check

The approved spec allows default cutover only when all of the following are true:

1. `spawn / shadow / restore / attack / use / inventory / follow / monitor / repel` stay usable without changing command or UI syntax
2. `legacy` and `mixed` remain available as runtime fallback modes
3. Registry recovery can restore persisted bots into the configured runtime without losing state
4. If `nextgen` is unstable in a pack, a bot can still be restored via the legacy path

Because Wave C deliberately left `handleManipulation(...)` on the legacy `FakePlayer` path, Wave D must include a new action facade in addition to spawn/shadow/restore cutover. A plan that only switched creation and restore would fail the spec’s “no functional downgrade” rule.

## File Structure

### Create

- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotActionRuntime.java`
  - Runtime-neutral manipulation surface for `attack / use / jump / drop / move / look / turn / sneak / sprint / mount / hotbar / stop`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyActionRuntime.java`
  - Adapter from legacy `PlayerActionPack` and `FakePlayer` movement state to `BotActionRuntime`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenActionRuntime.java`
  - Nextgen manipulation adapter backed by a `PlayerActionPack` mounted on `GTstaffForgePlayer`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeMode.java`
  - Parser/normalizer for `legacy / nextgen / mixed`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotLifecycleManager.java`
  - Single entry point for spawn, shadow, restore, kill, and rollback decisions based on runtime mode.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotFactory.java`
  - Creates `GTstaffForgePlayer + BotSession + NextGenBotRuntime`, clones shadow state, restores persisted snapshots, and re-registers runtime metadata.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleActionTest.java`
  - Verifies legacy runtime exposes manipulation through `BotActionRuntime`.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotFactoryTest.java`
  - Verifies spawn/shadow/restore creation for nextgen bots and state application.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotLifecycleManagerTest.java`
  - Verifies `legacy / nextgen / mixed` mode routing and rollback behavior.

### Modify

- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java`
  - Add `action()` accessor.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java`
  - Expose `LegacyActionRuntime`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/GTstaffForgePlayer.java`
  - Implement `IFakePlayerHolder`-style action-pack access, hold owner UUID, and bind lifecycle helpers needed by nextgen spawn/restore.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java`
  - Hold owner UUID, `BotActionRuntime`, and lifecycle metadata required by registry/save/restore.
- `src/main/java/com/andgatech/gtstaff/config/Config.java`
  - Change default `fakePlayerRuntimeMode` to `nextgen`, validate allowed values.
- `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
  - Route `spawn`, `shadow`, `kill`, and manipulation subcommands through runtime-neutral lifecycle/action helpers.
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
  - Keep UI behavior unchanged while letting `executeAction`, `spawn`, and `shadow` work with nextgen defaults.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
  - Generalize snapshot/update/restore entry points so persisted runtime type and online runtime registration are not legacy-only.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java`
  - Restore `BotRuntimeView` instead of `FakePlayer`, and only hand legacy-style skin rebuild to runtimes that need it.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java`
  - Support runtime-aware rebuild/rebind entry points instead of `FakePlayer` only.
- `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`
  - Add nextgen-default spawn/manipulation coverage.
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`
  - Add UI-facing nextgen-default spawn/action coverage.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
  - Add persisted nextgen restore and rollback expectations.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java`
  - Update to runtime-aware restore scheduling.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`
  - Update to runtime-aware rebuild logic.
- `log.md`
  - Add Wave D plan entry at the top.
- `ToDOLIST.md`
  - Record Wave D plan writing.
- `context.md`
  - Add Wave D plan path and next planned default-cutover notes.

## Task 1: Add a Runtime-Neutral Manipulation Facade Before Default Cutover

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotActionRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyActionRuntime.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleActionTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java`

- [ ] **Step 1: Write the failing action-facade test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;
import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;

class LegacyBotHandleActionTest {

    @Test
    void legacyHandleExposesActionFacade() {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.actionPack = new PlayerActionPack(fakePlayer);

        LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

        assertNotNull(handle.action());
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return type.cast(unsafe.allocateInstance(type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubFakePlayer extends FakePlayer implements IFakePlayerHolder {

        private PlayerActionPack actionPack;

        private StubFakePlayer() {
            super(null, null, "stub");
        }

        @Override
        public PlayerActionPack getActionPack() {
            return actionPack;
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleActionTest
```

Expected: FAIL because `BotRuntimeView` and `LegacyBotHandle` do not expose `action()` yet.

- [ ] **Step 3: Add the runtime-neutral action interface**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;

import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;

public interface BotActionRuntime {

    void start(ActionType type, Action action) throws CommandException;

    void stop(ActionType type) throws CommandException;

    void stopAll() throws CommandException;

    void setSlot(int slot) throws CommandException;

    void setForward(float value) throws CommandException;

    void setStrafing(float value) throws CommandException;

    void stopMovement() throws CommandException;

    void look(float yaw, float pitch) throws CommandException;

    void turn(float yaw, float pitch) throws CommandException;

    void setSneaking(boolean value) throws CommandException;

    void setSprinting(boolean value) throws CommandException;

    void dismount() throws CommandException;

    boolean supportsMount();
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotRuntimeView extends BotHandle {

    boolean online();

    BotActionRuntime action();

    BotFollowRuntime follow();

    BotMonitorRuntime monitor();

    BotRepelRuntime repel();

    BotInventoryRuntime inventory();
}
```

- [ ] **Step 4: Implement the legacy adapter**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;

import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

final class LegacyActionRuntime implements BotActionRuntime {

    private final FakePlayer fakePlayer;

    LegacyActionRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public void start(ActionType type, Action action) throws CommandException {
        pack().start(type, action);
    }

    @Override
    public void stop(ActionType type) throws CommandException {
        pack().stop(type);
    }

    @Override
    public void stopAll() throws CommandException {
        pack().stopAll();
    }

    @Override
    public void setSlot(int slot) throws CommandException {
        pack().setSlot(slot);
    }

    @Override
    public void setForward(float value) throws CommandException {
        pack().setForward(value);
    }

    @Override
    public void setStrafing(float value) throws CommandException {
        pack().setStrafing(value);
    }

    @Override
    public void stopMovement() throws CommandException {
        pack().stopMovement();
    }

    @Override
    public void look(float yaw, float pitch) throws CommandException {
        pack().look(yaw, pitch);
    }

    @Override
    public void turn(float yaw, float pitch) throws CommandException {
        pack().turn(yaw, pitch);
    }

    @Override
    public void setSneaking(boolean value) throws CommandException {
        pack().setSneaking(value);
    }

    @Override
    public void setSprinting(boolean value) throws CommandException {
        pack().setSprinting(value);
    }

    @Override
    public void dismount() throws CommandException {
        fakePlayer.mountEntity((net.minecraft.entity.Entity) null);
    }

    @Override
    public boolean supportsMount() {
        return true;
    }

    private PlayerActionPack pack() throws CommandException {
        if (!(fakePlayer instanceof IFakePlayerHolder holder) || holder.getActionPack() == null) {
            throw new CommandException("Action pack is unavailable for " + fakePlayer.getCommandSenderName());
        }
        return holder.getActionPack();
    }
}
```

```java
private final BotActionRuntime action;

public LegacyBotHandle(FakePlayer fakePlayer) {
    this.fakePlayer = fakePlayer;
    this.action = new LegacyActionRuntime(fakePlayer);
    this.follow = new LegacyFollowRuntime(fakePlayer);
    this.monitor = new LegacyMonitorRuntime(fakePlayer);
    this.repel = new LegacyRepelRuntime(fakePlayer);
    this.inventory = new LegacyInventoryRuntime(fakePlayer);
}

@Override
public BotActionRuntime action() {
    return action;
}
```

- [ ] **Step 5: Run the focused test to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleActionTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotActionRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyActionRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleActionTest.java
git commit -m "feat: add runtime-neutral bot action facade"
```

## Task 2: Implement NextGen Creation and Manipulation Support

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeMode.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenActionRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotFactory.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotFactoryTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/GTstaffForgePlayer.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java`

- [ ] **Step 1: Write the failing nextgen factory test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.Test;

class NextGenBotFactoryTest {

    @Test
    void spawnCreatesNextGenRuntimeWithBoundActionPack() {
        TestHarness harness = new TestHarness();
        NextGenBotRuntime runtime = harness.factory.spawn(
            "WaveDBot",
            harness.server,
            new ChunkCoordinates(10, 64, 12),
            90.0F,
            0.0F,
            0,
            WorldSettings.GameType.SURVIVAL,
            false,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertNotNull(runtime);
        assertEquals(BotRuntimeType.NEXTGEN, runtime.runtimeType());
        assertNotNull(runtime.action());
        assertSame(runtime, runtime.entity().asPlayer().runtime());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotFactoryTest
```

Expected: FAIL because no nextgen factory or action-backed player binding exists yet.

- [ ] **Step 3: Add runtime mode parsing**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.Locale;

public enum BotRuntimeMode {
    LEGACY,
    NEXTGEN,
    MIXED;

    public static BotRuntimeMode fromConfig(String rawValue) {
        if (rawValue == null) {
            return NEXTGEN;
        }
        try {
            return BotRuntimeMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NEXTGEN;
        }
    }

    public boolean prefersNextGen() {
        return this == NEXTGEN || this == MIXED;
    }
}
```

- [ ] **Step 4: Make `GTstaffForgePlayer` action-capable and owner-aware**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;
import com.mojang.authlib.GameProfile;

public class GTstaffForgePlayer extends FakePlayer implements IFakePlayerHolder {

    private NextGenBotRuntime runtime;
    private PlayerActionPack actionPack;
    private UUID ownerUUID;

    public GTstaffForgePlayer(MinecraftServer server, WorldServer world, GameProfile profile) {
        super(world, profile);
        this.actionPack = new PlayerActionPack(this);
    }

    public void bindRuntime(NextGenBotRuntime runtime) {
        this.runtime = runtime;
    }

    public NextGenBotRuntime runtime() {
        return runtime;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    public PlayerActionPack getActionPack() {
        return actionPack;
    }
}
```

- [ ] **Step 5: Implement nextgen manipulation and creation**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;

import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

final class NextGenActionRuntime implements BotActionRuntime {

    private final GTstaffForgePlayer player;

    NextGenActionRuntime(GTstaffForgePlayer player) {
        this.player = player;
    }

    @Override
    public void start(ActionType type, Action action) throws CommandException {
        pack().start(type, action);
    }

    @Override
    public void stop(ActionType type) throws CommandException {
        pack().stop(type);
    }

    @Override
    public void stopAll() throws CommandException {
        pack().stopAll();
    }

    @Override
    public void setSlot(int slot) throws CommandException {
        pack().setSlot(slot);
    }

    @Override
    public void setForward(float value) throws CommandException {
        pack().setForward(value);
    }

    @Override
    public void setStrafing(float value) throws CommandException {
        pack().setStrafing(value);
    }

    @Override
    public void stopMovement() throws CommandException {
        pack().stopMovement();
    }

    @Override
    public void look(float yaw, float pitch) throws CommandException {
        pack().look(yaw, pitch);
    }

    @Override
    public void turn(float yaw, float pitch) throws CommandException {
        pack().turn(yaw, pitch);
    }

    @Override
    public void setSneaking(boolean value) throws CommandException {
        pack().setSneaking(value);
    }

    @Override
    public void setSprinting(boolean value) throws CommandException {
        pack().setSprinting(value);
    }

    @Override
    public void dismount() throws CommandException {
        player.mountEntity((net.minecraft.entity.Entity) null);
    }

    @Override
    public boolean supportsMount() {
        return true;
    }

    private PlayerActionPack pack() throws CommandException {
        if (player.getActionPack() == null) {
            throw new CommandException("Action pack is unavailable for " + player.getCommandSenderName());
        }
        return player.getActionPack();
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import com.andgatech.gtstaff.fakeplayer.FakePlayerProfiles;
import com.mojang.authlib.GameProfile;

public final class NextGenBotFactory {

    public NextGenBotRuntime spawn(String botName, MinecraftServer server, ChunkCoordinates position, float yaw,
        float pitch, int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        GameProfile profile = FakePlayerProfiles.createSpawnProfile(botName);
        WorldServer world = server.worldServerForDimension(dimension);
        GTstaffForgePlayer player = new GTstaffForgePlayer(server, world, profile);
        player.setOwnerUUID(ownerUUID);
        player.setPositionAndRotation(position.posX + 0.5D, position.posY, position.posZ + 0.5D, yaw, pitch);
        player.capabilities.isFlying = flying;
        player.theItemInWorldManager.setGameType(gameType);
        BotSession session = new BotSession(player);
        NextGenBotRuntime runtime = new NextGenBotRuntime(player, session, ownerUUID);
        session.attach(server);
        return runtime;
    }

    public NextGenBotRuntime shadow(MinecraftServer server, EntityPlayerMP sourcePlayer) {
        // same profile/position clone path as legacy shadow, but return runtime instead of FakePlayer
        return null;
    }
}
```

- [ ] **Step 6: Update `NextGenBotRuntime` to expose owner and action facade**

```java
private final UUID ownerUUID;
private final BotActionRuntime action;

public NextGenBotRuntime(GTstaffForgePlayer player, BotSession session, UUID ownerUUID) {
    this.player = player;
    this.session = session;
    this.ownerUUID = ownerUUID;
    this.action = new NextGenActionRuntime(player);
    this.follow = new NextGenFollowRuntime();
    this.monitor = new NextGenMonitorRuntime();
    this.repel = new NextGenRepelRuntime();
    this.inventory = new NextGenInventoryRuntime(player);
    this.player.bindRuntime(this);
}

@Override
public UUID ownerUUID() {
    return ownerUUID;
}

@Override
public BotActionRuntime action() {
    return action;
}
```

- [ ] **Step 7: Run the focused nextgen tests**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotFactoryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntimeServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeMode.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenActionRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotFactory.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/GTstaffForgePlayer.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotFactoryTest.java
git commit -m "feat: add nextgen bot factory and action runtime"
```

## Task 3: Make Registry Recovery and Restore Scheduling Runtime-Aware

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotLifecycleManager.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotLifecycleManagerTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`

- [ ] **Step 1: Write the failing lifecycle-routing test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BotLifecycleManagerTest {

    @Test
    void mixedModeRestoresPersistedLegacySnapshotsAsLegacyButUsesNextGenForFreshSpawn() {
        RecordingLifecycleManager manager = new RecordingLifecycleManager(BotRuntimeMode.MIXED);

        manager.spawn("WaveDSpawnBot", UUID.randomUUID());
        manager.restore(BotRuntimeType.LEGACY, "WaveDRestoreBot", UUID.randomUUID());

        assertEquals("spawn:nextgen", manager.events.get(0));
        assertEquals("restore:legacy", manager.events.get(1));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.BotLifecycleManagerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest
```

Expected: FAIL because lifecycle routing and runtime-aware restore scheduling do not exist yet.

- [ ] **Step 3: Add lifecycle routing**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldSettings;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

public final class BotLifecycleManager {

    private final NextGenBotFactory nextGenFactory = new NextGenBotFactory();

    public BotRuntimeView spawn(String botName, MinecraftServer server, ChunkCoordinates position, float yaw, float pitch,
        int dimension, WorldSettings.GameType gameType, boolean flying, UUID ownerUUID) {
        BotRuntimeMode mode = BotRuntimeMode.fromConfig(Config.fakePlayerRuntimeMode);
        if (!mode.prefersNextGen()) {
            return FakePlayer.createFake(botName, server, position, yaw, pitch, dimension, gameType, flying).asRuntimeView();
        }
        NextGenBotRuntime runtime = nextGenFactory.spawn(botName, server, position, yaw, pitch, dimension, gameType, flying,
            ownerUUID);
        FakePlayerRegistry.registerRuntime(runtime);
        return runtime;
    }

    public BotRuntimeView restore(MinecraftServer server, FakePlayerRegistry.PersistedBotData data) {
        BotRuntimeMode mode = BotRuntimeMode.fromConfig(Config.fakePlayerRuntimeMode);
        if (mode == BotRuntimeMode.LEGACY || data.getRuntimeType() == BotRuntimeType.LEGACY && mode == BotRuntimeMode.MIXED) {
            FakePlayer restored = FakePlayer.restorePersisted(server, data);
            return restored == null ? null : restored.asRuntimeView();
        }
        NextGenBotRuntime runtime = nextGenFactory.restore(server, data);
        FakePlayerRegistry.registerRuntime(runtime);
        return runtime;
    }

    public BotRuntimeView shadow(MinecraftServer server, EntityPlayerMP sourcePlayer) throws CommandException {
        BotRuntimeMode mode = BotRuntimeMode.fromConfig(Config.fakePlayerRuntimeMode);
        if (!mode.prefersNextGen()) {
            return FakePlayer.createShadow(server, sourcePlayer).asRuntimeView();
        }
        NextGenBotRuntime runtime = nextGenFactory.shadow(server, sourcePlayer);
        FakePlayerRegistry.registerRuntime(runtime);
        return runtime;
    }
}
```

- [ ] **Step 4: Generalize registry and restore scheduler**

```java
@FunctionalInterface
public interface RuntimeRestorer {

    BotRuntimeView restore(PersistedBotData data);
}

public static List<BotRuntimeView> restorePersisted(RuntimeRestorer restorer) {
    List<BotRuntimeView> restored = new ArrayList<BotRuntimeView>();
    for (PersistedBotData data : persistedBots.values()) {
        BotRuntimeView runtime = restorer.restore(data);
        if (runtime != null) {
            restored.add(runtime);
            registerRuntime(runtime);
        }
    }
    return restored;
}
```

```java
private static Function<MinecraftServer, List<BotRuntimeView>> restoreAction = server ->
    FakePlayerRegistry.restorePersisted(data -> new BotLifecycleManager().restore(server, data));
```

```java
for (BotRuntimeView runtime : restoredBots) {
    if (runtime != null && runtime.entity().asPlayer() instanceof FakePlayer fakePlayer) {
        skinScheduleAction.accept(server, fakePlayer);
    }
}
```

- [ ] **Step 5: Keep skin rebuild runtime-aware**

```java
public static void schedule(MinecraftServer server, BotRuntimeView runtime) {
    if (!(runtime.entity().asPlayer() instanceof FakePlayer fakePlayer)) {
        return;
    }
    schedule(server, fakePlayer);
}
```

```java
FakePlayerRestoreScheduler.setSkinScheduleActionForTesting((minecraftServer, runtime) -> scheduledBots.add(runtime.name()));
```

- [ ] **Step 6: Run the registry and restore regression tests**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.BotLifecycleManagerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotLifecycleManager.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotLifecycleManagerTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java
git commit -m "feat: make bot restore lifecycle runtime-aware"
```

## Task 4: Switch Commands, UI, and Config Defaults to the Lifecycle Layer

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/config/Config.java`
- Modify: `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Modify: `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`
- Modify: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: Write the failing nextgen-default command tests**

```java
@Test
void spawnUsesNextGenLifecycleWhenRuntimeModeDefaultsToNextGen() {
    Config.fakePlayerRuntimeMode = "nextgen";
    RecordingLifecycleManager lifecycleManager = new RecordingLifecycleManager();
    CommandPlayer command = new CommandPlayer(lifecycleManager);

    command.processCommand(sender(), new String[] { "WaveDSpawnBot", "spawn" });

    assertEquals("spawn:nextgen", lifecycleManager.events.get(0));
}

@Test
void attackRoutesThroughRuntimeActionFacadeWhenBotIsNextGenOnly() {
    StubRuntimeView runtime = nextGenRuntime("WaveDActionBot");
    FakePlayerRegistry.registerRuntime(runtime);

    new CommandPlayer().processCommand(sender(), new String[] { "WaveDActionBot", "attack", "once" });

    assertEquals(ActionType.ATTACK, runtime.lastStartedType);
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest
```

Expected: FAIL because spawn and manipulation still route directly to legacy classes.

- [ ] **Step 3: Change config default and command creation path**

```java
public static String fakePlayerRuntimeMode = "nextgen";
```

```java
protected void handleSpawn(ICommandSender sender, String botName, String[] args) {
    SpawnOptions options = parseSpawnOptions(sender, args);
    BotRuntimeView runtime = lifecycleManager.spawn(
        botName,
        requireServer(),
        options.position,
        options.yaw,
        options.pitch,
        options.dimension,
        options.gameType,
        options.flying,
        sender instanceof EntityPlayerMP player ? player.getUniqueID() : null);
    notifySender(sender, "Spawned fake player " + runtime.name() + " at " + formatPosition(runtime.entity().asPlayer()));
}
```

```java
protected void handleShadow(ICommandSender sender, String botName) {
    EntityPlayerMP realPlayer = getPlayer(sender, botName);
    BotRuntimeView runtime = lifecycleManager.shadow(requireServer(), realPlayer);
    notifySender(sender, "Created shadow fake player " + runtime.name());
}
```

- [ ] **Step 4: Route manipulation commands through `BotActionRuntime`**

```java
protected void handleManipulation(ICommandSender sender, String botName, String action, String[] args) {
    BotRuntimeView runtime = requireOnlineRuntime(botName);
    if (runtime.entity().asPlayer() instanceof FakePlayer target && PermissionHelper.cantManipulate(sender, target)) {
        throw new CommandException("You do not have permission to control that bot");
    }

    BotActionRuntime actionRuntime = runtime.action();
    switch (action.toLowerCase(Locale.ROOT)) {
        case "attack":
            actionRuntime.start(ActionType.ATTACK, parseAction(sender, args));
            notifySender(sender, "Set attack action for " + runtime.name());
            return;
        case "use":
            actionRuntime.start(ActionType.USE, parseAction(sender, args));
            notifySender(sender, "Set use action for " + runtime.name());
            return;
        case "stopattack":
            actionRuntime.stop(ActionType.ATTACK);
            notifySender(sender, "Stopped attack for " + runtime.name());
            return;
        case "stopuse":
            actionRuntime.stop(ActionType.USE);
            notifySender(sender, "Stopped use for " + runtime.name());
            return;
        case "hotbar":
            actionRuntime.setSlot(parseIntBounded(sender, args[0], 1, 9));
            notifySender(sender, runtime.name() + " switched hotbar slot");
            return;
        case "stop":
            actionRuntime.stopAll();
            notifySender(sender, runtime.name() + " stopped all actions");
            return;
        default:
            // keep move/look/turn/sneak/sprint/mount branches, but route through actionRuntime
    }
}
```

- [ ] **Step 5: Keep the UI behavior unchanged while using runtime-aware commands**

```java
public String executeAction(ICommandSender sender, String botName, String action) {
    String normalizedBotName = requireBotName(botName);
    String normalizedAction = requireAction(action);
    this.commandRunner.run(sender, new String[] { normalizedBotName, normalizedAction });
    return "Executed " + normalizedAction + " for " + normalizedBotName + ".";
}
```

```java
public String submitSpawn(ICommandSender sender, SpawnDraft draft) {
    String botName = requireBotName(draft.botName);
    this.commandRunner.run(sender, buildSpawnArgs(botName, draft));
    return "Spawned fake player " + botName + ".";
}
```

- [ ] **Step 6: Run the command/UI regression tests**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleActionTest --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntimeServicesTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/config/Config.java src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java
git commit -m "feat: switch commands and ui to nextgen lifecycle"
```

## Task 5: Run the Wave D Regression Gate and Refresh Project Docs

**Files:**
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: Run the Wave D regression suite**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleActionTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotFactoryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntimeServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotLifecycleManagerTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the Wave D build smoke test**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble
```

Expected: `BUILD SUCCESSFUL` and refreshed jars under `build/libs/`.

- [ ] **Step 3: Update `log.md`**

```markdown
## 2026-04-21：完成 nextgen fake player runtime Wave D 默认切换与回退闭环

### 已完成
- 默认 `fakePlayerRuntimeMode` 改为 `nextgen`
- `spawn / shadow / restore / manipulation` 现已走 runtime-neutral lifecycle + action facade
- 保留 `legacy` 与 `mixed` 回退模式，registry 恢复与 restore scheduler 已支持按 runtime 解释
- Wave D 回归测试与离线 `assemble` 均通过

### 遇到的问题
- `handleManipulation(...)` 原先仍直接依赖 legacy `FakePlayer`，若不先补 `BotActionRuntime` 就无法切默认 runtime

### 做出的决定
- 保留 legacy 实现作为显式 fallback，不在 Wave D 删除旧链路
```

- [ ] **Step 4: Update `ToDOLIST.md`**

```markdown
## 当前计划
- [ ] 为 GTstaff 假人背包管理界面接入 Baubles Expanded 饰品栏支持：在现有统一背包容器中合并展示并编辑假人的饰品槽，槽位数量、类型与滚动布局跟随 Baubles Expanded 当前配置

## 已完成
- [x] 完成 nextgen fake player runtime Wave D：将默认 runtime 切到 `nextgen`，并保留 `legacy / mixed` 回退
```

- [ ] **Step 5: Update `context.md`**

```markdown
- nextgen fake player runtime 的第四阶段计划已写入 `docs/superpowers/plans/2026-04-21-gtstaff-nextgen-fake-player-runtime-wave-d.md`；Wave D 完成后默认 runtime 已切到 `nextgen`，且 `legacy / mixed` 仍可回退
- `CommandPlayer` 的 `spawn / shadow / attack / use / jump / move / look / hotbar / stop` 现已走 runtime-neutral lifecycle/action facade
- `FakePlayerRegistry.restorePersisted(...)` 与 `FakePlayerRestoreScheduler` 现按配置 runtime 与 persisted runtime type 共同决定恢复路径
```

- [ ] **Step 6: Commit**

```bash
git add log.md ToDOLIST.md context.md
git commit -m "docs: record wave d runtime cutover"
```

## Self-Review

- Spec coverage: this plan now covers the missing Wave D prerequisite that the conservative idea initially skipped, namely manipulation parity through `BotActionRuntime`, in addition to `spawn / shadow / restore / default runtime / rollback`.
- Placeholder scan: no `TODO`, `TBD`, or “implement later” instructions remain in the plan body.
- Type consistency: `BotActionRuntime`, `BotRuntimeMode`, `BotLifecycleManager`, and `NextGenBotFactory` are used consistently across later tasks with the same names and responsibilities.
