# GTstaff NextGen Fake Player Runtime Wave B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the legacy `PlayerActionPack` internals to a small action pipeline with explicit targeting, use, attack, feedback, and diagnostic boundaries while preserving current player-visible behavior.

**Architecture:** Wave B keeps `PlayerActionPack` as the public shell used by commands and mixins, but moves its heavy logic into focused helper classes under `fakeplayer.action`. Target acquisition, use execution, attack execution, and feedback sync each get their own unit so later Wave C/D runtime migration can reuse them without dragging the whole legacy class forward.

**Tech Stack:** Java 8, Forge 1.7.10, Sponge Mixin, JUnit 5, Minecraft `EntityPlayerMP`, existing GTstaff compat bridges

---

## Scope Check

The approved spec splits implementation across four waves. This plan intentionally covers **Wave B only**:

1. Extract `TargetingService`
2. Extract `UseExecutor` and `FeedbackSync`
3. Extract `AttackExecutor`
4. Add lightweight action diagnostics behind a default-off config flag
5. Keep `PlayerActionPack` API and current command behavior intact

This plan does **not** migrate follow/monitor/repel/inventory services (Wave C) or switch the default runtime (Wave D).

## File Structure

### Create

- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/TargetingResult.java`
  - Immutable read model describing what the action pipeline hit.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/TargetingService.java`
  - Extracted raycast + nearby-entity targeting logic from `PlayerActionPack`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/FeedbackSync.java`
  - Extracted visible feedback boundary for swing/equipment/watcher sync.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/UseResult.java`
  - Immutable use-pipeline outcome describing block/item/bridge/feedback path.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/UseExecutor.java`
  - Extracted block/item/client-bridge use flow from `PlayerActionPack`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/AttackResult.java`
  - Immutable attack-pipeline outcome describing target kind and fallback stage.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/AttackExecutor.java`
  - Extracted entity/block/empty attack flow from `PlayerActionPack`.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/action/ActionDiagnostics.java`
  - Default-off action trace logger for `attack/use`.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/action/TargetingServiceTest.java`
  - Unit tests for targeting extraction.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/action/UseExecutorTest.java`
  - Unit tests for use-pipeline extraction.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/action/AttackExecutorTest.java`
  - Unit tests for attack-pipeline extraction.

### Modify

- `src/main/java/com/andgatech/gtstaff/config/Config.java`
  - Add default-off action diagnostics toggle.
- `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`
  - Delegate targeting/use/attack/feedback to the new helper classes while preserving existing API.
- `src/test/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPackTest.java`
  - Keep regression coverage at the shell level after extraction.
- `log.md`
  - Add Wave B implementation entry at the top.
- `ToDOLIST.md`
  - Move Wave B progress from current to completed.
- `context.md`
  - Record the new action-pipeline units and diagnostics flag.

## Task 1: Extract Targeting Service Without Changing Hit Semantics

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/TargetingResult.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/TargetingService.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/action/TargetingServiceTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`

- [ ] **Step 1: Write the failing targeting service test**

```java
package com.andgatech.gtstaff.fakeplayer.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.Test;

class TargetingServiceTest {

    @Test
    void findsEntityWhenEyeStartsInsideExpandedHitbox() {
        StubPlayer player = stubPlayer();
        StubWorld world = allocate(StubWorld.class);
        setField(Entity.class, player, "worldObj", world);
        player.posX = 0.0D;
        player.posY = 0.0D;
        player.posZ = 0.0D;
        player.rotationYaw = 0.0F;
        player.rotationPitch = 0.0F;
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D));

        StubTargetEntity target = allocate(StubTargetEntity.class);
        setField(Entity.class, target, "worldObj", world);
        setField(
            Entity.class,
            target,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(-0.1D, 1.2D, -0.1D, 0.1D, 1.8D, 0.1D));
        world.entities = java.util.Collections.<Entity>singletonList(target);

        TargetingResult result = new TargetingService(player).resolve();

        assertTrue(result.hitEntity());
        assertEquals(target, result.entity());
    }

    private static StubPlayer stubPlayer() {
        StubPlayer player = allocate(StubPlayer.class);
        setField(EntityPlayer.class, player, "eyeHeight", 1.62F);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        return player;
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

    private static final class StubPlayer extends EntityPlayerMP {

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void sendContainerToPlayer(Container container) {}
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }

    private static final class StubWorld extends WorldServer {

        private java.util.List<Entity> entities = java.util.Collections.emptyList();

        private StubWorld() {
            super(null, null, null, 0, null, null);
        }

        @Override
        public MovingObjectPosition func_147447_a(net.minecraft.util.Vec3 start, net.minecraft.util.Vec3 end,
            boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
            return null;
        }

        @Override
        public java.util.List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB bounds) {
            return entities;
        }
    }

    private static final class StubTargetEntity extends Entity {

        private StubTargetEntity() {
            super((World) null);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public boolean canBeCollidedWith() {
            return true;
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.TargetingServiceTest
```

Expected: FAIL because `TargetingResult` and `TargetingService` do not exist yet.

- [ ] **Step 3: Add the minimal targeting types and delegate `PlayerActionPack.getTarget()` through them**

```java
package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

public final class TargetingResult {

    private final MovingObjectPosition hit;

    public TargetingResult(MovingObjectPosition hit) {
        this.hit = hit;
    }

    public MovingObjectPosition hit() {
        return hit;
    }

    public boolean hitEntity() {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null;
    }

    public Entity entity() {
        return hitEntity() ? hit.entityHit : null;
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.action;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class TargetingService {

    private final EntityPlayerMP player;

    public TargetingService(EntityPlayerMP player) {
        this.player = player;
    }

    @SuppressWarnings("unchecked")
    public TargetingResult resolve() {
        double reach = player.theItemInWorldManager != null && player.theItemInWorldManager.isCreative() ? 5.0D : 4.5D;
        Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 lookVec = player.getLookVec();
        Vec3 endPos = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);

        MovingObjectPosition blockHit = player.worldObj.func_147447_a(eyePos, endPos, false, false, true);
        double blockDist = blockHit != null ? eyePos.distanceTo(blockHit.hitVec) : Double.MAX_VALUE;

        Entity closestEntity = null;
        Vec3 closestEntityHit = null;
        double closestEntityDist = blockDist;

        List<Entity> entities = player.worldObj.getEntitiesWithinAABBExcludingEntity(
            player,
            player.boundingBox.addCoord(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach)
                .expand(1.0D, 1.0D, 1.0D));
        for (Entity entity : entities) {
            if (!entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB expandedBB = entity.boundingBox.expand(border, border, border);
            MovingObjectPosition intercept = expandedBB.calculateIntercept(eyePos, endPos);

            if (expandedBB.isVecInside(eyePos)) {
                if (0.0D <= closestEntityDist) {
                    closestEntityDist = 0.0D;
                    closestEntityHit = intercept == null ? eyePos : intercept.hitVec;
                    closestEntity = entity;
                }
                continue;
            }

            if (intercept != null) {
                double dist = eyePos.distanceTo(intercept.hitVec);
                if (dist < closestEntityDist || closestEntityDist == 0.0D) {
                    if (entity == player.ridingEntity && !entity.canRiderInteract()) {
                        if (closestEntityDist == 0.0D) {
                            closestEntityHit = intercept.hitVec;
                            closestEntity = entity;
                        }
                    } else {
                        closestEntityDist = dist;
                        closestEntityHit = intercept.hitVec;
                        closestEntity = entity;
                    }
                }
            }
        }

        if (closestEntity != null && (closestEntityDist < blockDist || blockHit == null)) {
            return new TargetingResult(new MovingObjectPosition(closestEntity, closestEntityHit));
        }
        return new TargetingResult(blockHit);
    }
}
```

```java
protected MovingObjectPosition getTarget() {
    return new TargetingService(player).resolve().hit();
}
```

- [ ] **Step 4: Run the targeting tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.TargetingServiceTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest
```

Expected: PASS, including existing shell-level targeting regression coverage.

## Task 2: Extract Use Execution And Feedback Sync

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/FeedbackSync.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/UseResult.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/UseExecutor.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/action/UseExecutorTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`

- [ ] **Step 1: Write the failing use executor test**

```java
package com.andgatech.gtstaff.fakeplayer.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.Test;

class UseExecutorTest {

    @Test
    void reportsBridgeStateAfterDirectUseSucceeds() {
        StubPlayer player = allocate(StubPlayer.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.inventory.mainInventory[0] = new ItemStack(new Item());
        player.inventory.currentItem = 0;
        setField(net.minecraft.entity.Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));

        TrackingFeedbackSync feedback = new TrackingFeedbackSync();
        UseExecutor executor = new UseExecutor(player, feedback) {
            @Override
            protected boolean performDirectItemUse(ItemStack held) {
                return true;
            }

            @Override
            protected boolean performClientUseBridge(net.minecraft.util.MovingObjectPosition target, ItemStack held,
                boolean blockUsed, boolean itemUsed) {
                return blockUsed == false && itemUsed;
            }
        };

        UseResult result = executor.execute(null, 0);

        assertTrue(result.itemUsed());
        assertTrue(result.bridgeUsed());
        assertFalse(result.blockUsed());
        assertFalse(result.swingTriggered());
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

    private static final class StubPlayer extends EntityPlayerMP {

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void sendContainerToPlayer(net.minecraft.inventory.Container container) {}
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }

    private static final class TrackingFeedbackSync extends FeedbackSync {

        private boolean swung;

        private TrackingFeedbackSync() {
            super(null);
        }

        @Override
        public void swing() {
            swung = true;
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.UseExecutorTest
```

Expected: FAIL because `FeedbackSync`, `UseResult`, and `UseExecutor` do not exist yet.

- [ ] **Step 3: Add the use executor and route `PlayerActionPack.performUse(...)` through it**

```java
package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

public class FeedbackSync {

    private final EntityPlayerMP player;

    public FeedbackSync(EntityPlayerMP player) {
        this.player = player;
    }

    public void swing() {
        if (player == null) {
            return;
        }
        player.swingItem();
        if (player instanceof FakePlayer fake) {
            fake.broadcastSwingAnimation();
        }
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.action;

public final class UseResult {

    private final boolean blockUsed;
    private final boolean itemUsed;
    private final boolean bridgeUsed;
    private final boolean swingTriggered;

    public UseResult(boolean blockUsed, boolean itemUsed, boolean bridgeUsed, boolean swingTriggered) {
        this.blockUsed = blockUsed;
        this.itemUsed = itemUsed;
        this.bridgeUsed = bridgeUsed;
        this.swingTriggered = swingTriggered;
    }

    public boolean blockUsed() {
        return blockUsed;
    }

    public boolean itemUsed() {
        return itemUsed;
    }

    public boolean bridgeUsed() {
        return bridgeUsed;
    }

    public boolean swingTriggered() {
        return swingTriggered;
    }

    public boolean accepted() {
        return blockUsed || itemUsed || bridgeUsed || swingTriggered;
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.andgatech.gtstaff.integration.FakePlayerClientUseCompat;

public class UseExecutor {

    private final EntityPlayerMP player;
    private final FeedbackSync feedbackSync;

    public UseExecutor(EntityPlayerMP player, FeedbackSync feedbackSync) {
        this.player = player;
        this.feedbackSync = feedbackSync;
    }

    public UseResult execute(MovingObjectPosition target, int itemUseCooldown) {
        if (player == null || player.theItemInWorldManager == null || player.worldObj == null) {
            return new UseResult(false, false, false, false);
        }
        if (itemUseCooldown > 0 || player.isUsingItem()) {
            return new UseResult(false, false, false, false);
        }

        ItemStack held = player.getCurrentEquippedItem();
        boolean blockUsed = false;
        boolean itemUsed = false;

        if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            float hitX = 0.5F;
            float hitY = 0.5F;
            float hitZ = 0.5F;
            Vec3 hitVec = target.hitVec;
            if (hitVec != null) {
                hitX = (float) (hitVec.xCoord - target.blockX);
                hitY = (float) (hitVec.yCoord - target.blockY);
                hitZ = (float) (hitVec.zCoord - target.blockZ);
            }
            blockUsed = performBlockActivationUse(target, held, hitX, hitY, hitZ);
        }

        if (!blockUsed && held != null) {
            itemUsed = performDirectItemUse(held);
        }

        boolean bridgeUsed = performClientUseBridge(target, held, blockUsed, itemUsed);
        if (blockUsed || itemUsed || bridgeUsed) {
            return new UseResult(blockUsed, itemUsed, bridgeUsed, false);
        }

        feedbackSync.swing();
        return new UseResult(false, false, false, true);
    }

    protected boolean performBlockActivationUse(MovingObjectPosition target, ItemStack held, float hitX, float hitY,
        float hitZ) {
        return player.theItemInWorldManager.activateBlockOrUseItem(
            player,
            player.worldObj,
            held,
            target.blockX,
            target.blockY,
            target.blockZ,
            target.sideHit,
            hitX,
            hitY,
            hitZ);
    }

    protected boolean performDirectItemUse(ItemStack held) {
        return player.theItemInWorldManager.tryUseItem(player, player.worldObj, held);
    }

    protected boolean performClientUseBridge(MovingObjectPosition target, ItemStack held, boolean blockUsed,
        boolean itemUsed) {
        return FakePlayerClientUseCompat.tryUse(player, held, target, blockUsed, itemUsed);
    }
}
```

```java
protected boolean performUse(MovingObjectPosition target) {
    UseResult result = new UseExecutor(player, new FeedbackSync(player)).execute(target, itemUseCooldown);
    if (result.accepted()) {
        itemUseCooldown = 3;
        return true;
    }
    return false;
}
```

- [ ] **Step 4: Run the use regression tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.UseExecutorTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest
```

Expected: PASS, including existing swing and compat-bridge regressions.

## Task 3: Extract Attack Executor And Preserve Current Fallback Semantics

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/AttackResult.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/AttackExecutor.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/action/AttackExecutorTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`

- [ ] **Step 1: Write the failing attack executor test**

```java
package com.andgatech.gtstaff.fakeplayer.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.junit.jupiter.api.Test;

class AttackExecutorTest {

    @Test
    void fallsBackToDirectDamageWhenVanillaAttackHasNoEffect() {
        StubPlayer player = allocate(StubPlayer.class);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        TrackingEntity target = new TrackingEntity();

        AttackExecutor executor = new AttackExecutor(player, new FeedbackSync(player)) {
            @Override
            protected boolean performEntityAttackFallback(Entity targetEntity) {
                return targetEntity.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), 4.0F);
            }
        };

        AttackResult result = executor.execute(new MovingObjectPosition(target, Vec3.createVectorHelper(0.0D, 0.0D, 0.0D)));

        assertTrue(result.usedFallback());
        assertEquals(1, target.damageCalls);
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

    private static final class StubPlayer extends EntityPlayerMP {

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void attackTargetEntityWithCurrentItem(Entity targetEntity) {}

        @Override
        public void sendContainerToPlayer(net.minecraft.inventory.Container container) {}
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }

    private static final class TrackingEntity extends Entity {

        private int damageCalls;

        private TrackingEntity() {
            super((net.minecraft.world.World) null);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public boolean attackEntityFrom(net.minecraft.util.DamageSource source, float amount) {
            damageCalls++;
            return true;
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.AttackExecutorTest
```

Expected: FAIL because `AttackResult` and `AttackExecutor` do not exist yet.

- [ ] **Step 3: Add the attack executor and route `PlayerActionPack.performAttack(...)` through it**

```java
package com.andgatech.gtstaff.fakeplayer.action;

public final class AttackResult {

    private final boolean accepted;
    private final boolean usedFallback;
    private final boolean swung;

    public AttackResult(boolean accepted, boolean usedFallback, boolean swung) {
        this.accepted = accepted;
        this.usedFallback = usedFallback;
        this.swung = swung;
    }

    public boolean accepted() {
        return accepted;
    }

    public boolean usedFallback() {
        return usedFallback;
    }

    public boolean swung() {
        return swung;
    }
}
```

```java
package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;

public class AttackExecutor {

    private final EntityPlayerMP player;
    private final FeedbackSync feedbackSync;

    public AttackExecutor(EntityPlayerMP player, FeedbackSync feedbackSync) {
        this.player = player;
        this.feedbackSync = feedbackSync;
    }

    public AttackResult execute(MovingObjectPosition target) {
        if (target == null) {
            feedbackSync.swing();
            return new AttackResult(true, false, true);
        }
        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity targetEntity = target.entityHit;
            if (targetEntity == null) {
                feedbackSync.swing();
                return new AttackResult(true, false, true);
            }

            boolean usedFallback = performEntityAttack(targetEntity);
            feedbackSync.swing();
            return new AttackResult(true, usedFallback, true);
        }
        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new AttackResult(false, false, false);
        }
        feedbackSync.swing();
        return new AttackResult(true, false, true);
    }

    protected boolean performEntityAttack(Entity targetEntity) {
        EntityAttackState attackState = EntityAttackState.capture(targetEntity);
        player.attackTargetEntityWithCurrentItem(targetEntity);
        if (!attackState.hasObservableAttackEffect(targetEntity)) {
            return performEntityAttackFallback(targetEntity);
        }
        return false;
    }

    protected boolean performEntityAttackFallback(Entity targetEntity) {
        float fallbackDamage = resolveFallbackAttackDamage();
        if (fallbackDamage <= 0.0F) {
            return false;
        }
        DamageSource damageSource = DamageSource.causePlayerDamage(player);
        if (targetEntity instanceof EntityLivingBase livingTarget) {
            return forceLivingEntityDamage(livingTarget, damageSource, fallbackDamage, livingTarget.getHealth());
        }
        return targetEntity.attackEntityFrom(damageSource, fallbackDamage);
    }

    protected float resolveFallbackAttackDamage() {
        if (player == null || player.getEntityAttribute(SharedMonsterAttributes.attackDamage) == null) {
            return 1.0F;
        }
        return Math.max(1.0F, (float) player.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue());
    }

    protected boolean forceLivingEntityDamage(EntityLivingBase targetEntity, DamageSource damageSource, float damage,
        float previousHealth) {
        if (targetEntity == null || damage <= 0.0F || previousHealth <= 0.0F) {
            return false;
        }
        targetEntity.prevHealth = previousHealth;
        targetEntity.hurtResistantTime = targetEntity.maxHurtResistantTime;
        targetEntity.maxHurtTime = 10;
        targetEntity.hurtTime = targetEntity.maxHurtTime;
        targetEntity.velocityChanged = true;
        float updatedHealth = Math.max(0.0F, previousHealth - damage);
        targetEntity.setHealth(updatedHealth);
        if (updatedHealth <= 0.0F && !targetEntity.isDead) {
            targetEntity.onDeath(damageSource);
        }
        return updatedHealth < previousHealth;
    }

    private static final class EntityAttackState {

        private final boolean dead;
        private final boolean velocityChanged;
        private final boolean burning;
        private final float health;
        private final int deathTime;
        private final boolean living;

        private EntityAttackState(boolean dead, boolean velocityChanged, boolean burning, float health, int deathTime,
            boolean living) {
            this.dead = dead;
            this.velocityChanged = velocityChanged;
            this.burning = burning;
            this.health = health;
            this.deathTime = deathTime;
            this.living = living;
        }

        private static EntityAttackState capture(Entity targetEntity) {
            if (targetEntity instanceof EntityLivingBase livingTarget) {
                return new EntityAttackState(
                    livingTarget.isDead,
                    livingTarget.velocityChanged,
                    livingTarget.isBurning(),
                    livingTarget.getHealth(),
                    livingTarget.deathTime,
                    true);
            }
            return new EntityAttackState(
                targetEntity != null && targetEntity.isDead,
                targetEntity != null && targetEntity.velocityChanged,
                targetEntity != null && targetEntity.isBurning(),
                0.0F,
                0,
                false);
        }

        private boolean hasObservableAttackEffect(Entity targetEntity) {
            if (targetEntity == null) {
                return false;
            }
            if (!living) {
                return targetEntity.isDead != dead || targetEntity.velocityChanged != velocityChanged
                    || targetEntity.isBurning() != burning;
            }
            if (!(targetEntity instanceof EntityLivingBase livingTarget)) {
                return true;
            }
            return livingTarget.isDead != dead || livingTarget.getHealth() < health || livingTarget.deathTime > deathTime;
        }
    }
}
```

```java
protected boolean performAttack(MovingObjectPosition target) {
    if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        return attackBlock(target);
    }
    return new AttackExecutor(player, new FeedbackSync(player)).execute(target).accepted();
}
```

- [ ] **Step 4: Run the attack regression tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.AttackExecutorTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest
```

Expected: PASS, including current fallback and empty-swing regressions.

## Task 4: Add Lightweight Action Diagnostics And Finish PlayerActionPack Wiring

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/action/ActionDiagnostics.java`
- Modify: `src/main/java/com/andgatech/gtstaff/config/Config.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`
- Modify: `src/test/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPackTest.java`

- [ ] **Step 1: Write the failing diagnostics regression test**

```java
@Test
void diagnosticsDisabledByDefault() {
    assertFalse(Config.fakePlayerActionDiagnostics);
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest
```

Expected: FAIL because `fakePlayerActionDiagnostics` does not exist yet.

- [ ] **Step 3: Add the diagnostics flag and log attack/use outcomes only when enabled**

```java
public static boolean fakePlayerActionDiagnostics = false;
```

```java
fakePlayerActionDiagnostics = configuration.getBoolean(
    "fakePlayerActionDiagnostics",
    FAKE_PLAYER,
    fakePlayerActionDiagnostics,
    "Enable lightweight fake-player attack/use diagnostics in the server log.");
```

```java
package com.andgatech.gtstaff.fakeplayer.action;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.config.Config;

public final class ActionDiagnostics {

    private ActionDiagnostics() {}

    public static void logUse(String botName, UseResult result) {
        if (!Config.fakePlayerActionDiagnostics) {
            return;
        }
        GTstaff.LOG.info(
            "[ActionDiagnostics] use bot={} blockUsed={} itemUsed={} bridgeUsed={} swingTriggered={}",
            botName,
            result.blockUsed(),
            result.itemUsed(),
            result.bridgeUsed(),
            result.swingTriggered());
    }

    public static void logAttack(String botName, AttackResult result) {
        if (!Config.fakePlayerActionDiagnostics) {
            return;
        }
        GTstaff.LOG.info(
            "[ActionDiagnostics] attack bot={} accepted={} usedFallback={} swung={}",
            botName,
            result.accepted(),
            result.usedFallback(),
            result.swung());
    }
}
```

```java
protected boolean performUse(MovingObjectPosition target) {
    UseResult result = new UseExecutor(player, new FeedbackSync(player)).execute(target, itemUseCooldown);
    ActionDiagnostics.logUse(player.getCommandSenderName(), result);
    if (result.accepted()) {
        itemUseCooldown = 3;
        return true;
    }
    return false;
}

protected boolean performAttack(MovingObjectPosition target) {
    if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        return attackBlock(target);
    }
    AttackResult result = new AttackExecutor(player, new FeedbackSync(player)).execute(target);
    ActionDiagnostics.logAttack(player.getCommandSenderName(), result);
    return result.accepted();
}
```

- [ ] **Step 4: Run the full Wave B verification set**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.TargetingServiceTest --tests com.andgatech.gtstaff.fakeplayer.action.UseExecutorTest --tests com.andgatech.gtstaff.fakeplayer.action.AttackExecutorTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest
```

Expected: PASS. The action pipeline is extracted, diagnostics stay default-off, and existing command/registry regressions remain green.

## Task 5: Documentation Updates

**Files:**
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: Add the work log entry**

```markdown
## 2026-04-20：完成 nextgen fake player runtime Wave B 动作链迁移第一阶段

### 已完成
- 将 `PlayerActionPack` 内部动作链拆出为 `TargetingService`、`UseExecutor`、`AttackExecutor`、`FeedbackSync`
- 保持 `PlayerActionPack` 外部 API 不变，现有 `/player` 命令与 mixin 入口无需改动
- 新增默认关闭的 `fakePlayerActionDiagnostics` 诊断开关，用于记录一次 `attack/use` 的最小链路摘要
- 通过 Wave B 回归测试集，确认攻击 fallback、右键兼容桥和空挥手反馈保持不变
```

- [ ] **Step 2: Update TODO and context**

Update `ToDOLIST.md`:

```markdown
- [x] 完成 nextgen fake player runtime Wave B 动作链迁移：抽出 targeting / use / attack / feedback / diagnostics，保持 `PlayerActionPack` 外部行为不变
```

Update `context.md`:

```markdown
### Runtime 动作链
- `fakeplayer.action.TargetingService`：统一封装射线命中与近距离实体兜底逻辑
- `fakeplayer.action.UseExecutor`：统一封装方块交互、物品右键、客户端链路兼容桥与空挥手反馈
- `fakeplayer.action.AttackExecutor`：统一封装实体攻击主链、fallback 与空挥手反馈
- `fakeplayer.action.FeedbackSync`：统一封装 swing watcher 同步入口
- `Config.fakePlayerActionDiagnostics`：默认关闭的动作诊断开关
```

