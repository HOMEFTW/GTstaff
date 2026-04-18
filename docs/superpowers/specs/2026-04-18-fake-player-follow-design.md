# Fake Player Follow Feature Design

**Date:** 2026-04-18
**Status:** Approved
**Approach:** 混合模式 — 近距离物理移动跟随，超距传送拉回，跨维度延迟传送

---

## Overview

为 GTstaff 的假人系统新增跟随玩家功能。假人通过 moveForward/moveStrafing 物理追踪目标玩家，超出距离阈值时传送拉回，维度不同时延迟 5 秒后跨维度传送。支持命令和 UI 两种触发方式。

---

## Core Logic: FollowService

新增 `FollowService` 类，作为 `FakePlayer` 的字段，每 tick 在 `FakePlayer.onUpdate()` 末尾调用。

### Per-tick Flow

```
1. 检查目标 — target 为 null 或不在线 → 停止跟随，清零移动输入
2. 维度检查 — 不同维度 → 进入跨维度传送流程
3. 距离检查 — 距离 > teleportRange → 传送到玩家附近（偏移 yaw 方向 2 格），本 tick 结束
4. 飞行同步 — fakePlayer.capabilities.isFlying = target.capabilities.isFlying
5. 方向计算 — 计算 moveForward / moveStrafing
6. Y 轴处理 — 空中时通过 setJumping / setSneaking 控制升降
7. 距离检查 — 距离 < followRange → 清零移动输入，停止
```

### Direction Calculation

```
dx = target.posX - fake.posX
dz = target.posZ - fake.posZ
targetYaw = atan2(-dx, dz) * 180 / PI
yawDiff = normalizeTo180(targetYaw - fake.rotationYaw)
moveForward = cos(yawDiff)    // 前后分量
moveStrafing = sin(yawDiff)   // 左右分量
```

### Y-axis Control (Airborne)

- 目标高于假人 0.5 格以上 → `setJumping(true)`
- 目标低于假人 0.5 格以下 → `setSneaking(true)` (同时会降低移动速度，可接受)
- 高度差在 0.5 格以内 → 不做额外处理

仅在 `!fakePlayer.onGround || fakePlayer.capabilities.isFlying` 时激活 Y 轴控制。

### Cross-Dimension Teleport

1. 检测到维度不同 → 进入等待状态，发送聊天消息：`"[假人名] 将在 5 秒后传送至你的维度"`
2. 等待 100 tick（5 秒），期间假人原地不动
3. 等待期间玩家又换维度 → 重置计时器，重新发消息
4. 等待期间玩家下线或停止跟随 → 取消传送
5. 计时结束 → 执行跨维度传送

实现：`FollowService` 内用 `crossDimTicksRemaining` 计数器，不需要额外 scheduler。

跨维度传送使用 `MinecraftServer.worldServerForDimension(targetDim)` 获取目标世界，手动将假人从旧世界移除、在新世界创建并设置坐标。传送后延迟 1 tick 再开始移动。

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| followRange | 3 blocks | 距离小于此值时停止移动 |
| teleportRange | 32 blocks | 距离大于此值时传送拉回 |
| crossDimDelay | 100 ticks (5s) | 跨维度传送等待时间 |
| target | null | 目标玩家 EntityPlayerMP |

---

## Command Interface

在 `CommandPlayer` 中新增 `follow` 子命令：

```
/player <name> follow [player]     — 开始跟随指定玩家（省略则跟随调用者）
/player <name> follow stop         — 停止跟随
/player <name> follow range <n>    — 设置跟随停止距离（默认 3）
/player <name> follow tprange <n>  — 设置传送拉回距离（默认 32）
```

权限：复用现有 `PermissionHelper.cantManipulate()` 检查。

---

## UI Interface

在 `FakePlayerManagerUI` 的 `Other` 页签中新增：

- "跟随我" 按钮 — 点击后假人跟随当前玩家
- "停止跟随" 按钮
- 跟随距离滑块（1-10 格）
- 传送距离滑块（16-128 格）

交互走现有 `InteractionSyncHandler` + `ServerThreadUtil` 服务端执行模式。

---

## Persistence

`FakePlayerRegistry` 持久化新增字段：

- `followTarget` (UUID) — 跟随目标玩家 UUID，null 表示未跟随
- `followRange` (int) — 跟随停止距离
- `teleportRange` (int) — 传送拉回距离

恢复逻辑：服务器重启后读取持久化数据，查找目标玩家是否在线。在线则自动继续跟随，不在线则不激活跟随。

---

## Integration Points

### New Files

- `src/main/java/com/andgatech/gtstaff/fakeplayer/FollowService.java`

### Modified Files

| File | Change |
|------|--------|
| `FakePlayer.java` | 添加 `FollowService` 字段，`onUpdate()` 末尾调用 `followService.tick()` |
| `PlayerActionPack.java` | 确认 `setForward` + `setStrafing` 可同时设置 |
| `CommandPlayer.java` | 新增 `follow` 子命令分支 |
| `FakePlayerManagerUI.java` | `Other` 页签添加跟随相关控件 |
| `FakePlayerManagerService.java` | 新增 `startFollow` / `stopFollow` / `setFollowRange` / `setTeleportRange` 方法 |
| `FakePlayerRegistry.java` | 持久化新增 followTarget/followRange/teleportRange |
| `context.md` | 更新项目上下文 |

### Unchanged

- Mixin 层 — 不需要新 Mixin
- `FakeNetworkManager` / `FakeNetHandlerPlayServer` — 不涉及
- `MachineMonitorService` — 独立运行
- `MonsterRepellentService` — 假人移动时驱逐范围自然跟随

### Tick Integration Order

```
FakePlayer.onUpdate()
  1. super.onUpdate()                    // vanilla tick
  2. actionPack.onUpdate()               // 注入手动控制的移动值
  3. followService.tick()                // 覆盖移动值（follow 激活时优先）
  4. runLivingUpdate(this::onLivingUpdate) // vanilla 移动引擎消费移动值
  5. machineMonitorService.tick(this)     // 机器监控
```

Follow 激活时覆盖 actionPack 设置的 moveForward/moveStrafing，优先级高于手动 move 命令。停止跟随后恢复手动控制。
