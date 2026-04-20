# GTstaff Fake Player Baubles Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `GTstaff` 的假人统一背包容器接入 `Baubles Expanded` 饰品栏支持，在同一个 `Container/GuiContainer` 中管理假人的普通背包与动态饰品槽。

**Architecture:** 保留现有 `FakePlayerManagerService.openInventoryManager(...) -> FakePlayerInventoryGuiHandler -> FakePlayerInventoryContainer/FakePlayerInventoryGui` 打开链路，不新增独立窗口。服务端容器直接持有假人的普通背包视图和假人的 `InventoryBaubles`，客户端沿用同一 GUI ID 构造等价槽位结构；饰品槽布局和滚动计算抽成纯逻辑 helper，先用单测锁死，再用于容器和 GUI。

**Tech Stack:** Java 8, Forge 1.7.10, Baubles Expanded, vanilla `Container` / `GuiContainer`, JUnit 5, Gradle

---

## File Structure

### Create

- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerBaublesSlotLayout.java`
  - 纯逻辑 helper；负责计算饰品区列数、可滚动性、滚动行偏移、槽位显示位置。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerBaublesSlotLayoutTest.java`
  - 锁定动态饰品区布局和滚动计算。

### Modify

- `dependencies.gradle`
  - 添加 `Baubles-Expanded` 硬依赖。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java`
  - 在现有假人背包容器中插入 `InventoryBaubles` 槽位区，扩展 `transferStackInSlot(...)`、槽位索引分区与滚动支持。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java`
  - 扩展 GUI 尺寸、绘制右侧饰品区、显示动态槽位、处理鼠标滚轮。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiHandler.java`
  - 服务端/客户端都解析假人的 `InventoryBaubles`，并把布局数据交给统一容器。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`
  - 为新增的饰品槽分区、Shift 点击优先级和主手高亮回归补充测试。
- `log.md`
  - 追加本轮实现计划落地记录。
- `ToDOLIST.md`
  - 保持当前计划项不变，仅在任务完成时再移动。
- `context.md`
  - 本轮仅写计划，不改实现；无需新增实现内容条目。

## Task 1: Add Baubles Dependency And Lock Layout Math

**Files:**
- Modify: `dependencies.gradle`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerBaublesSlotLayout.java`
- Create: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerBaublesSlotLayoutTest.java`

- [ ] **Step 1: Write the failing layout test**

```java
@Test
void computesColumnsAndScrollRowsForTallBaublesPanel() {
    assertEquals(4, FakePlayerBaublesSlotLayout.resolveColumns(20, 4));
    assertTrue(FakePlayerBaublesSlotLayout.canScroll(40, 4, 8));
    assertEquals(2, FakePlayerBaublesSlotLayout.resolveRowOffset(40, 4, 8, 0.5F));
}

@Test
void hidesSlotsOutsideVisibleBaublesWindow() {
    int hiddenY = FakePlayerBaublesSlotLayout.resolveDisplayY(35, 4, 0, 12, 18, -2000);
    assertEquals(-2000, hiddenY);
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerBaublesSlotLayoutTest
```

Expected: FAIL with `FakePlayerBaublesSlotLayout` missing.

- [ ] **Step 3: Add the Baubles dependency and minimal layout helper**

```groovy
dependencies {
    implementation(gtnh("GT5-Unofficial"))
    implementation(gtnh("GTNHLib"))
    implementation(gtnh("ModularUI"))
    implementation(gtnh("Baubles-Expanded"))
}
```

```java
public final class FakePlayerBaublesSlotLayout {

    private FakePlayerBaublesSlotLayout() {}

    public static int resolveColumns(int visibleSlots, int maxColumns) {
        for (int cols = 1; cols < maxColumns; cols++) {
            if ((visibleSlots + cols - 1) / cols <= 8) {
                return cols;
            }
        }
        return maxColumns;
    }

    public static boolean canScroll(int visibleSlots, int columns, int visibleRows) {
        return columns > 0 && visibleSlots > columns * visibleRows;
    }

    public static int resolveRowOffset(int visibleSlots, int columns, int visibleRows, float scrollOffset) {
        int totalRows = (visibleSlots + columns - 1) / columns;
        int maxOffset = Math.max(0, totalRows - visibleRows);
        int rowOffset = (int) (Math.max(0F, Math.min(1F, scrollOffset)) * maxOffset + 0.5F);
        return Math.max(0, Math.min(maxOffset, rowOffset));
    }

    public static int resolveDisplayY(int slotIndex, int columns, int rowOffset, int startY, int spacing, int hiddenY) {
        int row = slotIndex / columns;
        int scrolledRow = row - rowOffset;
        int y = startY + scrolledRow * spacing;
        return y < startY || y > startY + spacing * 7 ? hiddenY : y;
    }
}
```

- [ ] **Step 4: Run the layout test to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerBaublesSlotLayoutTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dependencies.gradle src/main/java/com/andgatech/gtstaff/ui/FakePlayerBaublesSlotLayout.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerBaublesSlotLayoutTest.java
git commit -m "build: add baubles expanded dependency"
```

## Task 2: Extend The Container With Baubles Slots

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java`
- Modify: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`

- [ ] **Step 1: Write the failing container test for slot partitioning**

```java
@Test
void serverContainerAddsBaublesSlotsBetweenFakeAndPlayerInventories() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    TestPlayer player = stubPlayer();
    InventoryBaubles baubles = new InventoryBaubles(fakePlayer);

    FakePlayerInventoryContainer container =
        FakePlayerInventoryContainer.forTest(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer), baubles);

    assertTrue(container.isFakeInventorySlot(39));
    assertTrue(container.isBaublesSlot(40));
    assertFalse(container.isBaublesSlot(40 + baubles.getSizeInventory()));
}
```

- [ ] **Step 2: Write the failing container test for bauble-first transfer**

```java
@Test
void transferStackPrefersCompatibleBaublesSlotBeforeFakeMainInventory() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    TestPlayer player = stubPlayer();
    player.inventory.mainInventory[9] = universalBaubleStack("Ring");
    InventoryBaubles baubles = new InventoryBaubles(fakePlayer);

    FakePlayerInventoryContainer container =
        FakePlayerInventoryContainer.forTest(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer), baubles);

    container.transferStackInSlot(player, container.getPlayerInventoryStartIndex());

    assertNotNull(baubles.getStackInSlot(0));
    assertEquals("Ring", baubles.getStackInSlot(0).getDisplayName());
}
```

- [ ] **Step 3: Run the container test to verify it fails**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest
```

Expected: FAIL because the container has no baubles partition or bauble-first transfer path.

- [ ] **Step 4: Implement the minimal container changes**

```java
public class FakePlayerInventoryContainer extends Container {

    private final int baublesSlotStart;
    private final int baublesSlotEnd;
    private final int playerSlotStart;
    private final int playerSlotEnd;

    public static FakePlayerInventoryContainer server(EntityPlayerMP player, FakePlayer fakePlayer) {
        InventoryBaubles baubles = requireBaublesInventory(fakePlayer);
        return new FakePlayerInventoryContainer(player, fakePlayer, FakePlayerInventoryView.server(fakePlayer), baubles);
    }

    public static FakePlayerInventoryContainer client(EntityPlayer player, FakePlayerInventoryView fakeInventory,
        InventoryBaubles baubles) {
        return new FakePlayerInventoryContainer(player, null, fakeInventory, baubles);
    }

    static FakePlayerInventoryContainer forTest(EntityPlayer player, FakePlayer fakePlayer,
        FakePlayerInventoryView fakeInventory, InventoryBaubles baubles) {
        return new FakePlayerInventoryContainer(player, fakePlayer, fakeInventory, baubles);
    }

    public boolean isBaublesSlot(int slotIndex) {
        return slotIndex >= this.baublesSlotStart && slotIndex < this.baublesSlotEnd;
    }

    public int getPlayerInventoryStartIndex() {
        return this.playerSlotStart;
    }

    public int getBaublesPanelWidth() {
        return Math.max(18, getBaublesColumns() * 18);
    }

    public int getBaublesTitleX() {
        return 176 + 8;
    }

    public boolean canScrollBaubles() {
        return FakePlayerBaublesSlotLayout.canScroll(
            this.baublesSlotEnd - this.baublesSlotStart,
            getBaublesColumns(),
            8);
    }

    public float adjustBaublesScroll(float currentScroll, int wheelStep) {
        int totalRows = (this.baublesSlotEnd - this.baublesSlotStart + getBaublesColumns() - 1) / getBaublesColumns();
        int maxOffset = Math.max(0, totalRows - 8);
        if (maxOffset == 0) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, currentScroll + wheelStep / (float) maxOffset));
    }

    public void applyBaublesScroll(float scroll) {
        int rowOffset = FakePlayerBaublesSlotLayout.resolveRowOffset(
            this.baublesSlotEnd - this.baublesSlotStart,
            getBaublesColumns(),
            8,
            scroll);
        for (int slot = this.baublesSlotStart; slot < this.baublesSlotEnd; slot++) {
            Slot baubleSlot = (Slot) this.inventorySlots.get(slot);
            baubleSlot.yDisplayPosition = FakePlayerBaublesSlotLayout.resolveDisplayY(
                slot - this.baublesSlotStart,
                getBaublesColumns(),
                rowOffset,
                18,
                18,
                -2000);
        }
    }

    private int getBaublesColumns() {
        return FakePlayerBaublesSlotLayout.resolveColumns(this.baublesSlotEnd - this.baublesSlotStart, BaublesConfig.maxColumns);
    }

    private static InventoryBaubles requireBaublesInventory(FakePlayer fakePlayer) {
        IInventory inventory = BaublesApi.getBaubles(fakePlayer);
        if (!(inventory instanceof InventoryBaubles baubles)) {
            throw new IllegalStateException("Missing baubles inventory for " + fakePlayer.getCommandSenderName());
        }
        return baubles;
    }

    private boolean isPlayerInventorySlot(int slotIndex) {
        return slotIndex >= this.playerSlotStart && slotIndex < this.playerSlotEnd;
    }

    private boolean tryMergeIntoBaubles(ItemStack stack) {
        for (int slot = this.baublesSlotStart; slot < this.baublesSlotEnd; slot++) {
            Slot target = (Slot) this.inventorySlots.get(slot);
            if (!target.getHasStack() && target.isItemValid(stack) && mergeItemStack(stack, slot, slot + 1, false)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = slotIndex >= 0 && slotIndex < this.inventorySlots.size() ? (Slot) this.inventorySlots.get(slotIndex) : null;
        if (slot == null || !slot.getHasStack()) {
            return null;
        }

        ItemStack stackInSlot = slot.getStack();
        ItemStack originalStack = stackInSlot.copy();

        if (isPlayerInventorySlot(slotIndex) && tryMergeIntoBaubles(stackInSlot)) {
            return originalStack;
        }

        if (isBaublesSlot(slotIndex) || isFakeInventorySlot(slotIndex)) {
            if (!mergeItemStack(stackInSlot, this.playerSlotStart, this.playerSlotEnd, false)) {
                return null;
            }
        } else if (!mergeIntoFakeInventory(stackInSlot)) {
            return null;
        }

        if (stackInSlot.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return originalStack;
    }
}
```

- [ ] **Step 5: Re-run the container test to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java
git commit -m "feat: merge baubles slots into fake player container"
```

## Task 3: Wire Baubles Inventory Resolution Through The GUI Handler

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiHandler.java`

- [ ] **Step 1: Write the failing compile-time integration target inside the handler**

```java
private InventoryBaubles resolveBaublesInventory(FakePlayer fakePlayer) {
    IInventory inventory = BaublesApi.getBaubles(fakePlayer);
    if (!(inventory instanceof InventoryBaubles baubles)) {
        throw new IllegalStateException("Missing baubles inventory for " + fakePlayer.getCommandSenderName());
    }
    return baubles;
}
```

- [ ] **Step 2: Run compile to verify the current handler does not yet build with the new constructor usage**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava
```

Expected: FAIL with constructor mismatch or missing baubles inventory resolution helpers in `FakePlayerInventoryGuiHandler`.

- [ ] **Step 3: Implement the minimal handler changes**

```java
@Override
public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
    FakePlayer fakePlayer = findServerFakePlayer(x);
    return fakePlayer == null ? null : FakePlayerInventoryContainer.server((EntityPlayerMP) player, fakePlayer);
}

@Override
public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
    FakePlayer fakePlayer = resolveClientFakePlayer(world, x);
    if (fakePlayer == null) {
        return null;
    }
    FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.client(fakePlayer.getCommandSenderName());
    InventoryBaubles baubles = resolveBaublesInventory(fakePlayer);
    FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory, baubles);
    return new FakePlayerInventoryGui(container, player.inventory.getInventoryName());
}

private FakePlayer resolveClientFakePlayer(World world, int entityId) {
    Entity entity = world == null ? null : world.getEntityByID(entityId);
    return entity instanceof FakePlayer ? (FakePlayer) entity : null;
}
```

- [ ] **Step 4: Run compile to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiHandler.java
git commit -m "feat: resolve baubles inventories for fake player gui"
```

## Task 4: Expand The GUI With A Scrollable Baubles Panel

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java`

- [ ] **Step 1: Write the failing compile-time GUI target by introducing the new fields**

```java
private static final int BAUBLES_PANEL_X = 176 + 8;
private static final int BAUBLES_PANEL_Y = 18;
private static final int BAUBLES_VISIBLE_ROWS = 8;

private float baublesScroll;
private boolean scrollingBaubles;
```

- [ ] **Step 2: Run compile to verify the GUI still lacks the new drawing and scroll methods**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava
```

Expected: BUILD SUCCESSFUL only after the GUI code is updated to use the new fields for panel sizing, labeling and mouse-wheel scrolling.

- [ ] **Step 3: Implement the minimal GUI changes**

```java
public FakePlayerInventoryGui(FakePlayerInventoryContainer container, String playerInventoryLabel) {
    super(container);
    this.xSize = 176 + 18 + container.getBaublesPanelWidth();
    this.ySize = 203;
}

@Override
protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    this.fontRendererObj.drawString(this.container.getFakeInventory().getInventoryName(), 8, 6, 4210752);
    this.fontRendererObj.drawString("Baubles", this.container.getBaublesTitleX(), 6, 4210752);
}

@Override
public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0 && this.container.canScrollBaubles()) {
        this.baublesScroll = this.container.adjustBaublesScroll(this.baublesScroll, wheel > 0 ? -1 : 1);
        this.container.applyBaublesScroll(this.baublesScroll);
    }
}
```

- [ ] **Step 4: Run compile to verify it passes**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java
git commit -m "feat: render scrollable baubles panel in fake player gui"
```

## Task 5: Run Focused Verification And Update Project Logs

**Files:**
- Modify: `log.md`

- [ ] **Step 1: Run the focused unit tests**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.ui.FakePlayerBaublesSlotLayoutTest --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest
```

Expected: PASS.

- [ ] **Step 2: Run assemble**

Run:

```bash
./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble
```

Expected: BUILD SUCCESSFUL and a new reobf jar under `build/libs/`.

- [ ] **Step 3: Update the Chinese work log**

```markdown
## 2026-04-20：GTstaff 假人统一背包接入 Baubles Expanded

### 已完成
- 为 `FakePlayerInventoryContainer` 增加假人 `InventoryBaubles` 槽位区
- `FakePlayerInventoryGui` 支持右侧动态饰品栏与滚轮滚动
- Shift 点击会优先把兼容饰品放进假人饰品槽

### 遇到的问题
- `SlotBauble` 会把传入库存强转为 `InventoryBaubles`

### 做出的决定
- 客户端和服务端都直接解析假人的 `InventoryBaubles`，不再引入平行占位库存类型
```

- [ ] **Step 4: Commit**

```bash
git add log.md
git commit -m "docs: record baubles fake player inventory work"
```

## Self-Review

- Spec coverage:
  - `Baubles Expanded` 硬依赖：Task 1
  - 统一容器合并普通背包与饰品栏：Task 2
  - 服务端/客户端沿用现有 GUI 打开链路：Task 3
  - 动态饰品区与滚动布局：Task 1 + Task 4
  - Shift 点击优先放入兼容饰品槽：Task 2
  - 不新增独立窗口：Task 3 + Task 4
- Placeholder scan:
  - 未使用 `TODO`、`TBD`、“后续再补”或“类似上一任务”的占位写法。
- Type consistency:
  - 统一使用 `InventoryBaubles` 作为饰品库存类型。
  - 统一使用 `FakePlayerBaublesSlotLayout` 处理列数、滚动和显示位置计算。
