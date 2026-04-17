# 开发日志

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
