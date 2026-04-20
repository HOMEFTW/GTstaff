# GTstaff 假人统一背包接入 Backhand 副手槽设计

## 背景

当前工作树中的 `GTstaff` 已经把假人背包管理扩展为统一界面：

- 左侧展示假人的护甲、快捷栏与主背包
- 右侧展示 `Baubles Expanded` 饰品栏
- 下方展示当前真人玩家背包

用户现在希望继续把 `GTNH LIB/Backhand-master` 中的副手能力并入这套统一背包界面，并明确要求：

- 副手槽直接放在假人护甲槽旁边
- 行为完全跟随 `Backhand` 当前规则
- 包括副手黑名单校验
- 包括 Shift 点击时优先尝试放入副手槽

这意味着本次不是仅在 GUI 上额外画一个“看起来像副手”的槽，而是要把假人的真实副手库存正式并入现有视图、容器和同步链路。

## 目标

实现一套与 `Backhand` 当前行为一致的假人副手管理能力，满足以下目标：

1. 在现有统一背包界面中，为假人增加一个真实可编辑的副手槽
2. 副手槽位置固定在护甲区旁边，不新增独立分页或弹窗
3. 服务端对假人副手的读写直接走 `Backhand` 提供的真实库存接口
4. 客户端即使暂时拿不到假人实体，也能显示副手槽 UI
5. 副手槽的物品合法性判断直接复用 `BackhandSlot`
6. 从玩家背包 Shift 点击物品到假人时，副手槽优先级高于普通背包槽，但低于对应护甲槽

## 非目标

本次不做以下内容：

- 不改 `FakePlayerManagerUI` 的页签结构
- 不额外增加“副手配置”按钮或命令
- 不重做统一背包 GUI 的整体视觉布局
- 不修改 `Backhand` 本身的逻辑、配置或黑名单实现
- 不引入“多个副手槽”或双持快捷切换 UI

## 方案对比与结论

### 方案 A：把副手槽正式并入 `FakePlayerInventoryView`

优点：

- 假人“装备区”数据模型完整，护甲/副手/普通背包都在同一套视图映射中
- 服务端、客户端、容器测试都能围绕统一槽位顺序展开
- 后续 Shift 点击、点击同步、GUI 渲染都更直接
- 最容易做到与 `Backhand` 真实库存一致

缺点：

- 需要调整现有 `FakePlayerInventoryView.SLOT_COUNT` 以及相关测试

### 方案 B：不改视图，只在容器中额外挂一个副手代理槽

优点：

- 表面改动较小

缺点：

- 视图和容器的槽位模型分裂
- 客户端回退场景会更难同步
- 后续 Shift 点击与索引测试更容易混乱

### 方案 C：为护甲、副手、饰品单独抽一个新“装备库存包装层”

优点：

- 理论上结构更抽象

缺点：

- 对当前任务明显过度设计
- 改动面大于收益

### 结论

采用方案 A。

也就是：

- 正式扩展 `FakePlayerInventoryView`
- 正式调整 `FakePlayerInventoryContainer`
- GUI 只做最小布局补位
- 底层真实副手库存与合法性规则全部复用 `Backhand`

## 架构设计

### 1. 依赖与集成边界

`Backhand` 与前面的 `Baubles Expanded` 一样，作为当前统一背包功能的硬依赖接入。

职责划分如下：

- `GTstaff` 负责：
  - 找到目标假人
  - 打开统一背包 GUI
  - 决定统一界面中的槽位顺序与位置
  - 处理玩家背包和假人装备区之间的 Shift 点击流转
- `Backhand` 负责：
  - 提供假人的真实副手库存读写入口
  - 提供当前副手槽索引
  - 提供副手物品是否合法的规则

`GTstaff` 不重新实现副手黑名单逻辑，只复用 `BackhandSlot.isItemValid(...)`。

### 2. 假人视图槽位顺序

当前 `FakePlayerInventoryView` 的固定顺序是：

- `0-3` 护甲
- `4-12` hotbar
- `13-39` 主背包

本次改成：

- `0-3` 护甲
- `4` 副手
- `5-13` hotbar
- `14-40` 主背包

也就是说：

- 总槽位数从 `40` 扩展为 `41`
- hotbar 和主背包索引整体后移 1 位

服务端读写规则：

- 副手槽读取：`BackhandUtils.getOffhandItem(fakePlayer)`
- 副手槽写入：`BackhandUtils.setPlayerOffhandItem(fakePlayer, stack)`

客户端读写规则：

- 客户端 `FakePlayerInventoryView.client(...)` 本地缓存数组同步扩为 41 槽
- 副手槽沿用容器同步，不依赖客户端本地假人实体存在

### 3. 容器结构

统一容器中的槽位顺序改为：

1. 假人护甲槽
2. 假人副手槽
3. 假人快捷栏
4. 假人主背包
5. 假人 `Baubles Expanded` 饰品槽
6. 当前真人玩家背包
7. 当前真人玩家快捷栏

副手槽实现：

- 使用 `BackhandSlot`
- 底层 inventory 仍然使用假人的 `InventoryPlayer`
- `slotIndex` 使用 `BackhandUtils.getOffhandSlot(fakePlayer)`

这样可以直接吃到 `Backhand` 现有的副手黑名单规则。

### 4. Shift 点击优先级

从真人玩家背包 Shift 点击到假人时，优先级改为：

1. 如果物品是护甲并且对应护甲槽可放，优先进护甲槽
2. 如果副手槽为空且 `BackhandSlot.isItemValid(stack)` 为真，优先进副手槽
3. 如果物品可进入 `Baubles` 槽，尝试饰品槽
4. 其余进入假人普通背包区

从假人副手槽 Shift 点击移出时：

- 目标和其他装备槽一致，优先移入当前真人玩家背包

### 5. GUI 布局

GUI 不增加新面板，只在现有护甲区旁边补一个 18x18 的副手槽位。

参考 `Backhand` 在原版背包中的习惯位置：

- 原版 `GuiInventory` 使用的是 `guiLeft + 78, guiTop + 63`

但在 `GTstaff` 当前统一背包里不需要机械复刻同一坐标，只需要保证：

- 副手槽紧贴护甲区
- 与护甲槽视觉属于同一组“装备槽”
- 不挤压 `Baubles` 区域和玩家背包区域

建议布局：

- 保持现有 4 个护甲槽横向排列
- 在其右侧补一个副手槽
- 不新增额外文字标签

### 6. 客户端回退行为

与之前 `Baubles` 的修复经验一致，客户端打开假人背包时不能假设“客户端一定拿得到假人实体”。

因此副手 UI 的策略是：

- 服务端始终基于真实假人实体构建容器
- 客户端即使只拿到 `FakePlayerInventoryView.client(...)`，也必须构造出包含副手槽的容器
- 客户端副手槽显示依赖容器同步，不依赖本地再解析一次假人实体

这能避免出现“功能已经接好了，但客户端完全看不到副手槽”的问题。

## 测试设计

### 1. 视图测试

扩展 `FakePlayerInventoryViewTest`，覆盖：

- 服务端视图能从 `BackhandUtils` 读取副手物品
- `setInventorySlotContents(副手槽)` 会回写到假人的真实副手库存
- 原护甲、hotbar、主背包映射在索引整体后移后仍然正确

### 2. 容器测试

扩展 `FakePlayerInventoryContainerTest`，覆盖：

- 副手槽位于护甲槽之后、普通背包槽之前
- 容器能够识别副手槽位置与玩家背包起始索引
- 从玩家背包 Shift 点击一个合法副手物品时，会优先进入副手槽
- 黑名单物品不会进入副手槽，而会继续走后续普通背包流转
- 客户端回退构造时仍能创建副手槽 UI

### 3. 构建验证

至少重新运行：

- `FakePlayerInventoryViewTest`
- `FakePlayerInventoryContainerTest`
- 与统一背包相关的 `Baubles` 布局测试

最终重新打包客户端测试 jar。

## 风险与约束

### 1. 固定槽位索引整体后移

副手槽插入后，hotbar 与主背包的所有固定索引都会变化。

影响面包括：

- `FakePlayerInventoryView`
- `FakePlayerInventoryContainer`
- 现有容器测试
- GUI 高亮当前 hotbar 槽位的逻辑

因此必须优先补红测试，不能直接改实现。

### 2. `Backhand` 的真实副手槽不在普通 `mainInventory[0..35]`

它通过 `IOffhandInventory` 和 mixin 扩展了 `InventoryPlayer`，因此不能把副手槽当成普通背包尾部槽位硬编码处理。

必须统一通过：

- `BackhandUtils.getOffhandSlot(player)`
- `BackhandUtils.getOffhandItem(player)`
- `BackhandUtils.setPlayerOffhandItem(player, stack)`

### 3. 与 `Baubles` 的装备优先级共存

副手优先级加入后，装备流转顺序更复杂。

必须明确：

- 护甲优先于副手
- 副手优先于饰品
- 饰品优先于普通背包

避免出现“火把、盾牌或任意物品被先吸到饰品区或普通背包”的回归。

## 结论

本次以最小但正式的方式把 `Backhand` 副手并入假人统一背包：

- 数据层正式扩一个副手槽
- 容器层正式插入 `BackhandSlot`
- GUI 层只做一个靠近护甲区的最小视觉补位
- 行为层完全复用 `Backhand` 当前规则

这样可以让假人的护甲、副手、饰品和普通背包都在一张统一界面中管理，同时保持 `GTstaff` 当前已经落地的统一背包结构不被推翻。
