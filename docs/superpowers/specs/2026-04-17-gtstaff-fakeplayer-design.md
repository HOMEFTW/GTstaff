# GTstaff 虚拟玩家模组设计文档

## 概述

GTstaff 是一个 GTNH 附属模组，在 Minecraft 1.7.10 + Forge 环境中实现虚拟玩家（Fake Player）功能。移植 Carpet Mod 的虚拟玩家核心架构，并新增 GT 机器监控和 MUI2 管理界面。

## 模组信息

- **Mod 名称**: GTstaff
- **Mod ID**: `GTstaff`（驼峰）/ `gtstaff`（资源命名空间）
- **包名**: `com.andgatech.gtstaff`
- **目标**: GTNH 1.7.10 (Minecraft 1.7.10 + Forge)
- **依赖**: GT5-Unofficial、GTNHLib、ModularUI

## 核心组件移植映射（Carpet → GTstaff）

| Carpet 组件 | GTstaff 对应 | 1.7.10 适配 |
|-------------|-------------|-------------|
| `EntityPlayerMPFake` | `FakePlayer` | `ServerPlayer` → `EntityPlayerMP` |
| `FakeClientConnection` | `FakeNetworkManager` | `Connection` → `NetworkManager` |
| `NetHandlerPlayServerFake` | `FakeNetHandlerPlayServer` | `ServerGamePacketListenerImpl` → `NetHandlerPlayServer` |
| `EntityPlayerActionPack` | `PlayerActionPack` | 核心逻辑基本不变 |
| `PlayerCommand` (Brigadier) | `CommandPlayer` (Forge ICommand) | Brigadier → Forge 原生命令系统 |
| Mixin: `ServerPlayer_actionPackMixin` | `EntityPlayerMPMixin` | 注入 `EntityPlayerMP.onUpdate()` |
| Mixin: `PlayerList_fakePlayersMixin` | `ServerConfigurationHandlerMixin` | 适配 1.7.10 玩家列表管理 |
| MUI2 管理界面 | `FakePlayerManagerUI` | 新增，Carpet 没有 |

## 项目包结构

```
com.andgatech.gtstaff/
├── GTstaff.java                    # @Mod 入口
├── CommonProxy.java                # 服务端初始化
├── ClientProxy.java                # 客户端初始化
├── config/
│   └── Config.java                 # 配置 + 权限系统
├── fakeplayer/
│   ├── FakePlayer.java             # 虚拟玩家实体（核心）
│   ├── FakeNetworkManager.java     # 空壳网络连接
│   ├── FakeNetHandlerPlayServer.java # 空壳网络处理器
│   ├── PlayerActionPack.java       # 动作调度引擎
│   ├── ActionType.java             # 动作类型枚举
│   ├── Action.java                 # 动作参数类
│   └── FakePlayerRegistry.java     # 虚拟玩家注册表
├── command/
│   └── CommandPlayer.java          # /player 命令
├── ui/
│   └── FakePlayerManagerUI.java    # MUI2 管理界面
├── mixin/
│   ├── EntityPlayerMPMixin.java    # 注入 ActionPack
│   ├── EntityPlayerMP_RespawnMixin.java  # 虚拟玩家重生
│   ├── ServerConfigurationHandlerMixin.java  # 玩家列表管理
│   ├── Entity_KnockbackMixin.java  # 击退特殊处理
│   └── EntityPlayerMP_TickFreezeMixin.java # tick 冻结豁免
└── util/
    └── PermissionHelper.java       # 权限工具
```

## 一、FakePlayer 核心实体

继承 `EntityPlayerMP`，构造函数为 private，通过静态工厂方法创建。

### 1.1 工厂方法

| 方法 | 用途 |
|------|------|
| `createFake(username, server, pos, yaw, pitch, dimension, gamemode, flying)` | 创建全新虚拟玩家 |
| `createShadow(server, player)` | 克隆真实玩家为影子玩家 |
| `respawnFake()` | 死亡后重生 |

### 1.2 生命周期重写

| 方法 | 行为 |
|------|------|
| `onUpdate()` | 每 10 tick 修正位置同步 + 调用 `actionPack.onUpdate()` + `super.onUpdate()` |
| `onDeath(DamageSource)` | 立即满血重生、重置食物数据、调用 `kill()` 断开连接 |
| `kill()` | 清除骑乘关系、根据原因选择立即/延迟断开 |
| `teleportTo()` | 跨维度传送特殊处理 |

### 1.3 与真实玩家的关键区别

- IP 固定为 `127.0.0.1`（`FakeNetworkManager`）
- 死亡后立即满血重生（不进入死亡界面）
- 不受 tick 冻结影响
- 击退逻辑特殊处理（不受 `hurtMarked` 阻止）

### 1.4 FakeNetworkManager

继承 `NetworkManager`，空壳实现：

- 构造时创建 `EmbeddedChannel` 使 `isChannelOpen()` 返回 true
- `sendPacket()` — 空实现
- `closeChannel()` — 空实现
- `processReceivedPackets()` — 空实现

### 1.5 FakeNetHandlerPlayServer

继承 `NetHandlerPlayServer`，仅重写：

- `disconnect(String)` — 仅响应 `idling` 和 `duplicate_login`，触发 `FakePlayer.kill()`
- `setPlayerLocation()` — 传送后立即重置位置同步

## 二、动作调度系统

### 2.1 ActionType 枚举（5 种动作）

所有动作仅作用于主手。

| ActionType | 含义 | 核心逻辑 |
|------------|------|---------|
| `USE` | 右键/使用 | 对方块/实体交互；空手时使用手持物品 |
| `ATTACK` | 左键/攻击 | 攻击实体；对方块执行挖掘（创造/生存两种模式） |
| `JUMP` | 跳跃 | 地面跳跃；空中尝试鞘翅飞行 |
| `DROP_ITEM` | 丢一个 | 丢弃主手物品（1个） |
| `DROP_STACK` | 丢一组 | 丢弃主手物品（整组） |

### 2.2 Action 类

```java
public static class Action {
    boolean done;           // 动作是否已完成
    int limit;              // 执行次数（-1=无限）
    int interval;           // 执行间隔（tick）
    int offset;             // 首次执行偏移
    boolean isContinuous;   // 是否持续动作
}
```

三种工厂方法：

- `Action.once()` — 执行一次（limit=1, interval=1, offset=0）
- `Action.continuous()` — 每 tick 持续执行（limit=-1, interval=1, isContinuous=true）
- `Action.interval(n)` — 每 n tick 执行一次（limit=-1, interval=n）

### 2.3 onUpdate() 调度流程（每 tick 调用）

1. 清理已完成动作（`done == true`）
2. 遍历活跃动作，调用 `action.tick()`
3. USE/ATTACK 互斥与自动重试（模拟客户端 `handleInputEvents` 行为）
4. 应用移动量（潜行时 0.3x 速度，设置 `moveForward`/`moveStrafing`）

### 2.4 ATTACK 方块挖掘状态机

- **创造模式**：直接调用 `handleBlockBreakAction(START)`，5 tick 冷却
- **生存模式**：维护 `currentBlock` + `curBlockDamageMP` 状态
  - 新方块 → 发送 `START`，计算破坏进度
  - 瞬间挖掘 → `getDestroyProgress() >= 1` 时直接完成
  - 持续挖掘 → 累加 `curBlockDamageMP`，达到 1.0 时发送 `STOP`
  - 切换目标 → 先对旧方块发送 `ABORT`

### 2.5 直接控制方法（非 ActionType）

| 方法 | 对应命令 |
|------|---------|
| `look(direction)` | `look north/south/...` |
| `lookAt(position)` | `look at <pos>` |
| `turn(yaw, pitch)` | `turn left/right/back` |
| `setForward(value)` | `move forward/backward` |
| `setStrafing(value)` | `move left/right` |
| `setSneaking(bool)` | `sneak/unsneak` |
| `setSprinting(bool)` | `sprint/unsprint` |
| `mount(onlyRideables)` | `mount/mount anything` |
| `dismount()` | `dismount` |
| `setSlot(slot)` | `hotbar <1-9>` |
| `stopAll()` | `stop` |

## 三、GT 机器监控功能

虚拟玩家可配置为监控附近 GT 机器状态，检测到异常时向所有者发送聊天消息。

### 3.1 触发方式

- `/player <name> monitor [on|off] [range <半径>]`
- MUI2 管理界面中勾选监控开关

### 3.2 监控规则

- 扫描频率：每 **60 tick（3秒）** 扫描一次
- 默认范围：16 格
- 仅在**状态变化时**上报（避免刷屏）

### 3.2.1 GT5U 参考实现

- 监控状态判定参考 `GT5-Unofficial-master` 的无人机监控链路：`MTEHatchDroneDownLink` 与 `DroneConnection`
- 运行态来源：`IGregTechTileEntity.isActive()`
- 跳电来源：`IGregTechTileEntity.getLastShutDownReason()`
- 维护来源：`MTEMultiBlockBase.getIdealStatus()` 与 `getRepairStatus()`
- 输出满来源：`MTEMultiBlockBase.getCheckRecipeResult()`
- 监控对象范围：以 fake player 为中心，只扫描当前世界已加载、且位于监控半径内的 `MTEMultiBlockBase` 多方块机器

### 3.3 上报事件

| 事件类型 | 触发条件 | 消息示例 |
|---------|---------|---------|
| 跳电 | 机器从运行变为停止，且无 EU 供给 | `[GTstaff] Bot_Steve: 机器 [电弧高炉] 在 (123, 64, -456) 跳电！` |
| 维护问题 | 机器需要维护 | `[GTstaff] Bot_Steve: 机器 [大型锅炉] 在 (100, 70, 200) 需要维护！` |
| 输出满 | 输出总线/仓已满 | `[GTstaff] Bot_Steve: 机器 [装配线] 在 (50, 65, 300) 输出已满！` |
| 恢复正常 | 异常恢复正常 | `[GTstaff] Bot_Steve: 机器 [电弧高炉] 在 (123, 64, -456) 已恢复正常。` |

### 3.4 数据结构

```java
// FakePlayer 中新增字段
private boolean monitoring = false;
private int monitorRange = 16;
private UUID ownerUUID;
private Map<BlockPos, MachineState> monitoredMachines = new HashMap<>();

static class MachineState {
    boolean wasActive;
    boolean hadPower;
    boolean neededMaintenance;
    boolean wasOutputFull;
}
```

### 3.5 1.7.10 / GTNH 适配备注

- `ShutDownReason` 与 `CheckRecipeResult` 不能可靠地用 `getID()` 区分具体语义，因为 GT 的 `SimpleShutDownReason` / `SimpleCheckRecipeResult` 会复用同一个 `simple_result`
- 跳电应优先按 `ShutDownReasonRegistry.POWER_LOSS` 身份比较；若运行时对象不是同一个实例，可再用反射尝试读取 `getKey()`
- 输出满应比较 `CheckRecipeResultRegistry.ITEM_OUTPUT_FULL` / `FLUID_OUTPUT_FULL` 常量，并允许使用 `equals(...)` 兼容可能的结果对象拷贝

## 四、命令系统

### 4.1 /player 命令（Forge ICommand）

```
/player <name> spawn [at <x> <y> <z>] [facing <yaw> <pitch>] [in <dim>] [as <gamemode>]
/player <name> kill
/player <name> shadow

/player <name> attack [once|continuous|interval <ticks>]
/player <name> use [once|continuous|interval <ticks>]
/player <name> jump [once|continuous|interval <ticks>]
/player <name> drop [once|continuous|interval <ticks>]
/player <name> dropStack [once|continuous|interval <ticks>]

/player <name> move [forward|backward|left|right|stop]
/player <name> look <north|south|east|west|up|down|at <x> <y> <z>>
/player <name> turn <left|right|back>
/player <name> sneak|unsneak
/player <name> sprint|unsprint
/player <name> mount [anything]
/player <name> dismount
/player <name> hotbar <1-9>
/player <name> stop

/player <name> monitor [on|off] [range <半径>]
/player list
```

### 4.2 /gtstaff 命令

```
/gtstaff ui          # 打开 MUI2 管理界面（仅 OP）
```

### 4.3 权限配置

| 配置项 | 默认值 | 说明 |
|--------|-------|------|
| `fakePlayerPermissionLevel` | 2 (OP) | 创建/销毁虚拟玩家所需 OP 等级 |
| `allowNonOpControlOwnBot` | true | 非 OP 玩家是否可操控自己创建的虚拟玩家 |
| `maxBotsPerPlayer` | 5 | 每个玩家最大虚拟玩家数量 |
| `maxBotsTotal` | 20 | 服务器最大虚拟玩家总数 |
| `defaultMonitorRange` | 16 | 默认监控范围 |

### 4.4 权限检查逻辑

- `cantSpawn()` — 检查 OP 等级、是否正在生成中、是否已在线、UUID 解析、是否被封禁、数量上限
- `cantManipulate()` — 目标玩家必须存在；非 OP 只能操控自己创建的虚拟玩家
- `cantRemove()` — 只有虚拟玩家可被 kill；非 OP 只能 kill 自己创建的

## 五、MUI2 管理界面

### 5.1 触发方式

`/gtstaff` 或 `/gtstaff ui`（仅 OP）

### 5.2 主窗口布局（300×200）

```
┌─────────────── GTstaff 虚拟玩家管理 ───────────────┐
│                                                      │
│  虚拟玩家列表 (左侧 180px)        │ 操作面板 (右侧)  │
│  ┌──────────────────────────┐     │                  │
│  │ > Bot_Steve    [运行中]  │     │ [生成新玩家]     │
│  │   Bot_Alex     [运行中]  │     │ [杀死选中]       │
│  │   Bot_Test     [已停止]  │     │ [停止所有动作]   │
│  │                          │     │ ──────────────── │
│  │                          │     │ 动作控制：       │
│  │                          │     │ [攻击] [使用]    │
│  │                          │     │ [跳跃] [丢弃]    │
│  └──────────────────────────┘     │ [查看背包]       │
│                                    │ ──────────────── │
│  状态信息 (底部)：                │ 移动控制：       │
│  位置: (123, 64, -456) dim: 0    │ [前进] [后退]    │
│  存活: 3m  动作: continuous attack│ [左移] [右移]    │
│                                    │ [潜行] [疾跑]    │
│  [x] 机器监控  范围: [16]         │ [视角设置...]    │
│                                    │ [骑乘] [下骑]    │
│  共 3 个虚拟玩家 (上限 20)        │ [快捷栏 1-9]     │
└────────────────────────────────────┴──────────────────┘
```

### 5.3 功能分区

| 区域 | 组件 | 功能 |
|------|------|------|
| 玩家列表 | 自定义列表 | 显示所有虚拟玩家名称 + 状态，点击选中 |
| 生成/杀死按钮 | `ButtonWidget` | 生成新玩家（弹出输入名字窗口）/ 杀死选中 |
| 动作控制 | `ButtonWidget`×5 | 攻击/使用/跳跃/丢弃/查看背包 |
| 移动控制 | `ButtonWidget`×6 | 前后左右 + 潜行疾跑切换 |
| 监控开关 | `ButtonWidget`(toggle) + `TextFieldWidget` | 开关监控、设置范围 |
| 状态信息 | `TextWidget` + `FakeSyncWidget` | 实时显示选中玩家位置、存活时间、当前动作 |
| 快捷栏 | `ButtonWidget`×9 | 切换选中玩家的快捷栏槽位 |

### 5.4 弹出窗口

1. **生成窗口** — 输入名字，选择游戏模式、维度、位置
2. **视角设置窗口** — 选择方向或输入坐标
3. **背包查看窗口** — 只读查看虚拟玩家完整背包

### 5.5 背包查看窗口（176×180）

只读模式，`SlotWidget` 设置 `.setEnabled(false)` 禁止交互。

```
┌──── Bot_Steve 的背包 ────┐
│  ─── 主背包 (4×9) ────   │
│  [  ][  ][  ][  ][  ][  ][  ][  ][  ]  ← 快捷栏 0-8
│  [  ][  ][  ][  ][  ][  ][  ][  ][  ]  ← 9-17
│  [  ][  ][  ][  ][  ][  ][  ][  ][  ]  ← 18-26
│  [  ][  ][  ][  ][  ][  ][  ][  ][  ]  ← 27-35
│  ─── 装备栏 ────         │
│  [头盔]  [胸甲]  [护腿]  [靴子]
│  [关闭]                   │
└───────────────────────────┘
```

快捷栏当前 `currentItem` 对应槽位用边框高亮标识。

### 5.6 同步机制

| 数据 | 同步器 |
|------|--------|
| 选中玩家索引 | `FakeSyncWidget.IntegerSyncer` |
| 监控开关 | `FakeSyncWidget.BooleanSyncer` |
| 监控范围 | `FakeSyncWidget.IntegerSyncer` |
| 状态文本 | `FakeSyncWidget.StringSyncer` |

## 六、Mixin 集成

### 6.1 Mixin 列表

| Mixin 类 | 目标类 | 注入点 | 功能 |
|---------|--------|--------|------|
| `EntityPlayerMPMixin` | `EntityPlayerMP` | 构造函数 + `onUpdate()` HEAD | 初始化 ActionPack，每 tick 调用 `onUpdate()` |
| `ServerConfigurationHandlerMixin` | `ServerConfigurationHandler` | `handleLogin()` | 替换网络处理器；执行 `fixStartingPosition` 回调 |
| `EntityPlayerMP_RespawnMixin` | `EntityPlayerMP` | 重生相关方法 | 虚拟玩家重生时创建新的 `FakePlayer` 实例 |
| `Entity_KnockbackMixin` | `Entity` | `knockBack()` | 虚拟玩家不受 `hurtMarked` 阻止击退 |
| `EntityPlayerMP_TickFreezeMixin` | `EntityPlayerMP` | tick 相关 | 虚拟玩家不受 tick 冻结影响 |

### 6.2 接口定义

```java
public interface IEntityPlayerMPMixin {
    PlayerActionPack getActionPack();
}
```

### 6.3 EntityPlayerMPMixin 核心逻辑

```java
@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMPMixin implements IEntityPlayerMPMixin {
    @Unique
    private PlayerActionPack actionPack;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initActionPack(...) {
        actionPack = new PlayerActionPack((EntityPlayerMP)(Object)this);
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onUpdateActionPack() {
        actionPack.onUpdate();
    }

    @Override
    public PlayerActionPack getActionPack() {
        return actionPack;
    }
}
```

### 6.4 accesstransformer.cfg

```cfg
net.minecraft.entity.player.EntityPlayerMP f_71103_d
net.minecraft.entity.player.EntityPlayerMP f_71091_bV
net.minecraft.network.NetworkManager f_150468_c
net.minecraft.server.network.ServerConfigurationHandler f_72368_b
```

### 6.5 mixins.gtstaff.json

```json
{
  "compatibilityLevel": "JAVA_8",
  "package": "com.andgatech.gtstaff.mixin",
  "mixins": [
    "EntityPlayerMPMixin",
    "EntityPlayerMP_RespawnMixin",
    "ServerConfigurationHandlerMixin",
    "Entity_KnockbackMixin",
    "EntityPlayerMP_TickFreezeMixin"
  ],
  "minVersion": "0.8",
  "refmap": "mixins.gtstaff.refmap.json",
  "required": true
}
```

## 七、完整生命周期与数据流

### 7.1 创建流程

```
/player Bot_Steve spawn / MUI2 [生成新玩家]
  │
  ├─ 权限检查（OP等级 / 数量上限）
  ├─ UUID 解析（在线查询 / 离线算法）
  ├─ new FakeNetworkManager() → EmbeddedChannel
  ├─ new FakePlayer(server, world, profile, false)
  ├─ server.getConfigurationManager().playerLoggedIn(fakePlayer)
  ├─ 替换 NetHandler → FakeNetHandlerPlayServer
  ├─ loadPlayerData()（从存档恢复）
  ├─ 设置位置、游戏模式、飞行状态
  └─ 注册到 FakePlayerRegistry（记录 ownerUUID）
```

### 7.2 运行时（每 tick）

```
EntityPlayerMPMixin.onUpdate()
  ├─ actionPack.onUpdate()
  │   ├─ 清理已完成动作
  │   ├─ 执行活跃动作（attack/use/jump/drop...）
  │   └─ 应用移动量（forward/strafing/sneaking/sprinting）
  ├─ super.onUpdate()（正常玩家 tick 逻辑）
  └─ 每 60 tick（3秒）：
      └─ 若监控开启 → 扫描范围内 GT 机器 → 状态变化时上报
```

### 7.3 销毁流程

```
/player Bot_Steve kill / MUI2 [杀死选中]
  │
  ├─ 权限检查（只能 kill 自己创建的，或 OP）
  ├─ fakePlayer.kill()
  │   ├─ 清除骑乘关系
  │   ├─ 保存玩家数据到存档
  │   └─ disconnect → FakeNetHandlerPlayServer
  │       └─ 从服务器玩家列表移除
  └─ 从 FakePlayerRegistry 注销
```

### 7.4 组件数据流

```
命令/MUI2 ──→ FakePlayerRegistry ──→ FakePlayer
                  │                      │
                  │                      ├→ PlayerActionPack
                  │                      │    ├→ ActionType (USE/ATTACK/JUMP/DROP_ITEM/DROP_STACK)
                  │                      │    └→ Action (limit/interval/offset)
                  │                      │
                  │                      ├→ FakeNetworkManager (空壳)
                  │                      │    └→ FakeNetHandlerPlayServer
                  │                      │
                  │                      └→ GT机器监控 (每60tick)
                  │                           └→ ChatMessage → ownerPlayer
                  │
                  └← ownerUUID 映射 (谁创建了哪个Bot)
```

### 7.5 存档持久化

- 虚拟玩家的物品栏、经验等由 Minecraft 原版玩家数据系统自动保存
- `FakePlayerRegistry` 在服务器停止时将映射关系（owner → bots）写入 `data/gtstaff_registry.dat`（NBT）
- 首版实现先在服务器启动时恢复 owner 映射，不自动恢复 fake player 实体；是否支持跨重启自动重建实体，放到后续任务单独评估

## 八、当前实施计划（2026-04-17 更新）

### 已完成

- Task 1 至 Task 6：fake player 核心、网络生命周期、Mixin、动作系统、命令系统均已落地并有对应单元测试
- Task 7 监控部分：已接入 GT 多方块真实扫描、状态 diff 上报、registry 持久化、`/gtstaff` 主界面与子窗口骨架
- GT 机器监控实现已参考 `GT5-Unofficial-master` 无人机监控语义，并通过离线 Gradle 回归验证

### 进行中

- Task 7 剩余部分：为 `FakePlayerSpawnWindow`、`FakePlayerInventoryWindow`、`FakePlayerLookWindow` 补齐真实交互，而不是仅显示占位文本
- 明确 fake player 的跨重启恢复策略：继续保持“仅恢复 owner 映射”，或增加实体自动重建能力

### 下一步

1. 为 MUI2 子窗口接入真实数据与按钮行为，至少能完成创建 bot、查看背包、设置视角。
2. 补用户提示与界面同步细节，确保命令路径与 UI 路径行为一致。
3. 统一执行 `--offline` 测试与编译回归，并在游戏内做一轮最终烟测。
