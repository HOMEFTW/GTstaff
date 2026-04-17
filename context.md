# 项目上下文

## 基本信息
- Mod Name: GTstaff
- Mod ID: gtstaff
- Root Package: `com.andgatech.gtstaff`
- Target: MC 1.7.10 + Forge 10.13.4.1614 + GTNH

## 已实现内容

### Fake Player 核心
- `Action`：支持 `once`、`continuous`、`interval`
- `FakeNetworkManager`：空壳网络连接，使用 `EmbeddedChannel`
- `FakeNetHandlerPlayServer`：在 idle / duplicate login 文本踢出时回收 fake player
- `FakePlayer`：支持 `createFake`、`createShadow`、owner 绑定、自动重生、自杀清理、机器监控挂接，以及基于持久化快照的 `restorePersisted(...)`
- `PlayerActionPack`：支持 `USE`、`ATTACK`、`JUMP`、`DROP_ITEM`、`DROP_STACK`，包含 `turn`、`stopMovement`、`setSlot` 与挖掘状态机

### 命令
- `CommandPlayer`：支持 `/player list`
- `CommandPlayer`：支持 `/player <name> spawn [at ...] [facing ...] [in ...] [as ...]`
- `CommandPlayer`：支持 `/player <name> kill`、`shadow`、`monitor`
- `CommandPlayer`：支持动作与控制子命令 `attack`、`use`、`jump`、`drop`、`dropStack`、`move`、`look`、`turn`、`sneak`、`unsneak`、`sprint`、`unsprint`、`mount`、`dismount`、`hotbar`、`stop`
- `CommandGTstaff`：支持 `/gtstaff ui` 打开 `FakePlayerManagerUI`

### 监控相关
- `MachineMonitorService`：包含 `monitoring`、`monitorRange`、`machineStates`，支持 `tick(FakePlayer)` 与状态 diff 消息生成
- `MachineMonitorService.scanMachines(...)`：已接入真实 GT 扫描，会遍历附近已加载 `IGregTechTileEntity`，筛选 `MTEMultiBlockBase`，并映射掉电、维护、输出满等状态
- `MachineState`：包含 `active`、`powered`、`maintenanceRequired`、`outputFull`

### 持久化
- `FakePlayerRegistry`：支持大小写不敏感查询、按 owner 计数、`save(File)`、`load(File)`、`restorePersisted(...)`
- `data/gtstaff_registry.dat`：当前保存 bot 名称、`profileId`、`owner UUID`、维度、坐标、朝向、游戏模式、飞行状态、监控开关与监控半径
- `CommonProxy`：在 `serverStarted` 加载并恢复 fake player，在 `serverStopping` 保存 registry

### UI
- `FakePlayerManagerUI`：基于 `ModularUI2` 的 fake player 管理面板，可由 `/gtstaff` 打开
- `FakePlayerManagerService`：负责把 MUI2 表单状态转换为命令参数或 fake player 只读库存快照；当前已支持 `spawn`、`look`、`inventory`
- `FakePlayerManagerService`：当在线 bot 只有一个时，会为 `LookDraft` 与 `InventoryDraft` 自动预填 bot 名称；inventory 未命中 bot 时会附带当前在线 bot 列表提示
- `FakePlayerSpawnWindow`：已接入真实表单，可输入 bot 名称、坐标、维度、游戏模式，并通过服务端按钮触发 `/player spawn`
- `FakePlayerLookWindow`：已接入真实交互，可输入 bot 名称，使用方向按钮或 `Look At` 坐标触发 `/player look`
- `FakePlayerInventoryWindow`：已接入真实交互，可输入 bot 名称并通过 `Read` 按钮刷新 inventory 只读快照；当只有一个在线 bot 时会自动预填名称
- `InventorySnapshot`：会区分 hotbar、main inventory、armor，并记录当前选中的 hotbar 槽位；UI 当前使用紧凑文本视图展示完整库存

### Mixin
- `EntityPlayerMPMixin`：为 `FakePlayer` 注入并 tick `PlayerActionPack`
- `EntityPlayerMP_RespawnMixin`：在 fake player respawn 后复制 owner 与监控状态并重新注册
- `ServerConfigurationManagerMixin`：在 respawn 路径中为 fake player 构造 `FakePlayer`
- `Entity_KnockbackMixin`：只为 `FakePlayer` 设置 `velocityChanged`
- `EntityPlayerMP_TickFreezeMixin`：保留 fake player tick-freeze 占位绕过

### 权限与配置
- `PermissionHelper.cantSpawn(...)`：校验 bot 名称、在线重名、总 bot 上限、按 owner 数量上限
- `PermissionHelper.cantManipulate(...)`：非 OP 仅可在 `allowNonOpControlOwnBot=true` 时控制自己的 bot
- `PermissionHelper.cantRemove(...)`：非 OP 仅可移除自己的 bot
- `Config`：包含 `fakePlayerPermissionLevel`、`allowNonOpControlOwnBot`、`maxBotsPerPlayer`、`maxBotsTotal`、`defaultMonitorRange`

### 测试
- `src/test/java/com/andgatech/gtstaff/fakeplayer/ActionTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/MachineMonitorServiceTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPackTest.java`
- `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`
- `src/test/java/com/andgatech/gtstaff/command/CommandGTstaffTest.java`
- `src/test/java/com/andgatech/gtstaff/util/PermissionHelperTest.java`
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`
- 已通过 `./gradlew.bat --offline test` 作为当前分支的最终自动化 smoke test

## 依赖
- JUnit Jupiter 5.10.2（测试）
- GTNHLib：当前已实际参考并接入 `ServerThreadUtil`；`AboveHotbarHUD` / `PacketMessageAboveHotbar` 与 `ConfigSyncHandler` 记录为后续可选参考

## 架构备注
- `IFakePlayerHolder` 负责把 `PlayerActionPack` 暴露给 `FakePlayer`
- `PlayerActionPack` 直接适配 1.7.10 `ItemInWorldManager`，没有照搬现代 Carpet API
- `FakePlayerRegistry.load(...)` 只负责读取持久化快照；实体恢复由 `restorePersisted(...)` 在服务端启动阶段统一执行
- 自动恢复采用“持久化快照 -> `FakePlayer.restorePersisted(...)` -> 统一注册”的流程，避免在纯 IO 阶段直接创建 Minecraft 实体
- `MachineMonitorService` 参考了 `GT5-Unofficial-master` 无人机监控链路，状态来源为 `getLastShutDownReason()`、`getIdealStatus()/getRepairStatus()`、`getCheckRecipeResult()`
- 不能依赖 `ShutDownReason.getID()` 或 `CheckRecipeResult.getID()` 判断具体语义；当前对掉电使用 `POWER_LOSS` 身份比较加反射兜底，对输出满使用常量比较加 `equals(...)`
- Spawn、Look、Inventory 三个子窗口都通过 `InteractionSyncHandler` + `GTNHLib` 的 `ServerThreadUtil` 把真实逻辑收口到服务端主线程
- `FakePlayerManagerUI` 当前仍没有主列表选中态，因此子窗口都先采用“输入 bot 名称或参数，再复用命令/服务层路径”的方案
- 当前已完成自动化 smoke test，但游戏内人工烟测仍待在可启动客户端/服务端的环境中补做
- 当前 Gradle 在线解析 `gtnhgradle:1.+` 偶发 TLS 握手失败，验证命令优先使用 `--offline`
