# GTstaff UI Inventory Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `GTstaff` 的 fake player 管理界面重构为 “MUI2 管理台 + 原版 chest-style 背包容器” 的双层结构，并支持真正的背包管理与玩家互传。

**Architecture:** `FakePlayerManagerUI` 保留为 `MUI2` 管理台，负责 bot 选择、摘要展示和打开入口；真正的库存编辑迁移到 Forge `IGuiHandler` 路线下的 `Container + GuiContainer`。服务端通过专用库存映射层把 fake player 的 `InventoryPlayer` 映射为固定的 40 槽布局，并同步当前主手槽位给客户端高亮。

**Tech Stack:** Java 8, Forge 1.7.10, ModularUI2, vanilla `Container` / `GuiContainer`, JUnit 5, Gradle

---

## File Structure

### Create

- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiIds.java`
  - 存放 Forge GUI id 常量。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryView.java`
  - 将 fake player 背包映射为固定 40 槽视图，服务端写穿到真实库存，客户端承接同步显示。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerArmorSlot.java`
  - 限制护甲槽放置规则。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java`
  - 管理 fake player 槽位、玩家背包槽位、shift-transfer 和当前主手槽位同步。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java`
  - 原版 chest-style `GuiContainer`，绘制标题、分区与当前主手槽位高亮。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiHandler.java`
  - Forge `IGuiHandler`，负责打开容器与客户端 GUI。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryViewTest.java`
  - 视图映射测试。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`
  - 容器槽位映射、当前主手槽位、transfer 逻辑测试。

### Modify

- `src/main/java/com/andgatech/gtstaff/CommonProxy.java`
  - 注册 `IGuiHandler`。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
  - 增加 bot 列表、选中 bot 概要、背包 GUI 打开入口。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java`
  - 重构为左侧 bot 列表 + 右侧详情页签。
- `src/main/java/com/andgatech/gtstaff/command/CommandGTstaff.java`
  - 保持入口不变，但依赖新的管理台结构。
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`
  - 改为覆盖列表/选中态/打开背包入口行为。

## Task 1: 锁定库存映射行为

**Files:**
- Create: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryViewTest.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryView.java`

- [ ] **Step 1: 写失败测试，锁定 40 槽映射顺序**

```java
@Test
void mapsArmorHotbarAndMainInventoryIntoFixedContainerOrder() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    fakePlayer.inventory.armorInventory[3] = namedStack("Helmet", 1);
    fakePlayer.inventory.mainInventory[0] = namedStack("Sword", 1);
    fakePlayer.inventory.mainInventory[9] = namedStack("Pickaxe", 1);

    FakePlayerInventoryView view = FakePlayerInventoryView.server(fakePlayer);

    assertEquals("Helmet", view.getStackInSlot(0).getDisplayName());
    assertEquals("Sword", view.getStackInSlot(4).getDisplayName());
    assertEquals("Pickaxe", view.getStackInSlot(13).getDisplayName());
}
```

- [ ] **Step 2: 运行失败测试确认当前不存在该实现**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest`
Expected: FAIL，因为 `FakePlayerInventoryView` 尚未实现。

- [ ] **Step 3: 实现最小库存映射层**

```java
public final class FakePlayerInventoryView implements IInventory {
    public static FakePlayerInventoryView server(FakePlayer fakePlayer) { ... }
    public static FakePlayerInventoryView client(String inventoryName) { ... }
    public int getSelectedHotbarSlot() { ... }
    public void setSelectedHotbarSlot(int slot) { ... }
}
```

- [ ] **Step 4: 重新运行测试确保变绿**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest`
Expected: PASS

## Task 2: 锁定容器槽位、主手选择与互传行为

**Files:**
- Create: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerArmorSlot.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainer.java`

- [ ] **Step 1: 写失败测试，锁定容器布局与 hotbar 选择行为**

```java
@Test
void clickingFakeHotbarSlotUpdatesSelectedMainHandSlot() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    TestPlayer player = stubPlayer();
    FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

    container.slotClick(4 + 3, 0, 0, player);

    assertEquals(3, fakePlayer.inventory.currentItem);
}
```

- [ ] **Step 2: 写失败测试，锁定 shift-transfer**

```java
@Test
void transferStackMovesItemsBetweenPlayerAndFakeInventory() {
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    TestPlayer player = stubPlayer();
    player.inventory.mainInventory[9] = namedStack("Cobblestone", 64);
    FakePlayerInventoryContainer container = FakePlayerInventoryContainer.server(player, fakePlayer);

    container.transferStackInSlot(player, 40);

    assertNotNull(fakePlayer.inventory.mainInventory[9]);
}
```

- [ ] **Step 3: 运行失败测试**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest`
Expected: FAIL，因为容器与槽位规则尚未实现。

- [ ] **Step 4: 实现容器、护甲槽与当前主手同步**

```java
public final class FakePlayerInventoryContainer extends Container {
    public static FakePlayerInventoryContainer server(EntityPlayerMP player, FakePlayer fakePlayer) { ... }
    public static FakePlayerInventoryContainer client(EntityPlayer player, int entityId, String title) { ... }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) { ... }

    @Override
    public ItemStack slotClick(int slotId, int button, int modifier, EntityPlayer player) { ... }
}
```

- [ ] **Step 5: 重新运行测试**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest`
Expected: PASS

## Task 3: 打通 Forge GUI 打开链路

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiIds.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGuiHandler.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryGui.java`
- Modify: `src/main/java/com/andgatech/gtstaff/CommonProxy.java`

- [ ] **Step 1: 注册 `IGuiHandler`**

```java
NetworkRegistry.INSTANCE.registerGuiHandler(GTstaff.instance, FakePlayerInventoryGuiHandler.INSTANCE);
```

- [ ] **Step 2: 实现 server/client GUI element 解析**

```java
public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) { ... }
public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) { ... }
```

- [ ] **Step 3: 实现 chest-style GUI 绘制与高亮**

```java
protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) { ... }
protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) { ... }
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew.bat --offline compileJava`
Expected: BUILD SUCCESSFUL

## Task 4: 重构 `FakePlayerManagerService`

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Modify: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: 写失败测试，锁定 bot 列表、默认选中与权限拒绝**

```java
@Test
void listBotsReturnsSortedNamesAndDefaultSelection() { ... }

@Test
void openInventoryManagerRejectsUnauthorizedPlayer() { ... }
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
Expected: FAIL，因为新服务 API 尚未实现。

- [ ] **Step 3: 实现管理台服务 API**

```java
public List<String> listBotNames() { ... }
public BotDetails describeBot(String botName) { ... }
public String openInventoryManager(EntityPlayerMP player, String botName) { ... }
```

- [ ] **Step 4: 重新运行服务测试**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
Expected: PASS

## Task 5: 重构 `FakePlayerManagerUI`

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java`

- [ ] **Step 1: 将主面板改为左侧 bot 列表 + 右侧页签**

```java
ModularPanel panel = ModularPanel.defaultPanel("GTstaffManager", 300, 210);
```

- [ ] **Step 2: 用同步值保存选中 bot 与当前页签**

```java
syncManager.syncValue("gtstaffSelectedBot", new StringSyncValue(...));
syncManager.syncValue("gtstaffActiveTab", new StringSyncValue(...));
```

- [ ] **Step 3: 在 `Inventory` 页签中接入 `Manage Inventory` 按钮**

```java
new ButtonWidget<>().overlay(IKey.str("Manage Inventory")).syncHandler(...)
```

- [ ] **Step 4: 保留 Spawn / Look 入口，但移除旧 Inventory popup 的主路径**

```java
// Inventory 不再走 FakePlayerInventoryWindow 文本快照
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew.bat --offline compileJava`
Expected: BUILD SUCCESSFUL

## Task 6: 回归验证与文档更新

**Files:**
- Modify: `log.md`
- Modify: `ToDOLIST.md`
- Modify: `context.md`

- [ ] **Step 1: 跑定向测试**

Run: `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
Expected: PASS

- [ ] **Step 2: 跑 `assemble` 产出测试 jar**

Run: `./gradlew.bat --offline assemble`
Expected: BUILD SUCCESSFUL，并生成新的 reobf jar。

- [ ] **Step 3: 用中文更新 `log.md` / `ToDOLIST.md` / `context.md`**

```markdown
- 已完成 MUI2 管理台重构
- 已完成 fake player 原版背包容器
- inventory 不再走文本快照，而是走 chest-style GUI
```

## Self-Review

- Spec coverage:
  - 管理台双栏结构：Task 4-5
  - 原版 chest-style 背包 GUI：Task 1-3
  - 当前主手槽位切换与高亮：Task 2-3
  - 玩家背包互传：Task 2
  - 权限复用：Task 4
- Placeholder scan:
  - 未使用 `TODO`、`TBD` 或“类似上一步”占位表述
- Type consistency:
  - fake player 背包固定映射统一由 `FakePlayerInventoryView` 负责
  - 背包打开入口统一从 `FakePlayerManagerService.openInventoryManager(...)` 发起
