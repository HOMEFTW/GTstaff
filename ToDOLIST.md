## 2026-04-18：调研记录

### 后续可评估方向
- [ ] 如果 `/player` 命令继续增长，评估把 `CommandPlayer` 拆为带子命令和细粒度权限节点的命令框架
- [ ] 如果 fake player 需要巡逻、路线或自动到达，评估引入 waypoint / path navigation 层，而不是只靠现在的 `moveForward` / `moveStrafing`
- [ ] 如果 UI 继续复杂化，评估是否为 MUI2 增加可序列化面板状态或 bot 模板持久化能力

### 已完成
- [x] 阅读 `CustomNPC-Plus-master` 源码并完成一次对 `GTstaff` 的可借鉴点调研
# TODO 列表

## 当前计划
- [ ] 在可启动游戏环境中完成最终人工 smoke test：重点验证新的 `/gtstaff ui` 420x200 分页布局、左侧可滚动 bot 列表、四个页签交互、Spawn/Look 弹窗、原版背包容器。
- [ ] 使用最新 `build/libs/gtstaff-9563983-master+*.jar` 进行客户端实机测试。
- [ ] 使用最新 `build/libs/gtstaff-7a7f3c5-master+7a7f3c523a-dirty.jar` 进行客户端实机测试，确认集成服启动、MUI2 管理台与 chest-style 背包 GUI 的行为。
- [ ] 如需让 `./gradlew.bat build` 全绿，补修 `spotlessJavaCheck` 的 CRLF/LF 格式问题。

## 未来想法
- [ ] 让 `Spawn / Look` 面板也直接消费主界面的当前选中 bot，而不是继续手填 bot 名称。
- [ ] 继续扩展 `Actions / Monitor` 页签，把常用控制与监控摘要直接放进主管理台右侧。

## 已完成
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


