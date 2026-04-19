# Fake Player SkinPort Lite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 GTstaff 在安装 SkinPort 时为新生成的假人按假人名字解析正版皮肤资料，并在未安装或解析失败时静默回退到现有离线 profile 逻辑。

**Architecture:** 新增一个仅负责反射调用 SkinPort 的 `SkinPortCompat`，把 “按名字获取已补全 `GameProfile`” 的可选兼容逻辑隔离出来。假人生成链路通过一个小型 profile 选择器优先使用该兼容层结果，恢复链路保持不变，从而把风险限制在生成时。

**Tech Stack:** Java 8, Minecraft Forge 1.7.10, Mojang `GameProfile`, JUnit 5, GTNH Gradle build

---

## File Structure

- Create: `src/main/java/com/andgatech/gtstaff/integration/SkinPortCompat.java`
  Responsibility: 通过反射检测并调用 `SkinPort` 的 `MojangService`，提供可选的正版 profile 解析入口。
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java`
  Responsibility: 统一封装“生成假人时应该使用哪个 `GameProfile`”的选择逻辑，优先尝试 SkinPort，失败则回退离线 profile。
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
  Responsibility: 在 `createFake(...)` 中改为调用 `FakePlayerProfiles`，不直接内联构造离线 profile。
- Create: `src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java`
  Responsibility: 覆盖 compat 层在未安装、失败、成功三种情况下的返回行为。
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java`
  Responsibility: 覆盖生成链路在 compat 成功和失败时的 profile 选择行为，并锁定回退 UUID 规则。
- Modify: `log.md`
  Responsibility: 记录本次 SkinPort 轻量兼容的实现与验证结果。
- Modify: `ToDOLIST.md`
  Responsibility: 更新当前任务状态。
- Modify: `context.md`
  Responsibility: 记录新增兼容层、边界和测试入口。

---

### Task 1: Lock Down Optional SkinPort Compat Behavior With Tests

**Files:**
- Create: `src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

class SkinPortCompatTest {

    @AfterEach
    void clearBridge() {
        SkinPortCompat.setBridgeForTests(null);
    }

    @Test
    void returnsEmptyWhenSkinPortBridgeIsUnavailable() {
        SkinPortCompat.setBridgeForTests(SkinPortCompat.Bridge.unavailable());

        Optional<GameProfile> result = SkinPortCompat.resolveProfile("TestBot");

        assertFalse(result.isPresent());
    }

    @Test
    void returnsEmptyWhenSkinPortCannotResolveFilledProfile() {
        SkinPortCompat.setBridgeForTests(new SkinPortCompat.Bridge() {

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public GameProfile resolveFilledProfile(String username) {
                return null;
            }
        });

        Optional<GameProfile> result = SkinPortCompat.resolveProfile("TestBot");

        assertFalse(result.isPresent());
    }

    @Test
    void returnsFilledProfileWhenSkinPortBridgeSucceeds() {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "TestBot");
        profile.getProperties().put("textures", new Property("textures", "value", "signature"));
        SkinPortCompat.setBridgeForTests(new SkinPortCompat.Bridge() {

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public GameProfile resolveFilledProfile(String username) {
                return profile;
            }
        });

        Optional<GameProfile> result = SkinPortCompat.resolveProfile("TestBot");

        assertTrue(result.isPresent());
        assertSame(profile, result.get());
        assertEquals("value", result.get().getProperties().get("textures").iterator().next().getValue());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`

Expected: FAIL with errors such as `cannot find symbol: class SkinPortCompat` and `package com.andgatech.gtstaff.integration does not contain SkinPortCompat`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.andgatech.gtstaff.integration;

import java.util.Optional;

import com.mojang.authlib.GameProfile;

public final class SkinPortCompat {

    interface Bridge {

        boolean isAvailable();

        GameProfile resolveFilledProfile(String username);

        static Bridge unavailable() {
            return new Bridge() {
                @Override
                public boolean isAvailable() {
                    return false;
                }

                @Override
                public GameProfile resolveFilledProfile(String username) {
                    return null;
                }
            };
        }
    }

    private static Bridge bridge = Bridge.unavailable();

    private SkinPortCompat() {}

    public static Optional<GameProfile> resolveProfile(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        if (!bridge.isAvailable()) {
            return Optional.empty();
        }
        GameProfile profile = bridge.resolveFilledProfile(username);
        if (profile == null || profile.getProperties() == null || profile.getProperties().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(profile);
    }

    static void setBridgeForTests(Bridge testBridge) {
        bridge = testBridge == null ? Bridge.unavailable() : testBridge;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`

Expected: PASS with 3 tests completed successfully.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/integration/SkinPortCompat.java src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java
git commit -m "test: lock optional skinport compat behavior"
```

---

### Task 2: Implement Reflection-Based SkinPort Bridge

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/integration/SkinPortCompat.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void rejectsProfilesWithoutTextureProperties() {
    GameProfile profile = new GameProfile(UUID.randomUUID(), "TestBot");
    SkinPortCompat.setBridgeForTests(new SkinPortCompat.Bridge() {

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public GameProfile resolveFilledProfile(String username) {
            return profile;
        }
    });

    Optional<GameProfile> result = SkinPortCompat.resolveProfile("TestBot");

    assertFalse(result.isPresent());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`

Expected: FAIL because the current compat implementation does not yet model the real SkinPort reflection bridge and will not survive runtime loading scenarios.

- [ ] **Step 3: Write minimal implementation**

```java
package com.andgatech.gtstaff.integration;

import java.lang.reflect.Method;
import java.util.Optional;

import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;

public final class SkinPortCompat {

    interface Bridge {

        boolean isAvailable();

        GameProfile resolveFilledProfile(String username);

        static Bridge unavailable() {
            return new Bridge() {
                @Override
                public boolean isAvailable() {
                    return false;
                }

                @Override
                public GameProfile resolveFilledProfile(String username) {
                    return null;
                }
            };
        }
    }

    private static final Bridge REFLECTION_BRIDGE = createReflectionBridge();
    private static Bridge testBridge;

    private SkinPortCompat() {}

    public static Optional<GameProfile> resolveProfile(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        Bridge bridge = testBridge != null ? testBridge : REFLECTION_BRIDGE;
        if (!bridge.isAvailable()) {
            return Optional.empty();
        }
        try {
            GameProfile profile = bridge.resolveFilledProfile(username);
            if (profile == null || profile.getProperties() == null || profile.getProperties().isEmpty()) {
                return Optional.empty();
            }
            if (profile.getProperties().get("textures").isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(profile);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static void setBridgeForTests(Bridge bridge) {
        testBridge = bridge;
    }

    private static Bridge createReflectionBridge() {
        try {
            Class<?> mojangService = Class.forName("lain.mods.skins.impl.MojangService");
            Method getProfile = mojangService.getMethod("getProfile", String.class);
            Method fillProfile = mojangService.getMethod("fillProfile", GameProfile.class);
            return new Bridge() {

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                @SuppressWarnings("unchecked")
                public GameProfile resolveFilledProfile(String username) {
                    try {
                        Object resolvedFuture = getProfile.invoke(null, username);
                        GameProfile resolved = ((ListenableFuture<GameProfile>) resolvedFuture).get();
                        if (resolved == null || resolved.getId() == null) {
                            return null;
                        }
                        Object filledFuture = fillProfile.invoke(null, resolved);
                        GameProfile filled = ((ListenableFuture<GameProfile>) filledFuture).get();
                        return filled;
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
        } catch (Exception e) {
            return Bridge.unavailable();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`

Expected: PASS with all compat tests green, including the new no-texture rejection case.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/integration/SkinPortCompat.java src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java
git commit -m "feat: add optional skinport reflection bridge"
```

---

### Task 3: Route Fake Player Spawn Through the Skin-Aware Profile Selector

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.integration.SkinPortCompat;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import net.minecraft.entity.player.EntityPlayer;

class FakePlayerProfilesTest {

    @AfterEach
    void clearBridge() {
        SkinPortCompat.setBridgeForTests(null);
    }

    @Test
    void usesSkinPortProfileWhenAvailable() {
        GameProfile filled = new GameProfile(UUID.randomUUID(), "SkinBot");
        filled.getProperties().put("textures", new Property("textures", "value", "signature"));
        SkinPortCompat.setBridgeForTests(new SkinPortCompat.Bridge() {

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public GameProfile resolveFilledProfile(String username) {
                return filled;
            }
        });

        GameProfile profile = FakePlayerProfiles.createSpawnProfile("SkinBot");

        assertSame(filled, profile);
    }

    @Test
    void fallsBackToOfflineProfileWhenSkinPortProfileIsUnavailable() {
        SkinPortCompat.setBridgeForTests(SkinPortCompat.Bridge.unavailable());

        GameProfile profile = FakePlayerProfiles.createSpawnProfile("FallbackBot");

        assertEquals("FallbackBot", profile.getName());
        assertEquals(EntityPlayer.func_146094_a(new GameProfile(null, "FallbackBot")), profile.getId());
        assertTrue(profile.getProperties().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest`

Expected: FAIL with errors such as `cannot find symbol: class FakePlayerProfiles`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.andgatech.gtstaff.fakeplayer;

import com.andgatech.gtstaff.integration.SkinPortCompat;
import com.mojang.authlib.GameProfile;

import net.minecraft.entity.player.EntityPlayer;

final class FakePlayerProfiles {

    private FakePlayerProfiles() {}

    static GameProfile createSpawnProfile(String username) {
        String safeUsername = username == null ? "" : username;
        return SkinPortCompat.resolveProfile(safeUsername)
            .orElseGet(() -> new GameProfile(
                EntityPlayer.func_146094_a(new GameProfile(null, safeUsername)),
                safeUsername));
    }
}
```

```java
public static FakePlayer createFake(String username, MinecraftServer server, ChunkCoordinates pos, float yaw,
    float pitch, int dimension, WorldSettings.GameType gamemode, boolean flying) {
    String safeUsername = username == null ? "" : username;
    GameProfile profile = FakePlayerProfiles.createSpawnProfile(safeUsername);
    ChunkCoordinates spawnPoint = pos;
    FakePlayer fakePlayer = createWithProfile(
        profile,
        server,
        spawnPoint == null ? Double.NaN : spawnPoint.posX + 0.5D,
        spawnPoint == null ? Double.NaN : spawnPoint.posY,
        spawnPoint == null ? Double.NaN : spawnPoint.posZ + 0.5D,
        yaw,
        pitch,
        dimension,
        gamemode,
        flying);
    fakePlayer.replaceExistingRegistryEntry();
    FakePlayerRegistry.register(fakePlayer, null);
    fakePlayer.respawnFake();
    return fakePlayer;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`

Expected: PASS with both compat and profile-selection tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java src/main/java/com/andgatech/gtstaff/integration/SkinPortCompat.java src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java
git commit -m "feat: use skin-aware profiles for fake player spawn"
```

---

### Task 4: Update GTNH Project Logs And Run Final Verification

**Files:**
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: Write the documentation changes**

```md
# log.md
- 2026-04-19: Added optional SkinPort fake-player skin integration. New fake players now try to resolve Mojang-backed skin profiles by bot name when SkinPort is present, and fall back cleanly when it is absent or resolution fails.
```

```md
# ToDOLIST.md
- [x] Add optional SkinPort-based skin support for newly spawned fake players without making SkinPort a hard dependency.
```

```md
# context.md
- 2026-04-19: `SkinPortCompat` is an optional reflection bridge around `lain.mods.skins.impl.MojangService`. `FakePlayerProfiles` uses it only for new spawns; persisted restore remains unchanged by design.
```

- [ ] **Step 2: Run targeted tests**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest`

Expected: PASS with all targeted tests successful.

- [ ] **Step 3: Run full compile/build verification**

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava`

Expected: `BUILD SUCCESSFUL`

Run: `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add log.md ToDOLIST.md context.md
git commit -m "docs: record skinport fake player skin integration"
```

---

## Self-Review

### Spec Coverage

- “仅新生成时取皮肤” is covered by Task 3 through `FakePlayerProfiles.createSpawnProfile(...)`.
- “SkinPort 为可选依赖” is covered by Tasks 1 and 2 through the reflection bridge and unavailable-path tests.
- “失败静默回退” is covered by Tasks 1, 2, and 3 through empty-result and fallback-profile tests.
- “不改恢复链路” is covered by Task 3 file scope, which explicitly modifies only spawn profile selection and leaves `restorePersisted(...)` untouched.
- “补日志记录” is covered by Task 4.

### Placeholder Scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- All code-changing steps include concrete code snippets.
- All verification steps include concrete commands and expected outcomes.

### Type Consistency

- Compat method name stays `resolveProfile(String username)` across tests and implementation.
- Profile selector method name stays `createSpawnProfile(String username)` across tests and implementation.
- Test bridge type stays `SkinPortCompat.Bridge` across all tasks.

