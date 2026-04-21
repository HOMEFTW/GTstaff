# GTstaff Fake Player Backhand Offhand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `GTstaff` 当前统一假人背包界面中接入 `Backhand` 副手槽，让管理员可以直接给假人装备副手物品，并且副手黑名单与 Shift 点击行为完全跟随 `Backhand` 当前规则。

**Architecture:** 保留现有 `FakePlayerManagerService.openInventoryManager(...) -> FakePlayerInventoryGuiHandler -> FakePlayerInventoryContainer/FakePlayerInventoryGui` 打开链路，不新增独立窗口。底层通过扩展 `FakePlayerInventoryView` 把副手槽纳入固定槽位映射，再由 `FakePlayerInventoryContainer` 插入 `BackhandSlot` 并调整 Shift 点击优先级；GUI 只做最小布局补位，在护甲槽旁边显示副手槽。

**Tech Stack:** Java 8, Forge 1.7.10, Backhand, Baubles Expanded, vanilla `Container` / `GuiContainer`, JUnit 5, Gradle

---

## File Structure

### Create

- 无新增业务类文件；本次在现有统一背包文件上增量扩展，避免引入第二套“装备库存包装层”。

### Modify

- `dependencies.gradle`
  - 添加 `Backhand` 硬依赖。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryView.java`
  - 扩展固定槽位总数，加入副手槽常量、服务端读写与客户端缓存映射。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java`
  - 在护甲槽与 hotbar 之间插入 `BackhandSlot`，暴露副手槽判定，调整 Shift 点击优先级。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java`
  - 在护甲区旁边绘制/容纳一个副手槽，不改整体分页与饰品区结构。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryViewTest.java`
  - 补副手槽映射与回写红绿测试。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`
  - 补副手槽顺序、客户端回退与 Shift 点击优先级红绿测试。
- `log.md`
  - 记录本次计划与实现完成情况。

---

### Task 1: Add Backhand Dependency And Extend Inventory View

**Files:**
- Modify: `dependencies.gradle`
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryView.java`
- Modify: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryViewTest.java`

- [ ] **Step 1: Write the failing view tests**

```java
@Test
void mapsOffhandBetweenArmorAndHotbar() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    fakePlayer.inventory.armorInventory[3] = namedStack("Helmet", 1);
    xonin.backhand.api.core.BackhandUtils.setPlayerOffhandItem(fakePlayer, namedStack("Torch", 16));
    fakePlayer.inventory.mainInventory[0] = namedStack("Sword", 1);
    fakePlayer.inventory.mainInventory[9] = namedStack("Pickaxe", 1);

    FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

    assertEquals("Helmet", view.getStackInSlot(0).getDisplayName());
    assertEquals("Torch", view.getStackInSlot(4).getDisplayName());
    assertEquals("Sword", view.getStackInSlot(5).getDisplayName());
    assertEquals("Pickaxe", view.getStackInSlot(14).getDisplayName());
}

@Test
void setInventorySlotContentsWritesBackIntoOffhand() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

    view.setInventorySlotContents(4, namedStack("Shield", 1));

    assertEquals(
        "Shield",
        xonin.backhand.api.core.BackhandUtils.getOffhandItem(fakePlayer).getDisplayName());
}
```

- [ ] **Step 2: Run the view tests to verify they fail**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest
```

Expected: FAIL because `FakePlayerInventoryView` 目前没有副手槽，`SLOT_COUNT` 和索引映射仍停留在 40 槽模型。

- [ ] **Step 3: Add Backhand dependency and minimal view implementation**

```groovy
dependencies {
    implementation(gtnh("GT5-Unofficial"))
    implementation(gtnh("GTNHLib"))
    implementation(gtnh("ModularUI"))
    implementation("com.github.GTNewHorizons:Baubles-Expanded:2.2.0-GTNH:dev")
    implementation(gtnh("Backhand"))
}
```

```java
public final class FakePlayerInventoryView implements IInventory {

    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int OFFHAND_SLOT_INDEX = 4;
    public static final int HOTBAR_SLOT_START = OFFHAND_SLOT_INDEX + 1;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final int MAIN_SLOT_START = HOTBAR_SLOT_START + HOTBAR_SLOT_COUNT;
    public static final int MAIN_SLOT_COUNT = 27;
    public static final int SLOT_COUNT = MAIN_SLOT_START + MAIN_SLOT_COUNT;

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isValidSlot(slot)) {
            return null;
        }
        if (this.fakePlayer != null) {
            return getServerStack(slot);
        }
        return this.clientSlots[slot];
    }

    private ItemStack getServerStack(int slot) {
        InventoryPlayer inventory = this.fakePlayer.inventory;
        if (slot < ARMOR_SLOT_COUNT) {
            return inventory.armorInventory[3 - slot];
        }
        if (slot == OFFHAND_SLOT_INDEX) {
            return xonin.backhand.api.core.BackhandUtils.getOffhandItem(this.fakePlayer);
        }
        if (slot < MAIN_SLOT_START) {
            return inventory.mainInventory[slot - HOTBAR_SLOT_START];
        }
        return inventory.mainInventory[slot - MAIN_SLOT_START + 9];
    }

    private void setServerStack(int slot, ItemStack stack) {
        InventoryPlayer inventory = this.fakePlayer.inventory;
        if (slot < ARMOR_SLOT_COUNT) {
            inventory.armorInventory[3 - slot] = stack;
            return;
        }
        if (slot == OFFHAND_SLOT_INDEX) {
            xonin.backhand.api.core.BackhandUtils.setPlayerOffhandItem(this.fakePlayer, stack);
            return;
        }
        if (slot < MAIN_SLOT_START) {
            inventory.mainInventory[slot - HOTBAR_SLOT_START] = stack;
            return;
        }
        inventory.mainInventory[slot - MAIN_SLOT_START + 9] = stack;
    }
}
```

- [ ] **Step 4: Run the view tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest
```

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add dependencies.gradle src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryView.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryViewTest.java
git commit -m "feat: add fake player offhand view support"
```

---

### Task 2: Insert Backhand Slot Into Unified Container

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java`
- Modify: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`

- [ ] **Step 1: Write the failing container tests**

```java
@Test
void serverContainerAddsOffhandSlotBetweenArmorAndHotbar() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    TestPlayer player = stubPlayer();

    FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

    assertTrue(container.isFakeInventorySlot(3));
    assertTrue(container.isOffhandSlot(4));
    assertFalse(container.isOffhandSlot(5));
}

@Test
void transferStackPrefersCompatibleOffhandSlotBeforeBaublesAndMainInventory() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    TestPlayer player = stubPlayer();
    player.inventory.mainInventory[9] = namedStack("Torch", 16);

    FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

    ItemStack moved = container.transferStackInSlot(player, container.getPlayerInventoryStartIndex());

    assertNotNull(moved);
    assertEquals("Torch", container.getFakeInventory().getStackInSlot(FakePlayerInventoryView.OFFHAND_SLOT_INDEX).getDisplayName());
}

@Test
void clientContainerStillAddsOffhandSlotWithoutFakePlayerEntity() {
    TestPlayer player = stubPlayer();

    FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, FakePlayerInventoryView.client("UiBot"));

    assertTrue(container.isOffhandSlot(4));
}
```

- [ ] **Step 2: Run the container tests to verify they fail**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest
```

Expected: FAIL because container 还没有副手槽分区、`isOffhandSlot(...)` 或副手优先 Shift 点击路径。

- [ ] **Step 3: Implement the minimal container changes**

```java
public class FakePlayerInventoryContainer extends Container {

    private static final int OFFHAND_SLOT_X = 80;
    private static final int OFFHAND_SLOT_Y = 18;

    public boolean isOffhandSlot(int slotIndex) {
        return slotIndex == FakePlayerInventoryView.OFFHAND_SLOT_INDEX;
    }

    private void addFakeInventorySlots() {
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 0, 8, 18, 0));
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 1, 26, 18, 1));
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 2, 44, 18, 2));
        addSlotToContainer(new FakePlayerArmorSlot(this.fakeInventory, 3, 62, 18, 3));
        addSlotToContainer(new xonin.backhand.api.core.BackhandSlot(
            this.fakeInventory,
            FakePlayerInventoryView.OFFHAND_SLOT_INDEX,
            OFFHAND_SLOT_X,
            OFFHAND_SLOT_Y));

        for (int hotbarSlot = 0; hotbarSlot < FakePlayerInventoryView.HOTBAR_SLOT_COUNT; hotbarSlot++) {
            addSlotToContainer(new Slot(
                this.fakeInventory,
                FakePlayerInventoryView.HOTBAR_SLOT_START + hotbarSlot,
                8 + hotbarSlot * 18,
                36));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = FakePlayerInventoryView.MAIN_SLOT_START + row * 9 + column;
                addSlotToContainer(new Slot(this.fakeInventory, slotIndex, 8 + column * 18, 54 + row * 18));
            }
        }
    }

    private boolean mergeIntoFakeInventory(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemArmor armor) {
            int armorSlotIndex = armorContainerSlotForType(armor.armorType);
            if (armorSlotIndex >= 0 && mergeItemStack(stack, armorSlotIndex, armorSlotIndex + 1, false)) {
                return true;
            }
        }
        if (mergeItemStack(
            stack,
            FakePlayerInventoryView.OFFHAND_SLOT_INDEX,
            FakePlayerInventoryView.OFFHAND_SLOT_INDEX + 1,
            false)) {
            return true;
        }
        if (mergeIntoBaublesInventory(stack)) {
            return true;
        }
        return mergeItemStack(
            stack,
            FakePlayerInventoryView.HOTBAR_SLOT_START,
            FakePlayerInventoryView.SLOT_COUNT,
            false);
    }
}
```

- [ ] **Step 4: Run the container tests to verify they pass**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest
```

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java
git commit -m "feat: add fake player offhand slot to inventory container"
```

---

### Task 3: Adjust Unified GUI Layout For Offhand Slot

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java`

- [ ] **Step 1: Confirm current offhand slot position requirement**

Use the design constraint directly:

```text
副手槽必须放在护甲槽旁边，不新增独立面板，不挤占右侧 Baubles 区域。
```

- [ ] **Step 2: Implement the minimal GUI layout change**

```java
public class FakePlayerInventoryGui extends GuiContainer {

    private static final int OFFHAND_SLOT_HINT_X = 80;
    private static final int OFFHAND_SLOT_HINT_Y = 18;

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        this.mc.getTextureManager().bindTexture(CHEST_TEXTURE);
        int originX = (this.width - this.xSize) / 2;
        int originY = (this.height - this.ySize) / 2;
        drawTexturedModalRect(originX, originY, 0, 0, 176, TOP_SECTION_HEIGHT);
        drawTexturedModalRect(originX, originY + TOP_SECTION_HEIGHT, 0, 126, 176, 96);
        drawOffhandSlotHint(originX, originY);
        drawBaublesPanel(originX, originY);
        drawSelectedHotbarHighlight(originX, originY);
    }

    private void drawOffhandSlotHint(int originX, int originY) {
        drawRect(originX + OFFHAND_SLOT_HINT_X, originY + OFFHAND_SLOT_HINT_Y, originX + OFFHAND_SLOT_HINT_X + 18, originY + OFFHAND_SLOT_HINT_Y + 18, 0xFF8B8B8B);
        drawRect(originX + OFFHAND_SLOT_HINT_X + 1, originY + OFFHAND_SLOT_HINT_Y + 1, originX + OFFHAND_SLOT_HINT_X + 17, originY + OFFHAND_SLOT_HINT_Y + 17, 0xFFD7CCAE);
    }
}
```

- [ ] **Step 3: Build to verify the GUI compiles**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java
git commit -m "feat: show fake player offhand slot in gui"
```

---

### Task 4: Run Verification And Refresh Project Log

**Files:**
- Modify: `log.md`

- [ ] **Step 1: Run the targeted automated checks**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest --tests com.andgatech.gtstaff.ui.FakePlayerBaublesSlotLayoutTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: Build the client test jar**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true build -x test
```

Expected: BUILD SUCCESSFUL and updated jars under `build/libs/`。

- [ ] **Step 3: Append the implementation result to `log.md`**

```markdown
## 2026-04-20：完成 Backhand 副手槽接入

### 已完成
- 为统一假人背包增加真实副手槽
- `FakePlayerInventoryView` 扩展为 41 槽并接入 `BackhandUtils`
- `FakePlayerInventoryContainer` 增加 `BackhandSlot`，Shift 点击优先级更新为“护甲 > 副手 > 饰品 > 普通背包”
- 重新打包客户端测试 jar

### 遇到的问题
- **副手槽索引插入后 hotbar/main inventory 全体后移**：通过先补视图和容器红测试锁定新索引，再做最小实现规避回归

### 做出的决定
- 继续沿用统一背包单窗口方案，不为副手单独新增分页或命令
```

- [ ] **Step 4: Commit**

```bash
git add log.md
git commit -m "docs: log fake player offhand implementation"
```
