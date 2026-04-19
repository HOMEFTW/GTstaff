# Fake Player Restore Skin Rebuild Design

**Date:** 2026-04-19
**Status:** Approved
**Approach:** 服务端重启恢复假人后异步联网取皮肤，成功后安全重建假人实体

---

## Overview

为 `GTstaff` 增加“服务端每次重启后，恢复出来的 fake player 也会联网尝试加载皮肤”的能力，但不阻塞服务器启动和 fake player 的基础恢复。

核心策略分两段：

- 第一段：仍按当前逻辑恢复 fake player，保证服务端重启后 bot 能立即回来
- 第二段：恢复完成后异步按 bot 名通过 `SkinPort` 解析正版皮肤资料；若成功拿到带 `textures` 的 `GameProfile`，则安全地下线旧 bot，并在原状态基础上重建一个带皮肤的新 bot

该方案刻意不做“在线热改 profile 并刷新客户端外观”，而是采用“异步成功后重建实体”的方式，换取更高的客户端可见性确定性。

---

## Goals

- 服务端每次重启后，对恢复出的 fake player 按 bot 名联网尝试加载正版皮肤
- 不阻塞 `restorePersisted(...)` 与服务器开服流程
- 皮肤解析成功后，让客户端最终看到带皮肤的 fake player
- 重建后保留 fake player 的核心运行状态

## Non-Goals

- 不修改持久化格式，不缓存 `textures` 到 registry
- 不保证无缝热更新原实体皮肤
- 不实现失败后的无限重试或定时重试队列
- 不改变“新生成 fake player”现有的皮肤逻辑

---

## Current Context

当前 `GTstaff` 已完成两部分皮肤相关能力：

- 新生成 fake player 时，`FakePlayer.createFake(...)` 会通过 `FakePlayerProfiles.createSpawnProfile(...)` 优先尝试 `SkinPortCompat.resolveProfile(name)`
- `SkinPortCompat` 会在安装 `SkinPort` 时通过反射调用 `lain.mods.skins.impl.MojangService.getProfile(String)` 与 `fillProfile(GameProfile)`，拿到带 `textures` 的正版 `GameProfile`

但 `FakePlayer.restorePersisted(...)` 仍然直接使用：

- `new GameProfile(profileId, data.getName())`

这意味着重启恢复出来的 fake player 只有 UUID 和名字，没有 `textures` 属性，因此客户端通常只能看到默认皮肤。

---

## Proposed Architecture

### 1. Restore Skin Rebuild Coordinator

新增一个恢复后调度器，例如：

- `FakePlayerSkinRestoreScheduler`

职责：

- 接收“刚恢复完成的 fake player”
- 在不阻塞主恢复链路的前提下异步请求 `SkinPortCompat.resolveProfile(botName)`
- 成功时安排一次服务端主线程中的“安全重建”
- 失败时静默结束，不影响当前已恢复的 bot

该调度器只处理“恢复路径”，不接管新生成 fake player 的皮肤逻辑。

### 2. Restore Snapshot For Rebuild

重建前需要保留旧 bot 的关键状态快照。可在 fake player 侧新增一个轻量快照结构，例如：

- `FakePlayerRestoreState`

至少包含：

- 名称
- owner UUID
- 维度
- 坐标
- 朝向
- 游戏模式
- 飞行状态
- 监控开关与范围、提醒频率
- 驱逐开关与范围
- 跟随目标 UUID、跟随范围、传送范围

快照来源应尽量复用现有字段读取逻辑，避免重复拼装状态。

### 3. Safe Rebuild Path

新增一条“使用显式 profile 重建恢复 bot”的路径，例如：

- `FakePlayer.rebuildRestoredWithProfile(server, oldFakePlayer, GameProfile profile)`

重建流程：

1. 从旧 bot 读取运行状态快照
2. 校验 registry 中当前同名 bot 仍然是这一个旧实例，避免异步任务对过期对象动手
3. 安全移除旧 bot
4. 使用新 profile 和旧状态创建一个新 fake player
5. 重新挂回运行状态
6. 更新 registry 中的在线实体引用

如果任一步失败：

- 不应把旧 bot 先删掉再无条件继续
- 应优先保留旧 bot 存活，避免出现“补皮失败反而把 bot 弄没了”

---

## Data Flow

### Server Restart Restore Flow

```
serverStarted
  -> FakePlayerRegistry.restorePersisted(...)
  -> FakePlayer.restorePersisted(...)
  -> 立即恢复默认 profile 的 fake player
  -> FakePlayerSkinRestoreScheduler.schedule(fakePlayer)
      -> 异步调用 SkinPortCompat.resolveProfile(botName)
      -> 失败 / 无 textures -> 结束
      -> 成功 -> 切回服务端主线程
          -> 校验当前 registry 中同名 bot 仍是原实例
          -> 用带皮肤 profile 重建 fake player
          -> 替换 registry 中的在线 bot 引用
```

### Failure Paths

- 未安装 `SkinPort`：调度器直接结束
- 联网失败：结束，保留原 bot
- 解析到的 profile 无 `textures`：结束，保留原 bot
- 异步任务完成时 bot 已被手动 kill / purge / 替换：结束，不再重建
- 重建过程中发生异常：优先保留旧 bot，避免 bot 丢失

---

## Rebuild Semantics

重建后的 bot 应保留：

- 世界与位置
- 朝向
- 游戏模式
- 飞行状态
- owner UUID
- 机器监控状态
- 敌对生物驱逐状态
- 跟随状态与参数

本次不要求额外迁移：

- 运行时瞬态 tick 计数器
- 正在进行中的跨维度倒计时
- 其他未持久化、也未对外承诺稳定的中间状态

若跟随服务存在运行态内部计时器，允许在重建后回到“由配置重新初始化”的状态，而不要求精确续接倒计时进度。

---

## Threading And Safety

- 联网请求必须在后台执行，不能阻塞主线程
- fake player 替换必须切回服务端主线程执行
- 异步结果回到主线程时，必须再次确认目标 bot 仍存在且实例未过期
- 任何异常都应降级为“不补皮，但 bot 继续活着”

这意味着调度器需要同时具备：

- 后台解析能力
- 主线程回切能力

实现上应优先复用现有项目里已使用的服务端主线程调度模式，而不是引入新的复杂线程框架。

---

## Testing Strategy

新增测试重点：

1. 恢复出的 fake player 会被调度进入“异步补皮”流程
2. 皮肤解析失败时，不会删除或替换当前 bot
3. 皮肤解析成功时，会触发一次重建并替换 registry 中的 bot 实例
4. 重建后关键状态仍保留：
   - 位置/维度
   - 游戏模式/飞行
   - owner
   - 监控/驱逐
   - 跟随目标与参数
5. 若异步结果返回时 bot 已经不是原实例，不会误替换新实例

测试应优先使用可注入 resolver / scheduler 的方式，不依赖真实 `SkinPort` 联网环境。

---

## Files Likely To Change

- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfiles.java`
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreScheduler.java`
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerSkinRestoreScheduler.java` (new)
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRestoreSchedulerTest.java`
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerProfilesTest.java` 或新增恢复相关测试
- `log.md`
- `ToDOLIST.md`
- `context.md`

---

## Risks And Trade-offs

- 重建方案比“热改 profile”更粗暴，恢复后会发生一次实体替换
- 若未来 bot 拥有更多未持久化运行态，重建时可能需要继续补迁移
- 由于仍依赖联网查询，每次重启的皮肤结果受外部服务状态影响

这些代价换来的是：

- 不阻塞开服
- 客户端更稳定地看到最终皮肤
- 不需要侵入客户端皮肤刷新链

---

## Success Criteria

- 服务端重启恢复的 fake player 会异步尝试联网加载正版皮肤
- 联网失败不会影响 bot 恢复与存活
- 联网成功后，同名 bot 会被安全替换为带皮肤的新实例
- 关键运行状态在替换后仍保留
