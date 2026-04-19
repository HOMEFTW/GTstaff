# Fake Player SkinPort Lite Integration Design

**Date:** 2026-04-19
**Status:** Approved
**Approach:** 轻量可选兼容，生成时按假人名字解析正版皮肤，失败静默回退

---

## Overview

为 `GTstaff` 生成的假人增加皮肤支持，但范围只限于“新生成假人时，按假人名字尝试获取对应正版账号的皮肤资料”。该能力依赖 `SkinPort` 提供的 Mojang 资料解析链，但 `SkinPort` 不是 GTNH 整合包默认组件，因此本集成必须是可选的：

- 装了 `SkinPort` 时，`GTstaff` 会在生成假人前尝试解析正版 `GameProfile`
- 没装 `SkinPort` 时，`GTstaff` 继续使用现有离线 profile 逻辑
- 解析失败、超时或名称无对应正版账号时，假人仍然正常生成，只是没有皮肤

本次不修改“持久化恢复后的假人皮肤”行为。服务端重启后恢复出的假人仍可能回到默认皮肤；这是当前方案刻意接受的范围边界。

---

## Goals

- 让 `/player <name> spawn` 与 UI 生成入口创建的新假人优先显示该名字对应正版玩家的皮肤
- 保持 `SkinPort` 为可选依赖，不安装时不报错、不影响现有功能
- 保持生成链路鲁棒，皮肤解析失败不应导致生成失败
- 尽量少改现有架构，不修改 registry 持久化格式

## Non-Goals

- 不保证假人在服务端重启恢复后仍保留皮肤
- 不为 `shadow` 单独设计“继承真人当前皮肤”的新规则
- 不改 `SkinPort` 自身源码，也不依赖客户端额外安装 GTstaff 专用补丁
- 不实现手动指定皮肤名、URL 或自定义材质签名

---

## Current Context

`GTstaff` 当前在 `FakePlayer.createFake(...)` 中直接构造：

- `new GameProfile(EntityPlayer.func_146094_a(new GameProfile(null, safeUsername)), safeUsername)`

这会生成一个基于名字的离线 UUID profile，但其中没有 `textures` 属性。`SkinPort` 客户端渲染逻辑会优先读取玩家 `GameProfile` 的完整资料；如果拿不到皮肤纹理，就回退到默认 Steve/Alex。

`SkinPort` 当前可利用的关键点：

- `lain.mods.skins.impl.MojangService.getProfile(String username)`：按正版名字解析 `GameProfile`
- `lain.mods.skins.impl.MojangService.fillProfile(GameProfile profile)`：补全 profile 属性，包括 `textures`

因此最小侵入方案不是改渲染，而是在假人创建前尽可能拿到一个“已补全的正版 profile”。

---

## Proposed Architecture

### 1. Optional Compatibility Layer

新增一个兼容类，例如：

- `com.andgatech.gtstaff.integration.SkinPortCompat`

职责只包含：

- 检测 `SkinPort` 是否存在
- 在存在时，按名字请求 `MojangService.getProfile(...)`
- 若解析成功，再请求 `MojangService.fillProfile(...)`
- 最终返回“可用于创建假人的 `GameProfile`”

此兼容层必须通过反射调用 `SkinPort`，避免在未安装 `SkinPort` 时因类加载失败导致 GTstaff 无法启动。

### 2. Fake Player Creation Hook

在 `FakePlayer.createFake(...)` 中增加“预解析 profile”步骤：

1. 先以假人名字调用 `SkinPortCompat.resolveProfile(name)`
2. 若成功返回带 `textures` 的 profile，则使用该 profile 创建假人
3. 若失败，则回退到当前离线 UUID profile 逻辑

生成链路的行为原则：

- 皮肤是增强项，不是前置条件
- 任一兼容层异常都只能降级，不能中断生成

### 3. Scope Boundary for Restore Path

`FakePlayer.restorePersisted(...)` 保持现状，不新增联网解析，不修改 registry 字段。

原因：

- 这是轻量方案的核心约束
- 恢复链路应保持快速、稳定，不因为外部服务状态影响开服
- 若后续需要“重启后仍保留皮肤”，应作为独立增强任务，通过持久化 `textures` 属性解决

---

## Runtime Flow

### Spawn Flow

```
/player <name> spawn 或 UI 生成
  -> PermissionHelper 正常校验
  -> FakePlayer.createFake(name, ...)
      -> SkinPortCompat.resolveProfile(name)
          -> 未安装 SkinPort -> 返回 empty
          -> 已安装但解析失败 -> 返回 empty
          -> 解析成功 -> 返回完整 GameProfile
      -> 若有完整 profile，直接创建 FakePlayer
      -> 否则回退到现有离线 profile 创建
  -> 注册到 FakePlayerRegistry
  -> 完成生成
```

### Restore Flow

```
服务端重启
  -> FakePlayerRegistry.restorePersisted(...)
  -> FakePlayer.restorePersisted(...)
  -> 使用当前 registry 中已有 profileId/name 恢复
  -> 不重新访问 SkinPort / Mojang
```

---

## Error Handling

兼容层需要显式吞掉并降级以下情况：

- `SkinPort` 未安装，相关类不存在
- 反射调用失败
- `getProfile(name)` 返回空结果或 dummy profile
- `fillProfile(profile)` 返回未补全 profile
- 任何超时、中断、执行异常

降级行为统一为：

- 记录一条低噪声日志或 debug 级信息
- 返回空结果
- 调用方继续走原有离线 profile 创建逻辑

不向玩家弹出错误提示，不改变现有命令/UI 成功语义。

---

## Testing Strategy

新增测试重点应覆盖：

1. `SkinPort` 不存在时，兼容层返回空结果，且不会抛类加载异常
2. 兼容层解析失败时，`FakePlayer.createFake(...)` 仍使用原有 profile 成功生成
3. 兼容层成功返回完整 profile 时，创建链路会使用该 profile
4. `restorePersisted(...)` 不触发任何 `SkinPort` 解析逻辑，行为保持不变

测试层建议把“如何拿 profile”抽成可替换入口，避免单元测试直接依赖真实 `SkinPort` 类与联网环境。

---

## Files Likely To Change

- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- `src/main/java/com/andgatech/gtstaff/integration/SkinPortCompat.java` (new)
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerTest.java` 或新增兼容层测试
- `log.md`
- `ToDOLIST.md`
- `context.md`

---

## Risks And Trade-offs

- 由于不持久化 `textures`，重启恢复后的假人仍可能没有皮肤
- 由于依赖 `SkinPort` 内部类名，若对方升级并改动 API，兼容层可能失效
- 若未来需要把恢复链路也做成有皮肤，需要再补 registry 数据结构迁移

这些风险是轻量方案有意接受的代价，用来换取：

- 最少的侵入
- 不改存档结构
- 不把假人恢复流程绑定到外部网络或可选模组状态

---

## Success Criteria

- 安装 `SkinPort` 时，新生成的假人通常能显示与其名字对应的正版皮肤
- 未安装 `SkinPort` 时，`GTstaff` 功能与当前版本一致
- 皮肤解析失败不会导致命令生成或 UI 生成失败
- 测试可以证明成功路径与回退路径都稳定
