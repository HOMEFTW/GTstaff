# GTstaff UI 与背包管理重构设计

## 背景

当前 `FakePlayerManagerUI` 仍然是“主面板 + 多个弹出子窗口 + 文本快照”的形态：

- 不像 `CustomNPC-Plus-master` 那样具备稳定的左侧对象列表与右侧持续编辑区
- `Inventory` 只能读取文本快照，无法真正管理 fake player 的库存
- 子窗口方案在交互规模增长后容易继续出现布局与可操作性问题

用户希望将 `GTstaff` 的管理 UI 重构为：

- 使用 `MUI2` 做主管理台
- 整体风格参考 `CustomNPC` 的“左侧实体列表 + 右侧详情页签”
- 真正的背包管理不再在 `MUI2` 中完成，而是从管理台打开一个“原版大箱子风格”的容器 GUI

## 目标

实现一套新的 fake player 管理 UI：

1. `MUI2` 主界面改为左侧 bot 列表、右侧详情与操作页签
2. 从主界面点击 `Inventory`/`Manage Inventory` 后，打开原版 `Container + GuiContainer`
3. 新背包 GUI 支持直接拖拽、Shift 点击和玩家背包互传
4. fake player 背包布局按用户确认的结构展示

## 非目标

本次不实现以下内容：

- 不在 `MUI2` 中实现完整的 `ItemSlot` 背包编辑
- 不扩展 `/player` 命令语法去承载所有 UI 背包操作
- 不实现滚动分页、多 bot 搜索、模板系统或监控大面板
- 不重做 Spawn / Look 的底层服务逻辑，只在主界面中重新组织入口

## 目标体验

### MUI2 主管理台

主界面采用类似 `CustomNPC` 的双栏布局：

- 左侧：在线 fake player 列表
- 右侧顶部：页签按钮，至少包含 `Overview`、`Inventory`、`Actions`、`Monitor`
- 右侧主体：显示当前选中 bot 的摘要信息和页签对应操作

其中本轮优先完成：

- `Overview`：基础状态、位置、维度、owner 摘要
- `Inventory`：显示说明与 `Manage Inventory` 按钮
- `Actions` / `Monitor`：先做轻量占位信息，避免继续走旧 popup 体系

### 原版 chest-style 背包 GUI

点击管理台中的背包入口后，打开新的原版容器 GUI。布局固定为：

- 第 1 行：4 个护甲槽，后面保留 5 个空位做视觉对齐
- 第 2 行：9 个 hotbar 槽
- 第 3-5 行：27 个主背包槽
- 下半部分：当前打开 GUI 的玩家背包

用户确认的语义如下：

- 第 1 行四个槽位就是护甲槽
- 第 2 行是 fake player 的快捷栏
- 点击第 2 行某个槽位后，应把它设置为 fake player 当前主手槽位
- 当前主手槽位要有明显高亮
- 下半部分玩家背包用于和 fake player 直接互传

## 架构拆分

### 1. `MUI2` 管理台

保留 `FakePlayerManagerUI` 作为唯一的 MUI2 factory，但重构其内容：

- 维护“当前选中的 bot 名称”与“当前页签”
- 左侧直接展示 bot 名称按钮列表
- 右侧用动态文本和按钮展示当前 bot 详情
- 背包管理按钮只负责打开新的 Forge GUI，不直接显示库存内容

### 2. 背包视图模型

新增一个专用库存映射层，将 fake player 的真实 `InventoryPlayer` 映射为容器所需的固定 40 槽布局：

- `0-3`：护甲槽
- `4-12`：hotbar
- `13-39`：主背包

这样可以同时服务于：

- 服务端真实写入 fake player 背包
- 客户端容器展示与槽位同步

### 3. 原版容器链路

新增 Forge `IGuiHandler` 路线：

- `FakePlayerInventoryGuiHandler`
- `FakePlayerInventoryContainer`
- `FakePlayerInventoryGui`

打开流程：

1. `MUI2` 管理台在服务端确认目标 bot 存在且玩家有权限
2. 服务端调用 `player.openGui(...)`
3. `IGuiHandler` 根据 fake player `entityId` 构造容器与客户端 GUI

## 权限规则

背包管理沿用 `PermissionHelper.cantManipulate(...)`：

- OP 可管理任意 fake player
- 非 OP 仅在 `allowNonOpControlOwnBot=true` 时可管理自己的 fake player
- 无权限时不打开 GUI，并返回明确状态消息

## 交互规则

### 槽位交互

- 普通拖拽、拿起、放下、交换由容器默认逻辑处理
- `Shift` 点击应支持 fake player 与当前玩家背包之间快速转移
- 护甲槽只允许对应护甲类型或空手放置

### 当前主手槽位

- 当玩家点击 fake player 第 2 行 hotbar 某个槽位时：
  - 保留原版容器点击行为
  - 同时将 fake player 的 `inventory.currentItem` 更新为该槽位
- 客户端 GUI 根据容器同步值高亮当前主手槽位

## 测试范围

本轮至少补充以下自动化回归：

1. 容器槽位映射正确
2. 点击 hotbar 槽会更新当前主手槽位
3. `transferStackInSlot(...)` 能在 fake player 与玩家背包之间互传
4. 管理服务在权限不足时拒绝打开背包 GUI
5. 管理服务能为 MUI2 主界面提供 bot 列表、选中态与概要信息

## 风险与约束

- `openGui(...)` 只能传递整型参数，因此客户端标题与目标定位优先使用 fake player `entityId`
- 客户端可能无法直接访问真实 fake player 背包对象，因此需要独立的客户端库存视图对象承接槽位同步
- 本次先保证单页稳定可用，不追求完整的滚动列表、复杂页签交互和视觉细节

## 完成标准

满足以下条件视为完成：

- `/gtstaff ui` 打开新的左侧 bot 列表管理台
- 从管理台可选中 bot 并打开新的原版背包 GUI
- 新 GUI 中可以像普通容器一样管理 fake player 背包
- 第 2 行 hotbar 点击能切换 fake player 当前主手槽位并显示高亮
- 相关测试通过，`assemble` 可产出可测试 jar
