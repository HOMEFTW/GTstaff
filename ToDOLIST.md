# TODO 列表

## 当前计划
- [ ] 在可启动游戏环境中完成最终人工 smoke test：手工验证 `/gtstaff ui`、Spawn/Look/Inventory 子窗口与 fake player 恢复链路。

## 未来想法
- [ ] 为 `FakePlayerManagerUI` 增加“选中 bot”状态，让 `spawn / look / inventory` 子窗口可以直接绑定当前选中的 fake player，而不是重复输入 bot 名称。
- [ ] 将 `FakePlayerInventoryWindow` 从文本快照升级为基于 MUI2 `ItemSlot` 的只读格子视图。

## 已完成
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

## 搁置 / 拒绝
- 暂无
