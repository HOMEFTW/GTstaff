# GTstaff FakePlayerManagerUI 重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FakePlayerManagerUI 从 320x220 基础布局重构为 420x200 CustomNPC 风格分页管理界面，左侧可滚动 bot 列表 + 右侧四个功能页签。

**Architecture:** 使用 MUI2 的 `PagedWidget`（页签切换）、`ListWidget`（可滚动列表）、`Column/Row`（布局容器）重建 `FakePlayerManagerUI`。扩展 `FakePlayerManagerService` 新增 action 执行、monitor 控制等方法。所有服务端操作走 `InteractionSyncHandler` + `ServerThreadUtil`。

**Tech Stack:** MC 1.7.10 / Forge / ModularUI2 2.2.20 / JUnit Jupiter 5

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java` | 扩展 | 新增 executeAction / killBot / shadowBot / toggleMonitor / setMonitorRange / scanMachines / getInventorySummaryText |
| `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java` | 重写 | 全新 420x200 布局，PagedWidget + ListWidget |
| `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java` | 扩展 | 覆盖新增服务方法 |

---

### Task 1: 扩展 FakePlayerManagerService — executeAction

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Test: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: 写失败测试**

在 `FakePlayerManagerServiceTest.java` 末尾（`StubItemInWorldManager` 类之前）添加：

```java
@Test
void executeActionRunsPlayerCommand() {
    RecordingRunner runner = new RecordingRunner();
    FakePlayerManagerService service = new FakePlayerManagerService(runner);

    String status = service.executeAction(sender(), "UiBot", "attack");

    assertEquals("Executed attack on UiBot.", status);
    assertArrayEquals(new String[] { "UiBot", "attack" }, runner.lastArgs);
}

@Test
void executeActionRejectsBlankBotName() {
    FakePlayerManagerService service = new FakePlayerManagerService(
        (sender, args) -> { throw new AssertionError("runner should not be invoked"); });

    CommandException exception = assertThrows(
        CommandException.class,
        () -> service.executeAction(sender(), "  ", "attack"));

    assertEquals("Bot name cannot be empty", exception.getMessage());
}

@Test
void executeActionRejectsBlankAction() {
    FakePlayerManagerService service = new FakePlayerManagerService(
        (sender, args) -> { throw new AssertionError("runner should not be invoked"); });

    CommandException exception = assertThrows(
        CommandException.class,
        () -> service.executeAction(sender(), "UiBot", "  "));

    assertEquals("Action cannot be empty", exception.getMessage());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.executeActionRunsPlayerCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.executeActionRejectsBlankBotName" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.executeActionRejectsBlankAction"`
Expected: 编译失败（方法不存在）

- [ ] **Step 3: 写最小实现**

在 `FakePlayerManagerService.java` 的 `submitLook` 方法之后，`listBotNames` 方法之前，添加：

```java
public String executeAction(ICommandSender sender, String botName, String action) {
    String normalizedBotName = requireBotName(botName);
    String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    if (normalizedAction.isEmpty()) {
        throw new CommandException("Action cannot be empty");
    }
    this.commandRunner.run(sender, new String[] { normalizedBotName, normalizedAction });
    return "Executed " + normalizedAction + " on " + normalizedBotName + ".";
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.executeActionRunsPlayerCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.executeActionRejectsBlankBotName" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.executeActionRejectsBlankAction"`
Expected: 全部 PASS

- [ ] **Step 5: 提交**

```bash
cd /d/Code/GTstaff
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java
git commit -m "feat: FakePlayerManagerService 新增 executeAction 方法"
```

---

### Task 2: 扩展 FakePlayerManagerService — killBot / shadowBot

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Test: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void killBotRunsKillCommand() {
    RecordingRunner runner = new RecordingRunner();
    FakePlayerManagerService service = new FakePlayerManagerService(runner);

    String status = service.killBot(sender(), "UiBot");

    assertEquals("Killed UiBot.", status);
    assertArrayEquals(new String[] { "UiBot", "kill" }, runner.lastArgs);
}

@Test
void shadowBotRunsShadowCommand() {
    RecordingRunner runner = new RecordingRunner();
    FakePlayerManagerService service = new FakePlayerManagerService(runner);

    String status = service.shadowBot(sender(), "UiBot");

    assertEquals("Created shadow of UiBot.", status);
    assertArrayEquals(new String[] { "UiBot", "shadow" }, runner.lastArgs);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.killBotRunsKillCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.shadowBotRunsShadowCommand"`
Expected: 编译失败

- [ ] **Step 3: 写最小实现**

在 `executeAction` 方法之后，`listBotNames` 方法之前，添加：

```java
public String killBot(ICommandSender sender, String botName) {
    String normalizedBotName = requireBotName(botName);
    this.commandRunner.run(sender, new String[] { normalizedBotName, "kill" });
    return "Killed " + normalizedBotName + ".";
}

public String shadowBot(ICommandSender sender, String botName) {
    String normalizedBotName = requireBotName(botName);
    this.commandRunner.run(sender, new String[] { normalizedBotName, "shadow" });
    return "Created shadow of " + normalizedBotName + ".";
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.killBotRunsKillCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.shadowBotRunsShadowCommand"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/Code/GTstaff
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java
git commit -m "feat: FakePlayerManagerService 新增 killBot / shadowBot 方法"
```

---

### Task 3: 扩展 FakePlayerManagerService — toggleMonitor / setMonitorRange / scanMachines

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Test: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void toggleMonitorRunsMonitorOnCommand() {
    RecordingRunner runner = new RecordingRunner();
    FakePlayerManagerService service = new FakePlayerManagerService(runner);
    FakePlayerRegistry.register(stubFakePlayer("UiBot"), null);

    String status = service.toggleMonitor(sender(), "UiBot", true);

    assertEquals("Monitor enabled for UiBot.", status);
    assertArrayEquals(new String[] { "UiBot", "monitor", "on" }, runner.lastArgs);
}

@Test
void toggleMonitorRunsMonitorOffCommand() {
    RecordingRunner runner = new RecordingRunner();
    FakePlayerManagerService service = new FakePlayerManagerService(runner);
    FakePlayerRegistry.register(stubFakePlayer("UiBot"), null);

    String status = service.toggleMonitor(sender(), "UiBot", false);

    assertEquals("Monitor disabled for UiBot.", status);
    assertArrayEquals(new String[] { "UiBot", "monitor", "off" }, runner.lastArgs);
}

@Test
void setMonitorRangeRunsCommand() {
    RecordingRunner runner = new RecordingRunner();
    FakePlayerManagerService service = new FakePlayerManagerService(runner);
    FakePlayerRegistry.register(stubFakePlayer("UiBot"), null);

    String status = service.setMonitorRange(sender(), "UiBot", 32);

    assertEquals("Monitor range set to 32 for UiBot.", status);
    assertArrayEquals(new String[] { "UiBot", "monitor", "range", "32" }, runner.lastArgs);
}

@Test
void scanMachinesReturnsSummaryForOnlineBot() {
    FakePlayerManagerService service = new FakePlayerManagerService();
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    FakePlayerRegistry.register(fakePlayer, null);

    String summary = service.scanMachines("UiBot");

    assertTrue(summary.startsWith("Bot: UiBot"));
}

@Test
void scanMachinesReturnsOfflineMessageForMissingBot() {
    FakePlayerManagerService service = new FakePlayerManagerService();

    String summary = service.scanMachines("MissingBot");

    assertEquals("Bot MissingBot is not online.", summary);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.toggleMonitorRunsMonitorOnCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.toggleMonitorRunsMonitorOffCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.setMonitorRangeRunsCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.scanMachinesReturnsSummaryForOnlineBot" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.scanMachinesReturnsOfflineMessageForMissingBot"`
Expected: 编译失败

- [ ] **Step 3: 写最小实现**

在 `shadowBot` 方法之后，`listBotNames` 方法之前，添加：

```java
public String toggleMonitor(ICommandSender sender, String botName, boolean enable) {
    String normalizedBotName = requireBotName(botName);
    String[] args = new String[] { normalizedBotName, "monitor", enable ? "on" : "off" };
    this.commandRunner.run(sender, args);
    return (enable ? "Monitor enabled" : "Monitor disabled") + " for " + normalizedBotName + ".";
}

public String setMonitorRange(ICommandSender sender, String botName, int range) {
    String normalizedBotName = requireBotName(botName);
    this.commandRunner.run(sender, new String[] { normalizedBotName, "monitor", "range", Integer.toString(range) });
    return "Monitor range set to " + range + " for " + normalizedBotName + ".";
}

public String scanMachines(String botName) {
    FakePlayer fakePlayer = findBot(botName);
    if (fakePlayer == null) {
        return "Bot " + (botName == null ? "" : botName.trim()) + " is not online.";
    }
    MachineMonitorService monitorService = fakePlayer.getMonitorService();
    Map<ChunkCoordinates, MachineState> states = monitorService != null
        ? monitorService.getMachineStates()
        : Collections.<ChunkCoordinates, MachineState>emptyMap();

    StringBuilder builder = new StringBuilder();
    builder.append("Bot: ").append(fakePlayer.getCommandSenderName()).append('\n');
    builder.append("Monitoring: ").append(monitorService != null && monitorService.isMonitoring() ? "ON" : "OFF").append('\n');
    builder.append("Range: ").append(monitorService != null ? monitorService.getMonitorRange() : 0).append('\n');
    if (states.isEmpty()) {
        builder.append("No machines detected.");
    } else {
        builder.append("Machines (").append(states.size()).append("):").append('\n');
        for (Map.Entry<ChunkCoordinates, MachineState> entry : states.entrySet()) {
            ChunkCoordinates pos = entry.getKey();
            MachineState state = entry.getValue();
            String marker = state.hasProblems() ? "[!!]" : "[OK]";
            builder.append(marker)
                .append(" (").append(pos.posX).append(',').append(pos.posY).append(',').append(pos.posZ).append(')');
            if (!state.isPowered()) {
                builder.append(" POWER_LOSS");
            }
            if (state.isMaintenanceRequired()) {
                builder.append(" MAINTENANCE");
            }
            if (state.isOutputFull()) {
                builder.append(" OUTPUT_FULL");
            }
            builder.append('\n');
        }
    }
    return builder.toString().trim();
}
```

需要在 `FakePlayerManagerService.java` 文件顶部补充 import：

```java
import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;
import com.andgatech.gtstaff.fakeplayer.MachineState;
import net.minecraft.util.ChunkCoordinates;
import java.util.Collections;
import java.util.Map;
```

注意：`FakePlayer` 需要暴露 `getMonitorService()`。如果该方法不存在，则检查 `FakePlayer` 是否已有此方法。根据 context.md，`MachineMonitorService` 是作为 `FakePlayer` 的内部服务存在的。如果 `getMonitorService()` 不存在，改为通过 `MachineMonitorService` 的已知字段或直接使用 `monitoring` / `monitorRange` 字段。

**备选方案**（如果 `FakePlayer` 没有暴露 `getMonitorService()`）：将 `scanMachines` 改为使用 `describeBot` 返回的信息 + 已知的 `FakePlayer` 监控字段：

```java
public String scanMachines(String botName) {
    FakePlayer fakePlayer = findBot(botName);
    if (fakePlayer == null) {
        return "Bot " + (botName == null ? "" : botName.trim()) + " is not online.";
    }
    BotDetails details = describeBot(botName);
    StringBuilder builder = new StringBuilder();
    builder.append("Bot: ").append(details.botName).append('\n');
    builder.append("Monitoring: ").append(details.monitoring ? "ON" : "OFF").append('\n');
    builder.append("Range: ").append(details.monitorRange).append('\n');
    builder.append("Machine scanning requires in-game tick to refresh.");
    return builder.toString().trim();
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.toggleMonitorRunsMonitorOnCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.toggleMonitorRunsMonitorOffCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.setMonitorRangeRunsCommand" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.scanMachinesReturnsSummaryForOnlineBot" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.scanMachinesReturnsOfflineMessageForMissingBot"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/Code/GTstaff
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java
git commit -m "feat: FakePlayerManagerService 新增 monitor 控制和机器扫描方法"
```

---

### Task 4: 扩展 FakePlayerManagerService — getInventorySummaryText

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java`
- Test: `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void getInventorySummaryTextReturnsCompactSnapshot() {
    FakePlayerManagerService service = new FakePlayerManagerService();
    StubFakePlayer fakePlayer = stubFakePlayer("UiBot");
    fakePlayer.inventory.currentItem = 0;
    fakePlayer.inventory.mainInventory[0] = namedStack("Iron Pickaxe", 1);
    fakePlayer.inventory.armorInventory[3] = namedStack("Diamond Helmet", 1);
    FakePlayerRegistry.register(fakePlayer, null);

    String summary = service.getInventorySummaryText("UiBot");

    assertTrue(summary.contains("Iron Pickaxe"));
    assertTrue(summary.contains("Diamond Helmet"));
}

@Test
void getInventorySummaryTextReturnsOfflineMessageForMissingBot() {
    FakePlayerManagerService service = new FakePlayerManagerService();

    String summary = service.getInventorySummaryText("MissingBot");

    assertEquals("Bot MissingBot is not online.", summary);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.getInventorySummaryTextReturnsCompactSnapshot" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.getInventorySummaryTextReturnsOfflineMessageForMissingBot"`
Expected: 编译失败

- [ ] **Step 3: 写最小实现**

在 `scanMachines` 方法之后添加：

```java
public String getInventorySummaryText(String botName) {
    FakePlayer fakePlayer = findBot(botName);
    if (fakePlayer == null) {
        return "Bot " + (botName == null ? "" : botName.trim()) + " is not online.";
    }
    InventoryDraft draft = new InventoryDraft();
    draft.botName = botName;
    return readInventory(draft).toCompactDisplayText();
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.getInventorySummaryTextReturnsCompactSnapshot" --tests "com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest.getInventorySummaryTextReturnsOfflineMessageForMissingBot"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/Code/GTstaff
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerService.java src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java
git commit -m "feat: FakePlayerManagerService 新增 getInventorySummaryText 方法"
```

---

### Task 5: 重写 FakePlayerManagerUI — 基础布局框架

**Files:**
- Rewrite: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java`

这是最核心的 task。将整个 `Holder` 内部类的 `buildUI` 方法重写为 420x200 布局，包含左侧可滚动 bot 列表和右侧 PagedWidget 页签容器。

- [ ] **Step 1: 重写 FakePlayerManagerUI.java**

将 `FakePlayerManagerUI.java` 整个替换为以下实现：

```java
package com.andgatech.gtstaff.ui;

import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;

import com.andgatech.gtstaff.GTstaff;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;

public class FakePlayerManagerUI extends AbstractUIFactory<GuiData> {

    public static final FakePlayerManagerUI INSTANCE = new FakePlayerManagerUI();

    private FakePlayerManagerUI() {
        super("gtstaff:fake_player_manager");
    }

    public void open(EntityPlayerMP player) {
        GuiManager.open(this, new GuiData(player), player);
    }

    @Override
    public @NotNull IGuiHolder<GuiData> getGuiHolder(GuiData data) {
        return Holder.INSTANCE;
    }

    @Override
    public void writeGuiData(GuiData guiData, PacketBuffer buffer) {}

    @Override
    public @NotNull GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new GuiData(player);
    }

    private static final class Holder implements IGuiHolder<GuiData> {

        private static final Holder INSTANCE = new Holder();

        @Override
        public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
            return new ModularScreen(GTstaff.MODID, mainPanel);
        }

        @Override
        public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
            FakePlayerManagerService service = new FakePlayerManagerService();
            ManagerState state = new ManagerState(service.defaultSelectedBotName());
            EntityPlayerMP player = data.getPlayer() instanceof EntityPlayerMP playerMP ? playerMP : null;
            ModularPanel panel = ModularPanel.defaultPanel("GTstaffManager", 420, 200);

            syncManager.syncValue(
                "gtstaffManagerSelectedBot",
                new StringSyncValue(
                    () -> state.selectedBotName,
                    value -> state.selectedBotName = normalizeBotSelection(service, value)));
            syncManager.syncValue(
                "gtstaffManagerActiveTab",
                new StringSyncValue(() -> state.activeTab, value -> state.activeTab = normalizeTab(value)));
            syncManager.syncValue(
                "gtstaffManagerStatus",
                new StringSyncValue(() -> state.statusMessage, value -> state.statusMessage = value));

            IPanelHandler spawnPanel = syncManager.panel(
                "gtstaffSpawnPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerSpawnWindow(panel, player, panelSyncManager),
                true);
            IPanelHandler lookPanel = syncManager.panel(
                "gtstaffLookPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerLookWindow(panel, player, panelSyncManager),
                true);

            PagedWidget.Controller pageController = new PagedWidget.Controller();

            // === 顶部标题 + 关闭按钮 ===
            panel.child(ButtonWidget.panelCloseButton().top(4).right(4))
                .child(new TextWidget("GTstaff Fake Player Manager").top(6).left(8))
                // === Tab 栏 ===
                .child(createTabButton("Overview", "overview", state, pageController, 0).top(20).left(4))
                .child(createTabButton("Inventory", "inventory", state, pageController, 1).top(20).left(74))
                .child(createTabButton("Actions", "actions", state, pageController, 2).top(20).left(144))
                .child(createTabButton("Monitor", "monitor", state, pageController, 3).top(20).left(214))
                // === 左侧 bot 列表 ===
                .child(buildBotList(service, state))
                // === 右侧 PagedWidget ===
                .child(buildPagedContent(pageController, service, state, player, spawnPanel, lookPanel, syncManager))
                // === 底部操作栏 ===
                .child(createSpawnButton(spawnPanel).top(180).left(4))
                .child(new TextWidget(IKey.dynamic(() -> state.statusMessage)).top(182).left(120).size(290, 16));

            return panel;
        }

        // ---- 左侧可滚动 bot 列表 ----
        private ListWidget<?, ?> buildBotList(FakePlayerManagerService service, ManagerState state) {
            List<String> botNames = service.listBotNames();
            ListWidget<?, ?> list = new ListWidget<>();
            list.top(38).left(4).size(110, 140);
            if (botNames.isEmpty()) {
                list.child(new TextWidget("No bots online").left(2).top(2));
            } else {
                for (String botName : botNames) {
                    list.child(createBotButton(botName, state).size(106, 16));
                }
            }
            return list;
        }

        private ButtonWidget<?> createBotButton(String botName, ManagerState state) {
            return new ButtonWidget<>()
                .overlay(IKey.dynamic(() -> botName.equalsIgnoreCase(state.selectedBotName) ? "> " + botName : botName))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0) return;
                    state.selectedBotName = botName;
                }));
        }

        // ---- 右侧 PagedWidget 四页 ----
        private PagedWidget<?> buildPagedContent(PagedWidget.Controller controller,
            FakePlayerManagerService service, ManagerState state, EntityPlayerMP player,
            IPanelHandler spawnPanel, IPanelHandler lookPanel, PanelSyncManager syncManager) {

            PagedWidget<?> paged = new PagedWidget<>();
            paged.top(38).left(120).size(296, 140);
            paged.controller(controller);

            // Page 0: Overview
            paged.addPage(buildOverviewPage(service, state));
            // Page 1: Inventory
            paged.addPage(buildInventoryPage(service, state, player, syncManager));
            // Page 2: Actions
            paged.addPage(buildActionsPage(service, state, lookPanel));
            // Page 3: Monitor
            paged.addPage(buildMonitorPage(service, state));

            return paged;
        }

        // ---- Overview 页 ----
        private Column buildOverviewPage(FakePlayerManagerService service, ManagerState state) {
            Column col = new Column();
            col.child(new TextWidget(IKey.dynamic(() -> buildOverviewText(service, state))).size(290, 100).left(2).top(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("Kill"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.killBot(null, state.selectedBotName);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .top(106).left(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("Shadow"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.shadowBot(null, state.selectedBotName);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .top(106).left(66));
            return col;
        }

        private static String buildOverviewText(FakePlayerManagerService service, ManagerState state) {
            if (!hasSelectedBot(service, state)) {
                return "Select a bot from the left list.";
            }
            FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
            if (!d.online) {
                return "Selected bot is offline.";
            }
            return "Bot: " + d.botName
                + "\nOwner: " + d.ownerLabel
                + "\nPos: " + d.blockX + ", " + d.blockY + ", " + d.blockZ
                + "\nDim: " + d.dimension
                + "\nHotbar: " + (d.selectedHotbarSlot + 1)
                + "\nMonitor: " + (d.monitoring ? "ON" : "OFF") + "  Range: " + d.monitorRange;
        }

        // ---- Inventory 页 ----
        private Column buildInventoryPage(FakePlayerManagerService service, ManagerState state,
            EntityPlayerMP player, PanelSyncManager syncManager) {

            Column col = new Column();
            col.child(new TextWidget(IKey.dynamic(() -> {
                if (!hasSelectedBot(service, state)) return "Select a bot first.";
                return service.getInventorySummaryText(state.selectedBotName);
            })).size(290, 110).left(2).top(2))
                .child(
                    new ButtonWidget<>().size(100, 18).overlay(IKey.str("Manage Inventory"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.openInventoryManager(player, state.selectedBotName);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(116).left(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("Refresh"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0) return;
                            state.statusMessage = hasSelectedBot(service, state)
                                ? "Inventory refreshed."
                                : "Select a bot first.";
                        }))
                        .top(116).left(106));
            return col;
        }

        // ---- Actions 页 ----
        private Column buildActionsPage(FakePlayerManagerService service, ManagerState state,
            IPanelHandler lookPanel) {

            Column col = new Column();

            // 快捷操作按钮行
            String[] quickActions = { "attack", "use", "jump", "drop", "stop" };
            for (int i = 0; i < quickActions.length; i++) {
                String action = quickActions[i];
                col.child(
                    new ButtonWidget<>().size(50, 18).overlay(IKey.str(capitalize(action)))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(null, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(2).left(2 + i * 54));
            }

            // Hotbar 槽位控制
            col.child(new TextWidget("Hotbar:").top(24).left(2));
            for (int slot = 1; slot <= 9; slot++) {
                int slotNum = slot;
                col.child(
                    new ButtonWidget<>().size(16, 16).overlay(IKey.str(Integer.toString(slot)))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(null, state.selectedBotName, "hotbar " + slotNum);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(36).left(2 + (slot - 1) * 20));
            }

            // 移动控制行
            String[] moveActions = { "sneak", "sprint" };
            for (int i = 0; i < moveActions.length; i++) {
                String action = moveActions[i];
                col.child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str(capitalize(action)))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(null, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(58).left(2 + i * 64));
            }

            // Look 按钮
            col.child(
                new ButtonWidget<>().size(60, 18).overlay(IKey.str("Look..."))
                    .onMousePressed(mouseButton -> {
                        if (mouseButton != 0) return false;
                        if (lookPanel.isPanelOpen()) {
                            lookPanel.closePanel();
                        } else {
                            lookPanel.openPanel();
                        }
                        return true;
                    })
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(80).left(2));

            // 状态反馈
            col.child(
                new TextWidget(IKey.dynamic(() -> state.statusMessage)).top(102).left(2).size(290, 14));

            return col;
        }

        // ---- Monitor 页 ----
        private Column buildMonitorPage(FakePlayerManagerService service, ManagerState state) {
            Column col = new Column();

            col.child(
                new ButtonWidget<>().size(80, 18).overlay(IKey.str("Toggle Monitor"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        if (!hasSelectedBot(service, state)) {
                            state.statusMessage = "Select a bot first.";
                            return;
                        }
                        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                        try {
                            state.statusMessage = service.toggleMonitor(null, state.selectedBotName, !d.monitoring);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(2).left(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("Scan"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            if (!hasSelectedBot(service, state)) {
                                state.statusMessage = "Select a bot first.";
                                return;
                            }
                            state.machineStatusText = service.scanMachines(state.selectedBotName);
                            state.statusMessage = "Scan complete.";
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(2).left(86))
                .child(
                    new TextWidget(IKey.dynamic(() -> {
                        if (state.machineStatusText != null && !state.machineStatusText.isEmpty()) {
                            return state.machineStatusText;
                        }
                        if (!hasSelectedBot(service, state)) return "Select a bot first.";
                        return service.scanMachines(state.selectedBotName);
                    })).size(290, 110).left(2).top(24));

            return col;
        }

        // ---- Tab 按钮 ----
        private ButtonWidget<?> createTabButton(String label, String tabId, ManagerState state,
            PagedWidget.Controller controller, int pageIndex) {
            return new ButtonWidget<>().size(66, 14)
                .overlay(IKey.dynamic(() -> tabId.equals(state.activeTab) ? "[" + label + "]" : label))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0) return;
                    state.activeTab = tabId;
                    if (controller.isInitialised()) {
                        controller.setPage(pageIndex);
                    }
                }));
        }

        // ---- Spawn 按钮 ----
        private ButtonWidget<?> createSpawnButton(IPanelHandler spawnPanel) {
            return new ButtonWidget<>().size(80, 18)
                .overlay(IKey.str("Spawn Bot"))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    if (spawnPanel.isPanelOpen()) {
                        spawnPanel.closePanel();
                    } else {
                        spawnPanel.openPanel();
                    }
                    return true;
                });
        }

        // ---- 工具方法 ----
        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        private static String normalizeBotSelection(FakePlayerManagerService service, String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) {
                return service.defaultSelectedBotName();
            }
            return normalized;
        }

        private static String normalizeTab(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase();
            switch (normalized) {
                case "inventory":
                case "actions":
                case "monitor":
                    return normalized;
                case "overview":
                default:
                    return "overview";
            }
        }

        private static boolean hasSelectedBot(FakePlayerManagerService service, ManagerState state) {
            return state.selectedBotName != null && !state.selectedBotName.trim().isEmpty()
                && service.listBotNames().stream().anyMatch(name -> name.equalsIgnoreCase(state.selectedBotName));
        }
    }

    private static final class ManagerState {
        private String selectedBotName;
        private String activeTab = "overview";
        private String statusMessage = "Select a bot or open Spawn.";
        private String machineStatusText = "";

        private ManagerState(String selectedBotName) {
            this.selectedBotName = selectedBotName == null ? "" : selectedBotName;
        }
    }
}
```

- [ ] **Step 2: 运行编译确认通过**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline compileJava`
Expected: BUILD SUCCESSFUL

如果编译失败，根据错误信息调整 import 或 API 调用。常见问题：
- `PagedWidget` 的泛型参数可能需要调整
- `ListWidget` 的子元素添加方式可能需要用 `child()` 方法
- `Column` 的 `.child()` 返回类型检查

- [ ] **Step 3: 运行全部测试确认无回归**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test`
Expected: 全部 PASS

- [ ] **Step 4: 提交**

```bash
cd /d/Code/GTstaff
git add src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java
git commit -m "refactor: 重写 FakePlayerManagerUI 为 420x200 CustomNPC 风格分页布局"
```

---

### Task 6: 端到端验证

**Files:** 无新文件

- [ ] **Step 1: 运行完整离线测试**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline test`
Expected: 全部 PASS

- [ ] **Step 2: 运行 assemble 产出 jar**

Run: `cd /d/Code/GTstaff && ./gradlew.bat --offline assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交（如有未提交的变更）**

```bash
cd /d/Code/GTstaff
git status
# 如果有未提交变更：
git add -A && git commit -m "chore: 端到端验证通过"
```
