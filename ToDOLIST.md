## 2026-04-18：调研记录

### 后续可评估方向
- [ ] 如果 `/player` 命令继续增长，评估把 `CommandPlayer` 拆为带子命令和细粒度权限节点的命令框架
- [ ] 如果 fake player 需要巡逻、路线或自动到达，评估引入 waypoint / path navigation 层，而不是只靠现在的 `moveForward` / `moveStrafing`
- [ ] 如果 UI 继续复杂化，评估是否为 MUI2 增加可序列化面板状态或 bot 模板持久化能力

### 已完成
- [x] 阅读 `CustomNPC-Plus-master` 源码并完成一次对 `GTstaff` 的可借鉴点调研
# TODO 列表



## 已完成
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

## 搁置 / 拒绝
- 暂无
