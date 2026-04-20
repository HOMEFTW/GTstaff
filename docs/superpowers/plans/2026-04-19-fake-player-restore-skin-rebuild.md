# Fake Player Restore Skin Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让服务端重启后恢复出来的 fake player 异步联网尝试加载皮肤，并在成功时安全重建为带皮肤的新实例，同时不阻塞开服、不影响恢复失败回退。

**Architecture:** 现有恢复链路继续先把 bot 恢复出来，再由一个新的 `FakePlayerSkinRestoreScheduler` 对每个恢复 bot 发起异步皮肤解析。拿到带 `textures` 的 profile 后，切回主线程执行一次受保护的“旧 bot 状态快照 -> 旧 bot 校验 -> 带皮肤 profile 重建 -> 运行状态回挂”的替换流程。

**Tech Stack:** Java 8, Minecraft Forge 1.7.10, Mojang `GameProfile`, Guava `Optional`/`ListenableFuture`, JUnit 5, GTNH Gradle build

---

## File Structure

- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java`
  Responsibility: 恢复完成后的异步皮肤解析与主线程重建调度。
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
  Responsibility: 增加恢复重建快照、显式 profile 恢复入口、以及“用新 profile 安全重建旧 bot”的主逻辑。
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java`
  Responsibility: 暴露一个“按名字优先取 SkinPort profile，否则回退离线 profile”的公共创建入口，供恢复重建复用。
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
  Responsibility: 让恢复流程能把“刚恢复出来的 fake player”回传给调度器，而不是只在内部注册。
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java`
  Responsibility: 在原有恢复动作执行后，把恢复出的 bot 批量交给皮肤重建调度器。
- Modify: `src/main/java/com/andgatech/gtstaff/CommonProxy.java`
  Responsibility: 服务器停止时取消皮肤恢复调度，避免跨停服残留任务。
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`
  Responsibility: 覆盖恢复后异步补皮、失败保留旧 bot、成功替换新 bot、实例过期不误替换等路径。
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java`
  Responsibility: 补上恢复重建会复用的 profile 创建入口测试。
- Modify: `log.md`
  Responsibility: 记录“重启恢复后异步联网补皮并重建 bot”的实现与验证结果。
- Modify: `ToDOLIST.md`
  Responsibility: 更新任务状态。
- Modify: `context.md`
  Responsibility: 记录新增恢复调度器与行为边界。

---

### Task 1: Make Persisted Restore Return Restored Bots To Callers

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void restorePersistedReturnsRestoredBotsInRegistrationOrder() {
    FakePlayerRegistry.clear();
    FakePlayerRegistry.load(writeRegistryFile(
        botTag("Alpha", UUID.randomUUID()),
        botTag("Beta", UUID.randomUUID())));

    List<FakePlayer> restored = FakePlayerRegistry.restorePersisted(data -> new StubRestoredFakePlayer(data.getName()));

    assertEquals(2, restored.size());
    assertEquals("Alpha", restored.get(0).getCommandSenderName());
    assertEquals("Beta", restored.get(1).getCommandSenderName());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest`

Expected: FAIL with compile errors like `restorePersisted(...) does not return List<FakePlayer>`.

- [ ] **Step 3: Write minimal implementation**

```java
public static List<FakePlayer> restorePersisted(MinecraftServer server) {
    return restorePersisted(data -> FakePlayer.restorePersisted(server, data));
}

public static List<FakePlayer> restorePersisted(BotRestorer restorer) {
    List<FakePlayer> restored = new ArrayList<FakePlayer>();
    if (restorer == null) {
        return restored;
    }

    for (PersistedBotData data : new ArrayList<PersistedBotData>(persistedBots.values())) {
        if (data == null || data.getName() == null || getFakePlayer(data.getName()) != null) {
            continue;
        }

        FakePlayer fakePlayer = restorer.restore(data);
        if (fakePlayer == null) {
            continue;
        }

        fakePlayer.setOwnerUUID(data.getOwnerUUID());
        fakePlayer.setMonitoring(data.isMonitoring());
        fakePlayer.setMonitorRange(data.getMonitorRange());
        fakePlayer.setReminderInterval(data.getReminderInterval());
        fakePlayer.setMonsterRepelling(data.isMonsterRepelling());
        fakePlayer.setMonsterRepelRange(data.getMonsterRepelRange());
        if (data.getFollowTarget() != null) {
            fakePlayer.getFollowService().setFollowRange(data.getFollowRange());
            fakePlayer.getFollowService().setTeleportRange(data.getTeleportRange());
            fakePlayer.getFollowService().startFollowing(data.getFollowTarget());
        }
        register(fakePlayer, data.getOwnerUUID());
        restored.add(fakePlayer);
    }

    return restored;
}
```

```java
List<FakePlayer> restored = FakePlayerRegistry.restorePersisted(data -> new StubRestoredFakePlayer(data.getName()));
assertEquals(2, restored.size());
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest`

Expected: PASS with the new restore return-value assertion green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java
git commit -m "refactor: return restored fake players from registry restore"
```

---

### Task 2: Add Restore-Skin Scheduler With Async Resolve And Main-Thread Rebuild Hook

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void successfulSkinResolveSchedulesRebuildForSameBotInstance() {
    List<String> rebuilt = new ArrayList<String>();
    FakePlayer bot = new StubSkinBot("SkinBot");
    GameProfile profile = texturedProfile("SkinBot");
    FakePlayerSkinRestoreScheduler.setResolverForTests(name -> Optional.of(profile));
    FakePlayerSkinRestoreScheduler.setRebuildActionForTests((server, oldBot, newProfile) -> {
        rebuilt.add(oldBot.getCommandSenderName());
        return oldBot;
    });

    FakePlayerSkinRestoreScheduler.schedule(new StubMinecraftServer(), bot);
    FakePlayerSkinRestoreScheduler.runAsyncWorkForTests();
    FakePlayerSkinRestoreScheduler.runMainThreadWorkForTests();

    assertEquals(Arrays.asList("SkinBot"), rebuilt);
}
```

```java
@Test
void failedSkinResolveLeavesOriginalBotUntouched() {
    List<String> rebuilt = new ArrayList<String>();
    FakePlayer bot = new StubSkinBot("FallbackBot");
    FakePlayerSkinRestoreScheduler.setResolverForTests(name -> Optional.empty());
    FakePlayerSkinRestoreScheduler.setRebuildActionForTests((server, oldBot, newProfile) -> {
        rebuilt.add(oldBot.getCommandSenderName());
        return oldBot;
    });

    FakePlayerSkinRestoreScheduler.schedule(new StubMinecraftServer(), bot);
    FakePlayerSkinRestoreScheduler.runAsyncWorkForTests();
    FakePlayerSkinRestoreScheduler.runMainThreadWorkForTests();

    assertTrue(rebuilt.isEmpty());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest`

Expected: FAIL with compile errors because `FakePlayerSkinRestoreScheduler` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
package com.andgatech.gtstaff.fakeplayer;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;

import com.mojang.authlib.GameProfile;

public final class FakePlayerSkinRestoreScheduler {

    interface Resolver {
        Optional<GameProfile> resolve(String botName);
    }

    interface RebuildAction {
        FakePlayer rebuild(MinecraftServer server, FakePlayer oldBot, GameProfile profile);
    }

    private static Resolver resolver = FakePlayerProfiles::resolveSkinProfile;
    private static RebuildAction rebuildAction = FakePlayer::rebuildRestoredWithProfile;
    private static final Queue<Runnable> asyncQueue = new ArrayDeque<Runnable>();
    private static final Queue<Runnable> mainThreadQueue = new ArrayDeque<Runnable>();

    private FakePlayerSkinRestoreScheduler() {}

    public static void schedule(MinecraftServer server, FakePlayer fakePlayer) {
        if (server == null || fakePlayer == null) {
            return;
        }
        asyncQueue.add(() -> {
            Optional<GameProfile> profile = resolver.resolve(fakePlayer.getCommandSenderName());
            if (!profile.isPresent()) {
                return;
            }
            mainThreadQueue.add(() -> rebuildAction.rebuild(server, fakePlayer, profile.get()));
        });
    }

    static void runAsyncWorkForTests() {
        while (!asyncQueue.isEmpty()) {
            asyncQueue.remove().run();
        }
    }

    static void runMainThreadWorkForTests() {
        while (!mainThreadQueue.isEmpty()) {
            mainThreadQueue.remove().run();
        }
    }

    static void setResolverForTests(Resolver testResolver) {
        resolver = testResolver == null ? FakePlayerProfiles::resolveSkinProfile : testResolver;
    }

    static void setRebuildActionForTests(RebuildAction action) {
        rebuildAction = action == null ? FakePlayer::rebuildRestoredWithProfile : action;
    }

    static void resetForTests() {
        asyncQueue.clear();
        mainThreadQueue.clear();
        resolver = FakePlayerProfiles::resolveSkinProfile;
        rebuildAction = FakePlayer::rebuildRestoredWithProfile;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest`

Expected: PASS with both the success and fallback paths green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java
git commit -m "feat: add restore skin rebuild scheduler"
```

---

### Task 3: Add Safe Rebuild Path On FakePlayer

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void rebuildWithProfileKeepsKeyStateAndReplacesRegistryEntry() {
    TestMinecraftServer server = allocate(TestMinecraftServer.class);
    StubSkinBot oldBot = new StubSkinBot("SkinBot");
    oldBot.dimension = 7;
    oldBot.posX = 10.5D;
    oldBot.posY = 64.0D;
    oldBot.posZ = -3.5D;
    oldBot.rotationYaw = 90.0F;
    oldBot.rotationPitch = 15.0F;
    oldBot.setOwnerUUID(UUID.randomUUID());
    oldBot.setMonitoring(true);
    oldBot.setMonitorRange(32);
    oldBot.setReminderInterval(1200);
    oldBot.setMonsterRepelling(true);
    oldBot.setMonsterRepelRange(128);
    oldBot.getFollowService().setFollowRange(5);
    oldBot.getFollowService().setTeleportRange(40);
    oldBot.getFollowService().startFollowing(UUID.randomUUID());
    FakePlayerRegistry.register(oldBot, oldBot.getOwnerUUID());

    GameProfile profile = texturedProfile("SkinBot");
    StubSkinBot rebuilt = allocate(StubSkinBot.class);
    FakePlayer.setFactoryForTests((gameProfile, restoreState, minecraftServer) -> rebuilt);

    FakePlayer result = FakePlayer.rebuildRestoredWithProfile(server, oldBot, profile);

    assertSame(rebuilt, result);
    assertEquals(oldBot.getOwnerUUID(), result.getOwnerUUID());
    assertTrue(result.isMonitoring());
    assertEquals(32, result.getMonitorRange());
    assertEquals(1200, result.getReminderInterval());
    assertTrue(result.isMonsterRepelling());
    assertEquals(128, result.getMonsterRepelRange());
    assertEquals(5, result.getFollowService().getFollowRange());
    assertEquals(40, result.getFollowService().getTeleportRange());
    assertEquals(oldBot.getFollowService().getFollowTargetUUID(), result.getFollowService().getFollowTargetUUID());
    assertSame(result, FakePlayerRegistry.getFakePlayer("SkinBot"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest`

Expected: FAIL because `rebuildRestoredWithProfile(...)` and the test factory hook do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
static final class RestoreState {
    final String name;
    final UUID ownerUUID;
    final int dimension;
    final double posX;
    final double posY;
    final double posZ;
    final float yaw;
    final float pitch;
    final int gameTypeId;
    final boolean flying;
    final boolean monitoring;
    final int monitorRange;
    final int reminderInterval;
    final boolean monsterRepelling;
    final int monsterRepelRange;
    final UUID followTarget;
    final int followRange;
    final int teleportRange;
}
```

```java
public static FakePlayer rebuildRestoredWithProfile(MinecraftServer server, FakePlayer oldBot, GameProfile profile) {
    if (server == null || oldBot == null || profile == null) {
        return oldBot;
    }
    if (FakePlayerRegistry.getFakePlayer(oldBot.getCommandSenderName()) != oldBot) {
        return oldBot;
    }

    RestoreState state = RestoreState.capture(oldBot);
    FakePlayer rebuilt = createRestoredWithProfile(server, profile, state);
    if (rebuilt == null) {
        return oldBot;
    }

    oldBot.kill();
    FakePlayerRegistry.register(rebuilt, state.ownerUUID);
    return rebuilt;
}
```

```java
static Optional<GameProfile> resolveSkinProfile(String username) {
    return DEFAULT_RESOLVER.resolve(username).map(FakePlayerProfiles::copyOf);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest`

Expected: PASS with state-retention assertions and helper tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java
git commit -m "feat: rebuild restored fake players with skin profiles"
```

---

### Task 4: Wire Restore Scheduler Into Server Restore Flow

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java`
- Modify: `src/main/java/com/andgatech/gtstaff/CommonProxy.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void restoreSchedulerHandsRestoredBotsToSkinScheduler() {
    List<FakePlayer> restoredBots = new ArrayList<FakePlayer>();
    List<FakePlayer> scheduledBots = new ArrayList<FakePlayer>();
    TestMinecraftServer server = allocate(TestMinecraftServer.class);
    server.dedicated = true;
    setField(MinecraftServer.class, server, "serverConfigManager", new TestServerConfigurationManager(server));
    FakePlayerRestoreScheduler.setRestoreActionForTesting(s -> restoredBots);
    FakePlayerRestoreScheduler.setSkinScheduleActionForTesting((minecraftServer, fakePlayer) -> scheduledBots.add(fakePlayer));
    restoredBots.add(new StubRestoredFakePlayer("Alpha"));
    restoredBots.add(new StubRestoredFakePlayer("Beta"));

    FakePlayerRestoreScheduler.schedule(server);
    FakePlayerRestoreScheduler.runPendingRestore();

    assertEquals(2, scheduledBots.size());
    assertEquals("Alpha", scheduledBots.get(0).getCommandSenderName());
    assertEquals("Beta", scheduledBots.get(1).getCommandSenderName());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest`

Expected: FAIL because `setSkinScheduleActionForTesting(...)` and list-based restore handling do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
private static Function<MinecraftServer, List<FakePlayer>> restoreAction = FakePlayerRegistry::restorePersisted;
private static BiConsumer<MinecraftServer, FakePlayer> skinScheduleAction = FakePlayerSkinRestoreScheduler::schedule;
```

```java
static void runPendingRestore() {
    MinecraftServer server = pendingServer;
    if (!isReady(server)) {
        return;
    }

    pendingServer = null;
    List<FakePlayer> restoredBots = restoreAction.apply(server);
    for (FakePlayer fakePlayer : restoredBots) {
        skinScheduleAction.accept(server, fakePlayer);
    }
}
```

```java
public void serverStopping(FMLServerStoppingEvent event) {
    FakePlayerRestoreScheduler.cancel();
    FakePlayerSkinRestoreScheduler.resetForTests();
    FakePlayerRegistry.save(getRegistryFile());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest`

Expected: PASS with restore-to-skin scheduling behavior green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java src/main/java/com/andgatech/gtstaff/CommonProxy.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java
git commit -m "feat: schedule skin rebuild after fake player restore"
```

---

### Task 5: Update GTNH Logs And Run Full Verification

**Files:**
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: Write the documentation changes**

```md
# ToDOLIST.md
- [x] 服务端重启恢复 fake player 后异步联网加载皮肤；成功时安全重建带皮肤的新实例，失败时保留原 bot
```

```md
# context.md
- `FakePlayerSkinRestoreScheduler`：接在恢复链路后，只负责恢复 bot 的异步补皮与主线程安全重建；不阻塞开服，不改 registry 持久化格式。
```

```md
# log.md
- 2026-04-19: Added async post-restore skin rebuild. Persisted fake players now restore immediately, then optionally rebuild with a SkinPort-resolved profile on successful network skin lookup.
```

- [ ] **Step 2: Run targeted tests**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest`

Expected: PASS with all restore/rebuild/profile tests successful.

- [ ] **Step 3: Run full compile/build verification**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava`

Expected: `BUILD SUCCESSFUL`

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add log.md ToDOLIST.md context.md
git commit -m "docs: record restore skin rebuild integration"
```

---

## Self-Review

### Spec Coverage

- 异步补皮且不阻塞恢复 is covered by Tasks 2 and 4.
- 成功后重建而不是热改 profile is covered by Task 3.
- 重建后保留 owner / 监控 / 驱逐 / 跟随 / 飞行 / 位置等关键状态 is covered by Task 3 tests and implementation.
- 失败时保留旧 bot、不误替换过期实例 is covered by Tasks 2 and 3.
- 文档与整体验证 is covered by Task 5.

### Placeholder Scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- All code steps include concrete snippets.
- All verification steps include exact commands and expected results.

### Type Consistency

- Scheduler type names stay `FakePlayerSkinRestoreScheduler`, `Resolver`, and `RebuildAction`.
- Fake player rebuild entry stays `rebuildRestoredWithProfile(...)`.
- Restore return type stays `List<FakePlayer>` across registry and scheduler tasks.

