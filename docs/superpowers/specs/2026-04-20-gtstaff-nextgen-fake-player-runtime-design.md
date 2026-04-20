# GTstaff NextGen Fake Player Runtime 重构设计

## 背景

`GTstaff` 当前的 fake player 直接继承 `EntityPlayerMP`，并通过自定义网络与登录挂接流程把 bot 作为长期在线玩家运行。这个架构已经支持以下能力：

- 假人生成、影子接管、持久化恢复
- `PlayerActionPack` 驱动的 `attack / use / jump / move / look / hotbar`
- 跟随、监控、驱逐、背包管理、UI 管理
- 若干整合兼容，例如 TST 阎魔刀右键桥、电梯触发桥、空挥手反馈

但当前实现存在一个越来越明显的结构性问题：

1. 它对服务器和客户端来说“足够像真人玩家”，因此能跑很多真实玩家行为。
2. 它对 Forge 生态来说却不是标准的 `net.minecraftforge.common.util.FakePlayer`，因此大量模组基于 `instanceof FakePlayer`、`playerNetServerHandler == null` 或类似语义做的分支不会命中。
3. 结果就是 GTstaff bot 落在“既不完全是真玩家，也不完全是 Forge fake player”的兼容灰区，导致攻击、右键、外部模组交互在某些环境中持续表现不稳定。

用户希望进行一次允许“动根”的重构，目标不是继续叠加单点补丁，而是把 GTstaff fake player 重构成“以 Forge fake-player 语义为核心，同时保留长期在线 bot 所需真玩家能力”的新架构。

## 目标

本次重构的目标如下：

1. 将 GTstaff fake player 的底层运行时重构为更接近 Forge 标准 fake player 的语义，以提高整合兼容性。
2. 保留 GTstaff 现有的长期在线 bot 能力，包括登录式会话、动作执行、背包管理、持久化恢复、跟随、监控、驱逐、UI 管理等。
3. 提供新旧运行时之间的迁移层，允许旧 bot 数据、旧命令、旧 UI 入口继续工作。
4. 以分阶段切换方式完成重构，避免一次性硬切导致现有功能整体失效。
5. 最终不接受功能降级，现有对外功能必须全量平移到新 runtime。

## 非目标

本次设计明确不做以下事情：

1. 不尝试直接把当前 `FakePlayer` 类简单改成 `extends net.minecraftforge.common.util.FakePlayer` 后继续堆逻辑。
2. 不把当前攻击无效问题继续当成单点 bug 修复处理。
3. 不在本次重构中顺手重写 UI 风格、命令语法或 registry 文件格式的人类可见结构。
4. 不要求第一版 nextgen runtime 一上来就删除 legacy 实现；允许双轨并存与渐进切换。

## 方案对比与结论

### 方案 A：直接把现有 `FakePlayer` 改为继承 Forge `FakePlayer`

优点：

- 最快获得 `instanceof FakePlayer` 兼容。
- 对外部模组的 fake-player 判定覆盖率提升最快。

缺点：

- Forge 1.7.10 自带 `FakePlayer` 是面向“轻量机器执行体”的瘦桩实现，默认覆盖了 `onUpdate()`、`onDeath()`、`travelToDimension()`、`isEntityInvulnerable()` 等关键行为，不适合直接承载 GTstaff 这种长期在线 bot。
- 当前 `FakePlayer` 类同时承担实体生命周期、网络会话、动作执行、跟随、监控、驱逐、恢复等大量职责，直接换父类会让回归面过大且难以诊断。

结论：不采用。

### 方案 B：新建 NextGen 运行时，旧实现并行，逐步切换

优点：

- 可以从底层引入 Forge `FakePlayer` 语义，同时保留 GTstaff 需要的长期在线 bot 能力。
- 可以把当前耦合在一个实体类中的职责拆开，形成更清晰的服务边界。
- 能支持双轨运行、可回滚切换和旧数据迁移，更符合“不能降级”的约束。

缺点：

- 实施成本最高。
- 需要同时维护一段时间的 legacy / nextgen 双实现与迁移层。

结论：采用。

### 方案 C：保留现有 `EntityPlayerMP` 核心，仅继续加兼容桥

优点：

- 改动最小。
- 对现有代码侵入较低。

缺点：

- 无法从根上解决 Forge fake-player 兼容灰区问题。
- 会继续把攻击、右键、外部模组交互的问题推向越来越多的特判与 mixin。

结论：不采用。

## 总体架构

新架构采用“实体核心 + 会话层 + 运行时服务 + 迁移层”的分层设计。

### 1. `GTstaffForgePlayer`

这是新的底层运行时实体。

职责：

- `extends net.minecraftforge.common.util.FakePlayer`
- 负责实体存在、世界挂接、玩家身份、基础属性与实体级覆写行为
- 覆盖 Forge `FakePlayer` 中不适合长期在线 bot 的默认行为，例如 `onUpdate()`、`onDeath()`、维度旅行、受击/死亡恢复等

约束：

- 不直接承载跟随、监控、驱逐、UI、registry 等高层业务
- 不把大段命令与服务逻辑继续堆回实体类

### 2. `BotSession`

这是“像真玩家”能力的集中层。

职责：

- 负责假人与服务器登录态的挂接
- 统一处理 `NetworkManager`、`NetHandlerPlayServer`、登录初始化、watcher 同步、位置同步、动画同步、维度切换会话逻辑
- 收口当前分散在 `FakePlayer.attachToServer(...)`、`FakeNetHandlerPlayServer`、装备与挥手广播中的登录式行为

设计原则：

- “像真玩家”的会话语义必须有单独边界，不能与业务功能耦在一起
- 所有后续对客户端可见的反馈同步都应通过 `BotSession` 或其下属同步器完成

### 3. `BotRuntime`

这是 bot 的功能宿主，而不是实体本身。

职责：

- 挂接动作、跟随、监控、驱逐、背包视图、持久化快照等功能服务
- 为命令层、UI 层、registry 层提供统一访问接口

建议子服务：

- `ActionRuntime`
- `FollowRuntime`
- `MonitorRuntime`
- `RepelRuntime`
- `InventoryRuntime`
- `PersistenceSnapshotView`

### 4. `ActionPipeline`

这是动作执行总线，用于替代当前职责过重的 `PlayerActionPack`。

建议拆分：

- `TargetingService`
- `AttackExecutor`
- `UseExecutor`
- `BlockBreakExecutor`
- `MovementExecutor`
- `FeedbackSync`

设计目标：

- 明确区分“没命中”“命中了但被拒绝”“命中并成功”
- 兼容桥只补“缺失的客户端链路”，不整体重写原版交互
- fallback 是最后兜底，而不是第一设计中心

### 5. `BotRegistryFacade` 与 `MigrationAdapter`

这是迁移层与持久化抽象层。

职责：

- 让 registry 记录 bot 身份、状态快照、runtime 类型与版本信息，而不是直接耦合某个实体类
- 提供 legacy -> nextgen 的状态迁移
- 支持必要时的 nextgen -> legacy 回退

## 运行时接口设计

命令层、UI 层和持久化层不应继续依赖具体实体类型，而应依赖统一接口。

建议引入以下抽象：

- `BotHandle`
  - 面向命令与 UI 的稳定入口
  - 提供 bot 名称、在线状态、位置、维度、所有者、运行时类型等基础信息

- `BotRuntimeView`
  - 暴露动作执行、背包访问、跟随、监控、驱逐、提醒频率等功能接口

- `BotEntityBridge`
  - 在确实需要访问底层实体时提供受控入口
  - 屏蔽 legacy 与 nextgen 两种实体实现差异

这样可以确保：

1. 命令和 UI 不因底层实体切换而大面积改动。
2. registry 不再直接存取某个具体实体类的细节。
3. nextgen 和 legacy 可以在同一套上层接口下双轨运行。

## 动作链设计

### 1. `TargetingService`

负责统一处理视线与目标选择。

输出信息至少包含：

- 命中类型：方块 / 实体 / 未命中
- 命中对象
- 命中距离
- 命中来源：射线命中 / 近身兜底命中

要求：

- 优先使用稳定射线命中链
- 保留近距离实体兜底，解决贴脸或边界盒特殊情况
- 结果必须可诊断，不允许再出现“看起来没反应，但不知道到底有没有取到目标”

### 2. `AttackExecutor`

攻击链按三级执行：

1. 原版玩家攻击主链
2. 必要时的 GTstaff 兼容桥
3. 最终 fallback

要求：

- 每次攻击必须记录自己走到了哪一级
- 对 `EntityLivingBase` 的最终伤害兜底只在确认原链无效时才使用
- 客户端反馈和服务端执行结果必须分开记录

### 3. `UseExecutor`

统一顺序处理：

1. 方块交互
2. 实体交互
3. 物品右键
4. 客户端链路兼容桥
5. 最终反馈

要求：

- 阎魔刀、电梯等兼容必须以 handler 形式插入 use pipeline
- 兼容层只负责补足“原模组写在客户端链路里而 fake player 缺失的动作”

### 4. `FeedbackSync`

负责以下内容：

- 空挥手与命中挥手动画
- 当前持有物同步
- 必要的装备同步
- 针对 watcher 的动作反馈广播

目标：

- 把“执行结果”与“客户端可见反馈”从动作执行代码中拆开

### 5. 诊断模式

nextgen runtime 必须内建轻量诊断能力，默认关闭。

开启后应能记录一次 `attack/use` 的最小链路：

- 命中了什么
- 走了哪条执行链
- 哪一步返回 false
- 最终是否出现真实结果

该能力是后续 GTNH 整合兼容排查的基础设施，而不是一次性调试代码。

## 迁移与切换策略

### 1. 双轨运行

切换期间允许同时存在：

- `LegacyBotRuntime`
- `NextGenBotRuntime`

规则：

- 新建 bot 可以按 feature flag 选择走 legacy 或 nextgen
- 旧 bot 恢复时默认先按迁移策略解释，而不是强制立即重建

### 2. 运行时无关 registry

registry 升级后必须记录：

- bot 身份
- 运行时类型
- 快照版本
- 通用状态快照

快照内容至少包含：

- 名称、profileId、ownerUUID
- 维度、位置、朝向
- 游戏模式、飞行状态
- 监控配置
- 跟随配置
- 驱逐配置
- 必要的动作状态

### 3. 迁移器原则

迁移器必须做“状态复制”，而不是“语义重算”。

不允许在迁移时偷偷重置：

- owner
- gamemode
- flying
- monitorRange / reminderInterval
- monsterRepelling / monsterRepelRange
- followTarget / followRange / teleportRange
- 背包与装备状态

### 4. 回滚能力

在 nextgen 默认启用之前，必须保留 runtime 级回退能力。

如果某个整合环境下 nextgen 发生严重兼容问题，应允许：

- 停止 nextgen bot
- 用同一份状态快照恢复为 legacy bot

### 5. 切换方式

采用 feature flag：

- `legacy`
- `nextgen`
- `mixed`

推荐切换路径：

1. 初期默认 `legacy`
2. 骨架可用后进入 `mixed`
3. 全量回归后默认 `nextgen`
4. 保留一段时间 `legacy` 回退能力

## 功能全量平移要求

由于本次明确“不接受功能降级”，因此 nextgen runtime 最终必须覆盖以下能力：

- `spawn`
- `shadow`
- 持久化恢复
- `attack / use / jump / move / look / turn / hotbar / stop`
- inventory 管理
- follow
- machine monitor
- monster repel
- purge / kill
- UI 打开与交互
- 皮肤解析与恢复补皮

切换默认实现前，不允许缺失上述任一项。

## 测试策略

### 1. 单元测试

必须覆盖：

- `TargetingService`
- `AttackExecutor`
- `UseExecutor`
- `MigrationAdapter`
- runtime 快照读写

重点验证：

- 命中判断
- 链路选择
- fallback 触发条件
- 旧快照 -> 新快照迁移完整性

### 2. 集成测试

至少覆盖：

- `spawn / move / look / attack / use / hotbar / inventory`
- 持久化恢复
- follow
- monitor
- repel
- shadow

目标是证明 nextgen runtime 可以完整替代 legacy runtime，而不只是局部跑通。

### 3. 兼容回归集

固定纳入以下场景：

- TST 阎魔刀右键
- 阎魔刀左键攻击
- OpenBlocks 电梯
- 空挥手可见性
- 实体攻击真实掉血

每次切换 feature flag 或重新打包前都必须跑这组回归。

## 风险与对策

### 风险 1：拿到 Forge `FakePlayer` 身份后，反而丢失 GTstaff 现有真玩家式行为

对策：

- 不直接替换当前实现
- 把实体身份与会话/动作能力拆层
- 用双轨运行保留 legacy 回退

### 风险 2：registry 迁移不完整导致旧 bot 状态丢失

对策：

- 定义统一快照模型
- 为每一项可迁移状态写明确测试
- 切换默认 runtime 前先在 mixed 模式验证恢复链

### 风险 3：动作链拆分后短期内兼容问题暴露更多

对策：

- 引入诊断模式
- 每个 executor 记录链路选择
- 保持 handler 化兼容扩展，而不是继续在一个类里叠条件分支

## 实施拆分

本设计建议拆成四个 implementation wave。

### Wave A：运行时解耦与 nextgen 骨架

- 引入 `BotHandle` / `BotRuntimeView` / `BotEntityBridge`
- 让命令、UI、registry 不再直接依赖具体实体类
- 新建 `GTstaffForgePlayer`、`BotSession`、`NextGenBotRuntime`
- 暂不切默认实现

### Wave B：动作链迁移

- 重构 `PlayerActionPack` 为新的 action pipeline
- 完成 targeting / attack / use / feedback
- 先用回归集压住当前攻击与右键痛点

### Wave C：业务服务迁移

- 迁移 follow / monitor / repel / inventory / UI 管理服务
- 保证旧命令与旧 UI 入口走统一 runtime 抽象

### Wave D：默认切换与清理

- 默认切换到 nextgen runtime
- 保留 legacy 回退开关
- 清理旧实现中的重复逻辑
- 在缓冲期后再考虑完全删除 legacy 实现

## 验收标准

满足以下条件才允许把默认 runtime 切到 nextgen：

1. 旧 registry 数据可无损恢复为 nextgen bot。
2. 现有命令与 UI 不需要用户改变使用方式。
3. `spawn / shadow / restore / attack / use / inventory / follow / monitor / repel` 全量可用。
4. 兼容回归集全部通过。
5. mixed 模式下可在必要时把 bot 回退到 legacy runtime。

## 本设计的结论

本次重构应被定义为：

- 一个以 Forge `FakePlayer` 语义为核心、同时保留 GTstaff 长期在线 bot 能力的 nextgen runtime 重构
- 一个必须保留旧 bot 数据、旧命令、旧 UI 行为的双轨迁移工程
- 一个优先解决 fake-player 兼容灰区与动作链黑箱问题，而不是继续叠单点补丁的架构升级

后续 implementation plan 必须基于本设计按 wave 拆解，不允许跳过运行时解耦直接硬切实体父类。
