# GTstaff FakePlayerManagerUI 重构设计

## 目标

将 `FakePlayerManagerUI` 从当前 320x220 的基础布局重构为 420x200 的 CustomNPC 风格完整分页管理界面，使用 MUI2 的 `PagedWidget`、`ListWidget`、`Column/Row` 等组件实现可滚动 bot 列表和丰富的页签内容。

## 整体布局

```
宽 420 x 高 200

+--[GTstaff Fake Player Manager]----------[x]--+
| [Overview] [Inventory] [Actions] [Monitor]    |  <- 顶部 tab 导航
|                                                |
| +--------+ +------------------------------+   |
| | Bot_1  | |                              |   |  左侧: ListWidget 可滚动
| | Bot_2  | |    页签内容区域              |   |  右侧: PagedWidget 四页
| | Bot_3  | |    (PagedWidget 切换)        |   |
| | ...    | |                              |   |
| |        | |                              |   |
| +--------+ +------------------------------+   |
| [Spawn Bot]                       状态消息     |
+------------------------------------------------+
```

### 区域划分

- **顶部标题栏 (y=0~16)**: 标题 + 关闭按钮
- **Tab 栏 (y=16~32)**: 4 个页签按钮，绑定 PagedWidget.Controller
- **左侧列表 (x=4, y=34, w=120, h=140)**: ListWidget，每项一个 ButtonWidget，选中态高亮，支持滚轮滚动
- **右侧内容 (x=128, y=34, w=288, h=140)**: PagedWidget 管理 4 个页面
- **底部操作栏 (y=178~200)**: Spawn 按钮 + 状态消息

## 四个页签详细设计

### Overview 页

显示选中 bot 的完整详情信息。

```
+--右侧内容区域---------------------------+
| Bot: Bot_Steve                          |
| Owner: <uuid>                           |
| 位置: 120, 64, -340  维度: 0            |
| 游戏模式: survival                       |
| 当前主手槽: 3                            |
| 监控: ON  范围: 16                       |
|                                         |
| [Kill]  [Shadow]                        |
+-----------------------------------------+
```

组件:
- 多个 `TextWidget` 动态显示详情（`IKey.dynamic`）
- `Kill` 按钮: 调用 `/player <name> kill`
- `Shadow` 按钮: 调用 `/player <name> shadow`

数据来源: `FakePlayerManagerService.describeBot()`

### Inventory 页

库存摘要 + 打开原版背包容器入口。

```
+--右侧内容区域---------------------------+
| === 装备 ===                            |
| 头盔: (空)  胸甲: 钻石胸甲 x1           |
| 裤子: (空)  靴子: (空)                   |
|                                         |
| === 快捷栏 (选中: 3) ===                |
| [1](空) [2](空) [3]铁镐x1 [4](空)...    |
|                                         |
| === 主背包 ===                          |
| 圆石x64  木板x32  (空)...               |
|                                         |
| [Manage Inventory]  [Refresh]           |
+-----------------------------------------+
```

组件:
- `TextWidget` 动态显示库存摘要（复用 `InventorySnapshot.toCompactDisplayText()`）
- `Manage Inventory` 按钮: 复用 `FakePlayerManagerService.openInventoryManager()`
- `Refresh` 按钮: 通过 `InteractionSyncHandler` 刷新摘要

数据来源: `FakePlayerManagerService.readInventory()`

### Actions 页

快捷操作按钮组。

```
+--右侧内容区域---------------------------+
| 快捷操作:                               |
| [Attack] [Use] [Jump] [Drop] [Stop]     |
|                                         |
| Hotbar 槽位: [< ] 3 [ >]               |
|                                         |
| 移动:                                   |
| [前进] [左] [停止] [右] [后退]          |
|                                         |
| 朝向:                                   |
| [Look...]                               |
|                                         |
| 状态: Attack 执行成功                    |
+-----------------------------------------+
```

组件:
- 快捷操作: 5 个 `ButtonWidget`（Attack/Use/Jump/Drop/Stop）
- Hotbar 槽位: `CycleButtonWidget`（1-9）或两个箭头按钮
- 移动控制: 5 个方向按钮
- Look 按钮: 打开 Look 弹窗（复用 `FakePlayerLookWindow`）
- 状态消息: `TextWidget`

数据来源: 通过 `InteractionSyncHandler` 在服务端调用 `/player <name> <action>` 命令

### Monitor 页

机器监控配置与状态查看。

```
+--右侧内容区域---------------------------+
| 监控开关: [ON/OFF]                      |
| 监控范围: [====slider====] 16           |
|                                         |
| 附近机器状态 (共 3 台):                  |
| [OK] 多方块电弧炉 (120, 64, -340)       |
| [!!] 大型锅炉 - 掉电 (120, 64, -338)    |
| [OK] 组装机 (120, 64, -336)             |
|                                         |
| [Scan]  [Toggle Monitor]                |
+-----------------------------------------+
```

组件:
- 监控开关: `ToggleButton`
- 监控范围: `SliderWidget`（1-64）
- 机器状态列表: `TextWidget` 动态文本
- `Scan` 按钮: 刷新机器状态
- `Toggle Monitor` 按钮: 切换监控

数据来源: `MachineMonitorService` + `FakePlayer` 监控状态

## 数据流

### 状态管理

保持现有 `ManagerState`，扩展字段:
- `selectedBotName`: 选中 bot 名称
- `activeTab`: 当前页签 (overview/inventory/actions/monitor)
- `statusMessage`: 状态消息
- `inventorySnapshotText`: 库存摘要缓存
- `machineStatusText`: 机器状态缓存

### 同步机制

- `StringSyncValue` 同步选中 bot 名、页签、状态消息
- 页签内容通过 `IKey.dynamic(() -> ...)` 动态读取 ManagerState
- 所有服务端操作通过 `InteractionSyncHandler` + `ServerThreadUtil` 收口

## FakePlayerManagerService 扩展

新增方法:
- `executeAction(EntityPlayerMP sender, String botName, String action)`: 执行快捷操作
- `setHotbarSlot(EntityPlayerMP sender, String botName, int slot)`: 切换 hotbar 槽位
- `toggleMonitor(EntityPlayerMP sender, String botName)`: 切换监控状态
- `setMonitorRange(EntityPlayerMP sender, String botName, int range)`: 设置监控范围
- `scanMachines(String botName)`: 获取机器状态摘要文本
- `killBot(EntityPlayerMP sender, String botName)`: 杀死 bot
- `shadowBot(EntityPlayerMP sender, String botName)`: 创建 shadow bot

## 文件变更

### 重写
- `FakePlayerManagerUI.java`: 全新的 420x200 布局，使用 PagedWidget + ListWidget

### 扩展
- `FakePlayerManagerService.java`: 新增 action/monitor 相关服务方法

### 保持不变
- `FakePlayerSpawnWindow.java`: Spawn 弹窗保持现有实现
- `FakePlayerLookWindow.java`: Look 弹窗保持现有实现
- `FakePlayerInventoryView.java` / `FakePlayerInventoryContainer.java` / `FakePlayerInventoryGui.java`: 背包容器保持现有实现
- `PopupPanelLayout.java`: 弹窗布局工具保持现有实现

## 技术约束

- 必须使用 MUI2 (ModularUI2 2.2.20) 提供的组件
- 不能引入 CustomNPC 的 GUI 基建（完全不同的技术栈）
- 所有服务端操作必须走 `InteractionSyncHandler` + `ServerThreadUtil`
- 保持与现有 `/player` 命令的兼容性（UI 操作仍然复用命令路径）
