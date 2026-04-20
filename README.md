# GTstaff

GTNH (GregTech New Horizons) 服务器端假人管理模组。允许玩家在服务器中生成和管理假人（Fake Player）
## 功能概览

- **假人生成与管理** — 生成、删除、清理假人，支持指定位置、朝向、维度、游戏模式
- **影子假人** — 将真实玩家克隆为假人，继承其位置和状态
- **动作控制** — 攻击、使用物品、跳跃、丢弃物品、移动、转向等
- **背包管理** — 查看假人背包摘要，或通过 GUI 打开完整背包界面
- **GT 机器监控** — 扫描假人附近的 GregTech 机器，定时提醒异常状态
- **怪物驱赶** — 自动驱逐假人周围指定范围内的怪物
- **跟随系统** — 假人跟随指定玩家，支持跨维度传送
- **图形界面** — 通过 `/gtstaff` 打开完整的管理面板 UI
- **权限控制** — 支持 OP 权限等级限制，非 OP 玩家可控制自己的假人
- **数据持久化** — 假人数据自动保存，服务器重启后自动恢复


## 命令参考

### `/gtstaff`

打开假人管理器 UI 面板。

```
/gtstaff
/gtstaff ui
```

### `/player list`

列出当前服务器中所有在线的假人。

```
/player list
```

### `/player <名称> spawn`

生成一个假人。

```
/player <名称> spawn [at <x> <y> <z>] [facing <偏航角> <俯仰角>] [in <维度ID>] [as <游戏模式>]
```

| 参数 | 说明 |
|------|------|
| `at <x> <y> <z>` | 指定生成坐标，默认为世界出生点 |
| `facing <偏航角> <俯仰角>` | 指定面朝方向 |
| `in <维度ID>` | 指定维度，默认为 0（主世界） |
| `as <游戏模式>` | 指定游戏模式（`survival`/`creative`/`adventure`/`1`/`2`/`3`） |

示例：
```
/player bot1 spawn
/player bot1 spawn at 100 64 -200 facing 0 0 in 0 as survival
```

### `/player <名称> kill`

移除一个在线假人。

```
/player <名称> kill
```

### `/player <名称> purge`

彻底移除假人，包括其离线数据文件（playerdata 等）。

```
/player <名称> purge
```

### `/player <名称> shadow`

将一个真实在线玩家克隆为影子假人。该玩家会被替换为假人。

```
/player <名称> shadow
```

### `/player <名称> attack`

让假人开始攻击。

```
/player <名称> attack [once|continuous|interval <tick间隔>]
```

| 模式 | 说明 |
|------|------|
| `once` | 攻击一次（默认） |
| `continuous` | 持续攻击 |
| `interval <tick>` | 每隔指定 tick 攻击一次 |

### `/player <名称> stopattack`

停止攻击。

```
/player <名称> stopattack
```

### `/player <名称> use`

让假人使用物品（右键）。

```
/player <名称> use [once|continuous|interval <tick间隔>]
```

### `/player <名称> stopuse`

停止使用物品。

```
/player <名称> stopuse
```

### `/player <名称> jump`

让假人跳跃。

```
/player <名称> jump [once|continuous|interval <tick间隔>]
```

### `/player <名称> drop`

丢弃手中物品（单个）。

```
/player <名称> drop [once|continuous|interval <tick间隔>]
```

### `/player <名称> dropstack`

丢弃手中整组物品。

```
/player <名称> dropstack [once|continuous|interval <tick间隔>]
```

### `/player <名称> move`

控制假人移动方向。

```
/player <名称> move <forward|backward|left|right|stop>
```

| 方向 | 说明 |
|------|------|
| `forward` | 向前移动 |
| `backward` | 向后移动 |
| `left` | 向左移动 |
| `right` | 向右移动 |
| `stop` | 停止移动 |

### `/player <名称> look`

设置假人的面朝方向。

```
/player <名称> look <north|south|east|west|up|down|at <x> <y> <z>>
```

| 方向 | 说明 |
|------|------|
| `north`/`south`/`east`/`west` | 面朝指定方向 |
| `up`/`down` | 抬头/低头 |
| `at <x> <y> <z>` | 面朝指定坐标 |

### `/player <名称> turn`

旋转假人的视角。

```
/player <名称> turn <left|right|back|<偏航角> [俯仰角]>
```

| 参数 | 说明 |
|------|------|
| `left` | 左转 90° |
| `right` | 右转 90° |
| `back` | 转身 180° |
| `<偏航角> [俯仰角]` | 指定旋转角度 |

### `/player <名称> sneak`

让假人潜行。

```
/player <名称> sneak
```

### `/player <名称> unsneak`

取消潜行。

```
/player <名称> unsneak
```

### `/player <名称> sprint`

让假人冲刺。

```
/player <名称> sprint
```

### `/player <名称> unsprint`

取消冲刺。

```
/player <名称> unsprint
```

### `/player <名称> mount`

让假人骑乘附近的实体。

```
/player <名称> mount [anything]
```

加 `anything` 可骑乘任意实体（包括其他玩家），否则只能骑乘非玩家实体。

### `/player <名称> dismount`

让假人取消骑乘。

```
/player <名称> dismount
```

### `/player <名称> hotbar`

切换假人快捷栏选中的槽位。

```
/player <名称> hotbar <1-9>
```

### `/player <名称> stop`

停止假人正在执行的所有动作。

```
/player <名称> stop
```

### `/player <名称> monitor`

管理 GT 机器监控功能。假人会扫描附近范围内的 GregTech 机器，并定时报告状态。

```
/player <名称> monitor [on|off] [range <半径>] [interval <tick间隔>] [scan]
```

| 参数 | 说明 |
|------|------|
| `on`/`off` | 开启/关闭监控 |
| `range <半径>` | 设置扫描半径（格），默认 16 |
| `interval <tick>` | 设置提醒间隔（tick），最小 60 |
| `scan` | 立即执行一次扫描并显示结果 |

示例：
```
/player bot1 monitor on range 32 interval 600 scan
/player bot1 monitor off
```

### `/player <名称> repel`

管理怪物驱赶功能。阻止怪物在假人附近生成。

```
/player <名称> repel [on|off] [range <半径>]
```

| 参数 | 说明 |
|------|------|
| `on`/`off` | 开启/关闭怪物驱赶 |
| `range <半径>` | 设置驱赶范围（格），默认 64 |

### `/player <名称> inventory`

查看或打开假人的背包。

```
/player <名称> inventory [open|summary]
```

| 子命令 | 说明 |
|--------|------|
| `summary` | 在聊天中显示背包摘要 |
| `open` | 打开背包 GUI 界面 |

### `/player <名称> follow`

让假人跟随指定玩家，支持跨维度传送。

```
/player <名称> follow [玩家名|stop|range <格数>|tprange <格数>]
```

| 参数 | 说明 |
|------|------|
| （无参数） | 跟随命令执行者 |
| `<玩家名>` | 跟随指定玩家 |
| `stop` | 停止跟随 |
| `range <格数>` | 设置跟随距离（最小 1） |
| `tprange <格数>` | 设置传送触发距离，距离超过此值时传送（最小 2） |

示例：
```
/player bot1 follow              — 跟随自己
/player bot1 follow Steve        — 跟随 Steve
/player bot1 follow range 3      — 保持 3 格距离
/player bot1 follow tprange 50   — 距离超过 50 格时传送
/player bot1 follow stop         — 停止跟随
```

## 配置

配置文件位于 `config/GTstaff.cfg`，可在服务器运行时修改。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `fakePlayerPermissionLevel` | 2 | 创建/销毁假人所需的 OP 等级（0-4） |
| `allowNonOpControlOwnBot` | true | 非 OP 玩家是否可以控制自己创建的假人 |
| `maxBotsPerPlayer` | 10 | 每个玩家最多可生成的假人数量 |
| `maxBotsTotal` | 20 | 服务器假人总数上限 |
| `defaultMonitorRange` | 16 | 默认 GT 机器监控扫描范围 |

## 权限说明

- **创建/删除假人**：需要达到 `fakePlayerPermissionLevel` 设定的 OP 等级
- **控制假人**：OP 玩家可控制所有假人；非 OP 玩家在 `allowNonOpControlOwnBot` 开启时可控制自己创建的假人
- **UI 面板**：所有玩家均可打开，但操作受上述权限限制

## 兼容性

- Minecraft 1.7.10
- GTNH (GregTech New Horizons) 整合包环境
- 支持 ServerUtilities 跨服传送兼容
