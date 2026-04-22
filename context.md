# 项目上下文

## 基本信息
- Mod Name: GTstaff
- Mod ID: gtstaff
- Version: v1.1.1
- Root Package: `com.andgatech.gtstaff`
- Target: MC 1.7.10 + Forge 10.13.4.1614 + GTNH
- GitHub: https://github.com/HOMEFTW/GTstaff
- Latest Release: https://github.com/HOMEFTW/GTstaff/releases/tag/v1.1.1
- 最新确认产物：`build/libs/gtstaff-v1.1.1.jar`、`build/libs/gtstaff-v1.1.1-dev.jar`、`build/libs/gtstaff-v1.1.1-sources.jar`（2026-04-22 离线 `test assemble` 成功，主 jar 时间已更新到 09:46）

### Mod 入口与代理
- `GTstaff`：`@Mod` 入口类，定义 MODID/VERSION/LOG，通过 `@SidedProxy` 委托所有 FML 生命周期事件
- `CommonProxy`：服务端/通用代理，负责配置同步、命令注册（`CommandGTstaff`/`CommandPlayer`）、`FakePlayerRestoreScheduler` 调度、`FakePlayerInventoryGuiHandler` 注册、`MonsterRepellentService` 事件注册、registry 保存/加载
- `ClientProxy`：客户端代理，扩展 `CommonProxy` 并额外向 `GuiManager` 注册 `FakePlayerManagerUI` factory（带防重复注册保护）

### Mixin 接口
- `IFakePlayerHolder`：单方法接口 `PlayerActionPack getActionPack()`，供 Mixin 在 `EntityPlayerMP` 上访问 action pack

### 架构备注
- 参考 `fabric-carpet-master` 后确认：其 fake player 攻击链依赖稳定的射线命中与原生 `player.attack(entity)`，并未使用额外伤害 fallback；因此对 GTstaff 更有参考价值的是“命中链设计”而不是“攻击 API 替换”
- GTNH/Forge 生态中不少外部模组按 `instanceof net.minecraftforge.common.util.FakePlayer` 或 `playerNetServerHandler == null` 识别 fake player；GTstaff 假人当前不属于这两类，存在兼容灰区
- nextgen fake player runtime 总体设计已写入 `docs/superpowers/specs/2026-04-20-gtstaff-nextgen-fake-player-runtime-design.md`，并以 commit `a7e2dc0` 单独提交；后续实现应按 spec 中的 `Wave A-D` 分阶段推进
- nextgen fake player runtime 的首个实现计划已写入 `docs/superpowers/plans/2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-a.md`；Wave A 现已实现完成，范围仅覆盖运行时抽象、registry 元数据、nextgen inert skeleton 与默认保持 `legacy` 的命令只读接线
- nextgen fake player runtime 的第二阶段计划已写入 `docs/superpowers/plans/2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-b.md`；Wave B 已完成 legacy 动作链的内部解耦，但默认 runtime 仍保持 `legacy`
- nextgen fake player runtime 的第三阶段计划已写入 `docs/superpowers/plans/2026-04-21-gtstaff-nextgen-fake-player-runtime-wave-c.md`；Wave C 现已完成，`follow / monitor / repel / inventory / UI manager` 已迁到 runtime facade，`NextGenBotRuntime` 也已有对应服务状态壳，但默认 runtime 仍保持 `legacy`
- nextgen fake player runtime 的第四阶段计划已写入 `docs/superpowers/plans/2026-04-21-gtstaff-nextgen-fake-player-runtime-wave-d.md`；Wave D 现已完成，默认 runtime 已切到 `nextgen`，同时保留 `legacy / mixed` 回退路径
- `NextGenBotFactory` 现已支持 `spawn / shadow / restore`，`BotRuntimeMode` / `BotLifecycleManager` 已接入 runtime-aware 的 `spawn / shadow / restore / kill` 决策
- `FakePlayerRegistry.registerRuntime(...)` 现在会为 runtime-only bot 写入持久化 snapshot，`FakePlayerRestoreScheduler` 已改为处理 `BotRuntimeView` 列表，并把所有恢复出的 runtime 统一交给 `FakePlayerSkinRestoreScheduler` 做异步补皮与主线程重建
- `CommandPlayer.handleSpawn(...)`、`handleManipulation(...)`、`handleShadow(...)` 与 runtime-only bot 的 `handleKill(...)` 已不再直接绑死 legacy `FakePlayer` / `PlayerActionPack`
- 2026-04-21 迁移推进确认：`CommandPlayer.handlePurge(...)` 也已补齐 runtime-aware 在线分支，runtime-only nextgen bot 现在会先经由 `BotLifecycleManager.kill(...)` 下线，再执行 purge 文件清理；若在线实体带有真实 `GameProfile`，则优先按该 profile UUID 清理 `playerdata/stats`
- 2026-04-21 迁移推进确认：`CommandPlayer.handleShadow(...)` 现已复用 `ServerUtilitiesCompat.isFakePlayer(...)` 做身份识别，nextgen `GTstaffForgePlayer` 也会被正确拒绝 shadow
- 2026-04-21 迁移推进确认：`FakePlayerRegistry.registerRuntimeInternal(...)` 现在会在注册 nextgen 等非 legacy runtime 时主动清理同名 `fakePlayers` 旧索引，避免命令/UI 再拿到陈旧 legacy 实体引用
- 2026-04-21 迁移推进确认：`BotLifecycleManagerTest.restoreNextGenReappliesPersistedServiceState()` 已显式固定 `BotRuntimeMode.NEXTGEN`，避免组合测试被全局 `fakePlayerRuntimeMode` 污染
- 2026-04-21 迁移推进确认：`GTstaffForgePlayer` 现已补齐 legacy 风格的自然死亡自动复活语义；`BotLifecycleManager.kill(...)` 会先为 nextgen bot 标记 `disconnected`，避免显式下线被自动复活逻辑反向拉回
- 2026-04-21 迁移推进确认：`GTstaffForgePlayer.attackEntityFrom(...)` 现已绕过 creative `capabilities.disableDamage` 对原版玩家受伤链的二次拦截；在线 nextgen bot 即使保留 creative/飞行能力状态也可以进入真实伤害链，显式 `disconnected` 后仍保持不受伤隔离
- 2026-04-22 兼容修复确认：`GTstaffForgePlayer.syncEquipmentToWatchers()` 现已和 legacy `FakePlayer` 一样，在发送主手/盔甲装备包后继续调用 `BackhandCompat.syncOffhandToWatchers(this)`；nextgen 假人副手槽写入后，观察客户端也能立即看到副手物品显示
- 2026-04-22 审计修复确认：`FakePlayerRegistry.save(...)` 不再只刷新 legacy `fakePlayers` 的快照，而是统一遍历 `onlineRuntimes` 重新拍当前 runtime 状态；nextgen bot 在线期间对 monitor/repel/follow 等运行时服务做出的修改，现在也会被正确持久化并参与后续 restore
- 2026-04-22 兼容修复确认：Backhand 原生 `sendPacketToAllTracking(...)` / `StartTracking` 链路会因 `BackhandUtils.isValidPlayer(...)` 排除 Forge `FakePlayer` 而拒绝 nextgen 假人；GTstaff 现已改为自行构造 `OffhandSyncItemPacket` 并逐玩家反射发送，同时在 `StartTracking` 时为 `GTstaffForgePlayer` 额外补发当前副手状态
- 2026-04-21 迁移推进确认：`FakePlayerRestoreScheduler.isReady(...)` 与 `Entity_KnockbackMixin` 现已统一复用 `ServerUtilitiesCompat.isFakePlayer(...)`，nextgen `GTstaffForgePlayer` 不再在集成服恢复等待或 knockback 标记上被误判成真实玩家
- 2026-04-21 迁移推进确认：`FakeNetHandlerPlayServer` 现已补齐 nextgen `GTstaffForgePlayer` 的 duplicate-login / idle kick 下线链，`BotSession` 挂接的会话 handler 不再只对 legacy 假人执行真正清理
- 2026-04-21 迁移推进确认：`ServerConfigurationManagerMixin` 与 `EntityPlayerMP_RespawnMixin` 现已补齐 nextgen 服务端 respawn 兼容；若外部链路触发 `respawnPlayer(...)`，`GTstaffForgePlayer` 不会再降级成原版 `EntityPlayerMP`，owner 与 monitor/follow/repel 等 runtime 状态也会重新回绑并注册
- 2026-04-21 迁移推进确认：`FakePlayerSkinRestoreScheduler` 与 `BotLifecycleManager.rebuildRestoredWithProfile(...)` 现已 runtime-aware；服务端重启恢复后的 nextgen bot 也会走异步 profile 解析与主线程重建，且 `NextGenBotFactory.rebuildRestoredWithProfile(...)` 会保留 monitor/follow/repel 等运行时状态并替换 registry 中的在线 runtime
- 2026-04-21 迁移推进确认：Wave D 的 runtime-neutral 接线已继续向下落地，`GTstaffForgePlayer` 现已补上 nextgen 动作逐 tick 驱动，`PlayerVisualSync` 也已把挥手/装备同步桥接到 legacy 与 nextgen 两侧
- 2026-04-21 迁移推进确认：`ServerUtilitiesCompat` 已能识别 `GTstaffForgePlayer`，`FakePlayerInventoryView` / `FakePlayerInventoryContainer` / `FakePlayerInventoryGuiHandler` 也已改为承载任意 GTstaff bot 实体，runtime-only nextgen bot 可打开背包容器
- 2026-04-21 迁移推进确认：`PermissionHelper` 与 `CommandPlayer` / `FakePlayerManagerService` 的主要权限链路已改为 runtime-neutral；命令/UI 中残留的 legacy-only helper 与旧 `purge` fallback 现已清理，当前剩余缺口已主要收敛为游戏内人工烟测
- 2026-04-21 迁移推进确认：`NextGenMonitorRuntime` 已不再是纯状态壳，现已持有真实 `MachineMonitorService`，并通过 `GTstaffForgePlayer` 的 runtime services phase 接回逐 tick 监控扫描与提醒；命令/UI 的 `scan` 也已走 `scanNow()`
- 2026-04-21 迁移推进确认：`FollowService` 已从 `FakePlayer` 泛化到 `EntityPlayerMP`，`NextGenFollowRuntime` 现已直接复用 legacy 跟随/跨维度/重试逻辑，nextgen 停止跟随后的移动清理也已不再限定 legacy `FakePlayer`
- 2026-04-21 迁移推进确认：假人背包管理容器现已通过 `FakePlayerInventoryExtraSlot` / `FakePlayerInventoryCompat` 追加可选附加槽，当前以纯反射兼容 Baubles Expanded 饰品栏与 Backhand 副手槽；UI 布局已恢复为固定 256x203 管理界面，Backhand 副手槽位于装备区，Baubles 饰品栏位于右侧独立可滚动面板，普通假人背包与玩家背包位置不会被附加槽下推；Baubles 附加槽会保留 `baublesSlotType` 并在客户端反射复用 Baubles 原生空槽背景图标，盔甲槽也会显示原版空槽图标；同时 `unknownType` 与空类型 Baubles 槽位已在兼容层被直接过滤，不会再显示问号槽位；客户端饰品槽位现在也会反射检查 `IBauble` / `IBaubleExpanded` 与 `slotType` 匹配关系，服务端额外槽写透传会尊重底层 `IInventory.isItemValidForSlot(...)`，`FakePlayerExtraSlot` 也已显式复用附加槽合法性校验，额外槽 Shift-click 不再复用会绕过 `isItemValid(...)` 的原版 `mergeItemStack(...)`

## 已实现内容

### Fake Player 核心
- `Action`：支持 `once`、`continuous`、`interval`
- `FakeNetworkManager`：空壳网络连接，使用 `EmbeddedChannel`
- `FakeNetHandlerPlayServer`：在 idle / duplicate login 文本踢出时回收 fake player
- `FakePlayer`：支持 `createFake`、`createShadow`、owner 绑定、自动重生、自杀清理、机器监控挂接、敌对生物驱逐（`monsterRepelling`/`monsterRepelRange`）、基于持久化快照的 `restorePersisted(...)`，以及恢复后基于带 `textures` profile 的 `rebuildRestoredWithProfile(...)`
- `FakePlayer.asRuntimeView()`：现已把 legacy bot 暴露为 `BotRuntimeView`，供命令/UI/registry 逐步切到运行时无关的只读查询接口
- `FakePlayerProfiles`：集中处理“新生成 fake player 应该使用哪个 `GameProfile`”；当前会优先走 `SkinPortCompat.resolveProfile(name)`，拿到正版皮肤 profile 后复制一份再用于创建假人，失败则回退到离线 UUID profile
- `FakePlayer.runLivingUpdate(...)`：在 `EntityPlayerMP.onUpdate()` 之后直接执行 `onLivingUpdate()`，补上 fake player 的移动/跳跃/碰撞链路，但不再调用 `onUpdateEntity()` 触发第二次 `PlayerTickEvent`
- `FakePlayerRegistry.restorePersisted(...)`：现在会返回本轮实际恢复出的 bot 列表，并保持持久化顺序，供恢复后的补皮调度复用
- `PlayerActionPack`：支持 `USE`、`ATTACK`、`JUMP`、`DROP_ITEM`、`DROP_STACK`，包含 `turn`、`stopMovement`、`setSlot` 与挖掘状态机；Wave B 后其内部动作链已拆分为 `TargetingService`、`UseExecutor`、`AttackExecutor`、`FeedbackSync` 四个执行边界，`USE` 现在会在常规服务端右键链路之后继续尝试“伪客户端物品使用桥”，且 `itemUseCooldown` 会随每个实体 tick 递减，保证 `use continuous` / `use interval <ticks>` 不会在第一次成功后永久卡住；`DROP_ITEM` / `DROP_STACK` 会在 `dropOneItem(...)` 后主动调用 `PlayerVisualSync.syncEquipmentToWatchers()`，避免服务端已丢出物品但客户端仍显示旧手持物；`JUMP` 与“开始潜行”则会继续尝试“移动触发兼容桥”；实体攻击目标选择已补齐 vanilla `getMouseOver()` 的近距离命中逻辑，且 `ATTACK` 在精确射线未命中实体时会额外在假人正脸约 45 度锥形范围内兜底选择最近可碰撞实体，避免从身侧大范围自动索敌；`attack/use` 在没有命中目标时也会执行一次可见空挥手反馈；实体左键会先保留原版 `attackTargetEntityWithCurrentItem(...)` 主链，但若 living 目标没有产生真实掉血或死亡，则直接在服务端强制扣减生命值并设置受击状态，避免伤害回退继续被外部事件链取消

### 客户端效果桥兼容
- `FakePlayerClientUseCompat`：为 fake player 的 `/player <name> use` 提供一层可扩展的“伪客户端物品使用桥”；当前通过纯反射方式按需探测 EnderIO / EnderCore / TST 运行时类，避免 GTstaff 对这些模组形成硬编译或硬运行时依赖
- 当前默认仅注册 `TstYamatoClientUseHandler`：用于兼容 TST `com.Nxer.TwistSpaceTechnology.common.item.ItemYamato`
- `TstYamatoClientUseHandler`：不去伪造整个客户端，而是复用 EnderIO `TravelController` 的目标搜索逻辑，并把原本客户端发出的 `PacketTravelEvent` 改为服务端直接执行 `doServerTeleport(...)`
- 兼容触发策略：仅在假人非潜行、未成功右键激活方块时尝试阎魔刀桥接，避免覆盖真实的方块交互；普通物品与未匹配 handler 的物品仍保持原有 `use` 行为

### 移动触发兼容
- `FakePlayerMovementCompat`：为 fake player 的 `jump` 与“开始潜行”提供一层可扩展的“移动触发兼容桥”；当前通过纯反射方式按需探测 `OpenBlocks` 电梯相关类，避免 GTstaff 对外部模组形成硬依赖
- 当前默认仅注册 `OpenBlocksElevatorHandler`：用于兼容 `OpenBlocks` 电梯方块依赖客户端 `PlayerMovementEvent` / `ElevatorActionEvent` 的上下楼逻辑
- `OpenBlocksElevatorHandler`：服务端按原模组客户端判定方式检查假人脚下电梯方块，再反射调用 `ElevatorActionHandler.activate(...)`，对 `jump` 映射上行、对“开始潜行”映射下行
- 兼容触发策略：`jump` 每次执行时都会尝试一次移动兼容桥；`sneak` 只在从非潜行切到潜行的 leading edge 尝试一次，避免持续潜行时重复触发

### SkinPort 兼容
- `SkinPortCompat`：通过反射可选接入 `lain.mods.skins.impl.MojangService`，但现在只把它用于按名字解析在线 `GameProfile`/UUID，不再直接信任 `SkinPort` 返回的 filled profile
- `SkinPortCompat.resolveProfile(...)`：拿到在线 UUID 后会统一回到服务端 `MinecraftSessionService.fillProfileProperties(profile, true)` 补全带签名的 `textures`；若 `SkinPort` 不可用，还会继续尝试服务端 `PlayerProfileCache` / `GameProfileRepository` 回退解析
- 当前兼容层的成功标准已从“存在 `textures` 属性”提升为“存在带签名的 `textures` 属性”，因为 1.7.10 客户端远程玩家皮肤加载会拒绝 unsigned textures
- `FakePlayerProfiles.resolveSkinProfile(...)`：为恢复补皮路径暴露可复用的拷贝型解析入口，成功时返回复制后的带纹理 profile，避免共享可变属性表
- `FakePlayerSkinRestoreScheduler`：接在 `FakePlayerRestoreScheduler` 之后；对每个恢复出的 bot 后台解析皮肤，成功后回主线程调用 `BotLifecycleManager.rebuildRestoredWithProfile(...)` 做 runtime-aware 安全替换，legacy 与 nextgen 都会走同一调度入口
- 当前皮肤支持范围已覆盖“新生成 fake player”与“服务端重启恢复后的异步补皮重建”；仍不会把 `textures` 持久化进 registry，也不会做无限重试队列

### ServerUtilities 兼容
- `ServerUtilitiesCompat.isFakePlayer(EntityPlayerMP)`：将 GTstaff 的 `FakePlayer` 暴露为可供 ServerUtilities 判断的兼容入口
- `ServerUtils_ServerUtilitiesMixin`：注入 `serverutils.lib.util.ServerUtils.isFake(EntityPlayerMP)` 返回阶段；如果玩家是 GTstaff `FakePlayer`，则强制返回 `true`
- 效果：GTstaff 生成的 legacy / nextgen 假人都不会再被 `ServerUtilities` 的统计、登录记录与相关玩家上下文逻辑计作真实玩家

### 命令
- `CommandPlayer`：支持 `/player list`
- `CommandPlayer`：支持 `/player <name> spawn [at ...] [facing ...] [in ...] [as ...]`
- `CommandPlayer`：支持 `/player <name> kill`、`purge`、`shadow`、`monitor`
- `CommandPlayer`：支持 `/player <name> monitor [on|off|range <radius>|interval <ticks>|scan]`
- `CommandPlayer`：支持 `/player <name> repel [on|off|range <radius>]`
- `CommandPlayer`：支持 `/player <name> inventory [summary|open]`
- `CommandPlayer`：支持动作与控制子命令 `attack`、`use`、`jump`、`drop`、`dropStack`、`move`、`look`、`turn`、`sneak`、`unsneak`、`sprint`、`unsprint`、`mount`、`dismount`、`hotbar`、`stop`、`follow`
- `CommandPlayer`：帮助文本现已显式列出 `stopattack` 与 `stopuse`
- `CommandPlayer.handleList(...)`：当前已改为通过 `FakePlayerRegistry.getAllBotHandles()` + `BotHandle::name` 读取 bot 列表，为后续混合 runtime 查询铺路
- `CommandPlayer` 的 `spawn / shadow / restore / kill / manipulation / monitor / repel / inventory / follow` 已全部改为优先通过 `BotRuntimeView` / `BotLifecycleManager` 路由
- `CommandPlayer.handlePurge(...)` 现已只保留“在线 `BotRuntimeView` 主链 + 离线持久化 bot”两条路径，不再保留依赖 `FakePlayerRegistry.getFakePlayer(...)` 的陈旧 online legacy fallback
- `CommandPlayer` 的 `kill / monitor / repel / inventory / follow / manipulation` 权限链路现已统一走 `PermissionHelper` 的 runtime-neutral owner 校验，nextgen runtime-only bot 不再卡在 legacy `instanceof FakePlayer` 前提
- `CommandPlayer.handleShadow(...)` 与 runtime-only bot 的 `handleKill(...)` 现已走 `BotLifecycleManager`，其中 `handleShadow(...)` 通过 `resolvePlayer(...)` 包装玩家解析，避免再次绑死全局 server 单例
- `CommandPlayer.handleMonitor(...)` 的 `scan` 分支现已统一走 `BotMonitorRuntime.scanNow(...)`，不再只读当前缓存概览
- `CommandPlayer.handleFollow(...)` 的 `stop` 分支现已对任意 runtime bot 实体清空 `moveForward / moveStrafing / jumping`，不再只对 legacy `FakePlayer` 做善后
- `CommandGTstaff`：支持 `/gtstaff ui` 打开 `FakePlayerManagerUI`

### 监控相关
- `MachineMonitorService`：包含 `monitoring`、`monitorRange`、`machineStates`，支持 `tick(FakePlayer)` 与状态 diff 消息生成
- `MachineMonitorService`：现已扩展出 runtime-neutral 的 `tick(String, EntityPlayerMP, UUID)` 与 `scanNow(String, EntityPlayerMP)`，legacy / nextgen 两侧共用同一套 GT 机器扫描、状态 diff 与提醒消息实现
- `MachineMonitorService.scanMachines(...)`：已接入真实 GT 扫描，会遍历附近已加载 `IGregTechTileEntity`，筛选 `MTEMultiBlockBase`，并映射掉电、维护、输出满、结构不完整、污染堵塞、无法修复、缺涡轮、部件错误、发电机不足、资源耗尽、电力不足、电压不足等13种故障状态
- `MachineState`：包含 `active`、`powered`、`maintenanceRequired`、`outputFull`、`structureIncomplete`、`pollutionFail`、`noRepair`、`noTurbine`、`noMachinePart`、`insufficientDynamo`、`outOfResource`、`insufficientPower`、`insufficientVoltage`
- `MachineMonitorService` 支持可配置 `reminderInterval`（默认600 tick=30秒），通过 `setReminderInterval()` 修改
- `MachineMonitorService.buildOverviewLines()` / `buildProblemSummaryLines()` 逐行返回消息，聊天栏每台机器独占一条消息
- `FakePlayer.getChatColor()` / `colorizeName()`：基于名称hash从10种 `EnumChatFormatting` 颜色中分配，聊天用 `ChatStyle.setColor()`，UI 用 `§x` 格式代码
- 监控报告已翻译为中文

### 敌对生物驱逐
- `MonsterRepellentService`：订阅 `MinecraftForge.EVENT_BUS` 的 `CheckSpawn` 事件；现已改为遍历在线 `BotRuntimeView`，按 `runtime.repel()` 状态与 `runtime.entity().asPlayer()` 位置统一阻止敌对生物生成
- `FakePlayer.monsterRepelling`：驱逐开关状态（boolean）
- `FakePlayer.monsterRepelRange`：驱逐范围（int，默认64格），UI 提供 32/64/128/256/400 五档选择
- 假人移动时驱逐范围自动跟随（每次 CheckSpawn 实时检查当前坐标）
- 驱逐状态通过 `FakePlayerRegistry` 持久化到 `data/gtstaff_registry.dat`
- nextgen runtime-only bot 现在也能真实参与敌对刷怪拒绝判定，不再只有 legacy `FakePlayer` 生效

### 假人跟随
- `FollowService`：现已泛化为可挂载在任意 `EntityPlayerMP` bot 实体上的跟随服务；legacy `FakePlayer` 与 nextgen `GTstaffForgePlayer` 共用同一套每 tick 跟随、传送与跨维度迁移逻辑
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
- `FakePlayerRegistry.PersistedBotData`：现已额外持久化 `RuntimeType` 与 `SnapshotVersion`；旧档未带这两个字段时默认回落到 `LEGACY` 与版本 `1`
- `FakePlayerRegistry.getBotHandle(...)` / `getAllBotHandles()`：提供运行时无关的 bot 只读视图；在线 runtime 现优先返回已注册 `BotRuntimeView`，离线持久化 bot 返回仅含快照元数据的 handle
- `FakePlayerRegistry.registerRuntime(...)` / `getRuntimeView(...)`：为 nextgen 或测试注入的在线 runtime-only bot 提供 registry 注册与查询入口
- `fakePlayerRuntimeMode` 现已作为默认 runtime 配置项正式主导 `spawn / shadow / restore / kill` 路由；默认值为 `nextgen`，非法值会通过 `BotRuntimeMode` 归一到 `nextgen`
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
- `FakePlayerManagerService`：负责把 MUI2 表单状态转换为命令参数、bot 列表/概要与背包管理入口；Wave C 后其 bot 列表、inventory 摘要、monitor、repel、follow、inventory manager 打开逻辑已迁到 `BotRuntimeView` facade，当前已支持 `spawn`、`look`、`inventory`、`killBot()`、`purgeBot()`、`listBotNames()`、`defaultSelectedBotName()`、`describeBot()`、`openInventoryManager()`
- `FakePlayerManagerService`：内部未使用的 legacy `findBot(...)` / `findBotOrThrow(...)` helper 已清理，UI 服务层现仅保留 runtime-neutral 的 `findRuntime(...)` / `findRuntimeOrThrow(...)` 查询入口
- `FakePlayerManagerService.scanMachines(...)` 现已调用 `runtime.monitor().scanNow(runtime.name())`，nextgen bot 的监控扫描不再只返回空缓存
- `FakePlayerManagerService.stopFollow(...)` 现已对任意 runtime bot 实体清空移动输入，不再只在 `instanceof FakePlayer` 时生效
- `FakePlayerSpawnWindow`：生成按钮当前不再依赖文本框同步和按钮点击的包顺序；点击时会把 bot 名、坐标、维度、模式打包成单个请求并在服务端解析执行
- `FakePlayerManagerService`：当在线 bot 只有一个时，会为 `LookDraft` 与 `InventoryDraft` 自动预填 bot 名称；inventory 未命中 bot 时会附带当前在线 bot 列表提示
- `FakePlayerManagerService` 支持设置提醒频率 `setReminderInterval()`，`BotDetails` 包含 `reminderInterval` 字段
- `FakePlayerSpawnWindow`：已接入真实表单，可输入 bot 名称、坐标、维度、游戏模式，并通过服务端按钮触发 `/player spawn`
- `FakePlayerLookWindow`：已接入真实交互，可输入 bot 名称，使用方向按钮或 `Look At` 坐标触发 `/player look`
- `FakePlayerInventoryWindow`：保留旧的只读文本快照窗口实现，但已不再是主路径；主管理台的 `Inventory` 页签现在打开原版容器
- `InventorySnapshot`：会区分 hotbar、main inventory、armor，并记录当前选中的 hotbar 槽位；UI 当前使用紧凑文本视图展示完整库存
- `FakePlayerInventoryView`：将 fake player 背包映射为基础 40 槽布局：`0-3` 护甲、`4-12` hotbar、`13-39` 主背包；若运行环境存在兼容模组，会在 40 槽后追加附加槽
- `FakePlayerInventoryExtraSlot`：统一描述假人背包附加槽，当前类型包含 `BAUBLES` 与 `OFFHAND`，支持底层 `IInventory` 写回、客户端同步槽、独立堆叠上限与合法性校验
- `FakePlayerInventoryCompat`：通过纯反射可选接入 Baubles Expanded 与 Backhand；Baubles 使用 `BaublesApi.getBaubles(...)` 返回的 `IInventory`，Backhand 使用 `BackhandUtils` 虚拟出 1 个副手槽并尊重 `Backhand.isOffhandBlacklisted(...)`
- `FakePlayerArmorSlot`：限定护甲槽只接受对应类型护甲
- `FakePlayerInventoryContainer`：负责 fake player 与当前玩家背包的槽位布局、Shift 点击互传、点击 hotbar 槽同步 `currentItem`；当前会按实际 fake slot 数量动态计算玩家背包起点与 GUI 高度，Baubles 合法饰品 Shift-click 优先进饰品槽，普通物品先进入基础背包，基础背包满后才尝试副手槽
- `FakePlayerInventoryContainer`：服务端入参已从 legacy `FakePlayer` 放宽到任意 `EntityPlayerMP` bot 实体，并通过 `PlayerVisualSync` 做装备同步，nextgen bot 也能复用同一套容器
- `FakePlayerInventoryGuiHandler`：通过 Forge `IGuiHandler` 打通 `MUI2 -> openGui -> Container/GuiContainer`；服务端现通过 registry 中的在线 `BotRuntimeView` 反查 `entityId` 对应 bot，不再只扫描 legacy `FakePlayerRegistry.getAll()`
- `FakePlayerInventoryGui`：原版 chest-style `GuiContainer`，会高亮 fake player 当前主手 hotbar 槽位，并按容器动态高度绘制附加槽区域

### Mixin
- `EntityPlayerMPMixin`：为 `FakePlayer` 注入并 tick `PlayerActionPack`
- `EntityPlayerMP_RespawnMixin`：在 respawn 后为 legacy fake player 复制 owner/monitor 注册信息，也会为 nextgen `GTstaffForgePlayer` 重新绑定 runtime 并复制 monitor/follow/repel 状态
- `ServerConfigurationManagerMixin`：在 respawn 路径中为 legacy 假人继续构造 `FakePlayer`，并为 nextgen bot 保留 `GTstaffForgePlayer` 身份而不是回退成原版 `EntityPlayerMP`
- `RespawnMixinHooks`：承载 respawn 相关的可复用静态逻辑与测试钩子，避免 `EntityPlayerMP_RespawnMixin` / `ServerConfigurationManagerMixin` 在 mixin 本体内暴露非私有静态 helper 而触发 `InvalidMixinException`
- `ServerUtils_ServerUtilitiesMixin`：为 `ServerUtilities` 的 `ServerUtils.isFake(...)` 补充 GTstaff `FakePlayer` 识别
- `Entity_KnockbackMixin`：现已通过统一 fake-player 识别 helper 为 legacy / nextgen GTstaff bot 设置 `velocityChanged`
- `EntityPlayerMP_TickFreezeMixin`：保留 fake player tick-freeze 占位绕过

### 权限与配置
- `PermissionHelper.cantSpawn(...)`：校验 bot 名称、在线重名、总 bot 上限、按 owner 数量上限
- `PermissionHelper.cantManipulate(...)`：非 OP 仅可在 `allowNonOpControlOwnBot=true` 时控制自己的 bot
- `PermissionHelper.cantRemove(...)`：非 OP 仅可移除自己的 bot
- `Config`：包含 `fakePlayerPermissionLevel`、`allowNonOpControlOwnBot`、`maxBotsPerPlayer`、`maxBotsTotal`、`defaultMonitorRange`、`fakePlayerRuntimeMode`（默认 `nextgen`）、`fakePlayerActionDiagnostics`（默认 `false`）

### Runtime 抽象
- `fakeplayer.runtime.BotRuntimeType`：当前定义 `LEGACY` 与 `NEXTGEN`
- `fakeplayer.runtime.BotHandle` / `BotRuntimeView` / `BotEntityBridge`：为 bot 名称、owner、维度、runtime 类型和底层实体桥提供统一读模型
- `fakeplayer.runtime.LegacyBotHandle`：对现有 `FakePlayer` 的 legacy 适配层
- `fakeplayer.runtime.GTstaffForgePlayer`：基于 Forge `FakePlayer` 的 nextgen 实体骨架
- `fakeplayer.runtime.GTstaffForgePlayer`：现已补上 `onUpdate()` 驱动，顺序为 `super.onUpdate() -> actionPack.onUpdate() -> onLivingUpdate()`，并实现 `PlayerVisualSync`
- `fakeplayer.runtime.GTstaffForgePlayer`：现已覆盖 Forge `FakePlayer` 默认的 `isEntityInvulnerable()` 与 `canAttackPlayer(...)` 行为；在线 nextgen bot 不再永久无敌，且可作为攻击者通过玩家目标攻击检查，显式下线后仍保持隔离
- `fakeplayer.runtime.BotSession`：nextgen bot 的登录/连接壳，当前复用 `FakeNetworkManager` + `FakeNetHandlerPlayServer`
- `fakeplayer.runtime.NextGenBotRuntime`：nextgen runtime 视图当前已完成 entity/session 绑定，并已接入默认 `spawn / shadow / restore / kill / manipulation` 路由
- Wave C 已落地 runtime 服务抽象：`BotFollowRuntime`、`BotMonitorRuntime`、`BotRepelRuntime`、`BotInventoryRuntime`、`BotInventorySummary`，命令层与 UI 层对 `FakePlayer` 的 monitor/repel/inventory/follow 直连已被 facade 收口
- Wave D 已落地关键抽象 `BotActionRuntime`、`BotRuntimeMode` 与 `BotLifecycleManager`
- 2026-04-21 迁移推进确认：`NextGenRepelRuntime` 已不再只是状态壳，`MonsterRepellentService` 现已读取 runtime facade 并让 nextgen runtime-only bot 真实参与刷怪拒绝
- 2026-04-21 迁移推进确认：`GTstaffForgePlayer` 的 `onUpdate()` 现已包含 runtime services phase；`NextGenFollowRuntime` / `NextGenMonitorRuntime` / `NextGenRepelRuntime` 已分别接回真实 `FollowService`、`MachineMonitorService` 与 `MonsterRepellentService` 对应链路
- 2026-04-21 迁移推进确认：`NextGenInventoryRuntime.summary()` 与 `inventory open` 已都能工作，后者现复用 runtime-neutral 的 `FakePlayerInventoryGuiHandler` / `FakePlayerInventoryContainer`

### 动作链抽象（Wave B）
- `fakeplayer.action.TargetingService` / `TargetingResult`：封装 legacy `getMouseOver()` 风格的方块/实体命中选择，供后续 runtime 共用命中判定边界
- `fakeplayer.action.UseExecutor` / `UseResult`：封装一次 use 指令里的方块交互、物品使用、客户端兼容桥与动作反馈结果
- `fakeplayer.action.AttackExecutor` / `AttackResult`：封装一次 attack 指令里的实体攻击、方块攻击与动作反馈结果
- `fakeplayer.action.FeedbackSync`：统一处理挥手与相关反馈同步，避免反馈逻辑继续散落在 `PlayerActionPack`
- `fakeplayer.action.ActionDiagnostics`：受 `Config.fakePlayerActionDiagnostics` 控制，默认关闭，仅在需要时打印 fake player use/attack 的执行结果摘要

### 测试
- `src/test/java/com/andgatech/gtstaff/fakeplayer/ActionTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerMovementUpdateTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakeNetworkManagerTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreSchedulerTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/MachineMonitorServiceTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPackTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/action/TargetingServiceTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/action/UseExecutorTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/action/AttackExecutorTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/LegacyBotHandleTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/runtime/BotSessionTest.java`
- `src/test/java/com/andgatech/gtstaff/integration/SkinPortCompatTest.java`
- `src/test/java/com/andgatech/gtstaff/integration/FakePlayerClientUseCompatTest.java`
- `src/test/java/com/andgatech/gtstaff/integration/FakePlayerMovementCompatTest.java`
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
- 当前最新 jar：`build/libs/gtstaff-v1.0.2.jar`
- 当前最新 dev jar：`build/libs/gtstaff-v1.0.2-dev.jar`
- 当前最新 sources jar：`build/libs/gtstaff-v1.0.2-sources.jar`
- 最新重新打包 jar：`build/libs/gtstaff-v1.0.2.jar`
- 当前 `v1.0.2` 产物已重新包含 fake player 实体攻击目标修复
- 当前 `v1.0.2` 产物已重新包含 fake player 实体攻击 fallback 修复
- 当前 `v1.0.2` 产物已重新包含 `attack/use` 无目标时的可见空挥手反馈
- 已通过 Wave A 验证命令：`./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest`
- 已通过 Wave B 验证命令：`./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.TargetingServiceTest --tests com.andgatech.gtstaff.fakeplayer.action.UseExecutorTest --tests com.andgatech.gtstaff.fakeplayer.action.AttackExecutorTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest`
- 最新客户端测试主 jar：`build/libs/gtstaff-v0.1.1-master+1f334d4b20-dirty.jar`（含敌对生物驱逐器、其他功能页签）

## 依赖
- JUnit Jupiter 5.10.2（测试）
- GTNHLib：当前已实际参考并接入 `ServerThreadUtil`；`AboveHotbarHUD` / `PacketMessageAboveHotbar` 与 `ConfigSyncHandler` 记录为后续可选参考
- `ServerUtilities`：当前通过可选 mixin 接入其 `ServerUtils.isFake(...)` 判断链，不要求 GTstaff 在无该模组环境下强依赖加载

## 架构备注
- 已完成对 `CustomNPC-Plus-master` 的对照调研；当前未引入其代码，但后续可优先参考其命令分层、可序列化 GUI 状态、分块同步、JSON/NBT 模板持久化与路径导航设计
- `IFakePlayerHolder` 负责把 `PlayerActionPack` 暴露给 `FakePlayer`
- `PlayerActionPack` 直接适配 1.7.10 `ItemInWorldManager`，没有照搬现代 Carpet API
- `PlayerActionPack.getTarget()` 的实体选择当前刻意贴近 vanilla `EntityRenderer.getMouseOver()`：搜索盒使用 `boundingBox.addCoord(lookVec * reach).expand(1,1,1)`，并保留 `isVecInside(...)` 分支，避免假人贴脸攻击时取不到实体
- `PlayerActionPack.performSwingAnimation()` 统一负责假人的挥手可见反馈；即使 `attack/use` 没有真实命中，客户端侧也应至少能看到一次空挥手
- `PlayerActionPack.performEntityAttack(...)` 当前采用“原版先执行、仅在攻击后目标没有任何可观察受击迹象时才补 fallback”的策略，尽量兼容会吞掉 `AttackEntityEvent` / 原版伤害链结果的整合环境，同时避免默认双击
- Wave C 的迁移边界已固定为“业务服务 facade 化，不提前切默认 runtime”：`CommandPlayer.handleManipulation(...)` 与 spawn/shadow/restore 仍保留 legacy 路径，服务子命令与 UI 管理逻辑先切到 runtime facade
- `FakePlayerRegistry.load(...)` 只负责读取持久化快照；实体恢复由 `restorePersisted(...)` 在服务端启动阶段统一执行
- 自动恢复采用“持久化快照 -> `FakePlayerRestoreScheduler` 延后调度 -> `FakePlayer.restorePersisted(...)` / `BotLifecycleManager.restore(...)` -> `FakePlayerSkinRestoreScheduler` 异步补皮 -> 主线程 `BotLifecycleManager.rebuildRestoredWithProfile(...)` runtime-aware 安全替换”的流程，避免在纯 IO 阶段直接创建 Minecraft 实体，同时不阻塞开服
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
- 项目根目录的 `log.md` 统一按 UTF-8 编码维护，并按日期倒序排列，最新记录放在最前
- `FollowService.tick()` 在 `actionPack.onUpdate()` 之后执行，覆盖 actionPack 设置的 moveForward/moveStrafing；跟随优先级高于手动 move 命令
- 跨维度传送通过 `FollowService` 内部的手动迁移流程实现：先从旧世界 `PlayerManager/playerEntities/loadedEntityList` 摘除，再在新世界预加载 chunk 后 `spawnEntityInWorld(...)`，失败时由快照回滚，不走 `transferPlayerToDimension`
- `ServerUtilities` 兼容不去改 `Universe` 等分散统计入口，而是统一挂接在 `ServerUtils.isFake(...)` 的中心判断点，减少后续版本漂移时的漏改风险


