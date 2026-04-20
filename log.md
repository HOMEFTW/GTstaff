# 开发日志

## 2026-04-19：发布 v1.0.1

### 已完成
- 将 `gradle.properties` 中的 `modVersion` 从 `v1.0.0` 更新为 `v1.0.1`
- 重新运行 release 验证测试：`SkinPortCompatTest`、`FakePlayerProfilesTest`、`FakePlayerSkinRestoreSchedulerTest`、`FakePlayerRestoreSchedulerTest`
- 重新运行 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 生成新产物 `build/libs/gtstaff-v1.0.1.jar`、`build/libs/gtstaff-v1.0.1-dev.jar`、`build/libs/gtstaff-v1.0.1-sources.jar`

### 遇到的问题
- **版本源不只影响源码显示**：GTNH 构建会同时把 `modVersion` 注入生成的 `Tags.VERSION` 与最终 jar 文件名，因此发布前必须先统一修改版本源，再重新打包验证

### 做出的决定
- 继续以 `gradle.properties` 的 `modVersion` 作为单一版本来源，避免手工改多个文件造成源码版本与 jar 文件名不一致

---

## 2026-04-20：补充 GTstaff 假人 Baubles 统一背包实现计划

### 已完成
- 基于已确认的设计文档，新增实现计划 `docs/superpowers/plans/2026-04-20-gtstaff-fake-player-baubles-inventory.md`
- 计划中明确拆分了依赖接入、动态饰品区布局计算、统一容器扩展、GUI handler 接线、GUI 滚动与最终验证五个任务
- 结合 `InventoryBaubles`、`SlotBauble` 与 `PlayerHandler` 实现细节，收紧计划约束：客户端与服务端统一直接解析假人的 `InventoryBaubles`

### 遇到的问题
- **`SlotBauble` 依赖 `InventoryBaubles` 具体类型**：它会直接把传入库存强转为 `InventoryBaubles`，不能在计划里再假设“任意 `IInventory` 占位库存”可用

### 做出的决定
- 在实现计划里显式加入 `InventoryBaubles` 解析和容器 helper API，避免执行阶段再临时改接口
- 本轮只完成计划与日志更新，不进入代码实现

---

## 2026-04-19：重新打包当前 GTstaff jar

### 已完成
- 运行 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 成功生成 `build/libs/gtstaff-v1.0.0.jar`
- 同时生成 `build/libs/gtstaff-v1.0.0-dev.jar` 与 `build/libs/gtstaff-v1.0.0-sources.jar`

### 遇到的问题
- **Gradle wrapper 仍需要访问用户级缓存目录**：本次仍通过允许访问本地 Gradle cache 的提权命令完成打包

### 做出的决定
- 继续沿用 `assemble` 作为当前分支的标准打包入口，不额外引入新的发布脚本

---

## 2026-04-19：重启恢复假人后异步补皮并安全重建

### 已完成
- `FakePlayerRegistry.restorePersisted(...)` 现在会返回本轮恢复出的 bot 列表，并保留持久化顺序，供恢复后续流程继续调度
- 新增 `FakePlayerSkinRestoreScheduler`，对每个恢复出的 bot 后台调用 `FakePlayerProfiles.resolveSkinProfile(name)`；成功后切回主线程执行 `FakePlayer.rebuildRestoredWithProfile(...)`
- `FakePlayer` 新增恢复态快照与 `rebuildRestoredWithProfile(...)`；重建后会保留 owner、监控开关、监控范围、提醒频率、敌对生物驱逐、跟随目标/距离、维度、坐标、朝向、飞行状态与游戏模式
- `FakePlayerRestoreScheduler` 现在会把恢复出的 bot 批量交给 `FakePlayerSkinRestoreScheduler`；`CommonProxy.serverStopping(...)` 会取消未完成的补皮任务，避免跨停服残留
- 新增 `FakePlayerSkinRestoreSchedulerTest`，并扩展 `FakePlayerRegistryTest`、`FakePlayerRestoreSchedulerTest`、`FakePlayerProfilesTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest`

### 遇到的问题
- **恢复链路之前只会构造离线 `GameProfile`**：重启恢复出的 bot 无法复用已有的 `SkinPort` 解析逻辑，需要把“先恢复实体”和“后补皮重建”拆成两段
- **异步补皮结果可能回到过期实例**：后台解析完成时，同名 bot 可能已经被手动 kill、purge 或重新替换，必须在主线程重建前再次确认 registry 仍指向原实例

### 做出的决定
- 不在原实体上热改 `GameProfile`，而是在补皮成功后重建实体，换取客户端皮肤可见性的确定性
- 补皮解析固定放在后台单线程执行，实体替换固定回主线程；停服时用 generation 失效旧任务结果，而不是尝试强杀正在运行的网络解析

---

## 2026-04-19：补齐缺失的假人命令入口

### 已完成
- 为 `CommandPlayer` 新增 `repel` 与 `inventory` 子命令，并扩展现有 `monitor` 子命令支持 `scan` 与 `interval <ticks>`
- `repel` 命令现已覆盖敌对生物驱逐的开关与范围设置，`inventory` 命令现已支持 `summary` 与 `open`
- `monitor` 无参状态输出现在会显示提醒频率，`monitor scan` 会输出当前机器概览，`monitor interval` 会更新提醒 tick 间隔
- 更新 `/player` 用法字符串，把 `stopattack`、`stopuse`、`repel`、`inventory` 一并写入帮助文本
- 扩展 `CommandPlayerTest`，覆盖新命令路由、用法字符串、`monitor interval` 状态更新与 `repel` 状态更新
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

### 遇到的问题
- **已有功能分散在 UI service 与命令层之间**：敌对生物驱逐、提醒频率和背包管理原本只有 UI 入口，需要回到 `CommandPlayer` 补齐统一的命令分发

### 做出的决定
- 命令语义尽量贴合现有 UI：`repel [on|off|range <n>]`、`monitor [scan|interval <ticks>]`、`inventory [summary|open]`
- 不额外新增新的 service 层抽象，优先在 `CommandPlayer` 中补最小命令入口，避免把本次改动扩散到更多类

## 2026-04-19：扩展完全清除以删除 tf 与 tfback

### 已完成
- 扩展 `PlayerDataCleanup.PLAYERDATA_NAME_EXTENSIONS`，让“完全清除”额外删除 `playerdata/<假人名>.tf` 与 `playerdata/<假人名>.tfback`
- 扩展 `PlayerDataCleanupTest`，覆盖 `.tf/.tfback` 会被删除、其他 bot 的同类文件会保留、`playerdata/<假人名>.dat` 仍不会被误删
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.util.PlayerDataCleanupTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

### 遇到的问题
- **现有回归只覆盖 baub/thaum 系列**：先补红灯测试后，失败点准确落在新增 `.tf/.tfback` 尚未纳入删除列表

### 做出的决定
- 继续沿用“按假人名扩展名白名单删除”的策略，只把 `.tf/.tfback` 加进 `playerdata` 名称型文件列表，不改变 UUID `.dat` 和名字 `.dat` 的现有规则

## 2026-04-19：接入 ServerUtilities 假人统计排除兼容

### 已完成
- 阅读 `ServerUtilities-master` 的 `serverutils.lib.util.ServerUtils` 与 `serverutils.lib.data.Universe`，确认统计、登录记录与玩家上下文都统一依赖 `ServerUtils.isFake(EntityPlayerMP)`
- 新增 `ServerUtilitiesCompat.isFakePlayer(...)`，集中定义 GTstaff 假人是否应被 ServerUtilities 视为 fake player
- 新增 `ServerUtils_ServerUtilitiesMixin`，在 `ServerUtils.isFake(...)` 返回阶段补充 `com.andgatech.gtstaff.fakeplayer.FakePlayer` 判断，避免 GTstaff 生成的假人被计入 ServerUtilities 统计
- 更新 `mixins.gtstaff.json` 注册 `ServerUtils_ServerUtilitiesMixin`
- 新增 `ServerUtilitiesCompatTest`，覆盖“GTstaff 假人会被兼容判断识别”为 true、“真实玩家为 false”以及 mixin 已注册到 `mixins.gtstaff.json`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.ServerUtilitiesCompatTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

### 遇到的问题
- **GTstaff 假人不满足 ServerUtilities 原生 fake 判定**：当前假人既不是 Forge `FakePlayer`，`playerNetServerHandler` 也不为 `null`，因此会被 `ServerUtils.isFake(...)` 当作真实玩家
- **单测环境不会真实应用 Mixin**：测试改为拆成两层，分别验证兼容 helper 的行为和 `mixins.gtstaff.json` 的注册，保证兼容逻辑与装配都能回归

### 做出的决定
- 不去零散修改 `Universe` 中的统计入口，而是直接接入 `ServerUtils.isFake(...)` 这一处中心判断，降低漏改和后续维护成本
- 使用 `@Pseudo` + `targets = "serverutils.lib.util.ServerUtils"` 的可选 mixin 方案，保持没有 ServerUtilities 时也能安全编译和启动

## 2026-04-19：新增完全清除假人功能

### 已完成
- 在 `FakePlayerManagerUI` 概览页新增“完全清除”按钮，服务端点击后会调用 `FakePlayerManagerService.purgeBot(...)`
- 在 `CommandPlayer` 新增 `/player <name> purge` 子命令，支持清除在线 fake player，也支持仅存在于持久化 registry 的离线 bot
- `FakePlayerRegistry` 新增 `contains(name)` 与 `saveServerRegistry(server)`，用于判断离线持久化 bot 是否存在，并在 purge 后立即写回 `data/gtstaff_registry.dat`
- `PlayerDataCleanup` 会继续清理 GTNH 存档中的 `playerdata`、`serverutilities/players` 与 `stats`：`playerdata` 删除 `<uuid>.dat` 和 `<name>.baub/.baubback/.thaum/.thaumback`，`serverutilities/players` 删除 `<name>.dat`，`stats` 删除 `<uuid>.*`
- `CommandPlayer.resolveSaveRoot(...)` 改为优先使用 overworld 的 `getChunkSaveLocation()`，其次回退到 `DimensionManager.getCurrentSaveRootDirectory()`，确保单人/集成服中的文件清理发生在当前世界存档目录而不是服务器工作目录
- `CommandPlayer.getSaveRootsForCleanup(...)` 会在 purge 时同时尝试世界存档根和 `server.getFile(...)` 推导出的工作根，修复“`playerdata` 成功删除，但 `serverutilities/players` 与 `stats` 仍残留”的场景
- 补充 `CommandPlayerTest`、`FakePlayerManagerServiceTest`、`FakePlayerRegistryTest` 回归用例，覆盖命令分发、UI service 参数构建与持久化存在性判断
- 新增 `PlayerDataCleanupTest`，覆盖 `playerdata/<uuid>.dat`、`playerdata/<name>.baub/.baubback/.thaum/.thaumback`、`serverutilities/players/<name>.dat` 与 `stats/<uuid>.*` 的删除场景
- 新增 `CommandPlayerTest.resolveSaveRootPrefersCurrentWorldSaveDirectory()`，覆盖“优先取当前世界存档根目录”的路径回归场景
- 新增 `CommandPlayerTest.cleanupRootsIncludeResolvedRootAndFallbackRootWhenDifferent()`，覆盖“清理时同时尝试世界根目录与工作根目录”的路径回归场景
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

### 遇到的问题
- **Gradle 沙箱下无法创建 wrapper lock 文件**：测试首次运行被卡在用户缓存目录，随后改为使用允许访问本地 Gradle 缓存的提权命令执行验证
- **离线 purge 需要区分“无 owner 的 bot”与“不存在的 bot”**：仅靠 `getOwnerUUID(name)` 无法判断两者，补上 `FakePlayerRegistry.contains(name)` 作为存在性判断
- **最初 purge 没有删到真实存档文件**：根因是使用 `server.getFile("playerdata")` 推导目录时拿到的是服务器工作目录而非当前世界 save root，导致命中错误路径
- **集成服映射环境下 world save API 名称不一致**：直接写 `func_72860_G()` 在当前工作区无法通过编译，最终改为复用 `WorldServer.getChunkSaveLocation()` 作为兼容的真实存档根目录来源
- **单人环境下不同数据目录可能不在同一根路径**：现场反馈 `playerdata` 能删但 `serverutilities/players`、`stats` 不能删，说明仅锁定一个 save root 仍不够，需额外覆盖服务端工作根

### 做出的决定
- `/player <name> purge` 语义定义为硬删除：在线时先走安全下线/移除，再清 registry，并立即保存持久化文件
- GTNH 存档中的 `playerdata` 与 `serverutilities/players` 视为固定存在路径，清理逻辑直接按这两个目录执行，不再额外做模组存在性判断
- `stats` 也按 GTNH 存档固定路径处理，且只按 UUID basename 删除，避免误删名字同名的其他统计文件
- purge 的存档根目录解析优先使用 overworld `getChunkSaveLocation()`，再回退到 Forge 当前 save root，最后才使用 `server.getFile(...)` 兜底
- purge 实际执行文件删除时不再只依赖单一路径，而是对“解析出的世界存档根”和“服务端工作根”都尝试一次并去重
- UI 仅在概览页补“完全清除”按钮，不改变现有 bot 列表的数据来源与刷新策略，尽量把改动控制在最小范围

## 2026-04-19：假人跟随玩家功能

### 已完成
- 新增 `FollowService`，挂载在 `FakePlayer` 上，每 tick 在 `actionPack.onUpdate()` 之后、`runLivingUpdate()` 之前执行
- 方向计算：`calculateMovement(fakeYaw, fromX, fromZ, toX, toZ)` 通过 yaw 差的 cos/sin 分量转换为 moveForward / moveStrafing
- Y 轴控制：飞行时直接设置 `motionY`（上升 +0.2、下降 -0.2），接近目标高度时阻尼减速；地面时使用 `setJumping`
- 超距传送：距离 > teleportRange 时传送到玩家背后 2 格
- 跨维度传送：维度不同时等待 100 tick（5 秒），聊天栏通知玩家，计时结束后手动执行维度转移（从服务端玩家列表移除→旧世界移除→重置 isDead→新世界生成→重新加入玩家列表）
- 飞行同步：跟随时自动将 `fakePlayer.capabilities.isFlying` 同步为目标玩家的飞行状态
- 命令：`/player <name> follow [player|stop|range <n>|tprange <n>]`
- UI：Other 页签上半区敌对生物驱逐器（开关+范围按钮），下半区假人跟随（跟随我/停止跟随+跟随距离+传送距离按钮），底栏状态文本
- 持久化：followTarget（UUID）、followRange、teleportRange 写入 `data/gtstaff_registry.dat`；重启后自动恢复跟随
- 13 个单元测试覆盖方向计算、Y 轴控制、默认参数
- 通过 `./gradlew.bat --offline test` + `./gradlew.bat --offline assemble` 端到端验证

### 遇到的问题
- **飞行跟随时假人只上升一格**：`setJumping(true)` 在飞行模式下不产生上升力，改为直接设置 `motionY += 0.2`
- **UI 跟随控件挤占驱逐器空间**：原左右两列布局重叠，改为上下分区+分隔线
- **`Unsafe.allocateInstance` 创建的测试 Stub 没有 `followService`**：`isFollowing()` 加 null 保护，`snapshot()` 加 null 安全读取
- **跨维度传送失败（vanilla `transferPlayerToDimension`）**：vanilla 方法可能在内部设置 dimension 后失败，导致维度记录与实际世界不一致，改为手动控制每一步
- **连续换维度时倒计时混乱**：玩家快速切换维度会重置倒计时并重新发消息，传送前再次检查维度是否仍不同

### 做出的决定
- 跟随优先级高于手动 move 命令（FollowService.tick() 在 actionPack.onUpdate() 之后执行，覆盖移动值）
- 跨维度传送不走 `transferPlayerToDimension`，手动控制完整生命周期避免半失败状态
- 停止跟随时清零 moveForward/moveStrafing/setJumping，恢复手动控制

---

## 2026-04-19：新增 SkinPort 轻量皮肤兼容

### 已完成
- 阅读 `SkinPort-master` 源码，确认其客户端最终依赖玩家 `GameProfile` 中的完整皮肤资料，而不是 GTstaff 侧专门改渲染
- 新增 `SkinPortCompat`，通过反射可选调用 `lain.mods.skins.impl.MojangService.getProfile(String)` 与 `fillProfile(GameProfile)`，在安装 `SkinPort` 时按 bot 名解析正版皮肤资料
- 新增 `FakePlayerProfiles`，把“新生成 fake player 用哪个 `GameProfile`”从 `FakePlayer.createFake(...)` 中抽离；优先使用带 `textures` 的正版 profile，失败时回退到现有离线 UUID profile
- 为避免共享可变状态，`FakePlayerProfiles` 在使用解析到的 profile 前会复制一份 `GameProfile` 和属性表，再交给假人创建链路
- 新增 `SkinPortCompatTest` 与 `FakePlayerProfilesTest`，覆盖未安装/返回空/无纹理/bridge 异常/反射 future/中断回退，以及“使用正版 profile”与“回退离线 profile”两条生成路径
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest`

### 遇到的问题
- `Gradle` 在沙箱里首次运行会因为无法写用户目录下的 wrapper/cache lock 而失败，后续统一改为使用允许访问本地 Gradle 缓存的提权命令做验证
- `SkinPort` 不是 GTNH 默认模组，必须避免任何硬依赖类加载；因此兼容层不能直接 import 它的实现类，只能通过反射软连接
- 如果直接复用 compat 返回的 `GameProfile` 引用，后续一旦接入缓存或重复复用，同一份属性表可能在不同 fake player 生成之间共享并互相污染

### 做出的决定
- 本轮只做“新生成假人按名字取正版皮肤”，明确不改 `restorePersisted(...)` 的重启恢复链路
- `SkinPortCompat` 的失败语义统一为静默回退到 `Optional.empty()`，包括未安装、反射失败、future 异常、中断和无 `textures` 资料
- `FakePlayer.createFake(...)` 只通过 `FakePlayerProfiles` 选择 profile，不把 `SkinPort` 兼容细节散落进实体创建代码

## 2026-04-18：敌对生物驱逐器功能

### 已完成
- 新建 `MonsterRepellentService`，订阅 `CheckSpawn` 事件，在假人球形范围内阻止敌对生物生成
- `FakePlayer` 新增 `monsterRepelling` / `monsterRepelRange` 字段及 getter/setter
- UI 新增"其他"页签（第5个 tab），含驱逐开关按钮和 32/64/128/256/400 格范围选择
- `FakePlayerRegistry.PersistedBotData` 扩展驱逐字段，save/load/snapshot/restore 全链路持久化
- `FakePlayerManagerService.BotDetails` 扩展驱逐字段，新增 `toggleMonsterRepel()` / `setMonsterRepelRange()`
- `CommonProxy.init()` 中注册 `MonsterRepellentService` 到 `MinecraftForge.EVENT_BUS`
- 编译通过、测试通过、打包 jar 成功
- 推送 GitHub 并打 tag `v0.1.1`

### 做出的决定
- 不修改 GT5-Unofficial 的 `GTSpawnEventHandler`，而是在 GTstaff 内自建事件处理器，避免跨项目耦合
- 假人移动时驱逐范围自动跟随（每次 CheckSpawn 实时检查假人当前坐标）

---

## 2026-04-18：GT机器监控增强 + 假人颜色 + 中文翻译 + 提醒频率

### 已完成
- 扩展 `MachineState` 新增8个故障字段（pollutionFail/noRepair/noTurbine/noMachinePart/insufficientDynamo/outOfResource/insufficientPower/insufficientVoltage），`hasProblems()` 覆盖全部13种故障
- 重写 `MachineMonitorService.createStateFromGregTech()`，接入 `ShutDownReasonRegistry` 全量检测（POWER_LOSS/POLLUTION_FAIL/STRUCTURE_INCOMPLETE/NO_REPAIR/NO_TURBINE/NO_MACHINE_PART/INSUFFICIENT_DYNAMO/out_of_fluid/out_of_stuff）和 `CheckRecipeResult.getID()` 检测（insufficient_power/insufficient_voltage）
- 新增 `matchesReason()` 使用引用比较+`getKey()`反射兜底，`isOutOfResource()` 检测 out_of_fluid/out_of_stuff，`matchesRecipeResult()` 检测 result ID
- 将所有监控报告翻译为中文（断电/需要维护/输出已满/结构不完整/污染排放失败/无法修复/缺少涡轮/机器部件错误/发电机不足/资源耗尽/电力不足/电压不足/已恢复正常）
- 为每个假人分配颜色（`FakePlayer.getChatColor()`/`colorizeName()`），基于名称hash从10种 `EnumChatFormatting` 中选取，聊天消息通过 `ChatStyle.setColor()` 着色
- UI中机器人列表和总览页面通过 `§x` 格式代码显示假人名称颜色
- 监控页面改用 `ListWidget+VerticalScrollData` 可滚动列表替换固定大小 TextWidget
- 新增可配置 `reminderInterval`（默认600 tick=30秒），UI添加4个频率按钮（10秒/30秒/1分钟/5分钟），当前选中频率显示 `[标签]` 高亮
- `PersistedBotData` 新增 `reminderInterval` 字段，支持 NBT 持久化保存和恢复
- 概览和问题提醒改为逐行发送（`buildOverviewLines()`/`buildProblemSummaryLines()`），聊天栏每台机器独占一条消息
- 新增7个测试覆盖新故障类型检测、中文断言、可配置提醒频率
- 通过 `./gradlew spotlessApply test build` 全部通过

### 做出的决定
- 跨 classloader 的 `ShutDownReason` 比较同时使用 `==` 引用比较和 `getKey()` 反射兜底
- 默认提醒频率设为30秒（600 tick），最小允许60 tick（3秒）
- 扫描按钮用于UI中手动刷新机器状态文本，不等待自动扫描周期
- 聊天消息逐行发送而非合并为一条，确保每台机器清晰可读

---

## 2026-04-18：改为直接运行 living update，绕开第二次 PlayerTickEvent

### 已完成
- 确认 `FakePlayer` 真正触发 `ae2fc` 崩溃的根因是手动调用 `EntityPlayerMP.onUpdateEntity()`，这会再次进入 `EntityPlayer.onUpdate()` 并额外发布一轮 `PlayerTickEvent`
- 将 `FakePlayer.onUpdate()` 的第二段更新从 `onUpdateEntity()` 改为直接执行 `onLivingUpdate()`，保留 fake player 的移动/跳跃/碰撞更新，但不再触发第二次玩家 tick 事件
- 删除仅靠识别 `ae2fc` 堆栈后吞 NPE 的旧兼容路径，避免继续在异常白名单上兜圈子
- 新增 `FakePlayerMovementUpdateTest`，验证新的 movement hook 会执行且不会静默吞掉异常
- 通过 `./gradlew.bat --offline test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerMovementUpdateTest`
- 通过 `./gradlew.bat --offline assemble` 重新打包 jar

### 做出的决定
- 优先修根因，不再依赖“识别外部模组 NPE 后吞掉”的脆弱兼容策略
- fake player 的“第二段 tick”职责收敛为 living movement update，而不是整段 `onUpdateEntity()`

---

## 2026-04-18：去掉已抑制 AE2FluidCraft 兼容异常的整段堆栈输出

### 已完成
- 确认 `FakePlayer.runEntityUpdateSafely(...)` 中的 `java.lang.NullPointerException` 堆栈来自 `GTstaff.LOG.warn(..., exception)` 的主动日志输出，而不是新的未捕获崩溃
- 将兼容日志改为只输出一行简短警告，不再附带被抑制异常的完整堆栈
- 重新执行 `./gradlew.bat --offline assemble` 打包新的 jar

### 做出的决定
- 保留一次性告警，方便确认兼容层确实生效
- 不再把已知外部模组兼容异常打印成完整堆栈，减少误判和日志噪音

---

## 2026-04-18：重新构建包含 fake player 兼容修复的 jar

### 已完成
- 使用 `./gradlew.bat --offline assemble` 成功重新构建 GTstaff 产物
- 确认最新修复已打包进 `build/libs/gtstaff-b166ed7-master+b166ed77c1-dirty.jar`

### 做出的决定
- 当前继续使用离线 `assemble` 作为快速打包路径，不额外触发 `spotlessJavaCheck`

---

## 2026-04-18：收紧 fake player 对 AE2FluidCraft tick NPE 的兼容处理

### 已完成
- 确认集成服崩溃根因是 `FakePlayer.onUpdateEntity()` 触发 `PlayerTickEvent` 后，`ae2fc` 的 `ClientProxy.tickEvent(...) -> Util.getUltraWirelessTerm(...)` 对 fake player 场景做了空指针假设
- 将 `FakePlayer` 对 `onUpdateEntity()` 的兼容层从“吞掉所有 `NullPointerException`”收紧为“只忽略已知的 `AE2FluidCraft` fake-player tick NPE，其他 NPE 继续抛出”
- 新增 `FakePlayerCompatibilityTest`，覆盖“已知 `ae2fc` NPE 被抑制”“无关 `NullPointerException` 不被吞掉”“非 NPE 异常继续抛出”三条回归场景
- 通过 `./gradlew.bat --offline test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerCompatibilityTest` 验证修复

### 做出的决定
- 继续在 GTstaff 侧保留兼容性兜底，不要求先修改 `ae2fc`
- 不再使用过宽的 `catch (NullPointerException)`，避免把 GTstaff 自身或其他真实缺陷静默吞掉

---

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
## 2026-04-19：修复假人跟随连续跨维度传送卡在错误维度
### 已完成
- 重新阅读 `ToDOLIST.md`、`log.md`、`context.md`，确认连续跨维度传送时的核心问题在 `FollowService.executeCrossDimensionTeleport(...)`
- 修复 `FollowService.handleCrossDimension(...)` 的服务端参数传递，避免跨维度倒计时结束时拿不到当前 `MinecraftServer`
- 将默认跨维度迁移改为更接近原版/GT5U 玩家迁移的流程：从旧世界 `PlayerManager`、`playerEntities`、chunk 实体列表和 `loadedEntityList` 正确摘除，再在目标世界预加载 chunk 后重新挂接
- 为跨维度迁移增加失败回滚快照；如果目标世界挂接失败，会恢复假人的原始 `dimension`、`worldObj`、坐标和飞行状态，避免后续 tick 因字段已污染而停止重试
- 扩展 `FollowServiceTest`，新增“失败后回滚维度状态”和“失败后下一个倒计时周期继续重试”两个回归场景
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`

### 遇到的问题
- **原实现过早写入 `fakePlayer.dimension` / `worldObj`**：一旦新世界 `spawnEntityInWorld(...)` 失败，后续 `tick()` 会误判假人已经在目标维度，不再继续跨维度传送
- **测试夹具被 `Unsafe` 空初始化干扰**：新增回滚测试最初因 `boundingBox` 为空在 `setLocationAndAngles(...)` 处崩溃，后续在测试里补齐最小实体状态
- **GTNH Gradle 插件在测试阶段会重新做 manifest 检查**：本次验证命令需要显式加上 `DISABLE_BUILDSCRIPT_UPDATE_CHECK`、`autoUpdateBuildScript=false` 和 `disableSpotless=true`

### 做出的决定
- 不直接调用 `transferPlayerToDimension(...)`，而是在 `FollowService` 内保留 fake player 专用迁移逻辑，但补齐旧世界摘除、新世界挂接和失败回滚
- 失败保护采用双层兜底：迁移逻辑内部尽量恢复旧世界挂接，`executeCrossDimensionTeleport(...)` 外层再用快照恢复关键字段，避免再次出现“维度字段已变更但实体未真正迁移”的半状态

---

## 2026-04-19：修复玩家重启后假人跟随状态被提前清空
### 已完成
- 定位到 `FollowService.tick()` 在目标玩家临时离线或重连过程实体失效时会直接 `stop()`，导致 `followTargetUUID` 在玩家重新上线前就被清空
- 调整 `FollowService`：目标玩家暂时不存在或 `isDead` 时仅重置跨维度倒计时状态，不再清除跟随目标 UUID
- 新增 `FollowServiceTest` 回归用例，覆盖“目标暂时不在线时保留跟随状态”与“目标重连后处于另一维度时能重新进入跨维度跟随”两个场景
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`

### 做出的决定
- 将“目标玩家临时离线”视为可恢复状态而不是明确停止条件，这样客户端重启、服务端重启后的玩家重连都能继续沿用原有跟随关系
- 玩家临时离线期间只清理跨维度倒计时，不保留旧倒计时进度，避免重连后立刻沿用过期计时器

---

## 2026-04-19：修复同 UUID 旧死实体阻塞后续跨维度跟随
### 已完成
- 定位到 `FollowService.findTargetPlayer(...)` 之前会返回 `playerEntityList` 里第一个 UUID 匹配的实体，即使它是旧的 `isDead=true` 残留对象
- 调整目标查找逻辑：优先返回同 UUID 的存活玩家实体，只有不存在存活实体时才回退到旧死实体
- 新增 `FollowServiceTest` 回归用例，覆盖“同 UUID 的旧死实体存在时，跟随逻辑仍会选中新的存活实体并继续跨维度传送”
- 重新通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`

### 做出的决定
- `findTargetPlayer(...)` 不再使用“遇到第一个 UUID 匹配对象就直接返回”的策略，避免玩家重连、切维或实体替换期间被过期对象卡死

---

## 2026-04-19：修复 UI 生成假人按钮失效
### 已完成
- 排查 `FakePlayerSpawnWindow` 提交链路，确认旧实现依赖“文本框同步包先于按钮点击包到达服务端”，而生成窗口的 bot 名默认为空，这会让服务端经常拿到空白 bot 名并直接拒绝生成
- 将生成按钮改为“一次请求提交整张表单”的模式：客户端点击时直接采集当前输入框内容，打包成单个 `gtstaffSpawnRequest` 同步值，由服务端统一解析并执行 `submitSpawn(...)`
- 在 `FakePlayerManagerService` 新增原始 UI 字段入口，服务端会直接解析 `botName/x/y/z/dimension/gameMode` 字符串并复用现有 `/player ... spawn ...` 命令路径
- 顺手修复 `FakePlayerManagerUI` 中一个遗留的链式调用语法错误，避免它阻塞本次编译验证
- 新增 `FakePlayerManagerServiceTest` 回归用例，覆盖“原始 UI 字段构建 spawn 命令参数”和“非法 X 坐标会被拦截”两个场景
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava test --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

### 做出的决定
- 生成窗口不再复用 `InteractionSyncHandler` 直接在服务端读取 `draft.copy()`，而是显式提交一份表单快照，避免 bot 名等焦点中字段因为同步竞态变成空值
- 保留 `FakePlayerManagerService.submitSpawn(SpawnDraft)` 作为原有服务接口，同时增加字符串入口，尽量把改动收敛在 UI 提交层

---
## 2026-04-19：修复假人皮肤仍回退默认 Steve/Alex

### 已完成
- 调整 `SkinPortCompat`：不再直接使用 `SkinPort` 的 `MojangService.fillProfile(...)` 结果生成假人皮肤，而是仅将其作为“按名字解析在线 `GameProfile`/UUID”的可选桥接
- 新增 secure fill 路径：在拿到在线 UUID 后统一回到服务端 `MinecraftSessionService.fillProfileProperties(profile, true)` 补全带签名的 `textures`，避免客户端 `getTextures(profile, true)` 因无签名而拒绝加载
- 为未安装 `SkinPort` 的情况补上服务端 profile cache/repository 回退；在线模式下即使没有 `SkinPort` 也能尝试解析正版 UUID，再决定是否补全皮肤
- 更新 `SkinPortCompatTest`，覆盖“secure filler 生效”“unsigned textures 被拒绝”“bridge 不可用时 fallback resolver 生效”“反射 bridge 只解析 base profile”四条关键回归
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.integration.SkinPortCompatTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FakePlayerProfilesTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerSkinRestoreSchedulerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRestoreSchedulerTest`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true compileJava`
- 通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`

### 遇到的问题
- **`SkinPort` 服务端补出来的是 unsigned textures**：阅读 `authlib` 与 `SkinPort` 源码后确认，`MojangService.fillProfile(...)` 内部走的是 `fillProfileProperties(profile, false)`；而 1.7.10 客户端远程玩家首次取皮肤使用 `getTextures(profile, true)`，会直接拒绝无签名纹理
- **客户端被拒绝后只会回到默认皮**：`SkinPort` 客户端当前注册的只是默认 Steve/Alex provider，没有继续替 GTstaff 假人下载真实远程皮肤，所以服务端必须提供可被 vanilla 安全校验接受的 signed `textures`

### 做出的决定
- 保留 `SkinPort` 作为“可选在线 UUID 解析器”，但不再信任它返回的 filled profile 可直接用于客户端皮肤加载
- 皮肤链路的成功标准从“有 `textures` 属性”提升为“有带签名的 `textures` 属性”，避免再次把客户端一定会拒绝的 unsigned profile 送进生成/恢复流程
# 2026-04-20：GTstaff 假人统一背包接入 Baubles Expanded 设计

### 已完成
- 重新阅读 `GTstaff` 现有假人背包管理链路与 `Baubles-Expanded-master` 的 `BaublesApi`、`ContainerPlayerExpanded`、`SlotBauble` 相关实现
- 与用户确认本次需求边界：`Baubles Expanded` 作为硬依赖、饰品栏并入现有统一背包容器、槽位数量与类型完全跟随当前配置
- 新增设计文档 `docs/superpowers/specs/2026-04-20-gtstaff-fake-player-baubles-inventory-design.md`
- 更新 `ToDOLIST.md`，把“为 GTstaff 假人统一背包接入 Baubles Expanded 饰品栏支持”登记为当前计划

### 遇到的问题
- **`ContainerPlayerExpanded` 默认以当前真人玩家为宿主**：其内部同时组合 crafting、玩家盔甲栏、玩家主背包和玩家自己的 baubles 库存，不适合直接复用为假人统一背包

### 做出的决定
- 采用“保留 GTstaff 现有打开链路，扩展 `FakePlayerInventoryContainer` / `FakePlayerInventoryGui`，底层直接接入假人的 Baubles 库存与槽位规则”的方案
- 本轮先停在设计与规格确认，不提前进入实现阶段

---
## 2026-04-20：完成 Backhand 副手槽设计

### 已完成
- 阅读 `GTNH LIB/Backhand-master` 中的 `BackhandUtils`、`BackhandSlot`、`IOffhandInventory` 与 `MixinGuiInventory`
- 确认 Backhand 的真实副手库存入口应通过 `BackhandUtils.getOffhandItem/setPlayerOffhandItem/getOffhandSlot` 访问，而不是把副手当作普通 `mainInventory` 槽位硬编码
- 新增设计文档 `docs/superpowers/specs/2026-04-20-gtstaff-fake-player-backhand-offhand-design.md`

### 遇到的问题
- **副手不是普通背包尾部槽位**：Backhand 通过 `IOffhandInventory` mixin 扩展 `InventoryPlayer`，如果只在 GTstaff 容器里临时挂一个代理槽，后续客户端同步和 Shift 点击优先级会变得很脆弱

### 做出的决定
- 采用“正式扩展 `FakePlayerInventoryView` 槽位模型”的方案，把副手槽并入统一背包固定索引
- 副手物品合法性直接复用 `BackhandSlot`，不在 GTstaff 内部重新实现黑名单逻辑
- Shift 点击优先级固定为“护甲 > 副手 > 饰品 > 普通背包”

---
# 2026-04-20：完成 GTstaff 假人统一背包 Backhand 副手接入

### 已完成
- 在统一假人背包中加入真实副手槽：`FakePlayerInventoryView` 现为 41 槽布局，顺序为护甲 -> 副手 -> hotbar -> 主背包
- 新增 `FakePlayerOffhandSlot` 与 `BackhandCompat`，让统一背包在运行时跟随 Backhand 当前副手黑名单，并读写假人的真实副手物品
- 调整统一背包 Shift 点击优先级为护甲 -> 饰品 -> 副手 -> 主背包，避免 `IBauble` 物品被副手槽抢走
- 在 `GTstaff` 的 `@Mod` 元数据中加入 `required-after:Baubles|Expanded` 与 `required-after:backhand`，把两者都声明为运行时必需依赖
- 通过 `FakePlayerInventoryViewTest` 与 `FakePlayerInventoryContainerTest`，并使用 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true build -x test` 重新打包 `build/libs/gtstaff-v1.0.1.jar`

### 遇到的问题
- **本地无法在线解析 `com.github.GTNewHorizons:Backhand` 运行时坐标**：离线缓存缺失时，`gtnhconvention` 在线拉取 manifest 又会失败，导致不能稳定依赖远程 Maven 坐标做本地验证

### 做出的决定
- 用 Forge `required-after:backhand` 保证游戏运行时硬依赖，再通过 `BackhandCompat` 反射桥接到 Backhand 当前库存扩展方法，避免本地构建被远程仓库状态卡死，同时保持游戏内行为跟随 Backhand 当前实现

---
# 2026-04-20：修复假人副手物品放入后客户端不显示

### 已完成
- 确认根因在于 `FakePlayer.syncEquipmentToWatchers()` 之前只同步原版主手/护甲，不会显式触发 Backhand 的副手同步包
- 在 `BackhandCompat` 中新增副手同步桥 `syncOffhandToWatchers(...)`，运行时通过反射构造 `OffhandSyncItemPacket` 并调用 Backhand 的跟踪玩家同步入口
- `FakePlayer.syncEquipmentToWatchers()` 现已在原版装备包之后补发一次副手同步，确保假人副手变化能及时同步到客户端显示
- 新增回归测试 `FakePlayerBackhandSyncTest`，并通过 `FakePlayerBackhandSyncTest`、`FakePlayerInventoryViewTest`、`FakePlayerInventoryContainerTest`
- 重新打包 `build/libs/gtstaff-v1.0.1.jar` 供客户端复测

### 遇到的问题
- **副手写入成功但显示链缺一段**：统一背包已经能把物品写入假人的真实副手库存，但 GTstaff 自己的装备同步方法只发原版 `S04PacketEntityEquipment`，客户端不会因此自动刷新 Backhand 副手

### 做出的决定
- 不依赖 Backhand 在常规 tick 中“顺带”检测到变化，而是在 GTstaff 的显式装备同步点补发一次 Backhand 副手同步，保证 UI 改动后的显示结果可预期

---
# 2026-04-20：准备发布 v1.0.2

## 已完成
- 将 `gradle.properties` 中的 `modVersion` 从 `v1.0.1` 更新为 `v1.0.2`
- 同步更新项目上下文中的当前版本与预期产物文件名为 `gtstaff-v1.0.2*.jar`
- 保持功能提交不变，单独准备后续合并到 `master`、推送 GitHub 与创建 `v1.0.2` release 的发布收尾

## 遇到的问题
- 当前功能工作树仍有用户自己的未提交改动 `src/main/resources/assets/gtstaff/lang/en_US.lang`，发布流程必须避免把它误并入 release 提交

## 做出的决定
- 采用“功能提交 + 版本发布准备提交”两段式提交，避免因为版本号变更去重写已完成的功能提交历史

---
