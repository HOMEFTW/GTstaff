## 2026-04-18：外部参考调研
- 已完成对 `CustomNPC-Plus-master` 的对照调研；当前未引入其代码，但后续可优先参考其命令分层、可序列化 GUI 状态、分块同步、JSON/NBT 模板持久化与路径导航设计
# 项目上下文

## 基本信息
- Mod Name: GTstaff
- Mod ID: gtstaff
- Version: v1.0.1
- Root Package: `com.andgatech.gtstaff`
- Target: MC 1.7.10 + Forge 10.13.4.1614 + GTNH
- GitHub: https://github.com/HOMEFTW/GTstaff

### Mod 入口与代理
- `GTstaff`：`@Mod` 入口类，定义 MODID/VERSION/LOG，通过 `@SidedProxy` 委托所有 FML 生命周期事件
- `CommonProxy`：服务端/通用代理，负责配置同步、命令注册（`CommandGTstaff`/`CommandPlayer`）、`FakePlayerRestoreScheduler` 调度、`FakePlayerInventoryGuiHandler` 注册、`MonsterRepellentService` 事件注册、registry 保存/加载
- `ClientProxy`：客户端代理，扩展 `CommonProxy` 并额外向 `GuiManager` 注册 `FakePlayerManagerUI` factory（带防重复注册保护）

### Mixin 接口
- `IFakePlayerHolder`：单方法接口 `PlayerActionPack getActionPack()`，供 Mixin 在 `EntityPlayerMP` 上访问 action pack

## 已实现内容

### Fake Player 核心
- `Action`：支持 `once`、`continuous`、`interval`
- `FakeNetworkManager`：空壳网络连接，使用 `EmbeddedChannel`
- `FakeNetHandlerPlayServer`：在 idle / duplicate login 文本踢出时回收 fake player
- `FakePlayer`：支持 `createFake`、`createShadow`、owner 绑定、自动重生、自杀清理、机器监控挂接、敌对生物驱逐（`monsterRepelling`/`monsterRepelRange`）、基于持久化快照的 `restorePersisted(...)`，以及恢复后基于带 `textures` profile 的 `rebuildRestoredWithProfile(...)`
- `FakePlayerProfiles`：集中处理“新生成 fake player 应该使用哪个 `GameProfile`”；当前会优先走 `SkinPortCompat.resolveProfile(name)`，拿到正版皮肤 profile 后复制一份再用于创建假人，失败则回退到离线 UUID profile
- `FakePlayer.runLivingUpdate(...)`：在 `EntityPlayerMP.onUpdate()` 之后直接执行 `onLivingUpdate()`，补上 fake player 的移动/跳跃/碰撞链路，但不再调用 `onUpdateEntity()` 触发第二次 `PlayerTickEvent`
- `FakePlayerRegistry.restorePersisted(...)`：现在会返回本轮实际恢复出的 bot 列表，并保持持久化顺序，供恢复后的补皮调度复用
- `PlayerActionPack`：支持 `USE`、`ATTACK`、`JUMP`、`DROP_ITEM`、`DROP_STACK`，包含 `turn`、`stopMovement`、`setSlot` 与挖掘状态机

### SkinPort 兼容
- `SkinPortCompat`：通过反射可选接入 `lain.mods.skins.impl.MojangService`，但现在只把它用于按名字解析在线 `GameProfile`/UUID，不再直接信任 `SkinPort` 返回的 filled profile
- `SkinPortCompat.resolveProfile(...)`：拿到在线 UUID 后会统一回到服务端 `MinecraftSessionService.fillProfileProperties(profile, true)` 补全带签名的 `textures`；若 `SkinPort` 不可用，还会继续尝试服务端 `PlayerProfileCache` / `GameProfileRepository` 回退解析
- 当前兼容层的成功标准已从“存在 `textures` 属性”提升为“存在带签名的 `textures` 属性”，因为 1.7.10 客户端远程玩家皮肤加载会拒绝 unsigned textures
- `FakePlayerProfiles.resolveSkinProfile(...)`：为恢复补皮路径暴露可复用的拷贝型解析入口，成功时返回复制后的带纹理 profile，避免共享可变属性表
- `FakePlayerSkinRestoreScheduler`：接在 `FakePlayerRestoreScheduler` 之后；对每个恢复出的 bot 后台解析皮肤，成功后回主线程调用 `FakePlayer.rebuildRestoredWithProfile(...)` 安全替换实体
- 当前皮肤支持范围已覆盖“新生成 fake player”与“服务端重启恢复后的异步补皮重建”；仍不会把 `textures` 持久化进 registry，也不会做无限重试队列

### ServerUtilities 兼容
- `ServerUtilitiesCompat.isFakePlayer(EntityPlayerMP)`：将 GTstaff 的 `FakePlayer` 暴露为可供 ServerUtilities 判断的兼容入口
- `ServerUtils_ServerUtilitiesMixin`：注入 `serverutils.lib.util.ServerUtils.isFake(EntityPlayerMP)` 返回阶段；如果玩家是 GTstaff `FakePlayer`，则强制返回 `true`
- 效果：GTstaff 生成的假人不会再被 `ServerUtilities` 的统计、登录记录与相关玩家上下文逻辑计作真实玩家

### 命令
- `CommandPlayer`：支持 `/player list`
- `CommandPlayer`：支持 `/player <name> spawn [at ...] [facing ...] [in ...] [as ...]`
- `CommandPlayer`：支持 `/player <name> kill`、`purge`、`shadow`、`monitor`
- `CommandPlayer`：支持 `/player <name> monitor [on|off|range <radius>|interval <ticks>|scan]`
- `CommandPlayer`：支持 `/player <name> repel [on|off|range <radius>]`
- `CommandPlayer`：支持 `/player <name> inventory [summary|open]`
- `CommandPlayer`：支持动作与控制子命令 `attack`、`use`、`jump`、`drop`、`dropStack`、`move`、`look`、`turn`、`sneak`、`unsneak`、`sprint`、`unsprint`、`mount`、`dismount`、`hotbar`、`stop`、`follow`
- `CommandPlayer`：帮助文本现已显式列出 `stopattack` 与 `stopuse`
- `CommandGTstaff`：支持 `/gtstaff ui` 打开 `FakePlayerManagerUI`

### 监控相关
- `MachineMonitorService`：包含 `monitoring`、`monitorRange`、`machineStates`，支持 `tick(FakePlayer)` 与状态 diff 消息生成
- `MachineMonitorService.scanMachines(...)`：已接入真实 GT 扫描，会遍历附近已加载 `IGregTechTileEntity`，筛选 `MTEMultiBlockBase`，并映射掉电、维护、输出满、结构不完整、污染堵塞、无法修复、缺涡轮、部件错误、发电机不足、资源耗尽、电力不足、电压不足等13种故障状态
- `MachineState`：包含 `active`、`powered`、`maintenanceRequired`、`outputFull`、`structureIncomplete`、`pollutionFail`、`noRepair`、`noTurbine`、`noMachinePart`、`insufficientDynamo`、`outOfResource`、`insufficientPower`、`insufficientVoltage`
- `MachineMonitorService` 支持可配置 `reminderInterval`（默认600 tick=30秒），通过 `setReminderInterval()` 修改
- `MachineMonitorService.buildOverviewLines()` / `buildProblemSummaryLines()` 逐行返回消息，聊天栏每台机器独占一条消息
- `FakePlayer.getChatColor()` / `colorizeName()`：基于名称hash从10种 `EnumChatFormatting` 颜色中分配，聊天用 `ChatStyle.setColor()`，UI 用 `§x` 格式代码
- 监控报告已翻译为中文

### 敌对生物驱逐
- `MonsterRepellentService`：订阅 `MinecraftForge.EVENT_BUS` 的 `CheckSpawn` 事件，遍历所有开启驱逐的 fake player，在球形范围内阻止敌对生物生成
- `FakePlayer.monsterRepelling`：驱逐开关状态（boolean）
- `FakePlayer.monsterRepelRange`：驱逐范围（int，默认64格），UI 提供 32/64/128/256/400 五档选择
- 假人移动时驱逐范围自动跟随（每次 CheckSpawn 实时检查当前坐标）
- 驱逐状态通过 `FakePlayerRegistry` 持久化到 `data/gtstaff_registry.dat`

### 假人跟随
- `FollowService`：挂载在 `FakePlayer` 上，每 tick 在 `actionPack.onUpdate()` 之后、`runLivingUpdate()` 之前执行
- `FollowService.tick()`：检查目标在线 → 维度检查 → 距离判断 → 飞行同步 → 方向计算 → Y 轴控制
- 方向计算：`calculateMovement(fakeYaw, fromX, fromZ, toX, toZ)` 将目标方向转换为 moveForward / moveStrafing（基于 yaw 差的 cos/sin 分量）
- Y 轴控制：空中时 `setJumping(true)` 上升、`motionY -= 0.1` 下降，阈值 0.5 格
- 超距传送：距离 > teleportRange 时传送到玩家背后 2 格
- 跨维度传送：维度不同时等待 100 tick（5 秒），聊天栏通知玩家，计时结束后跨维度传送
- 跨维度传送失败回滚：如果目标世界挂接失败，会恢复假人的原始 `dimension/worldObj/position`，并允许下一个倒计时周期继续重试
- 目标玩家临时离线/重连：`FollowService` 会保留 `followTargetUUID`，只重置跨维度倒计时；玩家重新上线后会继续跟随，并在必要时重新触发跨维度传送
- 目标玩家查找：`findTargetPlayer(...)` 会优先选择同 UUID 的存活实体，避免旧的 `isDead` 残留玩家对象阻塞后续跟随与跨维度传送
- 飞行同步：跟随时自动将 `fakePlayer.capabilities.isFlying` 同步为目标玩家的飞行状态
- 参数：followRange（默认 3 格）、teleportRange（默认 32 格），可通过命令和 UI 调节
- 命令：`/player <name> follow [player|stop|range <n>|tprange <n>]`
- UI：Other 页签新增"跟随我"/"停止跟随"按钮 + 跟随距离和传送距离按钮组
- 持久化：followTarget（UUID）、followRange、teleportRange 写入 `data/gtstaff_registry.dat`；重启后自动恢复跟随

### 持久化
- `FakePlayerRegistry`：支持大小写不敏感查询、按 owner 计数、`contains(name)`、`save(File)`、`saveServerRegistry(server)`、`load(File)`、`restorePersisted(...)`
- `data/gtstaff_registry.dat`：当前保存 bot 名称、`profileId`、`owner UUID`、维度、坐标、朝向、游戏模式、飞行状态、监控开关、监控半径、提醒频率、驱逐开关、驱逐范围、跟随目标、跟随距离、传送距离
- `CommonProxy`：在 `serverStarted` 加载并恢复 fake player，在 `serverStopping` 保存 registry
- `PlayerDataCleanup`：会在存档根目录下清理 `playerdata`、`serverutilities/players` 与 `stats` 中与 fake player 对应的玩家文件；`playerdata` 删除 `<uuid>.dat` 与 `<name>.baub/.baubback/.thaum/.thaumback`，`serverutilities/players` 删除 `<name>.dat`，`stats` 删除 `<uuid>.*`
- `PlayerDataCleanup`：`playerdata` 名称型文件删除白名单当前包含 `<name>.baub/.baubback/.thaum/.thaumback/.tf/.tfback`，但不会删除 `<name>.dat`
- `CommandPlayer.resolveSaveRoot(...)`：当前优先使用 overworld 的 `getChunkSaveLocation()` 获取真实世界存档根目录，其次才回退到 `DimensionManager.getCurrentSaveRootDirectory()` 与 `server.getFile(...)`
- `CommandPlayer.getSaveRootsForCleanup(...)`：清理时会同时尝试解析出的世界存档根目录和 `server.getFile(...)` 推导出的工作根目录，覆盖单人/集成服中 `playerdata` 与 `serverutilities/stats` 分散在不同根目录的情况

### UI
- `FakePlayerManagerUI`：基于 `ModularUI2` 的 fake player 管理面板，可由 `/gtstaff` 打开；当前已重构为左侧 bot 列表（带颜色） + 右侧 `Overview / Inventory / Actions / Monitor / Other` 页签
- `Overview` 页签：当前提供 `Kill` 与“完全清除”按钮；“完全清除”会调用 `/player <name> purge`，同时从在线实体、持久化 registry、当前世界存档的 `playerdata`、`serverutilities/players` 与 `stats` 中删除 bot 并立即保存
- Monitor 页签：切换监控开关 + 扫描按钮 + 4个提醒频率按钮（10秒/30秒/1分/5分）+ 可滚动机器状态列表
- Other 页签中的敌对生物驱逐、跟随距离与传送距离现在都已有对应命令入口，不再只依赖 UI
- `ClientProxy.init(...)`：当前会向 `GuiManager` 注册 `FakePlayerManagerUI.INSTANCE`，保证客户端能解开 `OpenGuiPacket`
- `PopupPanelLayout`：统一让 Spawn / Inventory / Look 三个子窗口相对主面板居中，避免 panel 出现在主界面右侧屏幕外缘
- `FakePlayerManagerService`：负责把 MUI2 表单状态转换为命令参数、bot 列表/概要与背包管理入口；当前已支持 `spawn`、`look`、`inventory`、`killBot()`、`purgeBot()`、`listBotNames()`、`defaultSelectedBotName()`、`describeBot()`、`openInventoryManager()`
- `FakePlayerSpawnWindow`：生成按钮当前不再依赖文本框同步和按钮点击的包顺序；点击时会把 bot 名、坐标、维度、模式打包成单个请求并在服务端解析执行
- `FakePlayerManagerService`：当在线 bot 只有一个时，会为 `LookDraft` 与 `InventoryDraft` 自动预填 bot 名称；inventory 未命中 bot 时会附带当前在线 bot 列表提示
- `FakePlayerManagerService` 支持设置提醒频率 `setReminderInterval()`，`BotDetails` 包含 `reminderInterval` 字段
- `FakePlayerSpawnWindow`：已接入真实表单，可输入 bot 名称、坐标、维度、游戏模式，并通过服务端按钮触发 `/player spawn`
- `FakePlayerLookWindow`：已接入真实交互，可输入 bot 名称，使用方向按钮或 `Look At` 坐标触发 `/player look`
- `FakePlayerInventoryWindow`：保留旧的只读文本快照窗口实现，但已不再是主路径；主管理台的 `Inventory` 页签现在打开原版容器
- `InventorySnapshot`：会区分 hotbar、main inventory、armor，并记录当前选中的 hotbar 槽位；UI 当前使用紧凑文本视图展示完整库存
- `FakePlayerInventoryView`：将 fake player 背包映射为固定 40 槽布局：`0-3` 护甲、`4-12` hotbar、`13-39` 主背包
- `FakePlayerArmorSlot`：限定护甲槽只接受对应类型护甲
- `FakePlayerInventoryContainer`：负责 fake player 与当前玩家背包的槽位布局、Shift 点击互传、点击 hotbar 槽同步 `currentItem`
- `FakePlayerInventoryGuiHandler`：通过 Forge `IGuiHandler` 打通 `MUI2 -> openGui -> Container/GuiContainer`
- `FakePlayerInventoryGui`：原版 chest-style `GuiContainer`，会高亮 fake player 当前主手 hotbar 槽位
- 当前工作树中的统一背包实现已接入 `Baubles Expanded`：右侧饰品区跟随 `BaubleExpandedSlots` 当前配置动态生成，支持滚轮/滚动条浏览，并复用 `InventoryBaubles` 与 `SlotBauble`

### Mixin
- `EntityPlayerMPMixin`：为 `FakePlayer` 注入并 tick `PlayerActionPack`
- `EntityPlayerMP_RespawnMixin`：在 fake player respawn 后复制 owner 与监控状态并重新注册
- `ServerConfigurationManagerMixin`：在 respawn 路径中为 fake player 构造 `FakePlayer`
- `ServerUtils_ServerUtilitiesMixin`：为 `ServerUtilities` 的 `ServerUtils.isFake(...)` 补充 GTstaff `FakePlayer` 识别
- `Entity_KnockbackMixin`：只为 `FakePlayer` 设置 `velocityChanged`
- `EntityPlayerMP_TickFreezeMixin`：保留 fake player tick-freeze 占位绕过

### 权限与配置
- `PermissionHelper.cantSpawn(...)`：校验 bot 名称、在线重名、总 bot 上限、按 owner 数量上限
- `PermissionHelper.cantManipulate(...)`：非 OP 仅可在 `allowNonOpControlOwnBot=true` 时控制自己的 bot
- `PermissionHelper.cantRemove(...)`：非 OP 仅可移除自己的 bot
- `Config`：包含 `fakePlayerPermissionLevel`、`allowNonOpControlOwnBot`、`maxBotsPerPlayer`、`maxBotsTotal`、`defaultMonitorRange`

### 测试
- `src/test/java/com/andgatech/gtstaff/fakeplayer/ActionTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerMovementUpdateTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakeNetworkManagerTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/MachineMonitorServiceTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPackTest.java`
- `src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java`
- `src/test/java/com/andgatech/gtstaff/integration/ServerUtilitiesCompatTest.java`
- `src/test/java/com/andgatech/gtstaff/command/CommandPlayerTest.java`
- `src/test/java/com/andgatech/gtstaff/ClientProxyTest.java`
- `src/test/java/com/andgatech/gtstaff/command/CommandGTstaffTest.java`
- `src/test/java/com/andgatech/gtstaff/util/PermissionHelperTest.java`
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerManagerServiceTest.java`
- `src/test/java/com/andgatech/gtstaff/ui/PopupPanelLayoutTest.java`
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryViewTest.java`
- `src/test/java/com/andgatech/gtstaff/ui/FakePlayerInventoryContainerTest.java`
- 已通过 `./gradlew.bat --offline test` 作为当前分支的最终自动化 smoke test
- 已通过 `./gradlew.bat --offline assemble` 产出客户端测试用 jar，产物位于 `build/libs/`
- 已通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble` 重新打包当前工作区产物
- 当前最新 jar：`build/libs/gtstaff-v1.0.1.jar`
- 当前最新 dev jar：`build/libs/gtstaff-v1.0.1-dev.jar`
- 当前最新 sources jar：`build/libs/gtstaff-v1.0.1-sources.jar`
- 最新重新打包 jar：`build/libs/gtstaff-b166ed7-master+b166ed77c1-dirty.jar`（含监控增强、颜色分配、中文翻译、提醒频率按钮）
- 最新客户端测试主 jar：`build/libs/gtstaff-v0.1.1-master+1f334d4b20-dirty.jar`（含敌对生物驱逐器、其他功能页签）

## 依赖
- JUnit Jupiter 5.10.2（测试）
- GTNHLib：当前已实际参考并接入 `ServerThreadUtil`；`AboveHotbarHUD` / `PacketMessageAboveHotbar` 与 `ConfigSyncHandler` 记录为后续可选参考
- `Baubles Expanded`：当前统一背包实现已作为硬依赖接入，统一背包右侧饰品区直接复用其 `InventoryBaubles`、`SlotBauble` 与动态槽位配置
- `ServerUtilities`：当前通过可选 mixin 接入其 `ServerUtils.isFake(...)` 判断链，不要求 GTstaff 在无该模组环境下强依赖加载

## 架构备注
- `IFakePlayerHolder` 负责把 `PlayerActionPack` 暴露给 `FakePlayer`
- `PlayerActionPack` 直接适配 1.7.10 `ItemInWorldManager`，没有照搬现代 Carpet API
- `FakePlayerRegistry.load(...)` 只负责读取持久化快照；实体恢复由 `restorePersisted(...)` 在服务端启动阶段统一执行
- 自动恢复采用“持久化快照 -> `FakePlayerRestoreScheduler` 延后调度 -> `FakePlayer.restorePersisted(...)` -> `FakePlayerSkinRestoreScheduler` 异步补皮 -> 主线程 `FakePlayer.rebuildRestoredWithProfile(...)` 安全替换”的流程，避免在纯 IO 阶段直接创建 Minecraft 实体，同时不阻塞开服
- `CommonProxy.serverStarted(...)` 当前只负责读取 registry 并登记待恢复状态，不再直接在 FML `serverStarted` 事件里构建 fake player
- `FakePlayerRestoreScheduler` 的恢复策略：
- 专用服：服务器启动后的下一次 `ServerTickEvent` 即可恢复持久化 bot
- 集成服：必须等到至少一名真实 `EntityPlayerMP` 在线后才执行自动恢复，避免单人世界启动链中过早触发 fake player 登录事件
- `FakePlayerSkinRestoreScheduler` 使用后台单线程 + 主线程回切的两段式调度；停服时通过 generation 递增让旧异步结果失效，避免跨停服替换过期 bot
- `MachineMonitorService` 参考了 `GT5-Unofficial-master` 无人机监控链路，状态来源为 `getLastShutDownReason()`、`getIdealStatus()/getRepairStatus()`、`getCheckRecipeResult()`
- 不能依赖 `ShutDownReason.getID()` 或 `CheckRecipeResult.getID()` 判断具体语义；当前对掉电使用 `POWER_LOSS` 身份比较加反射兜底，对输出满使用常量比较加 `equals(...)`
- Spawn、Look、Inventory 三个子窗口都通过 `InteractionSyncHandler` + `GTNHLib` 的 `ServerThreadUtil` 把真实逻辑收口到服务端主线程
- `FakeNetworkManager` 当前使用 `EmbeddedChannel(new ChannelInboundHandlerAdapter())`，用于兼容 GTNH 运行时的 Netty 4.0.10，不再触发 `handlers is empty` 崩溃
- fake player 当前不再手动调用 `EntityPlayerMP.onUpdateEntity()`；第二段 tick 只执行 `onLivingUpdate()`，避免在集成服里向外部模组额外发布一轮共享 `PlayerTickEvent`
- `FakePlayerManagerUI` 的 MUI2 factory 名称为 `gtstaff:fake_player_manager`；若客户端未在 `GuiManager` 注册该 factory，则会在 `OpenGuiPacket.read(...)` 时报 `NoSuchElementException`
- 当前三个 GTstaff 子窗口不再使用 `relative(parent).leftRel(1).topRel(0)`，避免 UI 被摆到主窗口右侧不可操作区域
- `FakePlayerManagerUI` 当前已经有主列表选中态，但 `Spawn / Look` 仍保留独立弹窗与手填 bot 名称；`Inventory` 主路径已切到原版容器
- fake player 背包管理采用“`MUI2` 主管理台 + Forge `IGuiHandler` + 原版 `Container/GuiContainer`”双层结构，而不是在 `MUI2` 中直接堆 `ItemSlot`
- `FakePlayerInventoryContainer` 使用容器 progress bar 同步当前 hotbar 选择，高亮当前 fake player 主手槽位
- `CommonProxy.init(...)` 当前会注册 `FakePlayerInventoryGuiHandler`，保证专用服与集成服都能打开新的 fake player 背包容器
- 当前已完成自动化 smoke test，但游戏内人工烟测仍待在可启动客户端/服务端的环境中补做
- `./gradlew.bat --offline build` 当前会在 `spotlessJavaCheck` 因 CRLF/LF 格式差异失败，但不影响 `assemble`、`test`、`compileJava` 与 reobf jar 产出
- 当前 Gradle 在线解析 `gtnhgradle:1.+` 偶发 TLS 握手失败，验证命令优先使用 `--offline`
- `FollowService.tick()` 在 `actionPack.onUpdate()` 之后执行，覆盖 actionPack 设置的 moveForward/moveStrafing；跟随优先级高于手动 move 命令
- 跨维度传送通过 `FollowService` 内部的手动迁移流程实现：先从旧世界 `PlayerManager/playerEntities/loadedEntityList` 摘除，再在新世界预加载 chunk 后 `spawnEntityInWorld(...)`，失败时由快照回滚，不走 `transferPlayerToDimension`
- `ServerUtilities` 兼容不去改 `Universe` 等分散统计入口，而是统一挂接在 `ServerUtils.isFake(...)` 的中心判断点，减少后续版本漂移时的漏改风险


