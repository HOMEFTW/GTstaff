# GTstaff 假人统一背包接入 Baubles Expanded 设计
## 背景

`GTstaff` 当前已经有一套可用的假人背包管理链路：

- `FakePlayerManagerUI` 在 `Inventory` 页签中提供“管理背包”入口
- `FakePlayerManagerService.openInventoryManager(...)` 负责权限校验与打开 GUI
- `FakePlayerInventoryGuiHandler` 通过 Forge `IGuiHandler` 打通服务端 `Container` 与客户端 `GuiContainer`
- `FakePlayerInventoryContainer` / `FakePlayerInventoryGui` 目前只管理假人的护甲、快捷栏、主背包，以及当前打开 GUI 的真人玩家背包

用户现在希望把 `GTNH LIB/Baubles-Expanded-master` 的饰品栏能力并入这套现有流程，使管理员可以在同一个假人背包界面里直接给假人装备和卸下饰品。

本次用户已明确确认以下约束：

- `Baubles Expanded` 是 `GTstaff` 的必需依赖，不做“未安装时静默禁用”的可选兼容
- 不打开独立的“饰品栏窗口”，而是并入现有假人背包容器，形成一个更大的统一界面
- 饰品槽数量、槽位类型和可滚动布局必须完全跟随 `Baubles Expanded` 当前运行时配置，而不是只支持经典 4 槽

## 目标

实现一套新的假人统一背包容器，满足以下目标：

1. 保留 `GTstaff` 现有假人背包入口、权限校验与 Forge GUI 打开链路
2. 在同一个 `Container/GuiContainer` 中同时展示并编辑：
   - 假人护甲槽
   - 假人快捷栏
   - 假人主背包
   - 假人 `Baubles Expanded` 饰品槽
   - 当前真人玩家背包
3. 饰品槽数量、类型、是否可见、是否需要滚动，全部以 `Baubles Expanded` 当前配置和槽位分配为准
4. 饰品物品能否放入某个槽位，沿用 `Baubles Expanded` 原生规则，不在 `GTstaff` 里重新实现一套判断逻辑
5. 保持现有普通背包交互不回归，包括拖拽、点击、Shift 点击互传、主手快捷栏高亮

## 非目标

本次不做以下内容：

- 不直接复用 `GuiPlayerExpanded` 作为假人饰品栏主界面
- 不把 `FakePlayerManagerUI` 的 `Inventory` 页签改成 MUI2 内嵌 `ItemSlot` 大界面
- 不新增独立的假人饰品命令或额外的弹窗入口
- 不为“未安装 `Baubles Expanded`”设计 fallback 行为
- 不顺手重构 `FakePlayerManagerUI` 的其余页签布局

## 方案对比与结论

### 方案 A：直接复用 `ContainerPlayerExpanded/GuiPlayerExpanded`

优点：

- 现成拥有饰品槽布局、滚动与槽位类型显示
- `Baubles Expanded` 自己已经验证过普通玩家场景

缺点：

- `ContainerPlayerExpanded` 是围绕“当前打开界面的真人玩家”设计的，默认宿主就是 `player`
- 同时混合了 crafting、玩家盔甲栏、玩家主背包与玩家自己的 baubles 库存
- 要改造成“真人玩家操作假人库存”，需要重写大量内部假设，风险高于收益

### 方案 B：扩展 GTstaff 现有统一容器，底层复用 Baubles 库存与槽位规则

优点：

- 保留 `GTstaff` 已经跑通的打开链路、权限链路与假人定位方式
- 只在现有假人背包容器上增量加入饰品区，回归范围可控
- 可直接复用 `BaublesApi`、`SlotBauble`、`BaubleExpandedSlots` 等原生规则

缺点：

- 需要自行整合统一布局、滚动与容器槽位映射

### 方案 C：完全重写一个全新的统一库存框架

优点：

- 架构最整齐，理论上未来更好扩展

缺点：

- 改动面过大，不符合当前任务的最小改动原则

### 结论

采用方案 B：

- 保留 `GTstaff` 现有入口与权限校验
- 扩展 `FakePlayerInventoryContainer` / `FakePlayerInventoryGui`
- 底层直接接入假人的 Baubles 库存与 `Baubles Expanded` 槽位规则

## 架构设计

### 1. 依赖与集成边界

将 `Baubles Expanded` 作为 `GTstaff` 的硬依赖加入构建。

这意味着：

- 主代码可以直接 `import baubles.*` 类，不必走反射或 `Mods.isModLoaded()` 保护分支
- 如果运行环境缺失 `Baubles Expanded`，视为依赖不完整，不再由 `GTstaff` 负责兼容

`GTstaff` 与 `Baubles Expanded` 的职责边界如下：

- `GTstaff` 负责：
  - 找到目标假人
  - 校验操作权限
  - 打开统一背包 GUI
  - 组合普通背包区与饰品区
  - 管理假人普通背包与真人玩家背包间的互传逻辑
- `Baubles Expanded` 负责：
  - 提供假人的真实饰品库存对象
  - 决定当前有哪些饰品槽类型
  - 决定某个物品是否允许放入某个饰品槽
  - 处理装备/卸下饰品时的原生槽位行为

### 2. 统一容器结构

保留现有 GUI ID 与打开路径：

- `FakePlayerManagerUI`
- `FakePlayerManagerService.openInventoryManager(...)`
- `FakePlayerInventoryGuiHandler`

但 `FakePlayerInventoryGuiHandler` 打开的不再是“只含普通背包”的容器，而是“普通背包 + 饰品区”的统一容器。

容器内部按职责拆成两部分库存源：

- 普通背包区：继续使用 `FakePlayerInventoryView`
- 饰品区：新增基于假人的 Baubles 库存适配访问，不复制数据，只代理到底层真实库存对象

统一容器中的槽位大致分为：

1. 假人护甲槽
2. 假人快捷栏
3. 假人主背包
4. 假人饰品槽
5. 当前真人玩家主背包
6. 当前真人玩家快捷栏

### 3. Baubles 库存接入方式

假人的饰品库存不自己维护副本，统一通过 `BaublesApi.getBaubles(fakePlayer)` 获取真实 `IInventory`。

设计要求：

- 每次打开容器时，都重新从假人实例获取一次 Baubles 库存
- 容器关闭时不做额外的“从临时结构回写到假人”步骤
- 所有饰品编辑都直接作用在底层真实库存对象上，避免双写不同步

这样可以确保：

- 假人的普通装备与饰品始终共享同一份运行时状态
- 避免出现“GUI 看起来更新了，但真实假人没装备上”的镜像问题

### 4. 槽位规则复用

饰品槽不自己实现类型判断，而是尽量复用 `Baubles Expanded` 已有规则：

- 槽位总数、槽位类型来源于 `BaubleExpandedSlots`
- 具体槽位对象优先复用 `SlotBauble`
- 若需要包装，也只在外围做位置与滚动控制，不改其类型校验逻辑

这保证：

- `IBauble` / `IBaubleExpanded` 的判定与 `Baubles Expanded` 保持一致
- 多类型饰品、通用槽、扩展槽位都天然受支持
- 后续如果 `Baubles Expanded` 配置改动，`GTstaff` 无需同步改一套平行规则

## UI 与布局设计

### 1. 总体布局

保留 `GuiContainer` 风格，不新增独立窗口。

新 GUI 扩展为“左侧普通背包，右侧饰品栏”的统一布局：

- 左侧上半部分：
  - 假人护甲 4 槽
  - 假人快捷栏 9 槽
  - 假人主背包 27 槽
- 右侧上半部分：
  - 假人饰品栏
- 下半部分：
  - 当前真人玩家背包 27 槽
  - 当前真人玩家快捷栏 9 槽

标题显示：

- 左上保留假人名称
- 右上增加“Baubles”或“饰品栏”区域标题

### 2. 饰品栏动态布局

饰品区不写死 4 槽。

布局规则：

- 每次打开 GUI 时重新读取 `BaubleExpandedSlots` 当前分配
- 跟随 `Baubles Expanded` 当前“可见槽位”和“是否显示未使用槽位”的配置语义
- 同一屏能放下时，直接完整展示
- 放不下时，仅对右侧饰品区启用滚动，不影响左侧普通背包区和下半部玩家背包区

滚动语义与 `Baubles Expanded` 尽量保持一致：

- 滚动的是“当前可见的饰品槽窗口”
- 服务端保留完整槽位集合
- 客户端只决定当前哪一段饰品槽在屏幕中可见

### 3. 槽位视觉

普通背包部分继续沿用当前 chest-style 风格。

饰品区要求：

- 能看出这是与普通背包不同的装备区域
- 尽量沿用 `SlotBauble` 自带的背景语义或 slot type 表示
- 不要求这次完全复制 `GuiPlayerExpanded` 的全部美术，但必须让玩家能分辨不同饰品槽

### 4. Inventory 页签入口

`FakePlayerManagerUI` 中 `Inventory` 页签不再加第二个“打开饰品栏”按钮。

当前“管理背包”按钮直接进入新的统一界面，用户在同一窗口里管理：

- 假人普通背包
- 假人饰品栏

## 交互与数据流

### 1. 打开流程

1. 用户在 `FakePlayerManagerUI` 选中一个假人
2. 点击 `Inventory` 页签中的“管理背包”
3. `FakePlayerManagerService.openInventoryManager(...)`：
   - 校验当前操作者是 `EntityPlayerMP`
   - 校验目标假人存在且在线
   - 调用 `PermissionHelper.cantManipulate(...)` 做权限检查
4. 校验通过后，沿用现有 `openGui(...)` 路径打开 Forge GUI
5. 服务端构造统一容器，客户端构造新的统一 GUI

### 2. 普通点击与拖拽

普通点击、拖拽、拿起、放下行为继续依赖原版 `Container` 流程。

新增要求：

- 当点击的是假人快捷栏区域时，除默认容器行为外，还要同步更新假人的 `inventory.currentItem`
- 当点击或修改的是假人饰品槽时，底层变更应立即反映到假人真实 Baubles 库存

### 3. Shift 点击互传

统一规则如下：

- 从假人普通背包 Shift 出来：
  - 优先移入当前真人玩家背包
- 从假人饰品槽 Shift 出来：
  - 优先移入当前真人玩家背包
- 从当前真人玩家背包 Shift 到假人：
  - 如果是护甲且对应护甲槽可放，优先尝试护甲槽
  - 如果能放入某个饰品槽，优先尝试合适的饰品槽
  - 否则回落到假人普通背包

判断某个物品能否进饰品槽时，必须交给 `SlotBauble` / `Baubles Expanded` 规则，而不是自己做 `instanceof` 的简化判断。

### 4. 客户端同步

不引入新的自定义网络包。

同步策略：

- 普通背包区继续依赖 `Container` 原生同步
- 饰品区也纳入同一个 `Container`，沿用原生槽位同步机制
- 当前主手快捷栏高亮继续使用已有容器进度条同步或等价机制
- 饰品区滚动位置只保存在客户端 GUI 状态中，不下沉为服务端业务状态

## 错误处理

### 1. 权限与目标校验

以下情况在打开 GUI 前直接失败，并返回明确提示：

- 操作者不是 `EntityPlayerMP`
- 目标假人不存在或不在线
- 当前玩家没有权限操作该假人

### 2. Baubles 库存异常

由于 `Baubles Expanded` 是硬依赖，如果容器构造时拿不到假人的 Baubles 库存对象：

- 视为集成异常
- 不打开一个“缺半边内容”的残缺 GUI
- 直接让打开流程失败并返回明确错误

### 3. 配置变化

为了避免配置变化导致错位：

- 不缓存旧的饰品槽数量或布局
- 每次打开容器时重新读取 `BaubleExpandedSlots` 当前配置

这样即使服务器调整了饰品槽配置，新打开的界面也会直接反映最新布局。

## 测试设计

本次按 TDD 补充测试，至少覆盖以下场景：

### 1. 容器结构测试

- 统一容器会包含原有普通背包槽位
- 统一容器会额外包含当前配置下的 Baubles 槽位
- 不同 Baubles 槽位数量下，容器索引分区正确

### 2. Shift 点击与槽位规则测试

- 真人玩家背包中的合法饰品会优先进入可接受的假人饰品槽
- 不合法物品不会被塞进假人饰品槽
- 假人饰品槽 Shift 出来后会进入真人玩家背包
- 原有护甲槽、普通背包互传逻辑不回归

### 3. 快捷栏测试

- 点击假人快捷栏槽位时，会更新假人的 `currentItem`
- GUI 仍会高亮当前主手快捷栏槽位

### 4. GUI 布局测试

- 新 GUI 尺寸与基础区域存在
- 饰品区在不同槽位数量下能正确计算可见区域
- 需要滚动时，滚动窗口只影响饰品区

### 5. 服务层回归测试

- `openInventoryManager(...)` 仍会对无权限玩家返回错误
- 目标假人离线时不会打开 GUI

## 风险与缓解

### 风险 1：直接复用 `SlotBauble` 时与统一容器索引耦合

缓解：

- 明确把普通背包区与饰品区的槽位范围分开
- 为统一容器增加清晰的索引常量与辅助方法

### 风险 2：饰品区滚动只改客户端，导致容器逻辑混乱

缓解：

- 服务端始终保留完整槽位
- 客户端仅修改显示位置，不改服务端实际槽位集合

### 风险 3：玩家背包 Shift 点击优先级改变，导致原普通背包行为回归

缓解：

- 在新增 Baubles 优先分支后补齐原有 `FakePlayerInventoryContainerTest`
- 明确把“护甲优先、饰品次之、普通背包兜底”的顺序写入测试

## 完成标准

满足以下条件视为本次实现完成：

- `GTstaff` 已将 `Baubles Expanded` 作为硬依赖接入构建
- 点击 `FakePlayerManagerUI` 的“管理背包”后，会打开新的统一容器 GUI
- 新 GUI 中可以直接查看和编辑假人的普通背包与饰品栏
- 饰品槽数量、类型和滚动布局跟随 `Baubles Expanded` 当前配置
- 合法饰品能按 `Baubles Expanded` 规则进入正确槽位
- 原有普通背包交互、主手快捷栏高亮与权限校验不回归
- 相关测试通过，且 `assemble` 能继续产出可测试 jar
