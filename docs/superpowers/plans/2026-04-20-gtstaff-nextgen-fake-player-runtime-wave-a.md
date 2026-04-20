# GTstaff NextGen Fake Player Runtime Wave A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce runtime-agnostic bot abstractions, registry/runtime decoupling, and a non-default nextgen fake-player skeleton without changing current user-facing behavior.

**Architecture:** Wave A does not replace the current `FakePlayer` implementation. Instead, it introduces a stable runtime abstraction layer, upgrades persistence metadata to describe runtime kind/version, and adds a `GTstaffForgePlayer` + `BotSession` skeleton that can be instantiated under a feature flag while legacy remains the default path.

**Tech Stack:** Java 8, Forge 1.7.10, Sponge Mixin, JUnit 5, Gradle, NBT persistence, Minecraft `EntityPlayerMP` / Forge `FakePlayer`

---

## Scope Check

The approved spec spans four implementation waves. This plan intentionally covers **Wave A only**:

1. Runtime abstraction interfaces
2. Registry and snapshot decoupling
3. Nextgen entity/session skeleton
4. Runtime mode feature flag and non-default factory wiring

Wave B-C-D require separate implementation plans after Wave A is merged and verified.

## File Structure

### Create

- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeType.java`
  - Runtime kind enum used by persistence and factory selection.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotEntityBridge.java`
  - Controlled access to the backing `EntityPlayerMP`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java`
  - Runtime-neutral behavior surface for commands, UI, and registry.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotHandle.java`
  - Runtime-neutral bot identity/read model.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java`
  - Adapter that wraps the existing `FakePlayer`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSession.java`
  - Session/login/sync skeleton for nextgen bots.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java`
  - Minimal runtime shell for nextgen bots.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/GTstaffForgePlayer.java`
  - New entity skeleton extending `net.minecraftforge.common.util.FakePlayer`.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleTest.java`
  - Unit tests for the legacy adapter.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSessionTest.java`
  - Unit tests for nextgen session skeleton.

### Modify

- `src/main/java/com/andgatech/gtstaff/config/Config.java`
  - Add runtime mode feature flag.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
  - Introduce runtime kind/version metadata and runtime-neutral lookup methods while preserving old behavior.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
  - Implement `BotRuntimeView` through a legacy adapter hook and register as `LEGACY`.
- `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
  - Switch read-only lookups to `BotHandle` where possible without changing command behavior.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
  - Cover runtime metadata persistence and adapter lookup.
- `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`
  - Verify commands still operate with legacy runtime metadata present.
- `log.md`
  - Add the Wave A planning record after the plan is written.
- `ToDOLIST.md`
  - Move “implementation plan” progress after plan creation.
- `context.md`
  - Record the new plan path and that implementation has not started yet.

## Task 1: Introduce Runtime Abstractions Around The Existing Legacy Bot

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeType.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotEntityBridge.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotHandle.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleTest.java`

- [ ] **Step 1: Write the failing legacy adapter test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.mojang.authlib.GameProfile;

class LegacyBotHandleTest {

    @Test
    void exposesLegacyFakePlayerThroughRuntimeNeutralInterface() {
        FakePlayer fakePlayer = allocate(FakePlayer.class);
        setField(Entity.class, fakePlayer, "worldObj", allocate(WorldServer.class));
        setField(
            EntityPlayerMP.class,
            fakePlayer,
            "theItemInWorldManager",
            allocate(ItemInWorldManager.class));
        setField(
            net.minecraft.entity.player.EntityPlayer.class,
            fakePlayer,
            "field_146106_i",
            new GameProfile(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "PlanBot"));
        fakePlayer.setOwnerUUID(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        fakePlayer.dimension = 7;

        LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

        assertEquals("PlanBot", handle.name());
        assertEquals(BotRuntimeType.LEGACY, handle.runtimeType());
        assertEquals(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), handle.ownerUUID());
        assertEquals(7, handle.dimension());
        assertSame(fakePlayer, handle.entity().asPlayer());
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

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest
```

Expected: FAIL because the runtime abstraction types do not exist yet.

- [ ] **Step 3: Add the runtime abstraction classes and minimal legacy adapter**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public enum BotRuntimeType {
    LEGACY,
    NEXTGEN
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.entity.player.EntityPlayerMP;

public interface BotEntityBridge {

    EntityPlayerMP asPlayer();
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import java.util.UUID;

public interface BotHandle {

    String name();

    UUID ownerUUID();

    int dimension();

    BotRuntimeType runtimeType();

    BotEntityBridge entity();
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public interface BotRuntimeView extends BotHandle {

    boolean online();
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public final class LegacyBotHandle implements BotRuntimeView {

    private final FakePlayer fakePlayer;

    public LegacyBotHandle(FakePlayer fakePlayer) {
        this.fakePlayer = fakePlayer;
    }

    @Override
    public String name() {
        return fakePlayer.getCommandSenderName();
    }

    @Override
    public java.util.UUID ownerUUID() {
        return fakePlayer.getOwnerUUID();
    }

    @Override
    public int dimension() {
        return fakePlayer.dimension;
    }

    @Override
    public BotRuntimeType runtimeType() {
        return BotRuntimeType.LEGACY;
    }

    @Override
    public BotEntityBridge entity() {
        return () -> fakePlayer;
    }

    @Override
    public boolean online() {
        return !fakePlayer.isDead;
    }
}
```

- [ ] **Step 4: Run the adapter test to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeType.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotEntityBridge.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotRuntimeView.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotHandle.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandle.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleTest.java
git commit -m "refactor: add runtime-neutral bot abstraction layer"
```

## Task 2: Upgrade Registry Persistence To Track Runtime Kind And Adapter Lookup

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`

- [ ] **Step 1: Write the failing registry metadata test**

```java
@Test
void persistedBotDataDefaultsLegacyRuntimeWhenOldTagMissing() {
    NBTTagCompound tag = new NBTTagCompound();
    tag.setString("Name", "LegacyBot");
    tag.setInteger("Dimension", 0);
    tag.setDouble("PosX", 1.0D);
    tag.setDouble("PosY", 2.0D);
    tag.setDouble("PosZ", 3.0D);
    tag.setFloat("Yaw", 0.0F);
    tag.setFloat("Pitch", 0.0F);
    tag.setInteger("GameType", 0);

    FakePlayerRegistry.PersistedBotData data = FakePlayerRegistry.PersistedBotData.fromTag(tag);

    assertEquals(BotRuntimeType.LEGACY, data.getRuntimeType());
    assertEquals(1, data.getSnapshotVersion());
}

@Test
void registryExposesRuntimeNeutralHandleForLegacyBot() {
    FakePlayer fakePlayer = fakePlayer("RegistryBot");
    FakePlayerRegistry.clear();
    FakePlayerRegistry.register(fakePlayer, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));

    BotHandle handle = FakePlayerRegistry.getBotHandle("RegistryBot");

    assertNotNull(handle);
    assertEquals(BotRuntimeType.LEGACY, handle.runtimeType());
    assertEquals("RegistryBot", handle.name());
}
```

- [ ] **Step 2: Run the registry test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest
```

Expected: FAIL because runtime metadata and `getBotHandle(...)` are missing.

- [ ] **Step 3: Add runtime metadata keys and adapter lookup without breaking old save data**

```java
private static final String RUNTIME_TYPE_KEY = "RuntimeType";
private static final String SNAPSHOT_VERSION_KEY = "SnapshotVersion";
```

```java
public static final class PersistedBotData {

    private final BotRuntimeType runtimeType;
    private final int snapshotVersion;

    private PersistedBotData(..., BotRuntimeType runtimeType, int snapshotVersion) {
        ...
        this.runtimeType = runtimeType;
        this.snapshotVersion = snapshotVersion;
    }

    public BotRuntimeType getRuntimeType() {
        return this.runtimeType;
    }

    public int getSnapshotVersion() {
        return this.snapshotVersion;
    }

    static PersistedBotData fromTag(NBTTagCompound bot) {
        BotRuntimeType runtimeType = BotRuntimeType.LEGACY;
        if (bot.hasKey(RUNTIME_TYPE_KEY, Constants.NBT.TAG_STRING)) {
            runtimeType = BotRuntimeType.valueOf(bot.getString(RUNTIME_TYPE_KEY).toUpperCase(Locale.ROOT));
        }
        int snapshotVersion = bot.hasKey(SNAPSHOT_VERSION_KEY, Constants.NBT.TAG_INT)
            ? bot.getInteger(SNAPSHOT_VERSION_KEY)
            : 1;
        return new PersistedBotData(
            ...,
            runtimeType,
            snapshotVersion);
    }
}
```

```java
public static BotHandle getBotHandle(String name) {
    FakePlayer fakePlayer = getFakePlayer(name);
    if (fakePlayer != null) {
        return new LegacyBotHandle(fakePlayer);
    }
    PersistedBotData data = persistedBots.get(normalize(name));
    if (data == null) {
        return null;
    }
    return new PersistedBotHandle(data);
}
```

```java
private static PersistedBotData snapshot(FakePlayer fakePlayer, UUID ownerUUID) {
    return new PersistedBotData(
        ...,
        BotRuntimeType.LEGACY,
        1);
}
```

- [ ] **Step 4: Run the registry tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest
```

Expected: PASS, including old-tag fallback and runtime-neutral lookup.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java
git commit -m "refactor: add runtime metadata to fake player registry"
```

## Task 3: Add The NextGen Entity And Session Skeleton Behind A Non-Default Path

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSession.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/GTstaffForgePlayer.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSessionTest.java`

- [ ] **Step 1: Write the failing nextgen skeleton test**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;

class BotSessionTest {

    @Test
    void nextGenRuntimeExposesForgeBackedPlayerAndSession() {
        GTstaffForgePlayer player = allocate(GTstaffForgePlayer.class);
        setField(net.minecraft.entity.Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            net.minecraft.entity.player.EntityPlayer.class,
            player,
            "field_146106_i",
            new GameProfile(java.util.UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), "NextGenBot"));

        BotSession session = new BotSession(player);
        NextGenBotRuntime runtime = new NextGenBotRuntime(player, session);

        assertEquals(BotRuntimeType.NEXTGEN, runtime.runtimeType());
        assertSame(session, runtime.session());
        assertSame(player, runtime.entity().asPlayer());
        assertSame(FakePlayer.class, GTstaffForgePlayer.class.getSuperclass());
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

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest
```

Expected: FAIL because the nextgen skeleton classes do not exist.

- [ ] **Step 3: Add the minimal nextgen entity/runtime/session shell**

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import com.andgatech.gtstaff.fakeplayer.FakeNetHandlerPlayServer;
import com.andgatech.gtstaff.fakeplayer.FakeNetworkManager;

public final class BotSession {

    private final GTstaffForgePlayer player;

    public BotSession(GTstaffForgePlayer player) {
        this.player = player;
    }

    public void attach(MinecraftServer server) {
        NetworkManager networkManager = new FakeNetworkManager();
        FakeNetHandlerPlayServer netHandler = new FakeNetHandlerPlayServer(server, networkManager, player);
        server.getConfigurationManager().initializeConnectionToPlayer(networkManager, player, netHandler);
    }

    public EntityPlayerMP player() {
        return player;
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

public class GTstaffForgePlayer extends FakePlayer {

    private NextGenBotRuntime runtime;

    public GTstaffForgePlayer(MinecraftServer server, WorldServer world, GameProfile profile) {
        super(world, profile);
        this.mcServer = server;
    }

    public void bindRuntime(NextGenBotRuntime runtime) {
        this.runtime = runtime;
    }

    public NextGenBotRuntime runtime() {
        return runtime;
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.runtime;

public final class NextGenBotRuntime implements BotRuntimeView {

    private final GTstaffForgePlayer player;
    private final BotSession session;

    public NextGenBotRuntime(GTstaffForgePlayer player, BotSession session) {
        this.player = player;
        this.session = session;
        this.player.bindRuntime(this);
    }

    public BotSession session() {
        return session;
    }

    @Override
    public String name() {
        return player.getCommandSenderName();
    }

    @Override
    public java.util.UUID ownerUUID() {
        return null;
    }

    @Override
    public int dimension() {
        return player.dimension;
    }

    @Override
    public BotRuntimeType runtimeType() {
        return BotRuntimeType.NEXTGEN;
    }

    @Override
    public BotEntityBridge entity() {
        return () -> player;
    }

    @Override
    public boolean online() {
        return !player.isDead;
    }
}
```

- [ ] **Step 4: Run the nextgen skeleton test to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest
```

Expected: PASS, proving the nextgen shell exists but is still inert.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSession.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/NextGenBotRuntime.java src/main/java/com/andgatech/gtstaff/fakeplayer/runtime/GTstaffForgePlayer.java src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSessionTest.java
git commit -m "feat: add nextgen fake player runtime skeleton"
```

## Task 4: Add Runtime Mode Selection And Wire Legacy Paths Through The New Abstractions

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/config/Config.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Modify: `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
- Modify: `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`

- [ ] **Step 1: Write the failing command/runtime mode test**

```java
@Test
void listStillUsesLegacyBotsWhenRuntimeModeDefaultsToLegacy() {
    Config.fakePlayerRuntimeMode = "legacy";
    FakePlayerRegistry.clear();
    FakePlayerRegistry.register(fakePlayer("WaveABot"), UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));

    TestCommandSender sender = new TestCommandSender();
    new CommandPlayer().processCommand(sender, new String[] { "list" });

    assertTrue(sender.messages().stream().anyMatch(line -> line.contains("WaveABot")));
}
```

- [ ] **Step 2: Run the command test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest
```

Expected: FAIL because `fakePlayerRuntimeMode` does not exist and the command layer has no runtime-neutral lookup.

- [ ] **Step 3: Add the runtime mode flag and non-behavior-changing command wiring**

```java
public static String fakePlayerRuntimeMode = "legacy";
```

```java
fakePlayerRuntimeMode = configuration.getString(
    "fakePlayerRuntimeMode",
    FAKE_PLAYER,
    fakePlayerRuntimeMode,
    "legacy",
    "Default fake-player runtime mode: legacy, nextgen, mixed.");
```

```java
public final class FakePlayer extends EntityPlayerMP {

    public BotRuntimeView asRuntimeView() {
        return new LegacyBotHandle(this);
    }
}
```

```java
protected void handleList(ICommandSender sender) {
    if (FakePlayerRegistry.getCount() == 0) {
        notifySender(sender, "No fake players are registered.");
        return;
    }

    String botNames = FakePlayerRegistry.getAllBotHandles()
        .stream()
        .map(BotHandle::name)
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .collect(Collectors.joining(", "));
    notifySender(sender, "Fake players (" + FakePlayerRegistry.getCount() + "): " + botNames);
}
```

```java
public static java.util.List<BotHandle> getAllBotHandles() {
    return fakePlayers.values().stream().map(LegacyBotHandle::new).collect(Collectors.toList());
}
```

- [ ] **Step 4: Run the command and regression tests to verify behavior is unchanged**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest
```

Expected: PASS. Legacy remains the default runtime and command behavior remains unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/config/Config.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java
git commit -m "refactor: wire legacy command flow through runtime handles"
```

## Task 5: Verification And Documentation Updates

**Files:**
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: Run the full Wave A verification set**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.command.CommandPlayerTest
```

Expected: PASS.

- [ ] **Step 2: Add the work log entry**

```markdown
## 2026-04-20：完成 nextgen fake player runtime Wave A 运行时解耦骨架

### 已完成
- 新增 runtime-neutral bot 抽象层：`BotHandle` / `BotRuntimeView` / `BotEntityBridge`
- 为 registry 快照补充 runtime kind 与 snapshot version 元数据，并保持旧档默认回落为 `LEGACY`
- 新增 `GTstaffForgePlayer`、`BotSession`、`NextGenBotRuntime` 骨架，但默认运行时仍保持 `legacy`
- 命令层开始通过 runtime handle 访问 bot 名称等只读信息，未改变既有用户行为
```

- [ ] **Step 3: Update TODO state**

```markdown
## 当前计划
- [ ] 基于 `docs/superpowers/plans/2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-a.md` 继续执行 Wave B：动作链迁移

## 已完成
- [x] 完成 nextgen fake player runtime Wave A：运行时抽象、registry 元数据、nextgen skeleton、legacy 默认模式保留
```

- [ ] **Step 4: Update context summary**

```markdown
### 架构备注
- 已落地 nextgen runtime Wave A 骨架：`BotHandle` / `BotRuntimeView` / `BotEntityBridge`、registry runtime 元数据、`GTstaffForgePlayer`、`BotSession`、`NextGenBotRuntime`
- 当前默认 runtime 仍为 `legacy`；nextgen 仅作为未启用的基础骨架存在
```

- [ ] **Step 5: Commit**

```bash
git add log.md ToDOLIST.md context.md
git commit -m "docs: record nextgen runtime wave a completion"
```

## Self-Review Checklist

Before execution, verify the plan against the approved spec:

- Wave A scope is respected: no action pipeline migration, no follow/monitor/repel migration, no default runtime switch.
- Every spec requirement addressed here maps to one of:
  - runtime abstraction layer
  - registry metadata decoupling
  - nextgen entity/session skeleton
  - runtime mode feature flag and legacy default behavior
- No placeholders remain in code or command steps.
- All newly introduced type names are consistent:
  - `BotRuntimeType`
  - `BotHandle`
  - `BotRuntimeView`
  - `BotEntityBridge`
  - `LegacyBotHandle`
  - `BotSession`
  - `NextGenBotRuntime`
  - `GTstaffForgePlayer`

## Handoff Notes

This plan intentionally stops before Wave B. After Wave A lands and passes verification, write a new plan for Wave B that migrates `TargetingService`, `AttackExecutor`, `UseExecutor`, and `FeedbackSync`.
