# 开发日志
## 2026-04-22：发布 v1.1.1 到 GitHub

### 已完成
- 已将当前 `master` 上的 nextgen 假人副手显示修复、Backhand 追踪补同步与 registry 持久化修复整理为 `v1.1.1`
- 已将 `gradle.properties` 中的版本号提升到 `v1.1.1`
- 已离线通过 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test assemble`
- 已确认最新产物更新为 `build/libs/gtstaff-v1.1.1.jar`、`build/libs/gtstaff-v1.1.1-dev.jar`、`build/libs/gtstaff-v1.1.1-sources.jar`
- 已将 `master` 推送到 GitHub，并创建、推送标签 `v1.1.1`
- 已在 GitHub Release 发布 `gtstaff-v1.1.1.jar`、`gtstaff-v1.1.1-dev.jar`、`gtstaff-v1.1.1-sources.jar`
- 发布地址：https://github.com/HOMEFTW/GTstaff/releases/tag/v1.1.1

### 遇到的问题
- 这次发布主要是对 `v1.1.0` 的 nextgen fake player 兼容收尾，重点问题集中在 Backhand 会把 Forge `FakePlayer` 判为无效玩家，导致副手同步链静默失效

### 做出的决定
- 将这轮确认有效的 nextgen 修复直接收口为 `v1.1.1` patch release，避免把仍有已知兼容缺口的 `v1.1.0` 继续作为推荐交付版本

## 2026-04-22：修复 nextgen 假人副手客户端显示仍不生效

### 已完成
- 继续对照 `Backhand-master` 源码排查后确认，之前那版修复虽然让 `GTstaffForgePlayer.syncEquipmentToWatchers()` 会调用 `BackhandCompat.syncOffhandToWatchers(this)`，但 Backhand 原本的 `BackhandPacketHandler.sendPacketToAllTracking(...)` 在内部会先走 `BackhandUtils.isValidPlayer(entity)`，而该判定会直接排除 Forge `FakePlayer`
- 这意味着 nextgen 假人虽然触发了副手同步桥，但由于 `GTstaffForgePlayer` 继承自 Forge `FakePlayer`，Backhand 仍会把它当成无效源实体，副手同步包根本不会真正发到客户端
- `BackhandCompat` 现已改为由 GTstaff 自己构造 `OffhandSyncItemPacket`，并逐个向同维度客户端玩家反射调用 `BackhandPacketHandler.sendPacketToPlayer(...)`，不再依赖会拒绝 Forge `FakePlayer` 的 `sendPacketToAllTracking(...)`
- 新增 `BackhandTrackingSyncService`，在玩家开始追踪 nextgen 假人时也主动补发一次当前副手状态，弥补 Backhand 自身 `StartTracking` 钩子同样会因 `isValidPlayer(target)` 拒绝 nextgen 假人的问题
- 新增回归测试：`FakePlayerBackhandSyncTest.syncOffhandToWatchersSendsPacketToEachWorldWatcher()`、`BackhandTrackingSyncServiceTest.startTrackingNextGenFakePlayerSyncsCurrentOffhandToWatcher()`
- 已重新通过离线测试：`FakePlayerBackhandSyncTest`、`BackhandTrackingSyncServiceTest`、`GTstaffForgePlayerTest`、`FakePlayerInventoryContainerTest`

### 遇到的问题
- 这次的真实根因不是容器没写入，也不是 `syncEquipmentToWatchers()` 没调用，而是 Backhand 老代码里把 Forge `FakePlayer` 语义直接当成“无效玩家”，导致 nextgen fake player 在副手同步链和开始追踪链上都被短路

### 做出的决定
- 对 nextgen fake player 的 Backhand 联动改为“GTstaff 自己负责发包，不再复用 Backhand 那条会排除 Forge `FakePlayer` 的源实体校验路径”，避免后续再被第三方兼容层的旧类型判断卡死

## 2026-04-22：修复 nextgen 假人副手放置后不显示

### 已完成
- 排查 `PlayerVisualSync` 双实现链后确认：legacy `FakePlayer.syncEquipmentToWatchers()` 已会追加 `BackhandCompat.syncOffhandToWatchers(this)`，但 nextgen `GTstaffForgePlayer.syncEquipmentToWatchers()` 漏掉了这一步，导致副手物品虽然写入成功，却没有把 Backhand 的副手同步包广播给观察客户端
- 先补上 `FakePlayerBackhandSyncTest.nextGenFakePlayerEquipmentSyncAlsoTriggersBackhandOffhandSync()`，用红灯锁定 nextgen 假人装备同步必须触发副手同步
- `GTstaffForgePlayer.syncEquipmentToWatchers()` 现已补齐 `BackhandCompat.syncOffhandToWatchers(this)`，让 nextgen 假人的副手显示链与 legacy 保持一致
- 已重新通过离线测试：`FakePlayerBackhandSyncTest`、`GTstaffForgePlayerTest`

### 遇到的问题
- 这次不是副手槽写入失败，而是 nextgen 假人的“可视同步”只发了主手和盔甲 `S04PacketEntityEquipment`，漏掉了 Backhand 自己的副手同步通道，所以客户端始终看不到副手更新

### 做出的决定
- 继续保留“主手/盔甲走原版装备包，副手走 Backhand 兼容桥”的同步策略，但要求 legacy 与 nextgen 两套假人实现都复用同一条副手广播语义，避免后续 runtime 切换时再次出现显示差异

## 2026-04-22：审计并修复 nextgen 迁移后的持久化缺口

### 已完成
- 沿着 nextgen/legacy 双轨接口对“仍绑死 legacy 容器或索引”的代码做了一轮定向审计，重点检查了 registry 保存链、命令入口、UI 列表来源和可视同步桥
- 确认 `FakePlayerRegistry.save(...)` 仍只会对 legacy `fakePlayers` 重新拍快照；nextgen 在线 runtime 虽然会在注册时写入一次 snapshot，但运行中后续发生的监控、驱逐、跟随等状态修改在保存时不会刷新
- 已将保存链改为统一遍历 `onlineRuntimes`，对 legacy 与 nextgen 都通过 `snapshot(runtime, previous)` 实时刷新持久化快照，不再让 nextgen bot 停留在注册时的旧状态
- 新增 `FakePlayerRegistryTest.saveRefreshesNextGenRuntimeStateInsteadOfKeepingInitialSnapshot()`，锁定 nextgen bot 在保存前发生的 monitor/repel/follow 状态变化必须被写入 registry
- 已重新通过离线测试：`FakePlayerRegistryTest`、`BotLifecycleManagerTest`、`FakePlayerRestoreSchedulerTest`

### 遇到的问题
- 这个问题比副手同步更隐蔽，因为 nextgen bot 平时在线时功能都正常，只有在 `save/load/restore` 链路上才会暴露为“重启后状态退回旧值”

### 做出的决定
- 后续继续把迁移审计重点放在“legacy map/legacy class-only 循环”和“双实现语义是否完全对齐”上；这类问题通常不会立刻报错，但会在保存、恢复、重建、同步这些边缘链路里悄悄丢行为

## 2026-04-22：发布 v1.1.0 到 GitHub

### 已完成
- 已将本地 master 推送到 origin/master
- 已创建标签 v1.1.0 并推送到 GitHub
- 已在 GitHub Release 发布 gtstaff-v1.1.0.jar、gtstaff-v1.1.0-dev.jar、gtstaff-v1.1.0-sources.jar
- 发布地址：https://github.com/HOMEFTW/GTstaff/releases/tag/v1.1.0

### 遇到的问题
- 无

### 做出的决定
- 当前正式对外交付版本切换为 v1.1.0，后续修复默认基于该版本线继续推进

## 2026-04-22：合并 nextgen 分支并准备发布 v1.1.0

### 已完成
- 将当前 feature/nextgen-runtime-wave-a 上的 nextgen runtime、动作链分层、Baubles/Backhand 背包兼容、TST/OpenBlocks 联动、背包 UI 重构与相关测试改动提交并合并回本地 master
- 先在功能分支上合并 origin/master，解决 v1.0.2 发布线与 nextgen 分支在背包/UI/文档上的分叉后，再将结果 fast-forward 到本地 master
- 将 gradle.properties 中的 modVersion 提升到 v1.1.0
- 重新离线执行 ./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test assemble
- 确认最新产物已更新为 build/libs/gtstaff-v1.1.0.jar、build/libs/gtstaff-v1.1.0-dev.jar、build/libs/gtstaff-v1.1.0-sources.jar

### 遇到的问题
- 本地 master 停在早于 origin/master 的 nextgen design spec 节点，而当前 worktree 里的真实实现还没有提交成分支历史，必须先提交功能分支、再吸收远端 v1.0.2 发布线，才能安全回合并

### 做出的决定
- 以“先整理功能分支提交 -> 再合并 origin/master -> 最后 fast-forward 到本地 master”的方式完成收口，避免把未提交工作直接覆盖到旧的 master

## 2026-04-22：修复假人饰品栏手动放置与 Shift 吞物品问题

### 已完成
- 根据实测反馈继续回查 FakePlayerInventoryContainer，确认 FakePlayerExtraSlot 之前没有显式 override isItemValid(...)，导致手动拖拽物品时额外槽仍会被当成可接受任意物品
- 对照本地反编译 Container.mergeItemStack(...) 确认第二个根因：原版 1.7.10 在塞进空槽时不会检查 slot.isItemValid(...)，因此饰品槽即使底层 inventory 会拒绝，Shift-click 仍会把源堆数量清零，表现成“物品被吞”
- 新增 FakePlayerInventoryContainerTest.extraSlotRejectsInvalidItemsForManualPlacement() 与 shiftClickDoesNotConsumeItemsWhenExtraSlotRejectsThem()，先锁住“手动不可放非法物品”和“Shift 不得吞物品”的红灯
- FakePlayerInventoryContainer.FakePlayerExtraSlot 现已显式 override isItemValid(...)，统一复用 FakePlayerInventoryView.isItemValidForSlot(...)
- mergeIntoExtraSlots(...) 不再复用会绕过槽位合法性检查的原版 mergeItemStack(...)，改为受限的额外槽合并逻辑：显式校验 slot.isItemValid(...)、堆叠兼容性与槽位上限，非法物品会直接保留在原槽位
- 重新通过 FakePlayerInventoryCompatTest、FakePlayerInventoryContainerTest、FakePlayerInventoryViewTest、FakePlayerInventoryGuiHandlerTest、FakePlayerManagerServiceTest，并离线打包 v1.0.2，最新主 jar 时间戳更新到 2026-04-22 00:38

### 遇到的问题
- 上一轮只补到了 view / inventory 层，容器层的手动点击与 Shift 合并仍各自保留一条绕过校验的路径，所以游戏内看起来仍然是“什么都能放进去，而且 Shift 会吞”

### 做出的决定
- 今后额外槽的点击放置与 Shift 合并都统一经过槽对象自己的合法性判断，不再把原版 mergeItemStack(...) 直接用于 Baubles / Offhand 这类受限槽
## 2026-04-22：为假人饰品栏补齐物品放置限制

### 已完成
- 根据实测反馈确认：客户端饰品槽虽然已显示图标和类型，但 `FakePlayerInventoryExtraSlot` 在无后端 inventory 的客户端路径里仍对任意物品返回 `isItemValid == true`，导致普通物品也能被放入 Baubles 槽
- 新增 `FakePlayerInventoryViewTest.clientBaublesExtraSlotRejectsPlainItem()`，先复现普通 `Item` 在客户端饰品槽里被错误接受的红灯
- `FakePlayerInventoryCompat` 现已新增客户端附加槽校验：Baubles 槽会反射检查物品是否实现 `IBauble`/`IBaubleExpanded`，并校验 `slotType` 是否匹配或是否为 `universal`
- `FakePlayerInventoryExtraSlot.isItemValid(...)` 在客户端无后端 inventory 时，不再无条件放行，而是委托 `FakePlayerInventoryCompat.isClientExtraSlotItemValid(...)`
- 保留服务端真实 `IInventory.isItemValidForSlot(...)` 校验链，不改变已接入的 Baubles / Backhand 后端槽写回逻辑
- 重新通过 `FakePlayerInventoryCompatTest` 以及 UI 相关回归测试，并离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-22 00:11

### 遇到的问题
- 这次崩溃的根因不是图标或布局，而是客户端先允许非法物品进入饰品槽，后续同步/渲染链再被外部模组假设“这里一定是合法 Bauble”而触发崩溃

### 做出的决定
- 客户端先挡住明显非法物品，服务端继续保留真实 inventory 校验，双层防线都在

## 2026-04-22：重新打包当前 v1.0.2 jar

### 已完成
- 离线执行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 确认当前工作区已成功生成最新 `build/libs/gtstaff-v1.0.2.jar`、`build/libs/gtstaff-v1.0.2-dev.jar`、`build/libs/gtstaff-v1.0.2-sources.jar`
- 最新主 jar 时间戳更新到 2026-04-22 00:02:57

### 遇到的问题
- 无

### 做出的决定
- 当前版本号继续保持 `v1.0.2`，本轮仅重新打包当前工作区产物，不改版本号

## 2026-04-21：放宽假人攻击兜底正脸范围到45度

### 已完成
- 根据需求把假人攻击兜底的正脸判定从原先约 30 度放宽到 45 度，让略偏侧前方的实体也能进入攻击 fallback
- 新增 `TargetingServiceTest.attackFallbackIncludesEntityInsideFortyFiveDegreeCone()`，先复现约 34 度位置的实体在旧阈值下不会被选中的红灯
- 将 `TargetingService` 的 `ATTACK_FALLBACK_FACING_DOT` 从约 `cos(30°)` 调整为 `cos(45°)`，保持实现仍然基于视线向量点乘
- 重新通过 `TargetingServiceTest`、`AttackExecutorTest` 与 `PlayerActionPackTest`

### 遇到的问题
- 现有 30 度锥形对“脸大致对着但没完全对正”的场景还是偏严，尤其在命令式假人实操时容易出现玩家主观上觉得“已经在正面范围”但 fallback 仍不触发

### 做出的决定
- 这次只放宽攻击 fallback 的朝向阈值，不改射线命中优先级、距离判定和“超出正脸范围仍不应命中”的约束

## 2026-04-21：移除假人背包里的问号饰品槽位

### 已完成
- 根据实测反馈确认：假人背包右侧饰品栏中仍会出现问号槽位，这些槽位对应 Baubles `unknownType`；把物品放进去后会进入外部模组并未准备好的 unknown 槽逻辑，导致客户端闪退
- 新增 `FakePlayerInventoryCompatTest.hidesUnknownBaublesSlotTypes()`，先锁住 `unknown` 与空字符串类型不应被 GTstaff 视为可见饰品槽的红灯
- `FakePlayerInventoryCompat` 现已新增 `isVisibleBaublesSlotType(...)`，统一过滤 `unknown` 与空类型
- 服务端 `serverSlots(...)` 与客户端 `clientSlots(...)` 生成 Baubles 附加槽时，现已只保留真实类型槽位，不再把 `unknownType` 槽位塞进管理背包 UI
- 重新通过 `FakePlayerInventoryCompatTest` 以及 UI 相关回归测试，并离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 23:56

### 遇到的问题
- 旧的硬依赖实现会按 `slotType` 决定是否显示槽位，但当前反射兼容最初只按槽数量遍历，等于把本来应隐藏的 `unknownType` 也暴露到了 UI

### 做出的决定
- GTstaff 管理背包一律不显示 Baubles `unknownType` 槽位，不跟随外部模组“显示未分配槽位”的可选配置，以优先保证可用性与不闪退

## 2026-04-21：恢复假人背包空饰品槽与盔甲槽图标

### 已完成
- 根据实测反馈确认：右侧 Baubles 饰品栏虽然恢复了槽位布局，但空槽内没有显示戒指/护符等类型图标，玩家无法判断饰品应放入哪个槽
- 对照 `Baubles-Expanded` 确认原图标来源：重构前使用 `SlotBauble#getBackgroundIconIndex()`，其内部通过 Baubles `itemDebugger.getBackgroundIconForSlotType(slotType)` 返回 `empty_bauble_slot_<type>` 图标
- `FakePlayerInventoryExtraSlot` 现已保存可选 `baublesSlotType`，`FakePlayerInventoryCompat` 会通过反射读取 `BaubleExpandedSlots.getSlotType(slot)` 并同步到服务端/客户端附加槽
- 新增 `FakePlayerBaublesIconCompat`，在客户端通过反射读取 Baubles `itemDebugger` 的空槽背景图标，保持 GTstaff 对 Baubles 无硬运行时依赖
- `FakePlayerInventoryContainer.FakePlayerExtraSlot` 现已为 Baubles 附加槽覆盖 `getBackgroundIconIndex()`，空饰品槽会恢复显示对应类型内部图标
- `FakePlayerArmorSlot` 现已覆盖 `getBackgroundIconIndex()` 并返回 `ItemArmor.func_94602_b(armorType)`，空盔甲槽恢复原版头盔/胸甲/护腿/靴子图标
- 新增回归测试覆盖 Baubles 槽类型元数据与盔甲槽背景图标方法，并重新通过 UI 相关测试
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 23:47

### 遇到的问题
- 上一轮为了无硬依赖把 Baubles 槽抽象成通用附加槽，但没有保留 `SlotBauble` 的 `slotType` 和背景图标链路，导致可用性恢复了、视觉提示丢失了

### 做出的决定
- 继续保持 Baubles/Backhand 反射兼容路线，不恢复硬依赖；只在客户端实际存在 Baubles 时读取其原生空槽图标
- 盔甲槽图标直接复用原版 `ItemArmor.func_94602_b(...)`，与玩家背包/外部 invsee 实现保持一致

## 2026-04-21：恢复假人管理背包饰品栏与副手栏布局

### 已完成
- 根据实测反馈确认：管理背包 UI 在饰品栏/副手迁移后发生错乱，附加槽被显示在假人主背包下方，导致玩家背包整体下移，没有恢复重构前的右侧饰品栏与副手装备位
- 新增 `FakePlayerInventoryContainerTest.containerRestoresEquipmentLayoutWithoutShiftingPlayerInventory()`，先复现 Baubles 槽坐标、副手槽坐标、玩家背包顶部与 GUI 高度不符合旧布局的红灯
- 新增 `FakePlayerBaublesSlotLayout`，把 Baubles 面板列数、滚动偏移与隐藏槽位计算集中管理
- `FakePlayerInventoryContainer` 现已恢复固定 203 高度布局：副手槽放回装备区 `80,18`，Baubles 槽放到右侧面板 `184,18` 起始位置，玩家背包顶部保持 `125`
- `FakePlayerInventoryGui` 现已恢复 256 宽管理界面：左侧仍绘制原版 chest-style 背包，右侧绘制独立 Baubles 面板和滚动条
- 重新通过 UI 相关测试，并离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 23:32

### 遇到的问题
- 根因不是附加槽同步失败，而是上一轮把附加槽作为“普通背包额外行”显示，和原设计的“副手在装备区、饰品栏在右侧独立面板”不一致

### 做出的决定
- 附加槽仍保留在容器 fake slot 区间中以维持同步与 Shift-click 逻辑，但显示布局不再影响普通假人背包与玩家背包位置
- 当前版本号继续保持 `v1.0.2`，本轮作为 UI 布局回归修复重新打包

## 2026-04-21：修复假人丢物品后的手持物残影

### 已完成
- 根据实测反馈确认：假人执行 `drop` / `dropStack` 后，服务端背包中物品已经不存在，但客户端仍可能看到物品残留在假人手上
- 确认根因：`PlayerActionPack` 的 `DROP_ITEM` / `DROP_STACK` 只调用 `player.dropOneItem(...)`，没有像 `setSlot` 或背包容器编辑那样调用 `PlayerVisualSync.syncEquipmentToWatchers()`
- 新增 `PlayerActionPackTest.dropItemActionsSyncHeldItemForVisualSyncPlayers()`，先复现丢物品后没有触发装备同步的红灯
- 将丢单个与丢整组收口到 `performDrop(...)`，在 `dropOneItem(...)` 后立即刷新观察客户端的手持装备包
- 保持 `setSlot(...)` 使用同一个 `syncEquipmentToWatchers()` helper，减少后续动作同步遗漏
- 重新通过 `PlayerActionPackTest`
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 23:16

### 遇到的问题
- 这个问题不是物品没有被真正丢出，而是服务端 inventory 已改变后缺少 `S04PacketEntityEquipment` 刷新，导致观察客户端沿用旧的手持物渲染缓存

### 做出的决定
- 丢物品动作属于会改变当前手持物的动作，必须和切换 hotbar / 背包容器编辑一样主动同步装备外观
- 当前版本号继续保持 `v1.0.2`，本轮作为动作视觉同步修复重新打包

## 2026-04-21：迁移假人背包饰品栏与副手槽位

### 已完成
- 确认根因：假人背包管理界面只暴露固定 40 槽（护甲、hotbar、主背包），`FakePlayerInventoryContainer` 也把玩家背包起点写死为 40，因此 Baubles Expanded 饰品栏与 Backhand 副手都没有进入容器同步
- 新增 `FakePlayerInventoryExtraSlot`，把可选扩展槽抽象为可追加的 fake inventory 槽位，基础 40 槽顺序保持不变
- 新增 `FakePlayerInventoryCompat`，通过纯反射可选接入 Baubles Expanded 与 Backhand：Baubles 使用 `BaublesApi.getBaubles(...)` 的 `IInventory`，Backhand 使用 `BackhandUtils.getOffhandItem(...)` / `setPlayerOffhandItem(...)` / `getOffhandSlot(...)`
- `FakePlayerInventoryView` 现在会在基础 40 槽后追加饰品槽和副手槽，客户端也会按本地已加载模组创建对应同步槽，避免服务端容器槽位和客户端 GUI 槽位数量不一致
- `FakePlayerInventoryContainer` 改为动态计算 fake slot 数量、玩家背包起点和 GUI 高度；附加槽会显示在假人主背包下方，玩家背包整体下移
- Shift-click 规则已调整：护甲仍优先进入护甲槽，Baubles 合法饰品优先进饰品槽，普通物品先进入假人普通背包，普通背包满后才尝试副手槽
- 补充 `FakePlayerInventoryViewTest` 与 `FakePlayerInventoryContainerTest` 覆盖附加槽写回、动态布局、附加槽堆叠限制、饰品 Shift-click 优先级和副手 fallback
- 重新通过 UI 相关测试，并离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 23:06

### 遇到的问题
- Baubles 的饰品槽最大堆叠应为 1，不能继续使用 `FakePlayerInventoryView.getInventoryStackLimit() == 64`；已为附加槽使用自有 `getSlotStackLimit()`
- Backhand 副手不是单独 inventory 文件，而是通过 mixin 扩展 `InventoryPlayer.mainInventory` 并暴露 `BackhandUtils` API；因此本轮通过反射虚拟出 1 槽 `IInventory` 来写回副手

### 做出的决定
- 不对 Baubles / Backhand 建立硬依赖，避免未安装对应模组时 GTstaff 启动失败
- 本轮先接入 GTNH 更常见的 Baubles Expanded 与 Backhand 副手；Battlegear2 副手未硬接，后续如果用户实测需要可按同一附加槽抽象继续增加 fallback
- 当前版本号继续保持 `v1.0.2`，本轮作为 UI/背包兼容迁移重新打包

## 2026-04-21：收窄攻击兜底实体扫描到正脸方向

### 已完成
- 根据实测反馈确认：上一轮攻击兜底虽然解决了“精确射线未命中就打不到”的问题，但扫描范围过大，会把身侧实体也纳入攻击目标
- 新增 `TargetingServiceTest.attackFallbackIgnoresEntityOutsideFacingCone()`，先复现侧前方实体会被大范围兜底误选中的红灯
- 在 `TargetingService.nearestAttackableEntity(...)` 中加入面朝方向约束：候选实体必须位于玩家视线前方约 30 度锥形范围内，才允许作为攻击兜底目标
- 兜底距离改为基于实体碰撞盒中心点与假人眼睛位置计算，避免单纯使用实体 `posX/posY/posZ` 带来的偏差
- 保留精确射线优先级：如果原版式射线已经命中实体，仍直接使用该目标；只有射线未命中实体时才进入正脸锥形兜底
- 重新通过 `PlayerActionPackTest`、`TargetingServiceTest` 与 `AttackExecutorTest`
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 22:49

### 遇到的问题
- 之前的兜底直接使用 `player.boundingBox.expand(reach, 1, reach)` 找最近可碰撞实体，本质是一个大盒子，不区分正面、侧面和背后
- 只按距离选最近实体对命令式假人太激进，容易出现“脸没对着也打到”的手感问题

### 做出的决定
- 攻击兜底只服务于“脸正对但射线没精确穿过”的容错，不再承担身边自动索敌功能
- 当前版本号继续保持 `v1.0.2`，本轮作为攻击目标选择手感修复重新打包

## 2026-04-21：修复 USE 频率动作只触发一次

### 已完成
- 根据实测反馈确认：`attack` 的频率档位正常，但 `use continuous / use interval` 不会持续触发，任何档位都只使用一次
- 确认根因：`PlayerActionPack.performUse(...)` 成功后会把 `itemUseCooldown` 设为 `3`，但该冷却值没有逐 tick 递减，导致第一次使用后后续 `USE` 调度永久被 `UseExecutor` 的 `itemUseCooldown > 0` 拦截
- 新增 `PlayerActionPackTest.intervalUseRepeatsAfterCooldownExpires()`，先复现 `use interval 5` 跑 6 tick 仍只触发一次使用的红灯
- 在 `PlayerActionPack.onUpdate()` 开头补齐 `itemUseCooldown` 每 tick 递减，使 `use interval` 与 `use continuous` 能在冷却结束后继续触发
- 重新通过 `PlayerActionPackTest`、`FakePlayerManagerServiceTest` 与 `CommandPlayerTest`
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 22:44

### 遇到的问题
- 第一次写红灯测试时使用真实 `new Item()` 会触发 Minecraft 静态初始化问题；已改为方块右键测试路径，避免测试依赖真实物品注册环境
- 这个问题只影响 `USE`，因为 `ATTACK` 没有 `itemUseCooldown`，所以攻击频率表现正常

### 做出的决定
- 冷却应该按实体 tick 自然流逝，而不是等下一次 `USE` 尝试时才处理；这样 UI 的各个使用频率档位和命令 `use interval <ticks>` 都能保持语义一致
- 当前版本号继续保持 `v1.0.2`，本轮作为 `USE` 动作调度修复重新打包

## 2026-04-21：修复 nextgen 假人 creative 能力位导致仍然无敌

### 已完成
- 根据实测反馈确认：假人现在可以造成伤害，但自身仍不会受到伤害，说明攻击输出链已经打通，剩余问题在假人受伤入口
- 追踪原版 `EntityPlayer.attackEntityFrom(...)`，确认根因是第二道拦截：`ItemInWorldManager.setGameType(CREATIVE)` 会设置 `capabilities.disableDamage = true`，即使 `GTstaffForgePlayer.isEntityInvulnerable()` 已覆盖为 `false`，原版玩家受伤链仍会因 `disableDamage && !source.canHarmInCreative()` 返回 `false`
- 新增 `GTstaffForgePlayerTest.attackTemporarilyClearsCreativeDamageFlag()`，先复现 creative 能力位下受伤链会被拦截的红灯
- 在 `GTstaffForgePlayer.attackEntityFrom(...)` 中为在线 nextgen bot 临时清除 `capabilities.disableDamage`，只包住一次原版受伤调用，并在 `finally` 中恢复原值
- 保留 `disconnected` 状态的隔离语义：显式下线后的 nextgen bot 仍不会受伤
- 重新通过 `GTstaffForgePlayerTest`、`PlayerActionPackTest`、`TargetingServiceTest` 与 `AttackExecutorTest`
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 22:38

### 遇到的问题
- 上轮测试只断言了 `isEntityInvulnerable()`，没有覆盖 `EntityPlayer.attackEntityFrom(...)` 内部的 `capabilities.disableDamage` 分支；这就是“看起来已经不是 invulnerable，但游戏内仍然不掉血”的原因
- creative/世界默认模式会通过 `setGameType(...)` 自动改写玩家能力，不能只修 Forge `FakePlayer` 的无敌覆盖

### 做出的决定
- nextgen bot 的受伤入口要以“命令式假人可被真实伤害”为优先级：即使当前游戏模式保留 creative 能力，也允许伤害链进入；但调用后恢复原能力位，避免破坏飞行、创造模式等状态显示
- 当前版本号继续保持 `v1.0.2`，本轮作为受伤链可靠性修复重新打包

## 2026-04-21：修复假人攻击精确射线未命中时实体无伤害

### 已完成
- 确认上轮“假人可受伤 / 可攻击玩家目标”修复仍不足以解决实战攻击无效：`PlayerActionPack` 只有精确射线命中实体时才会进入实体伤害链，射线未命中时只会空挥手
- 新增 `PlayerActionPackTest.attackFallsBackToNearbyEntityWhenPreciseRayMisses()`，先复现近战范围内有实体但精确射线未穿过时不会造成伤害的红灯
- 为 `TargetingService` 新增 `resolveForAttack()`：保留原精确命中优先级，但当攻击射线没有命中实体时，会在近战范围内选择最近可碰撞实体作为攻击目标
- `PlayerActionPack` 的 `ATTACK` 入口改为使用攻击专用目标解析；`USE` 仍使用原精确右键目标解析，避免影响方块/物品使用兼容
- 重新通过 `PlayerActionPackTest`、`TargetingServiceTest`、`AttackExecutorTest` 与 `GTstaffForgePlayerTest`
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 21:55

### 遇到的问题
- 实战中的 fake player 没有真实客户端准星反馈，单靠 vanilla 风格射线命中太脆弱；只要朝向、实体碰撞盒或脚下方块稍有偏差，就会表现为“攻击指令发出但实体完全无伤害”
- 上轮伤害 fallback 本身能扣血，但它挂在“已经选中实体”之后，因此目标解析为空时根本不会被调用

### 做出的决定
- 攻击动作单独加入近战实体兜底，优先解决命令式 fake player 的可靠性；右键使用不套用该兜底，避免误触附近实体或方块交互
- 当前版本号继续保持 `v1.0.2`，本轮作为攻击可靠性修复重新打包

## 2026-04-21：修复 nextgen 假人永久无敌与玩家目标攻击检查

### 已完成
- 确认根因：nextgen `GTstaffForgePlayer` 继承 Forge `FakePlayer`，而 Forge `FakePlayer.isEntityInvulnerable()` 默认永远返回 `true`，`canAttackPlayer(...)` 默认永远返回 `false`
- 为 `GTstaffForgePlayerTest` 新增 nextgen 假人可受伤与可攻击玩家目标的回归断言，先复现红灯
- 在 `GTstaffForgePlayer` 中覆盖 `isEntityInvulnerable()` 与 `canAttackPlayer(...)`：在线 bot 不再永久无敌，显式 `markDisconnected()` 后仍保持隔离
- 重新通过 `GTstaffForgePlayerTest` 与 `PlayerActionPackTest`，确认 nextgen 实体伤害语义与既有攻击 fallback 没有回退
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 21:41

### 遇到的问题
- “假人不受伤”不是 GTstaff 自动复活逻辑导致，而是 Forge `FakePlayer` 基类本身把实体设成永久 invulnerable；如果只看 `GTstaffForgePlayer.onDeath(...)` 会误判方向
- “不造成实体伤害”中至少玩家类目标会被 `canAttackPlayer(...) == false` 拦截；普通 living 目标仍有 `PlayerActionPack` 的强制伤害 fallback，但这次一并补齐了玩家目标攻击检查

### 做出的决定
- nextgen bot 在线时应该更接近真实玩家，允许进入原版伤害链；显式下线后的实体继续通过 `disconnected` 状态隔离，避免被自动复活或继续参与战斗
- 本轮继续保持 `v1.0.2` 版本号，仅重新打包稳定性修复产物

## 2026-04-21：修复 respawn mixin 的非私有静态 helper 启动崩溃

### 已完成
- 复现并确认启动报错根因：`EntityPlayerMP_RespawnMixin` 中的 `gtstaff$copyFakePlayerState(...)` 是包级 `static` helper，被 Sponge Mixin 判定为非法成员
- 顺手排查同类风险，确认 `ServerConfigurationManagerMixin` 中也残留包级 `static` respawn helper，存在“修完一个下次启动再炸另一个”的风险
- 新增 `RespawnMixinHooks`，把 `copyFakePlayerState(...)`、nextgen respawn player 构造与测试用 factory 注入入口统一抽离到独立 hooks 类
- `EntityPlayerMP_RespawnMixin` 与 `ServerConfigurationManagerMixin` 现在只保留真正的注入方法，mixin 本体不再暴露非私有静态 helper
- 调整 `RespawnMixinsTest` 改为直接调用 `RespawnMixinHooks`，并重新通过该聚焦回归
- 重新离线打包 `v1.0.2` 产物，最新 jar 时间戳更新到 2026-04-21 21:14

### 遇到的问题
- 第一次清理 `ServerConfigurationManagerMixin` 时把 `GameProfile` import 一起删掉了，导致中途出现一次纯编译错误；补回 import 后回归恢复为绿色
- 这类问题不会在普通单元测试里自然暴露，而是典型的“启动期 Mixin 验证失败”，所以这次额外做了目录级静态扫描，确认 mixin 目录里只剩合法的 `private static` 注入 helper

### 做出的决定
- 对 mixin 里需要被测试或复用的静态逻辑，不再继续放在 mixin 本体里做包级 helper；后续统一优先放到独立 hooks / helper 类，mixin 自己只保留注入入口
- 当前版本号继续保持 `v1.0.2`，这次是稳定性修复重打包，不额外抬版本号

## 2026-04-21：重新打包清理后代码的 v1.0.2 jar

### 已完成
- 离线执行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 确认最新产物已重新生成：`build/libs/gtstaff-v1.0.2.jar`、`build/libs/gtstaff-v1.0.2-dev.jar`、`build/libs/gtstaff-v1.0.2-sources.jar`
- 核对 `build/libs` 时间戳：主 jar 与 sources jar 更新时间为 2026-04-21 21:07，dev jar 为 21:07:12

### 遇到的问题
- 无新的构建阻塞；本轮继续沿用 `--offline` 与禁用 buildscript update check 的打包方式，避免 GTNH Gradle 在线元数据解析不稳定

### 做出的决定
- 当前对外交付仍以 `v1.0.2` 版本号维持不变；这次打包是把最近的 nextgen 收尾清理与恢复重建修复重新编入现有版本产物
- 后续若继续做游戏内人工烟测或 Baubles Expanded 兼容，再根据变更范围决定是否需要抬新版本号

## 2026-04-21：清理命令与 UI 层残留的 legacy-only helper

### 已完成
- 为 `CommandPlayerTest` 新增 `purgeUsesLifecycleManagerWhenLegacyFakePlayerIsOnline()`，确认在线 legacy bot 的 `purge` 也已经走 `BotRuntimeView + BotLifecycleManager` 主链
- 删除 `CommandPlayer.handlePurge(...)` 中依赖 `FakePlayerRegistry.getFakePlayer(...)` 的旧 online legacy fallback，保留“在线 runtime 主链 + 离线持久化 bot”两条真实路径
- 删除 `CommandPlayer` 中未再使用的 `requireFakePlayer(...)`、`requireActionPack(...)` 与 `formatArmorSummary(...)`
- 删除 `FakePlayerManagerService` 中未再使用的 `findBot(...)`、`findBotOrThrow(...)`
- 重新通过 `CommandPlayerTest` 与 `FakePlayerManagerServiceTest`

### 遇到的问题
- 清理 import 时发现 `FakePlayer` 虽然不再作为命令/UI 主查询入口存在，但 `colorizeName(...)` 这个静态颜色工具仍被提示文案复用，因此不能把 `FakePlayer` import 一刀切掉
- `CommandPlayer` 里还有一个只剩定义不再被调用的 `formatArmorSummary(FakePlayer target)`，第一次编译失败后才把它一起清掉

### 做出的决定
- 对命令/UI 层这类已经完成 runtime-neutral 收口的模块，优先把“死掉的 legacy helper”尽快删掉，避免后续维护时再次误把旧入口当成仍受支持的主链
- `FakePlayer` 在命令/UI 层仅继续作为静态展示工具类存在，不再保留作为在线 bot 查询与操作入口的职责

## 2026-04-21：补齐 nextgen 恢复后补皮重建链的 runtime-aware 收口

### 已完成
- 将 `FakePlayerRestoreScheduler` 的补皮调度从 legacy `FakePlayer` 收口改为统一传递 `BotRuntimeView`，nextgen 恢复出的 bot 也会进入异步补皮流程
- 将 `FakePlayerSkinRestoreScheduler` 的调度与重建回调改为 runtime-aware：成功解析 profile 后，统一回主线程调用 `BotLifecycleManager.rebuildRestoredWithProfile(...)`
- 为 `BotLifecycleManager` 新增 runtime-aware 的 `rebuildRestoredWithProfile(...)` 分派，并在 `NextGenBotFactory.rebuildRestoredWithProfile(...)` 中补齐 nextgen 重建时的 owner、位置、经验、背包 hotbar 选择以及 monitor/follow/repel 等运行时状态迁移
- 新增 `NextGenBotFactoryTest.rebuildRestoredWithProfileReplacesRegisteredRuntimeAndPreservesServiceState()`，确认 nextgen 重建后会替换 registry 中的在线 runtime、旧实体会正确下线，且服务状态不会丢
- 重新通过 `FakePlayerRestoreSchedulerTest`、`FakePlayerSkinRestoreSchedulerTest`、`NextGenBotFactoryTest` 与 `BotLifecycleManagerTest`

### 遇到的问题
- 之前恢复补皮链虽然已经能处理 `BotRuntimeView` 恢复列表，但真正安排异步补皮与主线程重建的部分仍只认 legacy `FakePlayer`，导致 nextgen bot 重启恢复后拿不到同等的皮肤重建闭环
- 新增回归测试时，一开始误把测试桩写成了“只按名字造 player，不复用传入 `GameProfile`”，导致 profile UUID 断言失败；最终确认是测试桩问题而不是生产实现掉 profile

### 做出的决定
- 对“恢复后补皮重建”这种已经跨过 registry / scheduler / lifecycle 的链路，优先在统一入口做 runtime-aware 收口，而不是继续在 legacy / nextgen 两边分散补分支
- Wave D 收尾阶段后续继续聚焦低优先级 legacy-only helper 清理与游戏内人工烟测，不再把 nextgen 恢复补皮视为未迁移缺口

## 2026-04-21：清理 nextgen 注册覆盖 legacy 时的陈旧 registry 映射

### 已完成
- 调整 `FakePlayerRegistry.registerRuntimeInternal(...)`：当同名 bot 以非 legacy runtime 重新注册时，会主动清除 `fakePlayers` 中同名的旧 legacy 实体映射
- 新增 `FakePlayerRegistryTest.registerRuntimeClearsStaleLegacyLookupWhenReplacingWithNextGen()`，覆盖“同名 bot 从 legacy 句柄切到 nextgen runtime 后，不再能通过 `getFakePlayer(...)` 拿到陈旧 legacy 实体”的回归场景
- 重新通过 `FakePlayerRegistryTest`，确认 runtime 注册、快照持久化与新的 stale-entry 清理行为都已稳定

### 遇到的问题
- `FakePlayerRegistry` 同时维护 `onlineRuntimes` 与 legacy `fakePlayers` 两套索引；如果同名 bot 被 nextgen runtime 覆盖注册，而旧的 legacy 索引不清理，后续命令或清理链就有机会拿到过期实体引用

### 做出的决定
- 对这种“runtime-neutral 主索引已正确，但 legacy 辅助索引会残留旧引用”的问题，优先在 registry 层做主动清理，而不是把每个调用方都改成额外防 stale

## 2026-04-21：补齐 nextgen bot 的 FakeNetHandler kick 下线链

### 已完成
- 将 `FakeNetHandlerPlayServer.kickPlayerFromServer(...)` 的 GTstaff 假人踢出逻辑抽成 `handleFakePlayerKick(...)`，便于对 legacy / nextgen 两条分支分别做回归验证
- legacy `FakePlayer` 继续沿用原有 `kill()` 语义；nextgen `GTstaffForgePlayer` 在 duplicate-login / idle kick 下现在会执行 `markDisconnected() -> unregister -> playerLoggedOut -> setDead`
- 新增 `FakeNetHandlerPlayServerTest`，覆盖 nextgen bot 的 duplicate-login 踢出会真正摘除在线 runtime，以及无关 reason 不会误清理
- 重新通过 `FakeNetHandlerPlayServerTest`、`BotSessionTest` 与 `BotLifecycleManagerTest`，确认 nextgen session handler 的下线链和已有 lifecycle 语义保持一致

### 遇到的问题
- `BotSession` 虽然早就把 nextgen bot 接到了 `FakeNetHandlerPlayServer`，但这个 handler 之前只会在 `playerEntity instanceof FakePlayer` 时做真正的清理；nextgen 遇到 duplicate-login/idle 这类会话级踢出时，实际上并不会被摘掉

### 做出的决定
- 继续优先补齐“nextgen 已经走进同一基础设施，但基础设施内部仍只认 legacy 类型”的缺口；这类问题通常不需要改大架构，但如果不补，运行时状态会悄悄泄漏

## 2026-04-21：统一 shadow 命令的 nextgen fake-player 防护

### 已完成
- 将 `CommandPlayer.handleShadow(...)` 的“目标已经是假人”判断从 legacy `FakePlayer` 类型判断改为复用 `ServerUtilitiesCompat.isFakePlayer(...)`
- nextgen `GTstaffForgePlayer` 现在也会被 `shadow` 命令正确拒绝，不会再因命令层只认 legacy 类型而被误当成真人目标
- 为 `CommandPlayerTest` 新增 `shadowRejectsNextGenFakePlayerTarget()`，覆盖 nextgen fake-player 的 shadow 防护回归场景
- 连同此前的 restore scheduler / fake-player 分类测试一起重新通过 `CommandPlayerTest`、`FakePlayerRestoreSchedulerTest` 与 `ServerUtilitiesCompatTest`

### 遇到的问题
- 默认 runtime 切到 nextgen 后，命令层仍残留少量“只是想判断 GTstaff 假人身份，却还只认 legacy `FakePlayer`”的分支；`shadow` 正是其中最容易被用户直接触发的一条入口

### 做出的决定
- 继续把“纯身份识别型”的 legacy 判断统一收口到 `ServerUtilitiesCompat.isFakePlayer(...)`，避免命令层、scheduler 与底层兼容点分别维护三套 nextgen 识别规则

## 2026-04-21：统一 restore scheduler 与 knockback 的 GTstaff fake-player 识别

### 已完成
- 将 `FakePlayerRestoreScheduler.isReady(...)` 的集成服就绪判断改为复用 `ServerUtilitiesCompat.isFakePlayer(...)`，不再只把 legacy `FakePlayer` 排除在“真实玩家已进入”之外
- 新增 `FakePlayerRestoreSchedulerTest.integratedServerStillWaitsWhenOnlyNextGenBotIsPresent()`，覆盖“集成服玩家列表里只有 nextgen bot 时，restore 仍应继续等待真实玩家进入”的回归场景
- 将 `Entity_KnockbackMixin` 也改为复用同一套 fake-player 识别逻辑，使 nextgen `GTstaffForgePlayer` 与 legacy 假人在 knockback 后都能一致地标记 `velocityChanged`
- 重新通过 `FakePlayerRestoreSchedulerTest` 与 `ServerUtilitiesCompatTest`，确认 fake-player 分类与 restore readiness 新逻辑都已稳定

### 遇到的问题
- nextgen runtime 默认启用后，代码里仍有少量“只是想判断 GTstaff 假人身份，却还只认 legacy `FakePlayer` 类型”的位置；这类判断如果继续分散维护，很容易一边补上 nextgen，另一边又漏掉 scheduler 或底层兼容点
- `ServerConfigurationManagerMixin` / `EntityPlayerMP_RespawnMixin` 这类 respawn 相关 Mixin 目前虽然也带 legacy-only 判断，但它们牵涉 nextgen runtime 绑定与会话重挂，不适合在没设计完整 nextgen respawn 重建链之前做半截迁移

### 做出的决定
- 先优先把“纯身份识别型”的 legacy-only 判断收口到统一 helper，比如 restore readiness 与 knockback；对牵涉 runtime 重建的 respawn mixin，暂时保持谨慎，不做只改类型判断但不补 runtime 绑定的半成品迁移

## 2026-04-21：补齐 nextgen bot 的自然死亡自动复活与显式下线隔离

### 已完成
- 为 `GTstaffForgePlayer` 补上与 legacy 假人一致的 `respawnFake()`、死亡后自动复活与 `disconnected` 短路保护
- `GTstaffForgePlayer.onUpdate()` 现在会在 bot 非下线状态下，对 `isDead && deathTime > 0` 的 nextgen 假人自动执行复活，再继续 `base -> action -> runtime -> living` 运行时 tick
- `GTstaffForgePlayer.onDeath(...)` 现在会在正常在线状态下自动复活，避免 nextgen bot 因自然死亡直接停摆
- 为 `BotLifecycleManager.kill(...)` 补上 `GTstaffForgePlayer.markDisconnected()`，确保显式 `kill/purge` 下线不会被新的自动复活逻辑反向拉活
- 新增并通过 `GTstaffForgePlayerTest` 的 nextgen 死亡/下线回归测试，以及 `BotLifecycleManagerTest.killMarksNextGenForgePlayerDisconnectedBeforeRemoval()`

### 遇到的问题
- nextgen 当前走的是 Forge `FakePlayer` 继承链，不会自动继承 legacy `FakePlayer` 的 `onDeath -> respawnFake()` 语义；如果不手动补齐，nextgen bot 自然死亡后会真的进入死亡态
- 为了验证 `BotLifecycleManager.kill(...)` 的 nextgen 分支，测试桩需要补齐 `inventoryContainer/openContainer/inventory` 的最小状态，否则 `EntityPlayer.setDead()` 会先在轻量测试环境里空指针

### 做出的决定
- nextgen 保持与 legacy 一致的“自然死亡自动复活、显式 kill/purge 不复活”语义，优先在 `GTstaffForgePlayer` 本体补齐，而不是继续依赖只覆盖 legacy 类型的 respawn mixin
- 显式下线语义继续收口到 lifecycle 层处理，由 `BotLifecycleManager.kill(...)` 为 nextgen bot 先打 `disconnected` 标记，再执行世界/会话摘除

## 2026-04-21：补齐 runtime-only nextgen bot 的 purge 清理并压稳恢复态回归测试

### 已完成
- 将 `CommandPlayer.handlePurge(...)` 的在线 bot 分支改为优先读取 `BotRuntimeView`，不再依赖 legacy `FakePlayer`
- runtime-only nextgen bot 现在执行 `purge` 时会先走 `BotLifecycleManager.kill(...)` 关闭在线实体，再继续做持久化与玩家数据清理，避免只删 registry 但在线 bot 仍残留
- 将 `resolveProfileId(...)` 从 legacy `FakePlayer` 放宽到通用 `EntityPlayerMP`，使 nextgen 在线 bot 会优先使用自身 `GameProfile` 的真实 UUID 清理 `playerdata/stats`
- 为 `CommandPlayerTest` 新增 `purgeUsesLifecycleManagerWhenRuntimeOnlyBotIsOnline()`，覆盖 nextgen runtime-only 假人的清理链路
- 为 `CommandPlayerTest` 新增 `purgeUsesOnlineRuntimeProfileIdForNextgenCleanup()`，覆盖 nextgen 在线 bot 的真实 profile UUID 文件清理
- 将 `BotLifecycleManagerTest.restoreNextGenReappliesPersistedServiceState()` 改成显式锁定 `BotRuntimeMode.NEXTGEN`，避免组合测试时受全局配置残留影响而误走 legacy 恢复分支
- 重新通过 `CommandPlayerTest` 与 `BotLifecycleManagerTest`，确认 purge 修复、profile UUID 清理与 nextgen 恢复态回归测试都已稳定

### 遇到的问题
- `purge` 之前虽然已经支持清离线持久化 bot，但在线分支仍默认先拿 legacy `FakePlayer`；对 runtime-only nextgen bot，这会导致“看起来 purge 成功，实际上在线 runtime 没有被 kill”
- 新补的恢复态回归测试单独跑是绿的，但和命令测试一起跑时会被别的测试留下的 `fakePlayerRuntimeMode=legacy` 污染，最终误走到 old restore path 并在轻量 server harness 上炸掉世界加载

### 做出的决定
- `purge` 后续统一以在线 `BotRuntimeView` 作为第一优先级入口，legacy `FakePlayer` 只作为实体/皮肤档案信息的可选补充，而不是主分支判定条件
- 运行时模式相关回归测试尽量显式固定 `runtimeMode()` 或测试配置，不再依赖全局静态配置的默认值

## 2026-04-21：让 nextgen runtime 复用真实 FollowService

### 已完成
- 将 `FollowService` 从 `FakePlayer` 绑定放宽到 `EntityPlayerMP`，保留 legacy 现有语义不变，同时允许 nextgen `GTstaffForgePlayer` 直接复用原跟随服务
- 将 `FollowService.ServerProvider` / `CrossDimensionMover` 与测试构造器放宽到可复用层级，使 nextgen runtime 测试能直接注入跟随服务桩
- 将 `NextGenFollowRuntime` 从纯状态壳改为真正持有 `FollowService` 的实现，`following / targetUUID / range / teleportRange / tick()` 现已全部委托到底层真实服务
- 将 `NextGenBotRuntime.tickRuntimeServices()` 扩展为按 `follow -> monitor` 顺序驱动 nextgen 运行时服务
- 将 `CommandPlayer.handleFollow(... stop)` 与 `FakePlayerManagerService.stopFollow(...)` 的“清移动输入”逻辑收口到 runtime-neutral `EntityPlayerMP`，nextgen 停止跟随后不会残留上一 tick 的移动量
- 新增并通过 `NextGenBotRuntimeServicesTest.nextGenFollowTickUsesFollowService()`，并重新通过 `FollowServiceTest`、`GTstaffForgePlayerTest`、`CommandPlayerTest`、`FakePlayerManagerServiceTest`

### 遇到的问题
- `FollowService` 之前虽然逻辑已经成熟，但类型边界卡在 `FakePlayer`，导致 nextgen 只能复制状态而无法接回真正的跟随/跨维度/重试链路；这轮的核心工作就是把服务本身泛化，而不是再写一套 nextgen 跟随逻辑

### 做出的决定
- `follow` 与 `monitor` 一样，继续采用“复用 legacy 服务实现 + runtime facade 包装 + 在 `GTstaffForgePlayer` runtime phase 驱动”的迁移方式，而不是维护两套平行行为

## 2026-04-21：让 nextgen runtime 的 monitor 从状态壳接回真实扫描与 tick 链

### 已完成
- 为 `GTstaffForgePlayer` 的 `onUpdate()` 增加 runtime services phase，执行顺序现为 `base -> action -> runtime -> living`，为 nextgen 服务类接回逐 tick 行为留出稳定挂点
- 为 `BotMonitorRuntime` 增加默认 `scanNow()`，让 `CommandPlayer` 与 `FakePlayerManagerService` 可以统一请求“立即扫描”，而不需要感知 runtime 类型
- 将 `MachineMonitorService` 扩展为 runtime-neutral：新增基于 `EntityPlayerMP` 的 `tick(...)`、`scanNow(...)` 与扫描/发消息链路，legacy `FakePlayer` 继续复用同一实现
- 将 `NextGenMonitorRuntime` 从简单状态壳改为真正持有 `MachineMonitorService` 的实现，现已支持 `scanNow()`、`overviewMessage()` 与逐 tick 监控提醒
- 更新 `LegacyMonitorRuntime` 以复用新的 `scanNow()`，顺手让 legacy 的手动扫描路径也不再只是读缓存概览
- 新增并通过 `GTstaffForgePlayerTest`、`NextGenBotRuntimeServicesTest`，确认 nextgen runtime services phase 已进入 tick 链，且 `NextGenMonitorRuntime.scanNow()` 会真正调用扫描逻辑

### 遇到的问题
- `CommandPlayer` 与 UI 层原先都只会读 `overviewMessage()`，这在 nextgen 下意味着“如果 tick 还没跑过，就只能看到空壳状态”；本轮通过 `scanNow()` 把“立即扫描”语义单独提出来，避免命令/UI 继续读旧缓存

### 做出的决定
- `monitor` 继续走“runtime-neutral service + runtime phase hook”的迁移方式，而不是为 nextgen 单独复制一套 GT 扫描逻辑；后续 `follow` 也优先沿着这条路线做

## 2026-04-21：让 nextgen runtime 的敌对刷怪驱逐真正生效

### 已完成
- 新增 `MonsterRepellentServiceTest`，覆盖“仅注册 runtime-only nextgen bot、开启 repel 后，怪物刷怪事件会被真正拒绝”的回归场景
- 将 `MonsterRepellentService` 的判定源从 legacy `FakePlayerRegistry.getAll()` 改为在线 `BotRuntimeView` 列表：按 `runtime.repel().repelling()`、`runtime.repel().repelRange()` 与 `runtime.entity().asPlayer()` 的当前位置进行统一计算
- 确认 `repel` 不再只是 facade 状态切换；nextgen runtime-only bot 现在也能通过 Forge `LivingSpawnEvent.CheckSpawn` 真实拦截敌对刷怪
- 重新通过 `MonsterRepellentServiceTest` 以及相关 `FakePlayerManagerServiceTest`、`CommandPlayerTest` 回归

### 遇到的问题
- `NextGenRepelRuntime` 之前虽然能记录“是否开启驱逐”和驱逐半径，但全局刷怪事件仍只遍历 legacy 假人实体表，导致 nextgen bot 完全没有机会参与判定

### 做出的决定
- 继续优先把“状态壳但无真实行为”的 nextgen 服务逐个接回事件或 tick 链路；`repel` 已完成后，后续重点转向 `follow / monitor`

## 2026-04-21：补齐 nextgen runtime 的 tick 驱动、权限链路与背包/ServerUtilities 兼容

### 已完成
- 为 `GTstaffForgePlayer` 补上 `onUpdate() -> runBaseUpdate() -> actionPack.onUpdate() -> onLivingUpdate()` 的 nextgen 动作逐 tick 驱动，并新增 `GTstaffForgePlayerTest` 回归覆盖执行顺序
- 引入 `PlayerVisualSync` 运行时无关接口，让 `PlayerActionPack`、`FeedbackSync`、`FakePlayerClientUseCompat` 对 legacy `FakePlayer` 与 nextgen `GTstaffForgePlayer` 共用装备同步与挥手动画广播
- 将 `PermissionHelper` 与 `CommandPlayer` / `FakePlayerManagerService` 的多处权限校验改为 runtime-neutral，runtime-only bot 现在也能正确按 owner / OP 策略判定
- 将 `ServerUtilitiesCompat.isFakePlayer(...)` 扩展到识别 `GTstaffForgePlayer`，避免 nextgen bot 在 `ServerUtilities` 侧重新被当成真实玩家
- 将 `FakePlayerInventoryView`、`FakePlayerInventoryContainer`、`FakePlayerInventoryGuiHandler` 从 legacy `FakePlayer` 入参改为可承载任意 GTstaff bot 实体，runtime-only nextgen bot 的 `inventory open` 现已能解析并打开服务端容器
- 新增并通过 `ServerUtilitiesCompatTest`、`FakePlayerInventoryContainerTest`、`FakePlayerInventoryGuiHandlerTest`，并重新通过一轮更宽的 nextgen 回归集

### 遇到的问题
- `CommandPlayerTest` 中 `TestCommandSender.messages` 未初始化，导致几条命令路由测试在发送聊天消息时空指针；已先修复测试桩，再继续推进业务改动
- nextgen 背包打开链路真正的阻塞点不在 `NextGenInventoryRuntime.openInventoryManager(...)`，而在 UI handler 和容器签名仍硬绑 `FakePlayer`，导致即使命令层已转到 runtime facade，GUI 依旧只对 legacy 生效

### 做出的决定
- 这轮优先补“桥接层缺口”而不是继续扩大行为迁移范围：先让 `tick / 权限 / 背包打开 / ServerUtilities` 达到 nextgen 可用，再继续处理 `follow / monitor / repel` 的真实行为平移
- 背包 GUI 解析继续沿用 `entityId` 作为打开参数，但服务端解析改为遍历 registry 中的在线 `BotRuntimeView`，避免再次写死到 legacy `FakePlayerRegistry.getAll()`

## 2026-04-21：完成 nextgen fake player runtime Wave D implementation plan

### 已完成
- 基于已批准总 spec 与已落地的 Wave C 状态，编写 Wave D 计划文档：`docs/superpowers/plans/2026-04-21-gtstaff-nextgen-fake-player-runtime-wave-d.md`
- 将 Wave D 范围明确扩展为“默认切换前的完整闭环”：不仅覆盖 `spawn / shadow / restore / fakePlayerRuntimeMode` 默认值切换，也显式纳入 `BotActionRuntime`，确保 `attack / use / jump / move / look / hotbar / stop` 不会因 nextgen 默认化而降级
- 在计划中补上 runtime-aware restore / skin rebuild / mixed rollback 的执行顺序，避免开服恢复仍然天然回落到 legacy
- 完成一轮计划自检：确认没有真正占位段落，也没有再遗漏 `handleManipulation(...)` 这条 legacy-only 动作链

### 遇到的问题
- Wave C 完成后，`spawn / shadow / restore` 虽然还没切到 nextgen，但更关键的阻塞其实是 `handleManipulation(...)` 仍只认 legacy `FakePlayer`；如果照最初的保守想法只切创建链，默认 runtime 改成 `nextgen` 后动作命令会直接功能降级

### 做出的决定
- Wave D 计划不再采用“只切 spawn/restore”的窄版本，而是按总 spec 要求把 `BotActionRuntime`、生命周期工厂、restore scheduler 和 rollback 一并纳入默认切换范围
- 这一轮仍然不计划删除 legacy 实现，只做“nextgen 默认启用 + legacy/mixed 明确可回退”的切换闭环

---

## 2026-04-21：完成 nextgen fake player runtime Wave C 业务服务 facade 迁移

### 已完成
- 落地 runtime 服务契约：`BotFollowRuntime`、`BotMonitorRuntime`、`BotRepelRuntime`、`BotInventoryRuntime`、`BotInventorySummary`，并将 `BotRuntimeView` 扩展为统一暴露四类业务服务入口
- 为 legacy 假人补齐 `LegacyFollowRuntime`、`LegacyMonitorRuntime`、`LegacyRepelRuntime`、`LegacyInventoryRuntime`，`LegacyBotHandle` 现可完整承载 monitor / repel / inventory / follow 的 facade 读写
- 为 `FakePlayerRegistry` 新增在线 `BotRuntimeView` 注册与查询能力：`registerRuntime(...)`、`getRuntimeView(...)`、runtime-first 的 `getBotHandle(...)` / `getAllBotHandles()`，为后续 nextgen 在线 bot 接线准备好 registry 入口
- `FakePlayerManagerService` 现已改为通过 runtime facade 处理 bot 列表、inventory 摘要、monitor、repel、follow 与 inventory manager 打开逻辑；无在线 runtime 时才回退到离线快照描述
- `CommandPlayer` 的 `monitor / repel / inventory / follow` 子命令已改走 runtime facade，runtime-only bot 也能走命令层状态读写；`handleManipulation(...)` 仍保持 legacy `FakePlayer` 动作链
- 将 `NextGenBotRuntime` 的匿名占位实现替换为正式 service shell：`NextGenFollowRuntime`、`NextGenMonitorRuntime`、`NextGenRepelRuntime`、`NextGenInventoryRuntime`
- 新增并通过 Wave C 回归测试：`NextGenBotRuntimeServicesTest`、`CommandPlayerTest`、`FakePlayerManagerServiceTest`、`FakePlayerRegistryTest`、`LegacyBotHandleServicesTest`、`LegacyBotHandleTest`、`BotSessionTest`
- 通过 Wave C 验证命令：`./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntimeServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.ui.FakePlayerManagerServiceTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleServicesTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest`

### 遇到的问题
- `CommandPlayer.handleFollow(...)` 里的中文提示文本在 PowerShell 默认代码页下显示成乱码，最初导致补丁匹配错位；最终改为按 UTF-8 读取源码并分段修正，避免把 follow 逻辑误插到 monitor 分支
- 现有 `FakePlayerRegistry` 原本只跟踪 `FakePlayer` 实体，无法让 runtime-only bot 出现在 manager/command/runtime 查询路径中；本轮顺手补齐了在线 runtime registry，才使 Wave C 的 facade 迁移真正闭环

### 做出的决定
- Wave C 到此为止只完成“业务服务 facade 化 + nextgen 状态壳”，默认 runtime 继续保持 `legacy`，不提前切换 `spawn / shadow / restore` 等创建链
- `CommandPlayer.handleManipulation(...)`、动作执行链和真实 nextgen 行为驱动仍留给后续 Wave D/更后续阶段处理，本轮重点是让命令/UI/registry 不再被 `FakePlayer` 类型直接绑死

---

## 2026-04-21：完成 nextgen fake player runtime Wave C implementation plan

### 已完成
- 基于总 spec 的 Wave C 边界，编写实现计划：`docs/superpowers/plans/2026-04-21-gtstaff-nextgen-fake-player-runtime-wave-c.md`
- 将 Wave C 范围明确限制为业务服务层迁移：`follow / monitor / repel / inventory / UI manager` 改走 runtime facade，不提前切默认 runtime，也不把 `spawn / shadow / restore` 拉进本轮
- 计划中已拆出 `BotFollowRuntime`、`BotMonitorRuntime`、`BotRepelRuntime`、`BotInventoryRuntime`、`BotInventorySummary` 等运行时服务接口，以及 `Legacy*Runtime` / `NextGen*Runtime` 适配与状态壳
- 计划中已为 `FakePlayerManagerService`、`CommandPlayer`、`LegacyBotHandle`、`NextGenBotRuntime`、相关回归测试与三份项目文档更新分别给出 TDD 步骤、验证命令与提交切片

### 遇到的问题
- 当前代码里 `CommandPlayer` 与 `FakePlayerManagerService` 对 `FakePlayer` 直连较深，尤其是 monitor / repel / inventory / follow 的读写路径分散，因此 Wave C 计划必须先把 runtime service contract 立住，再迁命令和 UI，否则后续 nextgen 很容易再次被 legacy 细节反向拖住

### 做出的决定
- Wave C 继续遵守“双轨迁移”节奏：动作链保持 Wave B 结果不动，业务服务先 facade 化，默认 runtime 保持 `legacy`
- `handleManipulation(...)` 这类动作命令本轮仍显式保留在 legacy `FakePlayer` 路径，避免把 Wave D 的默认切换风险提前卷入 Wave C

---

## 2026-04-20：完成 nextgen fake player runtime Wave B 动作链迁移

### 已完成
- 编写 Wave B implementation plan：`docs/superpowers/plans/2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-b.md`
- 新增 `fakeplayer.action` 分层：`TargetingService` / `TargetingResult`、`UseExecutor` / `UseResult`、`AttackExecutor` / `AttackResult`、`FeedbackSync`
- `PlayerActionPack` 现已把目标选择、使用执行、攻击执行与挥手反馈分别委托到独立执行边界，同时保留现有 `performBlockActivationUse(...)`、`performDirectItemUse(...)`、`performClientUseBridge(...)`、`performEntityAttack(...)`、`attackBlock(...)` 等兼容 hook
- 新增默认关闭的 `ActionDiagnostics` 与配置项 `fakePlayerActionDiagnostics`，用于在需要时记录 fake player 的 use/attack 执行结果，而不改变默认行为
- 新增并通过 Wave B 回归测试：`TargetingServiceTest`、`UseExecutorTest`、`AttackExecutorTest`，并再次通过 `PlayerActionPackTest`、`CommandPlayerTest`、`FakePlayerRegistryTest`、`LegacyBotHandleTest`、`BotSessionTest`
- 通过 Wave B 验证命令：`./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.action.TargetingServiceTest --tests com.andgatech.gtstaff.fakeplayer.action.UseExecutorTest --tests com.andgatech.gtstaff.fakeplayer.action.AttackExecutorTest --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest`

### 遇到的问题
- 诊断日志初版在调用点提前读取 `player.getCommandSenderName()`，即使 `fakePlayerActionDiagnostics=false` 也会让测试桩触发空指针；最终改为把 `EntityPlayerMP` 直接传入 `ActionDiagnostics`，并在配置开关通过后才解析 bot 名称
- `log.md` 在终端输出里仍会因为 PowerShell 当前代码页显示成乱码，但文件本身继续按 UTF-8 维护；本轮没有把日志内容回退成错误编码

### 做出的决定
- Wave B 只做 legacy 动作链的内部解耦与诊断补点，不提前切换默认 runtime，也不在这轮直接接入 nextgen 实体执行
- `PlayerActionPack` 的重构继续采用“外层抽象 + 内层保留 legacy hook”的迁移策略，优先保证现有客户端兼容桥、攻击 fallback 和动作反馈行为不回退

---


## 2026-04-20：完成 nextgen fake player runtime Wave A 运行时解耦骨架

### 已完成
- 新增运行时抽象层 `BotRuntimeType`、`BotHandle`、`BotRuntimeView`、`BotEntityBridge`，并为现有 `FakePlayer` 落地 `LegacyBotHandle`
- 为 `FakePlayerRegistry` 增加 `RuntimeType` / `SnapshotVersion` 元数据持久化、旧存档默认回落到 `LEGACY` 的兼容逻辑，以及 `getBotHandle(...)` / `getAllBotHandles()` 只读查询入口
- 新增 inert 的 nextgen skeleton：`GTstaffForgePlayer`、`BotSession`、`NextGenBotRuntime`，但默认运行时仍保持 `legacy`
- `Config` 新增 `fakePlayerRuntimeMode`，`CommandPlayer.handleList(...)` 改为通过 runtime handle 读取 bot 名称，现有 legacy 行为不变
- 通过 Wave A 验证命令：`./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.command.CommandPlayerTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.fakeplayer.runtime.LegacyBotHandleTest --tests com.andgatech.gtstaff.fakeplayer.runtime.BotSessionTest`

### 遇到的问题
- 新建 worktree 初次运行 Gradle 时触发 Git `dubious ownership` 噪音；已通过将该 worktree 加入 `safe.directory` 消除，避免后续测试输出被污染
- `net.minecraftforge.common.util.FakePlayer` 继承链中的 `mcServer` 为 final，`GTstaffForgePlayer` 不能手动重写该字段；最终改为完全复用 Forge `FakePlayer(WorldServer, GameProfile)` 既有初始化逻辑

### 做出的决定
- Wave A 只引入运行时解耦层、registry 元数据和 nextgen 空壳，不切默认 runtime，也不提前迁移动作链
- 命令层当前仅把 `/player list` 这类只读查询切到 runtime handle，其他需要真实实体操作的路径继续保持 legacy `FakePlayer` 直连

---

## 2026-04-20：完成 nextgen fake player runtime Wave A implementation plan

### 已完成
- 基于 `docs/superpowers/specs/2026-04-20-gtstaff-nextgen-fake-player-runtime-design.md` 编写 Wave A 计划文档 [2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-a.md](D:/Code/GTstaff/docs/superpowers/plans/2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-a.md)
- 将计划范围明确限制在 Wave A：运行时抽象、registry 元数据解耦、nextgen entity/session skeleton、runtime mode feature flag，暂不进入动作链迁移
- 完成计划自检：确认无真正占位符、未越界到 Wave B-C-D，且和已批准 spec 的分 wave 边界一致

### 遇到的问题
- `writing-plans` 的标准要求每步都写得足够具体，因此这次没有继续写总工程大计划，而是只落第一个可执行切片，避免计划本身失控

### 做出的决定
- 继续执行时应先按该 Wave A 计划落地，再单独为 Wave B 编写新的 implementation plan
- 在用户选择执行方式前，不直接开始改代码

---

## 2026-04-20：完成 nextgen fake player runtime 重构设计 spec

### 已完成
- 与用户确认“允许动底层类型与迁移、先做完整新架构骨架和迁移层、最终不接受功能降级”的设计边界
- 编写设计文档 [2026-04-20-gtstaff-nextgen-fake-player-runtime-design.md](D:/Code/GTstaff/docs/superpowers/specs/2026-04-20-gtstaff-nextgen-fake-player-runtime-design.md)
- 设计文档已通过占位符/一致性/范围自检，并单独提交为 `a7e2dc0` (`Add nextgen fake player runtime design spec`)

### 遇到的问题
- `git commit` 过程中工作区存在大量未提交改动，因此本轮只将 spec 单文件纳入提交，避免把其他开发中的代码与文档状态混进设计提交

### 做出的决定
- 后续实现必须基于该 spec 分 wave 推进，不允许直接跳过运行时解耦去硬切 `FakePlayer` 父类
- 在用户审阅并确认 spec 之前，不进入 implementation plan 与代码实施阶段

---

## 2026-04-20：对照 fabric-carpet 攻击实现排查 fake player 攻击无效问题

### 已完成
- 阅读 `GTNH LIB/fabric-carpet-master` 中的 [EntityPlayerActionPack.java](D:/Code/GTNH LIB/fabric-carpet-master/src/main/java/carpet/helpers/EntityPlayerActionPack.java:251) 与 [Player_fakePlayersMixin.java](D:/Code/GTNH LIB/fabric-carpet-master/src/main/java/carpet/mixins/Player_fakePlayersMixin.java:24)，对照其 fake player 攻击与命中实现
- 确认 carpet 在攻击实体时走的是“稳定射线命中 + 原生 `player.attack(entity)`”主链，并没有采用“服务端强制扣血”式 fallback
- 对照 1.7.10 原版 `EntityPlayer.attackTargetEntityWithCurrentItem(...)` 后确认：GTstaff 当前主链与 carpet 在语义上基本等价，单纯替换攻击 API 预计不能直接修复现象
- 额外排查 GTNH 依赖源码后确认：大量 Forge 模组按 `instanceof net.minecraftforge.common.util.FakePlayer` 或 `playerNetServerHandler == null` 识别 fake player，而 GTstaff 假人当前处于两者之间的兼容灰区

### 遇到的问题
- carpet 项目基于 Fabric 新版本，虽然可提供设计参照，但它的 fake player 生态与 1.7.10 Forge 并不一致，不能直接搬运为补丁
- 当前最像根因的仍不是“攻击方法选错了”，而是“目标命中链没有稳定落到实体”或“外部模组按 attacker 类型把 GTstaff 假人当成异常玩家处理”

### 做出的决定
- 暂不基于 carpet 继续叠加新的伤害 fallback；下一步应优先做运行时命中链/攻击上下文诊断，先确认 `getTarget()` 是否稳定拿到实体，以及是否有外部模组按 attacker 身份吞掉攻击结果

---

## 2026-04-20：确认并重新打包当前 v1.0.2 jar

### 已完成
- 重新运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 本轮构建结果为 `BUILD SUCCESSFUL`，且 `jar` / `reobfJar` / `sourcesJar` 均为 `UP-TO-DATE`，说明当前工作区对应的 `v1.0.2` 产物已经是最新状态
- 再次确认当前产物列表仍为 `build/libs/gtstaff-v1.0.2.jar`、`build/libs/gtstaff-v1.0.2-dev.jar`、`build/libs/gtstaff-v1.0.2-sources.jar`

### 遇到的问题
- 本轮没有新的编译错误；由于代码自上次打包后未发生变更，Gradle 直接复用了现有产物而没有重新生成新时间戳文件

### 做出的决定
- 当前继续沿用现有 `v1.0.2` jar 作为最新可交付产物；只有在代码再次变更后才需要重新生成新的二进制内容

---

## 2026-04-20：修复 fake player 攻击 living 实体仍然不掉血的问题并重新打包 v1.0.2

### 已完成
- 在 `PlayerActionPack` 中收紧实体攻击成功判定：对 `EntityLivingBase` 仅在真实掉血或死亡时才视为原版攻击生效，不再把单纯的击退标记、燃烧标记或受击计时变化当成成功
- 将 living 实体的 fallback 从“再补一次 `attackEntityFrom(...)`”调整为“直接在服务端强制扣减生命值并设置受击状态”，避免继续被 `LivingAttackEvent` / `LivingHurtEvent` 一类事件链吞掉
- 为 `PlayerActionPackTest` 新增两条回归测试，覆盖“原版左键只改 `velocityChanged` 但不掉血”和“`attackEntityFrom(...)` 返回 false 时仍必须掉血”的场景
- 通过 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest`
- 重新运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`，更新 `build/libs/gtstaff-v1.0.2.jar`

### 遇到的问题
- 之前那版“实体攻击 fallback 修复”对非 living 实体与部分普通场景有效，但对用户反馈的“任何实体都不会掉血”仍不够：因为 fallback 里对 living 目标继续调用 `attackEntityFrom(...)`，本质上还是会回到同一条可能被外部模组取消的伤害事件链
- 沙箱环境下 Gradle wrapper 依旧无法直接写入本机用户目录缓存，本轮测试与打包仍需要沿用允许访问本机 Gradle 缓存的提权命令

### 做出的决定
- 当前 fake player 的实体左键兼容策略调整为分层处理：原版 `attackTargetEntityWithCurrentItem(...)` 仍保留为第一优先级；如果目标是 living 且这次攻击没有造成真实掉血，则 GTstaff 直接在服务端执行强制生命扣减，优先保证“收到攻击指令就必须产生可见结果”

---

## 2026-04-20：重新打包 v1.0.2 以包含实体攻击 fallback 修复

### 已完成
- 在完成 fake player 实体攻击 fallback 修复后，重新运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 更新产物 `build/libs/gtstaff-v1.0.2.jar`
- 同步更新 `build/libs/gtstaff-v1.0.2-dev.jar` 与 `build/libs/gtstaff-v1.0.2-sources.jar`

### 遇到的问题
- 重新打包阶段仍需要沿用离线 `assemble`：当前环境下继续避免触发在线依赖解析与额外格式检查，只做产物重建

### 做出的决定
- 当前 `v1.0.2` 产物继续作为同版本覆盖包使用；只要功能修复还在同一发布轮次内，就直接重打同名 jar 覆盖旧文件

---

## 2026-04-20：修复 fake player 攻击实体时原版伤害链无效的问题

### 已完成
- 在 PlayerActionPack 中为实体左键补上一层“原版攻击后无可观察受击结果时才触发”的最小服务端伤害 fallback，继续优先保留 attackTargetEntityWithCurrentItem(...) 作为主链
- 新增 PlayerActionPackTest.attackFallsBackToDirectDamageWhenVanillaAttackHasNoEffect() 回归测试，覆盖“原版攻击无效果时仍应对目标调用 attackEntityFrom(...)”的场景
- 通过 ./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest
- 通过 ./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.integration.FakePlayerClientUseCompatTest --tests com.andgatech.gtstaff.integration.FakePlayerMovementCompatTest

### 遇到的问题
- 现有假人左键链路在部分整合环境里会发出攻击动作但目标没有任何受击结果：原版 attackTargetEntityWithCurrentItem(...) 虽然被调用，但在用户环境里并不总能真正落成可见伤害，需要在 GTstaff 侧补一个尽量保守的兜底
- 离线单测里 player.swingItem() 会掩盖实体攻击回归问题：补测试时先把挥手动画从这条例里隔离，只验证实体伤害 fallback 是否发生，避免被测试桩自身的动画空指针干扰

### 做出的决定
- 实体攻击兼容继续采用“保留原版主链，仅在攻击后没有任何可观察受击迹象时才补服务端直伤”的策略，而不是整条重写左键攻击逻辑

---


## 2026-04-20：重新打包 v1.0.2 以包含空挥手反馈

### 已完成
- 在 `attack/use` 无目标时也执行空挥手反馈后，重新运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 更新产物 `build/libs/gtstaff-v1.0.2.jar`
- 同步更新 `build/libs/gtstaff-v1.0.2-dev.jar` 与 `build/libs/gtstaff-v1.0.2-sources.jar`

### 遇到的问题
- **Gradle wrapper 在沙箱里仍无法创建用户目录锁文件**：本轮依旧需要切到允许访问本机 Gradle 缓存的提权命令完成打包

### 做出的决定
- 当前 `v1.0.2` 后续如继续追加行为修复，继续采用“保持版本号不变、显式重打包覆盖同名产物”的策略，直到需要正式发布新版本

---

## 2026-04-20：让 attack/use 指令无目标时也执行空挥手反馈

### 已完成
- `PlayerActionPack` 新增统一的 `performSwingAnimation()`，把 `player.swingItem()` 与 fake player 的 `broadcastSwingAnimation()` 收口到同一入口
- `performAttack(...)` 现在在“无目标”“实体目标为空”以及其他未命中的情况下，仍会执行一次空挥手并返回成功，避免 `/player <name> attack once` 看起来完全没反应
- `performUse(...)` 现在在“没有方块交互、没有物品使用、没有兼容桥命中”的情况下，也会执行一次空挥手并施加原有 3 tick 冷却，避免 `/player <name> use once` 看起来完全没反应
- 为回归补充 `PlayerActionPackTest`，验证 `attack` / `use` 在没有实际命中目标时仍会产生一次可见挥手反馈
- 通过 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.integration.FakePlayerClientUseCompatTest --tests com.andgatech.gtstaff.integration.FakePlayerMovementCompatTest`

### 遇到的问题
- **`Action.once()` 只给一次执行机会**：原实现里如果那一 tick 没有成功命中实体、方块或物品逻辑，指令仍会消耗掉，但客户端完全看不到假人有任何动作

### 做出的决定
- 默认把“收到 `attack/use` 指令后至少给出一次可见挥手反馈”视为正确语义，而不是维持“只有真实命中才有动画”的严格命中反馈模式

---

## 2026-04-20：重新打包 v1.0.2 以包含实体攻击修复

### 已完成
- 在修复 fake player 近距离无法锁定实体攻击后，重新运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 更新产物 `build/libs/gtstaff-v1.0.2.jar`
- 同步更新 `build/libs/gtstaff-v1.0.2-dev.jar` 与 `build/libs/gtstaff-v1.0.2-sources.jar`

### 遇到的问题
- **Gradle wrapper 在沙箱里仍无法创建用户目录锁文件**：本轮依旧需要切到允许访问本机 Gradle 缓存的提权命令完成打包

### 做出的决定
- `v1.0.2` 继续作为当前工作区版本号；后续功能修复如果不再涨版本，也需要显式重新打包覆盖同名产物

---

## 2026-04-20：修复 fake player 近距离无法锁定实体攻击

### 已完成
- 对照原版 `EntityRenderer.getMouseOver()`，修正 `PlayerActionPack.getTarget()` 的实体判定算法：实体搜索范围改为 `boundingBox.addCoord(lookVec * reach).expand(1,1,1)`，并补上“视线起点已落在实体包围盒内”的 `isVecInside(...)` 处理
- 同步补上原版的骑乘实体分支判断，避免后续和实体射线优先级再次偏离 vanilla 行为
- 为回归补充 `PlayerActionPackTest`，覆盖“近距离视线起点已进入实体命中盒时仍能锁定实体”的场景
- 通过 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.integration.FakePlayerClientUseCompatTest --tests com.andgatech.gtstaff.integration.FakePlayerMovementCompatTest`

### 遇到的问题
- **原有实体射线判定过于简化**：缺少 vanilla 的 `addCoord(...).expand(...)` 搜索盒与 `isVecInside(...)` 分支，导致假人贴近实体或命中盒与视线起点重叠时可能完全取不到实体目标

### 做出的决定
- fake player 的实体攻击目标选择继续尽量向 vanilla `getMouseOver()` 对齐，而不是维持自定义的简化射线逻辑

---

## 2026-04-20：修正版本号到 v1.0.2 并重新打包

### 已完成
- 将 `gradle.properties` 中的 `modVersion` 从 `v1.0.1` 更新为 `v1.0.2`
- 重新确认当前工作区已包含 TST 阎魔刀物品兼容与 OpenBlocks 电梯移动兼容改动，并检查已打出的 `v1.0.1` jar 内确实包含对应新类
- 重新运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`，产出 `build/libs/gtstaff-v1.0.2.jar`、`build/libs/gtstaff-v1.0.2-dev.jar`、`build/libs/gtstaff-v1.0.2-sources.jar`

### 遇到的问题
- **前一轮打包时版本源没有同步更新**：jar 文件名跟随 `gradle.properties` 的 `modVersion`，虽然功能已经编进产物，但因为版本源仍是 `v1.0.1`，最终文件名和运行时版本都保持在旧版本

### 做出的决定
- 以后把“更新 `modVersion`”视为发布/重打包前的必要检查项，避免再次出现“功能已合并但产物版本号没涨”的情况

---

## 2026-04-20：重新打包当前 GTstaff jar

### 已完成
- 运行 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble`
- 成功生成 `build/libs/gtstaff-v1.0.1.jar`
- 同时生成 `build/libs/gtstaff-v1.0.1-dev.jar` 与 `build/libs/gtstaff-v1.0.1-sources.jar`

### 遇到的问题
- **Gradle wrapper 在沙箱里无法创建用户目录锁文件**：首次打包因 `C:\Users\CodexSandboxOffline\.gradle\wrapper\...\.lck` 无法创建而失败，随后改用允许访问本机 Gradle 缓存的提权命令完成构建

### 做出的决定
- 继续沿用 `assemble` 作为当前工作区的标准打包入口；离线环境下优先使用 `--offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true`

---

## 2026-04-20：为 fake player 加上 OpenBlocks 电梯类移动触发兼容

### 已完成
- `PlayerActionPack` 新增 `MovementTrigger` 与 `performMovementCompatBridge(...)` 钩子；`jump` 现在会先尝试移动兼容桥，`setSneaking(true)` 只在从非潜行切到潜行的边沿触发一次兼容桥
- 新增 `integration/FakePlayerMovementCompat`，用纯反射方式按需探测 `OpenBlocks` 电梯相关类，避免 `GTstaff` 对外部模组形成硬编译或硬运行时依赖
- 新增 `OpenBlocksElevatorHandler`，在服务端复用 `ElevatorActionHandler.activate(...)`，让假人站在电梯方块上执行 `jump` 或开始潜行时，能够等效触发原本依赖客户端 `PlayerMovementEvent` 发包的上下楼逻辑
- 为回归补充 `PlayerActionPackTest`，验证 `jump` 与“开始潜行”都会命中移动兼容桥，且潜行只在 leading edge 触发
- 新增 `FakePlayerMovementCompatTest`，验证移动兼容桥的 handler 分发逻辑
- 通过 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.integration.FakePlayerMovementCompatTest`

### 遇到的问题
- **OpenBlocks 电梯不是右键触发**：它在客户端监听 `PlayerMovementEvent`，再发送 `ElevatorActionEvent` 到服务端，假人没有真实客户端，因此原生不会触发这条链路
- **`sneak` 在 GTstaff 里不是 action，而是状态切换**：如果只在 `ActionType.JUMP` 里补桥，仍然无法兼容电梯下行

### 做出的决定
- 不把电梯逻辑继续塞进现有的物品 `use` 兼容层，而是单独新增“移动触发兼容桥”，区分“客户端右键链路缺失”和“客户端移动事件链路缺失”两类问题
- `sneak` 兼容只在从非潜行切到潜行时触发一次，避免假人持续潜行时每 tick 反复尝试电梯下行

---

## 2026-04-20：修复 `log.md` 编码并整理日期顺序

### 已完成
- 确认 `log.md` 文件本体为 UTF-8，修正上一轮误写入的乱码条目
- 将本次“假人伪客户端物品使用桥 / TST 阎魔刀兼容”日志条目改写为正常中文内容
- 重新整理 `log.md` 条目顺序，按日期倒序排列，并在同一天内把较新的记录放到更前面

### 遇到的问题
- **之前的乱码主要来自写入方式错误**：文件里混入了一段以错误编码写入的文本，终端再次读取时就显示成乱码
- **现有日志顺序不是严格最新优先**：`2026-04-20` 的新增记录被追加到了文件底部，和 `log.md` 约定的“最新在前”不一致

### 做出的决定
- 以后继续以 UTF-8 方式维护 `log.md`
- `log.md` 统一保持“最新日期在最前，同日较新记录在更前”的整理规则

---

## 2026-04-20：为 fake player 加上 TST 阎魔刀类伪客户端物品使用兼容

### 已完成
- `PlayerActionPack.performUse(...)` 现在会把右键使用拆成“方块交互 + 直接物品使用 + 伪客户端物品使用桥”三段，其中最后一段为 fake player 的 `/player <name> use` 提供可扩展兼容入口
- 新增 `integration/FakePlayerClientUseCompat`，用纯反射方式按需探测 EnderIO / EnderCore / TST 运行时类型，避免 `GTstaff` 对外部模组形成硬编译或硬运行时依赖
- 新增 `TstYamatoClientUseHandler`，针对 `com.Nxer.TwistSpaceTechnology.common.item.ItemYamato` 在假人身上缺少客户端发包链路的场景，改为服务端直接复用 EnderIO `TravelController` 的 blink 目标搜索，并手动调用 `PacketTravelEvent.doServerTeleport(...)`
- 为回归补充 `PlayerActionPackTest`，验证普通 `use` 成功后仍可继续执行兼容桥，且兼容桥能拿到“方块交互是否已经成功”的状态
- 新增 `FakePlayerClientUseCompatTest`，验证兼容处理器的登记和匹配逻辑
- 通过 `./gradlew.bat --offline --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.PlayerActionPackTest --tests com.andgatech.gtstaff.integration.FakePlayerClientUseCompatTest`

### 遇到的问题
- **TST 阎魔刀的主效果写在客户端 `world.isRemote` 分支里**：假人没有真实客户端和对应的发包链路，因此不能只靠现有的 `tryUseItem(...)`
- **`GTstaff` 的编译和测试类路径不稳定包含 EnderIO / EnderCore**：如果直接 `import` 这些类，`FakePlayerClientUseCompat` 会在单测类加载阶段就抛 `NoClassDefFoundError`

### 做出的决定
- 不去硬模拟一个完整客户端，而是把“客户端计算目标 + 发包到服务端”的链路在服务端做等效桥接，只解决那些把效果写在客户端右键里的物品
- 为了保持兼容层可选可缺省，整个 `FakePlayerClientUseCompat` 改为纯反射实现，缺少外部模组时直接静默回退
- 阎魔刀兼容只在“未成功右键激活方块”且“假人非潜行”的前提下尝试，避免覆盖现有的方块交互语义和阎魔刀的潜行切换行为

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

## 2026-04-20：GTstaff 假人统一背包接入 Baubles Expanded 设计

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

## 2026-04-19：修复同 UUID 旧死实体阻塞后续跨维度跟随
### 已完成
- 定位到 `FollowService.findTargetPlayer(...)` 之前会返回 `playerEntityList` 里第一个 UUID 匹配的实体，即使它是旧的 `isDead=true` 残留对象
- 调整目标查找逻辑：优先返回同 UUID 的存活玩家实体，只有不存在存活实体时才回退到旧死实体
- 新增 `FollowServiceTest` 回归用例，覆盖“同 UUID 的旧死实体存在时，跟随逻辑仍会选中新的存活实体并继续跨维度传送”
- 重新通过 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true test --tests com.andgatech.gtstaff.fakeplayer.FollowServiceTest`

### 做出的决定
- `findTargetPlayer(...)` 不再使用“遇到第一个 UUID 匹配对象就直接返回”的策略，避免玩家重连、切维或实体替换期间被过期对象卡死

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

## 2026-04-18：重新构建包含 fake player 兼容修复的 jar

### 已完成
- 使用 `./gradlew.bat --offline assemble` 成功重新构建 GTstaff 产物
- 确认最新修复已打包进 `build/libs/gtstaff-b166ed7-master+b166ed77c1-dirty.jar`

### 做出的决定
- 当前继续使用离线 `assemble` 作为快速打包路径，不额外触发 `spotlessJavaCheck`

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

## 2026-04-17：Task 7 - Spawn 子窗口真实交互

### 已完成
- 新增 `FakePlayerManagerService`，把 MUI2 表单输入转换为 `/player <name> spawn ...` 命令参数。
- 将 `FakePlayerSpawnWindow` 改为真实表单，支持 `name`、`x / y / z`、`dimension`、`gameMode` 输入与服务端执行。
- 确认 `GTNHLib-0.7.10` 中当前最直接可用的参考 API 是 `ServerThreadUtil`。

### 做出的决定
- Spawn 逻辑不在 UI 层重复实现，而是统一复用现有 `/player spawn` 命令路径。

## 2026-04-17：Task 7 - 跨重启自动恢复 fake player 实体

### 已完成
- 扩展 `FakePlayerRegistry` 的持久化快照字段，新增 `PersistedBotData`、`BotRestorer` 与 `restorePersisted(...)`。
- 新增 `FakePlayer.restorePersisted(...)`，恢复 bot 的维度、坐标、朝向、游戏模式、飞行状态与监控状态。
- 在 `CommonProxy.serverStarted(...)` 接入 registry 自动恢复，在 `serverStopping(...)` 保存 registry。
- 为 `FakePlayerRegistryTest` 补充 `save -> load -> restore` 全链路回归。

### 做出的决定
- 将 IO 读取与 Minecraft 实体恢复分离，避免在 `load(...)` 中直接构建实体。

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
## 2026-04-21：推进 nextgen fake player runtime Wave D 生命周期与命令接线

### 已完成
- 为 nextgen fake player 补齐 `NextGenActionRuntime`、`GTstaffForgePlayer` 自持 `PlayerActionPack`、`NextGenBotFactory.spawn/restore`
- 新增 `BotRuntimeMode` 与 `BotLifecycleManager`，并让 `FakePlayerRegistry` / `FakePlayerRestoreScheduler` 支持 `BotRuntimeView` 级别的 runtime-aware 恢复
- `CommandPlayer.handleSpawn(...)` 与 `handleManipulation(...)` 已切到 lifecycle/action facade，runtime-only bot 也能走 `attack/use/jump/move/look/hotbar/stop`
- 通过定向离线验证：`NextGenBotFactoryTest`、`NextGenBotRuntimeServicesTest`、`BotSessionTest`、`BotLifecycleManagerTest`、`FakePlayerRegistryTest`、`FakePlayerRestoreSchedulerTest`、`CommandPlayerTest`

### 遇到的问题
- GTNH Gradle 在线解析仍会因 TLS/metadata 失败卡住，当前验证必须固定走带 `--offline` 与禁 buildscript update 的命令
- 新增 runtime-aware restore 入口后，`restorePersisted(...)` 的 lambda 重载出现二义性；最终改为单独命名 `restorePersistedRuntimes(...)`

### 做出的决定
- 先完成 spawn/manipulation 与 restore 生命周期切换，再继续补 `shadow` 与默认 `fakePlayerRuntimeMode=nextgen` 的最终切换
## 2026-04-21：收口 nextgen fake player runtime Wave D 默认切换

### 已完成
- 完成 `NextGenBotFactory.shadow(...)`、`BotLifecycleManager.shadow(...)` 与 `BotLifecycleManager.kill(...)`，让 nextgen runtime 覆盖 `shadow / kill / spawn / restore` 全生命周期关键路径
- `CommandPlayer` 已改为通过 lifecycle facade 处理 `shadow` 与 runtime-only bot 的 `kill`，并新增可覆写的 `resolvePlayer(...)`，避免命令层再次绑死全局 legacy 取人逻辑
- `PermissionHelper.cantSpawn(...)` 改为基于 `FakePlayerRegistry.contains(...)` 拦截 runtime-only 假人的重名创建；`Config.fakePlayerRuntimeMode` 默认值正式切到 `nextgen`，并通过 `BotRuntimeMode` 统一归一化
- 新增并通过 `NextGenBotFactoryTest`、`BotLifecycleManagerTest`、`CommandPlayerTest`、`PermissionHelperTest`、`ConfigTest` 对应回归，确认 `shadow / kill / duplicate-name / default-runtime` 路径可用
- 补跑 Wave D 扩大回归：`LegacyBotHandleActionTest`、`LegacyBotHandleServicesTest`、`NextGenBotFactoryTest`、`NextGenBotRuntimeServicesTest`、`BotLifecycleManagerTest`、`BotSessionTest`、`FakePlayerRegistryTest`、`FakePlayerRestoreSchedulerTest`、`FakePlayerSkinRestoreSchedulerTest`、`CommandPlayerTest`、`FakePlayerManagerServiceTest`、`PermissionHelperTest`、`ConfigTest`
- 执行离线 `assemble`，确认最新产物为 `build/libs/gtstaff-v1.0.2.jar`、`build/libs/gtstaff-v1.0.2-dev.jar`、`build/libs/gtstaff-v1.0.2-sources.jar`

### 遇到的问题
- `CommandPlayer.handleShadow(...)` 之前仍通过 `CommandBase.getPlayer(...)` 直接依赖全局 `MinecraftServer.getServer()`，在测试和后续 runtime-neutral 演进里都不稳定；本轮改为 `resolvePlayer(...)` 封装后收口
- `Config` 的 `fakePlayerRuntimeMode` 首次创建配置文件时，不能再通过 `configuration.getCategory(...).get(...)` 取属性并回写，否则在空配置场景会出现空指针；已改成直接持有 `Property`
- `NextGenBotFactory.shadow(...)` 复制源玩家状态时，测试桩的 `DataWatcher` 不完整会导致 `getHealth()/isSneaking()/isSprinting()` 空指针；已加防御性保护，避免 nextgen shadow 因不完整外部状态崩溃

### 做出的决定
- Wave D 在这轮正式完成默认切换闭环：`fakePlayerRuntimeMode=nextgen`、`legacy/mixed` 继续保留，且命令/UI/restore/registry 都已有回退路径
- `ConfigTest` 对“默认值为 nextgen”的校验改为稳定的源码断言，避免被其他测试对静态配置字段的污染误伤

---
## 2026-04-21：完成 nextgen runtime 迁移范围审计

### 已完成
- 对照当前源码重新核对 `nextgen` 迁移范围，区分“命令/接口已切换”和“真实行为已落地”
- 确认 `spawn / shadow / restore / kill / manipulation command routing` 已迁到 runtime-neutral 主链，`fakePlayerRuntimeMode` 默认值也已切到 `nextgen`
- 确认 `follow / monitor / repel / inventory` 在 `nextgen` 下目前主要是 service state shell：命令与 UI 可读写状态，但真实跟随、监控扫描、敌对生物驱逐与完整背包 GUI 仍未完整平移
- 确认 `ServerUtilitiesCompat`、部分权限校验与动作反馈仍带有 legacy `FakePlayer` 类型依赖，不能算“全量迁完”

### 遇到的问题
- `context.md` 里仍残留少量旧描述，例如配置默认值一处还写成 `legacy`，如果只看文档会高估迁移完成度
- `NextGenBotRuntimeServicesTest` 当前验证重点是 service state 可变与 inventory summary，可证明 facade 通了，但还不足以证明 nextgen 的真实业务行为与 legacy 等价

### 做出的决定
- 后续再谈“nextgen 全量迁移完成”时，必须以真实行为闭环为准，不能只以 facade 接线和状态读写通过作为完成标准
- 这轮审计结论将同步写入 `context.md` 与 `ToDOLIST.md`，避免后续继续把 state shell 误记为 fully migrated

---
## 2026-04-21：补齐 nextgen bot 的服务端 respawn 兼容链

### 已完成
- 调整 `ServerConfigurationManagerMixin`：当 `respawnPlayer(...)` 的来源实体是 nextgen `GTstaffForgePlayer` 时，重生实体不再回退成原版 `EntityPlayerMP`，而是继续保留 GTstaff fake-player 身份
- 调整 `EntityPlayerMP_RespawnMixin`：nextgen bot 在 `clonePlayer(...)` 之后会重新绑定 `NextGenBotRuntime`，并复制 owner、monitor、repel、follow 等运行时状态，再重新注册回 `FakePlayerRegistry`
- 新增 `RespawnMixinsTest`，覆盖 nextgen respawn 构造分支选择与 clone 后 runtime/service-state 回绑场景
- 重新通过 `RespawnMixinsTest`、`GTstaffForgePlayerTest`、`BotLifecycleManagerTest`、`FakePlayerRegistryTest`、`NextGenBotFactoryTest`、`FakePlayerRestoreSchedulerTest` 与 `CommandPlayerTest`

### 遇到的问题
- `net.minecraftforge.common.util.FakePlayer` 构造函数在纯 JUnit 环境里会触发 `FMLCommonHandler` 初始化，直接 new `GTstaffForgePlayer` 的单测桩不稳定

### 做出的决定
- 将 `ServerConfigurationManagerMixin` 的 nextgen respawn 构造动作抽成包级测试钩子，仅在测试中替换为轻量 stub；生产逻辑保持原本真实构造路径不变
## 2026-04-22：修复假人饰品栏服务端写回绕过 Baubles 校验

### 已完成
- 根据你提供的 `PacketSyncBauble` 崩溃栈重新核对 `Baubles-Expanded-master` 与当前 GTstaff 写回链，确认根因不是客户端图标或槽位显示，而是服务端 `FakePlayerInventoryExtraSlot.setStack(...)` 直接写透传到底层 `IInventory.setInventorySlotContents(...)`，绕过了 `InventoryBaubles.isItemValidForSlot(...)`
- 新增 `FakePlayerInventoryViewTest.serverExtraSlotWriteThroughRespectsUnderlyingValidation()`，先锁定“后端 inventory 明确拒绝该物品时，假人额外槽不应继续写入”的红灯
- `FakePlayerInventoryExtraSlot` 现在在有后端 `IInventory` 时，会先调用 `isItemValidForSlot(...)`；若物品非法则直接拒绝写入，不再把非 `IBauble` 物品下沉到 Baubles 同步包链路
- `FakePlayerInventoryView.setInventorySlotContents(...)` 也补了一层额外槽合法性校验，避免容器或其他调用路径再次绕过附加槽限制
- 修正测试夹具 `TestInventory` 的 `final` 继承问题后，重新通过 `FakePlayerInventoryCompatTest`、`FakePlayerInventoryContainerTest`、`FakePlayerInventoryViewTest`、`FakePlayerInventoryGuiHandlerTest`、`FakePlayerManagerServiceTest`
- 重新离线打包 `v1.0.2`，最新主 jar 时间戳更新到 2026-04-22 00:24

### 遇到的问题
- 上一轮只补了客户端无后端 inventory 的假槽校验，真实服务端 Baubles 写回路径仍可能被直接调用，因此像 Forestry 背包这类普通物品依旧会进入 `InventoryBaubles.func_70299_a(...)` 并触发 `ClassCastException`

### 做出的决定
- 继续保留客户端预校验，但真正的兜底以服务端底层 `IInventory.isItemValidForSlot(...)` 为准；只要后端 inventory 拒绝，该物品就绝不再进入同步包链路
