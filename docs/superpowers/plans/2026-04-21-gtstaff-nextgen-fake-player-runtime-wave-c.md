# GTstaff NextGen Fake Player Runtime Wave C Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate fake-player business services to runtime facades so `follow / monitor / repel / inventory / UI manager` stop hard-coding `FakePlayer` as the only online bot implementation.

**Architecture:** Wave C keeps spawn, shadow, restore, and action execution on the existing legacy path, but moves service-layer reads and writes behind `BotRuntimeView` sub-services. `LegacyBotHandle` becomes the full legacy adapter, `NextGenBotRuntime` grows stateful service shells, and `CommandPlayer` plus `FakePlayerManagerService` switch to runtime-facing helpers for service operations.

**Tech Stack:** Java 8, Forge 1.7.10, existing GTstaff fake-player services, JUnit 5, GTNHLib UI integration, Forge `IGuiHandler`

---

## Scope Check

The approved spec splits the runtime migration into four waves. This plan intentionally covers **Wave C only**:

1. Add runtime-facing business service interfaces for follow, monitor, repel, and inventory access
2. Adapt the legacy `FakePlayer` implementation through `LegacyBotHandle`
3. Switch `FakePlayerManagerService` to use runtime facades instead of directly reading `FakePlayer`
4. Switch `CommandPlayer` service subcommands (`follow / monitor / repel / inventory`) to runtime facades
5. Give `NextGenBotRuntime` stateful service shells so Wave D has a stable target

This plan does **not** switch default runtime mode, rewrite spawn/shadow/restore, or remove legacy classes.

## File Structure

### Create

- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotFollowRuntime.java`
  - Runtime interface for follow state and follow commands.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotMonitorRuntime.java`
  - Runtime interface for monitor toggles, range, interval, and overview text.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRepelRuntime.java`
  - Runtime interface for monster-repel state and range.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotInventoryRuntime.java`
  - Runtime interface for selected slot, inventory summary snapshot, and inventory-manager opening.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotInventorySummary.java`
  - Runtime-neutral inventory snapshot used by command/UI code.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyFollowRuntime.java`
  - Adapter from `FakePlayer.getFollowService()` to `BotFollowRuntime`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyMonitorRuntime.java`
  - Adapter from `MachineMonitorService` and legacy monitor fields to `BotMonitorRuntime`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyRepelRuntime.java`
  - Adapter from legacy monster-repel fields to `BotRepelRuntime`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyInventoryRuntime.java`
  - Adapter from legacy inventory/open-gui logic to `BotInventoryRuntime`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenFollowRuntime.java`
  - In-memory nextgen follow state shell.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenMonitorRuntime.java`
  - In-memory nextgen monitor state shell.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenRepelRuntime.java`
  - In-memory nextgen repel state shell.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenInventoryRuntime.java`
  - Nextgen inventory shell returning stable “unsupported yet” behavior until Wave D.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleServicesTest.java`
  - Service-level tests for the legacy runtime adapters.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntimeServicesTest.java`
  - State-holder tests for nextgen runtime shells.

### Modify

- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java`
  - Expose `follow()`, `monitor()`, `repel()`, and `inventory()`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java`
  - Instantiate legacy service adapters and expose them through `BotRuntimeView`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java`
  - Hold stateful nextgen service shells and owner identity.
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
  - Replace direct `FakePlayer` reads/writes in service methods with runtime facades.
- `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
  - Route service subcommands through runtime facades while leaving spawn/manipulation on legacy.
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`
  - Update service tests to assert runtime-facade behavior.
- `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`
  - Add command coverage for runtime-based service handling.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
  - Keep registry/runtime-handle expectations aligned with Wave C helpers.
- `log.md`
  - Add Wave C plan entry at the top.
- `ToDOLIST.md`
  - Record Wave C plan writing completion.
- `context.md`
  - Add Wave C plan path and runtime service-facade notes.

## Task 1: Define Runtime Service Contracts Before Touching Commands or UI

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotFollowRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotMonitorRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRepelRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotInventoryRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotInventorySummary.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java`
- Test: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleServicesTest.java`

- [ ] **Step 1: Write the failing runtime-contract test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;

import net.minecraft.entity.player.InventoryPlayer;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

class LegacyBotHandleServicesTest {

    @Test
    void legacyHandleExposesServiceFacades() {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.name = "WaveCBot";
        fakePlayer.inventory = new InventoryPlayer(fakePlayer);

        LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

        assertNotNull(handle.follow());
        assertNotNull(handle.monitor());
        assertNotNull(handle.repel());
        assertNotNull(handle.inventory());
        assertFalse(handle.follow().following());
        assertEquals(0, handle.inventory().selectedHotbarSlot());
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

    private static final class StubFakePlayer extends FakePlayer {

        private String name;

        private StubFakePlayer() {
            super(null, null, null, null);
        }

        @Override
        public String getCommandSenderName() {
            return this.name;
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest
```

Expected: FAIL because `BotRuntimeView` does not expose service accessors yet.

- [ ] **Step 3: Add the minimal runtime service interfaces and summary value type**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

public interface BotFollowRuntime {

    boolean following();

    UUID targetUUID();

    int followRange();

    int teleportRange();

    void startFollowing(UUID targetUUID);

    void stop();

    void setFollowRange(int range);

    void setTeleportRange(int range);
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotMonitorRuntime {

    boolean monitoring();

    int monitorRange();

    int reminderInterval();

    void setMonitoring(boolean monitoring);

    void setMonitorRange(int range);

    void setReminderInterval(int ticks);

    String overviewMessage(String botName);
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotRepelRuntime {

    boolean repelling();

    int repelRange();

    void setRepelling(boolean repelling);

    void setRepelRange(int range);
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.List;

public final class BotInventorySummary {

    private final String botName;
    private final int selectedHotbarSlot;
    private final List<String> hotbarLines;
    private final List<String> mainInventoryLines;
    private final List<String> armorLines;

    public BotInventorySummary(String botName, int selectedHotbarSlot, List<String> hotbarLines,
        List<String> mainInventoryLines, List<String> armorLines) {
        this.botName = botName;
        this.selectedHotbarSlot = selectedHotbarSlot;
        this.hotbarLines = hotbarLines;
        this.mainInventoryLines = mainInventoryLines;
        this.armorLines = armorLines;
    }

    public String botName() { return botName; }

    public int selectedHotbarSlot() { return selectedHotbarSlot; }

    public List<String> hotbarLines() { return hotbarLines; }

    public List<String> mainInventoryLines() { return mainInventoryLines; }

    public List<String> armorLines() { return armorLines; }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

public interface BotInventoryRuntime {

    int selectedHotbarSlot();

    BotInventorySummary summary();

    String openInventoryManager(EntityPlayerMP player) throws CommandException;
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotRuntimeView extends BotHandle {

    boolean online();

    BotFollowRuntime follow();

    BotMonitorRuntime monitor();

    BotRepelRuntime repel();

    BotInventoryRuntime inventory();
}
```

- [ ] **Step 4: Run the test again to verify interface wiring still fails in the expected place**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest
```

Expected: FAIL because `LegacyBotHandle` does not implement the new service methods yet.

- [ ] **Step 5: Commit the contract-only change**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotFollowRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotMonitorRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRepelRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotInventoryRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotInventorySummary.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleServicesTest.java
git commit -m "feat: define bot runtime service contracts"
```

## Task 2: Adapt Legacy `FakePlayer` Services Through `LegacyBotHandle`

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyFollowRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyMonitorRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyRepelRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyInventoryRuntime.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java`
- Test: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleServicesTest.java`

- [ ] **Step 1: Extend the failing test to assert real legacy behavior**

```java
@Test
void legacyHandleDelegatesMonitorFollowRepelAndInventory() {
    StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
    fakePlayer.name = "WaveCLegacy";
    fakePlayer.inventory = new InventoryPlayer(fakePlayer);
    fakePlayer.inventory.currentItem = 4;
    fakePlayer.setMonitorRange(32);
    fakePlayer.setReminderInterval(200);
    fakePlayer.setMonitoring(true);
    fakePlayer.setMonsterRepelling(true);
    fakePlayer.setMonsterRepelRange(96);

    LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

    assertEquals(4, handle.inventory().selectedHotbarSlot());
    assertTrue(handle.monitor().monitoring());
    assertEquals(32, handle.monitor().monitorRange());
    assertEquals(200, handle.monitor().reminderInterval());
    assertTrue(handle.repel().repelling());
    assertEquals(96, handle.repel().repelRange());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest
```

Expected: FAIL because `LegacyBotHandle` still has no service adapter implementation.

- [ ] **Step 3: Implement the minimal legacy adapters**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FollowService;

final class LegacyFollowRuntime implements BotFollowRuntime {

    private final FakePlayer fakePlayer;

    LegacyFollowRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public boolean following() {
        return fakePlayer.isFollowing();
    }

    @Override
    public UUID targetUUID() {
        FollowService service = fakePlayer.getFollowService();
        return service == null ? null : service.getFollowTargetUUID();
    }

    @Override
    public int followRange() {
        FollowService service = fakePlayer.getFollowService();
        return service == null ? FollowService.DEFAULT_FOLLOW_RANGE : service.getFollowRange();
    }

    @Override
    public int teleportRange() {
        FollowService service = fakePlayer.getFollowService();
        return service == null ? FollowService.DEFAULT_TELEPORT_RANGE : service.getTeleportRange();
    }

    @Override
    public void startFollowing(UUID targetUUID) {
        fakePlayer.getFollowService().startFollowing(targetUUID);
    }

    @Override
    public void stop() {
        fakePlayer.getFollowService().stop();
    }

    @Override
    public void setFollowRange(int range) {
        fakePlayer.getFollowService().setFollowRange(range);
    }

    @Override
    public void setTeleportRange(int range) {
        fakePlayer.getFollowService().setTeleportRange(range);
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

final class LegacyRepelRuntime implements BotRepelRuntime {

    private final FakePlayer fakePlayer;

    LegacyRepelRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public boolean repelling() { return fakePlayer.isMonsterRepelling(); }

    @Override
    public int repelRange() { return fakePlayer.getMonsterRepelRange(); }

    @Override
    public void setRepelling(boolean repelling) { fakePlayer.setMonsterRepelling(repelling); }

    @Override
    public void setRepelRange(int range) { fakePlayer.setMonsterRepelRange(range); }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;

final class LegacyMonitorRuntime implements BotMonitorRuntime {

    private final FakePlayer fakePlayer;

    LegacyMonitorRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public boolean monitoring() { return fakePlayer.isMonitoring(); }

    @Override
    public int monitorRange() { return fakePlayer.getMonitorRange(); }

    @Override
    public int reminderInterval() { return fakePlayer.getReminderInterval(); }

    @Override
    public void setMonitoring(boolean monitoring) { fakePlayer.setMonitoring(monitoring); }

    @Override
    public void setMonitorRange(int range) { fakePlayer.setMonitorRange(range); }

    @Override
    public void setReminderInterval(int ticks) { fakePlayer.setReminderInterval(ticks); }

    @Override
    public String overviewMessage(String botName) {
        MachineMonitorService service = fakePlayer.getMachineMonitorService();
        return service == null ? "" : service.buildOverviewMessage(botName);
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.MathHelper;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.ui.FakePlayerInventoryGuiIds;

final class LegacyInventoryRuntime implements BotInventoryRuntime {

    private final FakePlayer fakePlayer;

    LegacyInventoryRuntime(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public int selectedHotbarSlot() {
        return fakePlayer.inventory == null ? 0 : MathHelper.clamp_int(fakePlayer.inventory.currentItem, 0, 8);
    }

    @Override
    public BotInventorySummary summary() {
        InventoryPlayer inventory = fakePlayer.inventory;
        List<String> hotbar = new ArrayList<String>();
        List<String> main = new ArrayList<String>();
        List<String> armor = new ArrayList<String>();
        int selected = selectedHotbarSlot();
        if (inventory != null) {
            for (int index = 0; index < 9; index++) {
                hotbar.add((index == selected ? "[*] " : "[ ] ") + (index + 1) + ": " + formatStack(inventory.mainInventory[index]));
            }
            for (int index = 9; index < inventory.mainInventory.length; index++) {
                main.add("[ ] " + (index + 1) + ": " + formatStack(inventory.mainInventory[index]));
            }
            armor.add("Helmet: " + formatArmor(inventory, 3));
            armor.add("Chestplate: " + formatArmor(inventory, 2));
            armor.add("Leggings: " + formatArmor(inventory, 1));
            armor.add("Boots: " + formatArmor(inventory, 0));
        }
        return new BotInventorySummary(fakePlayer.getCommandSenderName(), selected, hotbar, main, armor);
    }

    @Override
    public String openInventoryManager(EntityPlayerMP player) throws CommandException {
        if (player == null) {
            throw new CommandException("Inventory manager can only be opened by a player");
        }
        player.openGui(GTstaff.instance, FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY, player.worldObj, fakePlayer.getEntityId(), 0, 0);
        return "Opening inventory manager for " + fakePlayer.getCommandSenderName() + ".";
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public final class LegacyBotHandle implements BotRuntimeView {

    private final FakePlayer fakePlayer;
    private final BotFollowRuntime follow;
    private final BotMonitorRuntime monitor;
    private final BotRepelRuntime repel;
    private final BotInventoryRuntime inventory;

    public LegacyBotHandle(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
        this.follow = new LegacyFollowRuntime(fakePlayer);
        this.monitor = new LegacyMonitorRuntime(fakePlayer);
        this.repel = new LegacyRepelRuntime(fakePlayer);
        this.inventory = new LegacyInventoryRuntime(fakePlayer);
    }

    @Override
    public String name() { return fakePlayer.getCommandSenderName(); }

    @Override
    public UUID ownerUUID() { return fakePlayer.getOwnerUUID(); }

    @Override
    public int dimension() { return fakePlayer.dimension; }

    @Override
    public BotRuntimeType runtimeType() { return BotRuntimeType.LEGACY; }

    @Override
    public BotEntityBridge entity() { return () -> fakePlayer; }

    @Override
    public boolean online() { return !fakePlayer.isDead; }

    @Override
    public BotFollowRuntime follow() { return follow; }

    @Override
    public BotMonitorRuntime monitor() { return monitor; }

    @Override
    public BotRepelRuntime repel() { return repel; }

    @Override
    public BotInventoryRuntime inventory() { return inventory; }
}
```

- [ ] **Step 4: Run the legacy adapter test to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest
```

Expected: PASS.

- [ ] **Step 5: Commit the legacy service adapter slice**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyFollowRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyMonitorRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyRepelRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyInventoryRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleServicesTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleTest.java
git commit -m "feat: adapt legacy bot services to runtime facade"
```

## Task 3: Migrate `FakePlayerManagerService` to Runtime Facades

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Modify: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: Write the failing manager-service tests against runtime paths**

```java
@Test
void describeBotUsesRuntimeFacadeForServiceState() {
    FakePlayerManagerService service = new FakePlayerManagerService();
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    fakePlayer.setMonitoring(true);
    fakePlayer.setMonitorRange(48);
    fakePlayer.setReminderInterval(240);
    fakePlayer.setMonsterRepelling(true);
    fakePlayer.setMonsterRepelRange(128);
    fakePlayer.getFollowService().setFollowRange(8);
    fakePlayer.getFollowService().setTeleportRange(24);
    FakePlayerRegistry.register(fakePlayer, null);

    FakePlayerManagerService.BotDetails details = service.describeBot("UiBot");

    assertTrue(details.monitoring);
    assertEquals(48, details.monitorRange);
    assertEquals(240, details.reminderInterval);
    assertTrue(details.monsterRepelling);
    assertEquals(128, details.monsterRepelRange);
    assertEquals(8, details.followRange);
    assertEquals(24, details.teleportRange);
}

@Test
void readInventoryUsesRuntimeInventorySummary() {
    FakePlayerManagerService service = new FakePlayerManagerService();
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    fakePlayer.inventory.currentItem = 1;
    fakePlayer.inventory.mainInventory[1] = namedStack("Torch", 16);
    FakePlayerRegistry.register(fakePlayer, null);

    FakePlayerManagerService.InventoryDraft draft = new FakePlayerManagerService.InventoryDraft();
    draft.botName = "UiBot";
    FakePlayerManagerService.InventorySnapshot snapshot = service.readInventory(draft);

    assertEquals(1, snapshot.selectedHotbarSlot);
    assertTrue(snapshot.hotbarLines.contains("[*] 2: Torch x16"));
}
```

- [ ] **Step 2: Run the manager-service test slice to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest
```

Expected: FAIL after the assertions are updated because `FakePlayerManagerService` still reads `FakePlayer` directly.

- [ ] **Step 3: Refactor `FakePlayerManagerService` to use `BotRuntimeView`**

```java
private BotRuntimeView findRuntime(String botName) {
    BotHandle handle = FakePlayerRegistry.getBotHandle(botName);
    return handle instanceof BotRuntimeView runtime && runtime.online() ? runtime : null;
}

private BotRuntimeView findRuntimeOrThrow(String botName) {
    String normalizedBotName = requireBotName(botName);
    BotRuntimeView runtime = findRuntime(normalizedBotName);
    if (runtime == null) {
        throw new CommandException(buildOfflineBotMessage(normalizedBotName));
    }
    return runtime;
}
```

```java
public String setReminderInterval(ICommandSender sender, String botName, int intervalTicks) {
    BotRuntimeView runtime = findRuntimeOrThrow(botName);
    runtime.monitor().setReminderInterval(intervalTicks);
    return "提醒频率已设置为 " + (intervalTicks / 20) + " 秒 for " + runtime.name() + ".";
}

public String toggleMonsterRepel(ICommandSender sender, String botName, boolean enable) {
    BotRuntimeView runtime = findRuntimeOrThrow(botName);
    runtime.repel().setRepelling(enable);
    return (enable ? "已开启敌对生物驱逐" : "已关闭敌对生物驱逐") + " for " + runtime.name()
        + " (范围: " + runtime.repel().repelRange() + "格)";
}

public String setMonsterRepelRange(ICommandSender sender, String botName, int range) {
    BotRuntimeView runtime = findRuntimeOrThrow(botName);
    runtime.repel().setRepelRange(range);
    return "敌对生物驱逐范围已设置为 " + range + " 格 for " + runtime.name() + ".";
}
```

```java
public String startFollow(ICommandSender sender, String botName) {
    if (!(sender instanceof EntityPlayerMP player)) {
        throw new CommandException("Only players can be followed");
    }
    BotRuntimeView runtime = findRuntimeOrThrow(botName);
    runtime.follow().startFollowing(player.getUniqueID());
    return FakePlayer.colorizeName(runtime.name()) + " 开始跟随你";
}
```

```java
public BotDetails describeBot(String botName) {
    BotHandle handle = FakePlayerRegistry.getBotHandle(botName);
    if (!(handle instanceof BotRuntimeView runtime) || !runtime.online()) {
        return new BotDetails(botName == null ? "" : botName.trim(), "(offline)", 0, 0, 0, 0, 0, false, 0, 600, false,
            false, 64, false, FollowService.DEFAULT_FOLLOW_RANGE, FollowService.DEFAULT_TELEPORT_RANGE);
    }
    EntityPlayerMP player = runtime.entity().asPlayer();
    return new BotDetails(
        runtime.name(),
        formatOwnerLabel(runtime.ownerUUID()),
        MathHelper.floor_double(player.posX),
        MathHelper.floor_double(player.posY),
        MathHelper.floor_double(player.posZ),
        runtime.dimension(),
        runtime.inventory().selectedHotbarSlot(),
        runtime.monitor().monitoring(),
        runtime.monitor().monitorRange(),
        runtime.monitor().reminderInterval(),
        runtime.online(),
        runtime.repel().repelling(),
        runtime.repel().repelRange(),
        runtime.follow().following(),
        runtime.follow().followRange(),
        runtime.follow().teleportRange());
}
```

```java
public InventorySnapshot readInventory(InventoryDraft draft) {
    String botName = requireBotName(draft.botName);
    BotRuntimeView runtime = findRuntimeOrThrow(botName);
    BotInventorySummary summary = runtime.inventory().summary();
    return new InventorySnapshot(
        summary.botName(),
        summary.selectedHotbarSlot(),
        summary.hotbarLines(),
        summary.mainInventoryLines(),
        summary.armorLines());
}
```

- [ ] **Step 4: Run the manager-service tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit the UI-manager runtime migration**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java
git commit -m "refactor: route fake player manager service through runtime facades"
```

## Task 4: Migrate `CommandPlayer` Service Subcommands to Runtime Facades

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
- Modify: `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`

- [ ] **Step 1: Add failing command tests for runtime-backed service handling**

```java
@Test
void monitorIntervalUsesRuntimeFacade() {
    CommandPlayer command = new CommandPlayer();
    StubFakePlayer bot = fakePlayer("Bot_Steve");
    FakePlayerRegistry.register(bot, null);

    command.processCommand(sender(), new String[] { "Bot_Steve", "monitor", "interval", "120" });

    assertEquals(120, bot.getReminderInterval());
}

@Test
void followRangeUsesRuntimeFacade() {
    CommandPlayer command = new CommandPlayer();
    StubFakePlayer bot = fakePlayer("Bot_Steve");
    FakePlayerRegistry.register(bot, null);

    command.processCommand(sender(), new String[] { "Bot_Steve", "follow", "range", "6" });

    assertEquals(6, bot.getFollowService().getFollowRange());
}
```

- [ ] **Step 2: Run the command test slice to verify it fails in the expected place**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest
```

Expected: FAIL after assertions are updated because service subcommands still directly require `FakePlayer`.

- [ ] **Step 3: Refactor `CommandPlayer` service helpers to use `BotRuntimeView`**

```java
private BotRuntimeView requireOnlineRuntime(String botName) {
    BotHandle handle = FakePlayerRegistry.getBotHandle(botName);
    if (!(handle instanceof BotRuntimeView runtime) || !runtime.online()) {
        throw new CommandException("Fake player not found: " + botName);
    }
    return runtime;
}

private FakePlayer requireLegacyFakePlayer(String botName) {
    FakePlayer target = FakePlayerRegistry.getFakePlayer(botName);
    if (target == null) {
        throw new CommandException("Fake player not found: " + botName);
    }
    return target;
}
```

```java
protected void handleMonitor(ICommandSender sender, String botName, String[] args) {
    BotRuntimeView runtime = requireOnlineRuntime(botName);
    if (runtime.entity().asPlayer() instanceof FakePlayer fakePlayer && PermissionHelper.cantManipulate(sender, fakePlayer)) {
        throw new CommandException("You do not have permission to control that bot");
    }

    if (args.length == 0) {
        notifySender(sender, "Monitor for " + runtime.name() + ": " + (runtime.monitor().monitoring() ? "on" : "off")
            + ", range=" + runtime.monitor().monitorRange() + ", interval=" + runtime.monitor().reminderInterval() + " ticks");
        return;
    }

    // parse args exactly as today
    if (monitoring != null) runtime.monitor().setMonitoring(monitoring);
    if (range != null) runtime.monitor().setMonitorRange(range);
    if (interval != null) runtime.monitor().setReminderInterval(interval);
    if (scan) notifySenderLines(sender, runtime.monitor().overviewMessage(runtime.name()));
}
```

```java
protected void handleRepel(ICommandSender sender, String botName, String[] args) {
    BotRuntimeView runtime = requireOnlineRuntime(botName);
    if (repelling != null) runtime.repel().setRepelling(repelling);
    if (range != null) runtime.repel().setRepelRange(range);
    notifySender(sender, "Repel for " + runtime.name() + ": " + (runtime.repel().repelling() ? "on" : "off")
        + ", range=" + runtime.repel().repelRange());
}
```

```java
protected void handleInventory(ICommandSender sender, String botName, String[] args) {
    BotRuntimeView runtime = requireOnlineRuntime(botName);
    if (args.length == 0 || "summary".equalsIgnoreCase(args[0])) {
        BotInventorySummary summary = runtime.inventory().summary();
        notifySenderLines(sender, "Inventory for " + summary.botName() + "\nSelected hotbar slot: "
            + (summary.selectedHotbarSlot() + 1) + "\nHotbar: " + String.join(", ", summary.hotbarLines()));
        return;
    }
    if (args.length == 1 && "open".equalsIgnoreCase(args[0])) {
        if (!(sender instanceof EntityPlayerMP player)) {
            throw new CommandException("Inventory manager can only be opened by a player");
        }
        notifySender(sender, runtime.inventory().openInventoryManager(player));
        return;
    }
    throw new WrongUsageException("/player <name> inventory [open|summary]");
}
```

```java
protected void handleFollow(ICommandSender sender, String botName, String[] args) {
    BotRuntimeView runtime = requireOnlineRuntime(botName);
    BotFollowRuntime follow = runtime.follow();
    // keep the current subcommand parsing, but delegate all state changes through follow
}
```

```java
protected void handleManipulation(ICommandSender sender, String botName, String action, String[] args) {
    FakePlayer target = requireLegacyFakePlayer(botName);
    // leave action pack behavior unchanged in Wave C
}
```

- [ ] **Step 4: Run command and registry tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest
```

Expected: PASS.

- [ ] **Step 5: Commit the command migration slice**

```bash
git add src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleServicesTest.java
git commit -m "refactor: route bot service commands through runtime facades"
```

## Task 5: Give `NextGenBotRuntime` Stateful Service Shells and Re-verify Wave C

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenFollowRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenMonitorRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenRepelRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenInventoryRuntime.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntimeServicesTest.java`
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: Write the failing nextgen-service-shell test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import net.minecraft.command.CommandException;

import org.junit.jupiter.api.Test;

class NextGenBotRuntimeServicesTest {

    @Test
    void nextGenRuntimeKeepsIndependentServiceState() {
        GTstaffForgePlayer player = TestPlayers.nextGen("WaveCNext");
        NextGenBotRuntime runtime = new NextGenBotRuntime(player, new BotSession(player), UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        runtime.follow().setFollowRange(7);
        runtime.follow().setTeleportRange(22);
        runtime.monitor().setMonitoring(true);
        runtime.monitor().setMonitorRange(48);
        runtime.repel().setRepelling(true);
        runtime.repel().setRepelRange(96);

        assertEquals(7, runtime.follow().followRange());
        assertEquals(22, runtime.follow().teleportRange());
        assertEquals(48, runtime.monitor().monitorRange());
        assertEquals(96, runtime.repel().repelRange());
        assertFalse(runtime.inventory().summary().hotbarLines().isEmpty());
        assertThrows(CommandException.class, () -> runtime.inventory().openInventoryManager(null));
    }
}
```

- [ ] **Step 2: Run the nextgen-service test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntimeServicesTest
```

Expected: FAIL because `NextGenBotRuntime` does not own service shells yet.

- [ ] **Step 3: Implement minimal nextgen state holders**

```java
final class NextGenFollowRuntime implements BotFollowRuntime {

    private UUID targetUUID;
    private int followRange = FollowService.DEFAULT_FOLLOW_RANGE;
    private int teleportRange = FollowService.DEFAULT_TELEPORT_RANGE;

    @Override public boolean following() { return targetUUID != null; }
    @Override public UUID targetUUID() { return targetUUID; }
    @Override public int followRange() { return followRange; }
    @Override public int teleportRange() { return teleportRange; }
    @Override public void startFollowing(UUID targetUUID) { this.targetUUID = targetUUID; }
    @Override public void stop() { this.targetUUID = null; }
    @Override public void setFollowRange(int range) { this.followRange = Math.max(1, range); }
    @Override public void setTeleportRange(int range) { this.teleportRange = Math.max(this.followRange + 1, range); }
}
```

```java
final class NextGenMonitorRuntime implements BotMonitorRuntime {

    private boolean monitoring;
    private int monitorRange = 16;
    private int reminderInterval = 600;

    @Override public boolean monitoring() { return monitoring; }
    @Override public int monitorRange() { return monitorRange; }
    @Override public int reminderInterval() { return reminderInterval; }
    @Override public void setMonitoring(boolean monitoring) { this.monitoring = monitoring; }
    @Override public void setMonitorRange(int range) { this.monitorRange = Math.max(1, range); }
    @Override public void setReminderInterval(int ticks) { this.reminderInterval = Math.max(60, ticks); }
    @Override public String overviewMessage(String botName) { return "Monitor service is not wired for " + botName + " yet."; }
}
```

```java
final class NextGenRepelRuntime implements BotRepelRuntime {

    private boolean repelling;
    private int repelRange = 64;

    @Override public boolean repelling() { return repelling; }
    @Override public int repelRange() { return repelRange; }
    @Override public void setRepelling(boolean repelling) { this.repelling = repelling; }
    @Override public void setRepelRange(int range) { this.repelRange = Math.max(1, range); }
}
```

```java
final class NextGenInventoryRuntime implements BotInventoryRuntime {

    private final GTstaffForgePlayer player;

    NextGenInventoryRuntime(GTstaffForgePlayer player) {
        this.player = player;
    }

    @Override
    public int selectedHotbarSlot() {
        return player.inventory == null ? 0 : MathHelper.clamp_int(player.inventory.currentItem, 0, 8);
    }

    @Override
    public BotInventorySummary summary() {
        return new BotInventorySummary(player.getCommandSenderName(), selectedHotbarSlot(),
            java.util.Collections.<String>emptyList(), java.util.Collections.<String>emptyList(), java.util.Collections.<String>emptyList());
    }

    @Override
    public String openInventoryManager(EntityPlayerMP player) throws CommandException {
        throw new CommandException("Nextgen inventory manager is not wired yet");
    }
}
```

```java
public final class NextGenBotRuntime implements BotRuntimeView {

    private final GTstaffForgePlayer player;
    private final BotSession session;
    private final UUID ownerUUID;
    private final BotFollowRuntime follow;
    private final BotMonitorRuntime monitor;
    private final BotRepelRuntime repel;
    private final BotInventoryRuntime inventory;

    public NextGenBotRuntime(GTstaffForgePlayer player, BotSession session, UUID ownerUUID) {
        this.player = player;
        this.session = session;
        this.ownerUUID = ownerUUID;
        this.follow = new NextGenFollowRuntime();
        this.monitor = new NextGenMonitorRuntime();
        this.repel = new NextGenRepelRuntime();
        this.inventory = new NextGenInventoryRuntime(player);
        this.player.bindRuntime(this);
    }

    @Override public UUID ownerUUID() { return ownerUUID; }
    @Override public BotFollowRuntime follow() { return follow; }
    @Override public BotMonitorRuntime monitor() { return monitor; }
    @Override public BotRepelRuntime repel() { return repel; }
    @Override public BotInventoryRuntime inventory() { return inventory; }
}
```

- [ ] **Step 4: Run the full Wave C verification command**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntimeServicesTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.action.TargetingServiceTest --tests com.andgatech.gtstaff.fakeplayer.action.UseExecutorTest --tests com.andgatech.gtstaff.fakeplayer.action.AttackExecutorTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest
```

Expected: PASS.

- [ ] **Step 5: Update project docs and commit the Wave C completion slice**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenFollowRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenMonitorRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenRepelRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenInventoryRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntimeServicesTest.java log.md ToDOLIST.md context.md
git commit -m "feat: add wave c runtime service facades"
```

## Self-Review Checklist

- Wave C only touches service-layer runtime facades; spawn/shadow/restore/default-runtime switching remain out of scope
- `CommandPlayer.handleManipulation(...)` explicitly stays on legacy `FakePlayer` in this wave
- `FakePlayerManagerService` and `CommandPlayer` both depend on the same `BotRuntimeView` sub-service surface
- `NextGenBotRuntime` grows state holders only; it does not claim full gameplay parity yet
- Tests cover contracts, legacy adapter behavior, manager-service usage, command usage, and nextgen placeholder state

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-21-gtstaff-nextgen-fake-player-runtime-wave-c.md`.

Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using the same worktree and TDD checkpoints
