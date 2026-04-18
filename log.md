# 开发日志

## 2026-04-18：重构 FakePlayerManagerUI 为 CustomNPC 风格分页布局

### 已完成
- 重写 `FakePlayerManagerUI` 从 320x220 基础布局到 420x200 CustomNPC 风格分页管理界面
- 左侧 `ListWidget` 可滚动 bot 列表（替代旧版硬编码 8 个按钮），支持无限 bot 数量
- 右侧 `PagedWidget` 四页签：Overview / Inventory / Actions / Monitor
- Overview 页签：显示 bot 详情（Owner、坐标、维度、游戏模式、监控状态）+ Kill / Shadow 按钮
- Inventory 页签：显示库存摘要 + "Manage Inventory" 打开原版背包容器 + "Refresh" 刷新
- Actions 页签：5 个快捷操作按钮（Attack/Use/Jump/Drop/Stop）+ 9 个 Hotbar 槽位按钮 + Sneak/Sprint + Look 弹窗
- Monitor 页签：Toggle Monitor 开关 + Scan 机器状态 + 动态显示监控摘要
- 扩展 `FakePlayerManagerService` 新增 7 个方法：`executeAction`、`killBot`、`shadowBot`、`toggleMonitor`、`setMonitorRange`、`scanMachines`、`getInventorySummaryText`
- 新增 12 个服务方法测试，全部通过
- 通过 `./gradlew.bat --offline test` + `./gradlew.bat --offline assemble` 端到端验证

### 做出的决定
- `scanMachines` 使用 `describeBot` 回退方案（不依赖 `getMonitorService()`），因为 `FakePlayer` 当前未暴露该方法
- Task 1-4 合并为一次提交（服务方法变更），Task 5 单独提交（UI 重写）
- `ListWidget` 使用 `@SuppressWarnings("rawtypes")` 解决泛型类型推断问题

---

## 2026-04-18：重构 fake player 管理台并接入原版背包容器

### 已完成
- 新增 `docs/superpowers/specs/2026-04-18-gtstaff-ui-inventory-redesign.md` 与 `docs/superpowers/plans/2026-04-18-gtstaff-ui-inventory-redesign.md`，把这次 UI 重构拆成 `MUI2 管理台` 与 `原版 chest-style 背包容器` 两条实现线
- 新增 `FakePlayerInventoryView`，将 fake player 库存映射为固定 40 槽布局：`0-3` 护甲、`4-12` hotbar、`13-39` 主背包
- 新增 `FakePlayerArmorSlot` 与 `FakePlayerInventoryContainer`，支持护甲槽规则、玩家与 fake player 背包互传、点击 fake hotbar 槽同步 `currentItem`
- 新增 `FakePlayerInventoryGuiHandler`、`FakePlayerInventoryGui` 与 `FakePlayerInventoryGuiIds`，打通 `MUI2 -> openGui -> Container/GuiContainer` 链路
- 重构 `FakePlayerManagerService`，新增 bot 列表、默认选中 bot、bot 摘要与 `openInventoryManager(...)` 服务入口
- 重写 `FakePlayerManagerUI`，改为左侧 bot 列表 + 右侧 `Overview / Inventory / Actions / Monitor` 页签；`Inventory` 页签现在打开原版容器，而不是旧的只读文本窗口
- `CommonProxy.init(...)` 现在会注册 `FakePlayerInventoryGuiHandler`
- 新增 `FakePlayerInventoryViewTest` 与 `FakePlayerInventoryContainerTest`，并扩展 `FakePlayerManagerServiceTest` 覆盖 bot 列表、详情与权限打开逻辑
- 通过 `./gradlew.bat --offline --no-daemon test --tests com.andgatech.gtstaff.ui.FakePlayerInventoryViewTest --tests com.andgatech.gtstaff.ui.FakePlayerInventoryContainerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 通过 `./gradlew.bat --offline --no-daemon assemble`，产出新的客户端测试 jar

### 遇到的问题
- **Gradle 沙箱默认用户目录不可写**：先定位到 `GRADLE_USER_HOME` 与 wrapper lock 文件问题，随后改为在真实本地 Gradle 缓存下执行离线验证
- **fake player 主背包槽位映射首版写错**：`13-39` 到 `InventoryPlayer.mainInventory[9-35]` 的换算少减了一段偏移，导致视图与容器测试首次红灯；修正公式后恢复正常
- **授权打开背包管理的测试桩不稳定**：成功用例改为以 OP 玩家打开，避免测试桩 owner 判断噪声影响 `openGui` 入口验证

### 做出的决定
- `MUI2` 只负责管理台与入口，不再承载真正的库存编辑
- fake player 背包管理优先复用原版 `Container` 交互语义，支持拖拽、Shift 点击和玩家背包互传
- 当前主手槽位用“点击 hotbar 槽 + 容器 progress bar 同步 + GUI 高亮”实现，而不是额外再做一套自定义网络包

---

## 2026-04-18：修复 MUI2 子窗口错位导致无法操作

### 已完成
- 根据 `2026-04-18_07.51.54.png`、`2026-04-18_07.51.58.png`、`2026-04-18_07.52.01.png` 确认三个子窗口都被定位到主面板右侧屏幕边缘，导致文字和输入控件大面积跑出可视区
- 新增 `PopupPanelLayout`，统一将 GTstaff 的 MUI2 子窗口按“相对主面板居中”的方式布局，避免继续使用 `leftRel(1)` 这种右侧偏移定位
- 将 `FakePlayerSpawnWindow`、`FakePlayerInventoryWindow`、`FakePlayerLookWindow` 切到新的 popup 布局 helper，并适度缩短初始状态文案，减少窄面板里的文本溢出
- 新增 `PopupPanelLayoutTest`，覆盖“子窗口相对父面板布局而不是向右偏移”的回归场景
- 通过 `./gradlew.bat test --tests com.andgatech.gtstaff.ui.PopupPanelLayoutTest --tests com.andgatech.gtstaff.ClientProxyTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 重新执行 `./gradlew.bat --offline assemble`，产出带布局修复的新测试 jar

### 做出的决定
- 子窗口先采用“相对主面板居中”而不是“屏幕右侧浮出”，这样改动最小，也最符合当时 manager UI 的使用习惯
- 本轮只修定位和可操作性，不同时重做整体视觉布局，避免把简单稳定性问题扩大成 UI 重构

---

## 2026-04-18：修复 MUI2 UI factory 未注册导致的客户端闪退

### 已完成
- 根据客户端报错 `There was a critical exception handling a packet on channel modularui2` 中的 `GuiManager.getFactory(...) -> NoSuchElementException`，确认 `/gtstaff ui` 闪退根因是 `gtstaff:fake_player_manager` 没有在客户端注册到 `GuiManager`
- 在 `ClientProxy.init(...)` 中补上 `FakePlayerManagerUI.INSTANCE` 的注册逻辑，并增加重复注册保护
- 新增 `ClientProxyTest`，覆盖“缺失时注册”和“已存在时不重复注册”两个回归场景
- 通过 `./gradlew.bat test --tests com.andgatech.gtstaff.ClientProxyTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest`
- 重新执行 `./gradlew.bat --offline assemble`，产出带 MUI2 修复的新测试 jar

### 做出的决定
- MUI2 factory 注册放在 `ClientProxy`，而不是 `CommonProxy`，避免把 UI factory 的客户端依赖强行带到专用服初始化路径
- 对 `GuiManager.registerFactory(...)` 先做存在性判断，避免重复初始化时触发 `Factory with name ... is already registered!`

---

## 2026-04-18：修复集成服启动期 fake player 自动恢复崩溃

### 已完成
- 阅读 `crash-2026-04-18_07.19.37-server.txt`，确认崩溃发生在 `Loader.serverStarted(...)` 阶段，且崩溃时 `Bot_Steve` 已经被自动恢复到玩家列表中
- 新增 `FakePlayerRestoreScheduler`，把持久化 bot 恢复从 `CommonProxy.serverStarted(...)` 挪到 `ServerTickEvent` 调度链中执行
- 为集成服增加“等到至少一名真实玩家在线后再恢复 bot”的保护，避免在单人世界尚未完成本地玩家接入时提前触发 fake player 登录链
- 补充 `FakePlayerRestoreSchedulerTest`，覆盖“专用服下一 tick 恢复”和“集成服等待真实玩家后再恢复”两个回归场景
- 跑通 `./gradlew.bat compileJava`、`./gradlew.bat compileTestJava`，以及 `ActionTest`、`FakePlayerRegistryTest`、`PermissionHelperTest`、`FakePlayerRestoreSchedulerTest`

### 做出的决定
- 自动恢复仍然保留，但不再直接耦合到 FML `serverStarted` 事件主体中执行
- 集成服与专用服使用不同恢复时机：专用服允许下一 tick 恢复，集成服要求先有真实玩家上线

---

## 2026-04-18：调研 `CustomNPC-Plus-master` 对 `GTstaff` 的可借鉴点

### 已完成
- 阅读 `CustomNPC-Plus-master` 的命令、GUI、同步、持久化与路径相关源码，并与 `GTstaff` 当前 fake player / UI 实现对照
- 确认较值得参考的方向：命令分层与子命令权限、可序列化 GUI 状态、分块同步、大对象 JSON/NBT 模板存储、路径巡逻/导航
- 确认不建议直接照搬的部分：庞大的 `PacketHandler*` 分发链路、旧式 GUI/Container 基建，以及以 NPC 实体为中心的行为实现

### 遇到的问题
- **`CustomNPC-Plus-master` 体量很大**：先按命令、GUI、同步、持久化、路径五条主线缩小范围，避免陷入无关功能
- **项目技术栈不同**：`GTstaff` 目前基于 fake player + ModularUI2，而 `CustomNPC` 主要围绕自定义 NPC、脚本 GUI 与自有网络协议

### 做出的决定
- 近期如果继续扩展 `GTstaff`，优先参考“结构设计”和“数据流模式”，而不是直接移植 `CustomNPC` 的具体实现
- 如果后续要做 bot 选中态、多 bot 管理面板、巡逻/路线系统、模板化 bot 预设，可回头针对对应模块做二次调研

---

## 2026-04-17：Task 8 - 用户提示与最终自动化 smoke test

### 已完成
- 扩展 `FakePlayerManagerService` 的用户提示逻辑：
- 当在线 fake player 只有一个时，`createLookDraft(...)` 会自动预填该 bot 名称
- `createInventoryDraft(...)` 也会自动预填该 bot 名称，减少 inventory 子窗口的重复输入
- 当 inventory 窗口输入了不存在的 bot 时，错误消息会附带当前在线 bot 列表，帮助快速纠正输入
- 调整 `FakePlayerInventoryWindow` 与 `FakePlayerManagerUI`，让 inventory 子窗口也能拿到当前玩家上下文并复用新的 draft 预填逻辑
- 扩展 `FakePlayerManagerServiceTest`，新增自动预填与未知 bot 提示两类回归
- 通过 `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 通过 `./gradlew.bat --offline test`
- 通过 `./gradlew.bat --offline compileJava`

### 遇到的问题
- **`createInventoryDraft(...)` 原本没有玩家上下文参数**：测试先在编译期失败，随后统一把 inventory 窗口与服务层改为显式传入 `EntityPlayer`
- **当前终端环境无法执行真正的游戏内 UI 手工烟测**：本轮只能完成离线 Gradle 自动化 smoke test，最终的游戏内点击验证需要在可启动客户端/服务端的环境中手工补做

### 做出的决定
- Task 8 的“用户提示 + 自动化联调验证”在当前环境中视为完成
- 将“进游戏手点 `/gtstaff ui`、Spawn/Look/Inventory 子窗口”的最终人工 smoke test 单独保留为后续事项，而不阻塞当前开发收尾

---

## 2026-04-17：Task 7 - Inventory 子窗口真实交互

### 已完成
- 扩展 `FakePlayerManagerService`，新增 `InventoryDraft`、`InventorySnapshot` 与 `readInventory(...)`，把 UI 请求收口为对 `FakePlayerRegistry` 的只读库存快照读取。
- 为库存快照补充格式化逻辑，区分 hotbar、main inventory、armor，并保留当前选中的 hotbar 槽位。
- 为 `InventorySnapshot` 增加 `toCompactDisplayText()`，用于在 MUI2 面板里展示完整但紧凑的只读库存摘要。
- 将 `FakePlayerInventoryWindow` 从占位文本改为真实窗口，支持输入 bot 名称、点击 `Read` 刷新，以及显示服务端返回的库存快照或错误信息。
- 调整 `FakePlayerManagerUI`，为 inventory 面板传入 `PanelSyncManager`，接通 `InteractionSyncHandler` 的服务端刷新链路。
- 扩展 `FakePlayerManagerServiceTest`，按 TDD 新增库存快照三类回归：
- 已注册 fake player 可返回只读库存快照
- 空 bot 名称会被拦截
- 未在线的 bot 会返回明确错误
- 通过 `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 通过 `./gradlew.bat --offline test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.command.CommandGTstaffTest --tests com.andgatech.gtstaff.util.PermissionHelperTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.MachineMonitorServiceTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 通过 `./gradlew.bat --offline compileJava`

### 遇到的问题
- **Inventory 相关模型最初完全缺失**：红灯测试先在编译期失败，随后补齐 `InventoryDraft`、`InventorySnapshot` 和 `readInventory(...)`。
- **测试里直接使用 `Items.*` 会在离线单测环境触发空指针**：改为使用自建 `Item` + `setStackDisplayName(...)` 的测试夹具，避免依赖静态物品注册表初始化。
- **测试桩注册进 `FakePlayerRegistry` 时会走持久化快照**：补齐 `StubFakePlayer` 的监控相关方法覆盖，避免 `MachineMonitorService` 为空导致的空指针。

### 做出的决定
- inventory 子窗口这一轮优先做成“可刷新、只读、完整快照”的真实交互，不在本轮把 MUI2 `ItemSlot`/handler 同步体系一并引入。
- UI 层继续复用 `InteractionSyncHandler` + `ServerThreadUtil` 的服务端执行模式，保持 Spawn、Look、Inventory 三个子窗口的交互一致性。

---

## 2026-04-17：Task 7 - Look 子窗口真实交互

### 已完成
- 扩展 `FakePlayerManagerService`，新增 `LookDraft`、`createLookDraft(...)`、`submitLook(...)` 与 `normalizeLookMode(...)`，将 UI 表单状态转换为 `/player <name> look ...` 命令参数。
- 将 `FakePlayerLookWindow` 从占位文本改为真实可交互表单，支持 bot 名称、六个方向按钮与 `Look At` 坐标输入。
- 调整 `FakePlayerManagerUI`，为 look 面板传入当前 `EntityPlayerMP` 与 `PanelSyncManager`。
- 通过 `./gradlew.bat --offline test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 通过 `./gradlew.bat --offline compileJava`

### 做出的决定
- 在主面板未加入“选中 bot”状态前，look 子窗口先采用“输入 bot 名称 + 复用命令路径”的方案，优先打通真实交互。

---

## 2026-04-17：Task 7 - 跨重启自动恢复 fake player 实体

### 已完成
- 扩展 `FakePlayerRegistry` 的持久化快照字段，新增 `PersistedBotData`、`BotRestorer` 与 `restorePersisted(...)`。
- 新增 `FakePlayer.restorePersisted(...)`，恢复 bot 的维度、坐标、朝向、游戏模式、飞行状态与监控状态。
- 在 `CommonProxy.serverStarted(...)` 接入 registry 自动恢复，在 `serverStopping(...)` 保存 registry。
- 为 `FakePlayerRegistryTest` 补充 `save -> load -> restore` 全链路回归。

### 做出的决定
- 将 IO 读取与 Minecraft 实体恢复分离，避免在 `load(...)` 中直接构建实体。

---

## 2026-04-17：Task 7 - Spawn 子窗口真实交互

### 已完成
- 新增 `FakePlayerManagerService`，把 MUI2 表单输入转换为 `/player <name> spawn ...` 命令参数。
- 将 `FakePlayerSpawnWindow` 改为真实表单，支持 `name`、`x / y / z`、`dimension`、`gameMode` 输入与服务端执行。
- 确认 `GTNHLib-0.7.10` 中当前最直接可用的参考 API 是 `ServerThreadUtil`。

### 做出的决定
- Spawn 逻辑不在 UI 层重复实现，而是统一复用现有 `/player spawn` 命令路径。
