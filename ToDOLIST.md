# TODO 列表

## 当前计划
- [ ] 选择 nextgen fake player runtime Wave A 的执行方式：`subagent-driven` 或 `inline`
- [ ] 为 GTstaff 假人背包管理界面接入 Baubles Expanded 饰品栏支持：在现有统一背包容器中合并展示并编辑假人的饰品槽，槽位数量、类型与滚动布局跟随 Baubles Expanded 当前配置

## 已完成
- [x] 编写 nextgen fake player runtime Wave A implementation plan：保存到 `docs/superpowers/plans/2026-04-20-gtstaff-nextgen-fake-player-runtime-wave-a.md`
- [x] 审阅并确认 `docs/superpowers/specs/2026-04-20-gtstaff-nextgen-fake-player-runtime-design.md`，确认后进入 implementation plan 阶段
- [x] 为 GTstaff 编写并提交 nextgen fake player runtime 重构设计 spec：明确以 Forge `FakePlayer` 语义为核心、双轨迁移、全量功能平移、不接受降级的总体方案
- [x] 对照 `fabric-carpet-master` 排查 fake player 攻击无效问题：确认 carpet 的攻击实现对 GTstaff 只有设计参考价值，当前更应继续排查命中链与 Forge fake player 兼容灰区
- [x] 重新确认并打包当前 v1.0.2 jar：执行离线 `assemble`，确认 `gtstaff-v1.0.2.jar` / `-dev.jar` / `-sources.jar` 已是最新产物
- [x] 修复 fake player 攻击 living 实体仍然无伤害的问题：当原版左键没有造成真实掉血时，直接在服务端强制扣减目标生命值，避免伤害回退继续被事件链吞掉
- [x] 重新打包当前 v1.0.2 jar：把 fake player 对 living 实体的强制伤害回退重新编入 `gtstaff-v1.0.2.jar` / `-dev.jar` / `-sources.jar`
- [x] 重新打包当前 v1.0.2 jar：把 fake player 实体攻击 fallback 修复重新编入 `gtstaff-v1.0.2.jar` / `-dev.jar` / `-sources.jar`
- [x] 修复 fake player 攻击实体时无伤害的问题：保留原版左键主链，但在这次攻击没有产生任何可观察受击结果时补一次最小服务端伤害 fallback
- [x] 重新打包当前 v1.0.2 jar：把 `attack/use` 无目标时的空挥手反馈重新编入 `gtstaff-v1.0.2.jar` / `-dev.jar` / `-sources.jar`
- [x] 让 `attack/use` 指令无目标时也执行空挥手反馈：即使没有实际命中实体或方块，客户端仍能看到假人立刻做出一次可见动作
- [x] 重新打包当前 v1.0.2 jar：把 fake player 实体攻击修复重新编入 `gtstaff-v1.0.2.jar` / `-dev.jar` / `-sources.jar`
- [x] 修复 fake player 近距离无法锁定实体攻击：让实体目标选择对齐 vanilla `getMouseOver()`，避免贴近实体时左键完全取不到目标
- [x] 修正版本号到 v1.0.2 并重新打包：确认新功能已合并到工作区与产物后，重新生成 `gtstaff-v1.0.2.jar` / `-dev.jar` / `-sources.jar`
- [x] 重新打包当前 GTstaff jar：运行离线 `assemble`，生成最新的 `gtstaff-v1.0.1.jar` / `-dev.jar` / `-sources.jar`
- [x] 为 GTstaff 假人新增 OpenBlocks 电梯类移动触发兼容：把 `jump` / `sneak` 的客户端移动事件缺口收口到独立兼容桥，让假人可在电梯方块上正常上下楼
- [x] 修复 `log.md` 乱码并按日期倒序整理：统一改回正常 UTF-8 内容，并保持最新记录在最前
- [x] 为 GTstaff 假人新增“伪客户端物品使用桥”兼容：在普通 `/player <name> use` 之后补一层可扩展的客户端效果桥，先兼容 TST `ItemYamato` 这类把主效果写在客户端右键链路里的物品
- [x] 发布 v1.0.1：更新项目版本号、重新打包新 jar，并将代码与 release 资产推送到 GitHub
- [x] 修复假人仍回退默认皮的问题：`SkinPortCompat` 现在先解析在线 UUID，再走服务端 secure `fillProfileProperties(..., true)` 生成带签名 `textures`；同时补上无 `SkinPort` 时的服务端 profile cache/repository 回退与回归测试
- [x] 使用 `./gradlew.bat --no-daemon -DDISABLE_BUILDSCRIPT_UPDATE_CHECK=true -PautoUpdateBuildScript=false -PdisableSpotless=true assemble` 打包当前 GTstaff jar
- [x] 服务端重启恢复 fake player 后异步按 bot 名联网补皮；成功时安全重建为带皮肤的新实例，失败、无纹理或任务过期时保留原 bot
- [x] 阅读 `SkinPort-master` 源码并加入轻量可选兼容：新生成的 fake player 会在安装 `SkinPort` 时按 bot 名尝试解析正版 `GameProfile` 皮肤资料；未安装、解析失败或无纹理时静默回退到现有离线 profile，且不改持久化恢复路径
- [x] 补齐缺失的假人命令入口：新增 `repel`、`inventory`，并为 `monitor` 增加 `scan` 与 `interval`；同时把 `stopattack`、`stopuse` 写入 `/player` 用法字符串
- [x] 扩展“完全清除”对 `playerdata` 的删除范围：额外删除 `<假人名>.tf` 与 `<假人名>.tfback`
- [x] 阅读 `ServerUtilities-master` 源码并加入兼容：让 GTstaff 生成的 fake player 被 `serverutils.lib.util.ServerUtils.isFake(...)` 识别为 fake player，不计入 ServerUtilities 统计
- [x] 在概览页新增“完全清除”按钮，并增加 `/player <name> purge` 命令：可将选中或指定 fake player 从在线实体、持久化 registry、当前世界存档与服务端工作根下的 `playerdata`、`serverutilities/players` 与 `stats` 中彻底删除；其中 `playerdata` 会清 UUID `.dat` 和名字对应的 `baub/baubback/thaum/thaumback`，并立即写回 `data/gtstaff_registry.dat`
- [x] 敌对生物驱逐器功能 + UI其他功能页签（v0.1.1）
- [x] 修复 fake player 手动调用 `onUpdateEntity()` 导致第二次 `PlayerTickEvent` 触发 `ae2fc` 崩溃的问题，并改为直接运行 `onLivingUpdate()`
- [x] 完成 Task 13（GT机器监控增强）：扩展 `MachineState` 支持13种故障类型（断电/需维护/输出满/结构不完整/污染堵塞/无法修复/缺涡轮/部件错误/发电机不足/资源耗尽/电力不足/电压不足），接入 `ShutDownReasonRegistry` 和 `CheckRecipeResult.getID()` 全量检测，翻译监控报告为中文
- [x] 完成 Task 14（假人颜色分配）：基于名称hash为每个假人分配唯一颜色（`EnumChatFormatting`），聊天消息通过 `ChatStyle.setColor()` 着色，UI 通过 `§x` 格式代码显示
- [x] 完成 Task 15（监控UI滚动+10秒周期提醒）：监控页面改用 `ListWidget+VerticalScrollData` 可滚动列表，聊天栏每10秒重复提醒存在问题的机器
- [x] 完成 Task 16（提醒频率可配置）：UI监控页面新增4个频率按钮（10秒/30秒/1分钟/5分钟），默认30秒，支持持久化保存
- [x] 完成 Task 17（聊天栏换行修复）：概览和问题提醒改为逐行发送，每台机器独占一条聊天消息
- [x] 修复集成服中 fake player 触发 `ae2fc` `PlayerTickEvent` 空指针崩溃的问题，并在 GTstaff 侧补上只针对已知外部 NPE 的兼容测试
- [x] 完成 Task UI-1~4：扩展 FakePlayerManagerService（executeAction / killBot / shadowBot / toggleMonitor / setMonitorRange / scanMachines / getInventorySummaryText）
- [x] 完成 Task UI-5：重写 FakePlayerManagerUI 为 420x200 CustomNPC 风格分页布局（ListWidget + PagedWidget + Column）
- [x] 完成 Task UI-6：端到端验证（test + assemble 通过）
- [x] 完成 Task 1：fake player core scaffolding 与基础测试
- [x] 完成 Task 2：fake network lifecycle
- [x] 完成 Task 3：`FakePlayer` 生命周期与 GT 机器监控骨架
- [x] 完成 Task 4：通过 Mixin 接入 `PlayerActionPack`
- [x] 完成 Task 5：补全 `PlayerActionPack` 行为
- [x] 完成 Task 6：实现 `/player` 与 `/gtstaff` 命令
- [x] 完成 Task 7（监控部分）：接入 GT 多方块真实扫描、状态映射与离线回归验证
- [x] 完成 Task 7（Spawn 子窗口）：打通 MUI2 Spawn 表单、服务端按钮同步与 `/player spawn` 命令复用
- [x] 完成 Task 7（持久化恢复部分）：支持跨重启自动恢复 fake player 实体
- [x] 完成 Task 7（Look 子窗口）：打通 MUI2 Look 表单、方向按钮与 `/player look` 命令复用
- [x] 完成 Task 7（Inventory 子窗口）：打通 MUI2 inventory 只读快照窗口与服务端刷新链路
- [x] 完成 Task 8（自动化部分）：补用户提示、跑完整离线测试，并完成最终自动化 smoke test
- [x] 完成 Task 9（稳定性修复）：修复集成服启动期 fake player 自动恢复导致的服务端崩溃
- [x] 完成 Task 10（MUI2 稳定性修复）：修复 `FakePlayerManagerUI` factory 未注册导致的客户端闪退
- [x] 完成 Task 11（MUI2 布局修复）：修复 Spawn/Inventory/Look 子窗口错位到屏幕右侧导致无法操作
- [x] 完成 Task 12（UI 重构）：将 `FakePlayerManagerUI` 重构为左侧 bot 列表 + 右侧页签，并接入原版 chest-style fake player 背包容器
- [x] 完成假人跟随功能（v0.1.2）：实现 `FollowService`，支持步行/飞行跟随、自动传送、跨维度5秒延迟传送（含聊天栏倒计时提示）、飞行状态同步、可配置跟随/传送距离；UI其他页面重新排布跟随与怪物驱逐区域；命令 `/player <name> follow` 支持；持久化保存跟随状态
- [x] 修复假人跟随连续跨维度传送卡维度 bug：跨维度迁移失败时会回滚 `dimension/worldObj` 并在下一个倒计时周期继续重试，同时补齐旧世界摘除与新世界挂接流程
- [x] 修复玩家重启客户端/服务端后假人停止跟随的问题：目标玩家临时离线时保留 `followTargetUUID`，玩家重连后可继续跟随并重新触发跨维度传送
- [x] 修复同 UUID 旧死玩家实体阻塞跟随的问题：目标查找优先选择存活实体，避免玩家重连/切维后第二次跨维度仍被旧实体卡住
- [x] 修复 UI 中生成假人按钮失效的问题：生成窗口改为一次请求提交整张表单，避免 bot 名等字段因同步竞态在服务端被读成空值

## 搁置 / 拒绝
- 暂无
