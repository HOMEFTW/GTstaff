# GTstaff FakePlayer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 GTNH 1.7.10 的 `GTstaff` 模组中实现可生成、控制、销毁、持久化、监控 GT 机器并可通过 MUI2 管理的虚拟玩家系统。

**Architecture:** 以 `fabric-carpet-master` 的 `EntityPlayerMPFake`、`EntityPlayerActionPack`、`PlayerCommand` 为参考，把 1.21 的 fake player 流程映射到 1.7.10 的 `EntityPlayerMP`、`NetworkManager`、`NetHandlerPlayServer`、`ICommand` 和 GTNH Mixin 环境。实现顺序必须先打通实体与网络生命周期，再接入动作调度和命令入口，最后做 GT 机器监控、持久化和 MUI2 管理界面。

**Tech Stack:** Java 8, Forge 1.7.10, Sponge Mixin 0.8.5-GTNH, GTNHLib, ModularUI, GT5-Unofficial, JUnit 5, Gradle

---

## File Structure

### Existing files to extend

- `src/main/java/com/andgatech/gtstaff/GTstaff.java`
  - 保持模组入口不动，只补充生命周期内对 registry 持久化和服务端 UI 命令的挂接。
- `src/main/java/com/andgatech/gtstaff/CommonProxy.java`
  - 注册 `/player`、`/gtstaff`，在服务端启动和关闭时加载/保存 fake player registry。
- `src/main/java/com/andgatech/gtstaff/config/Config.java`
  - 保留现有配置项，补充文档文本和必要的范围修正逻辑。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
  - 从骨架扩展为完整 fake player 核心实体。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakeNetworkManager.java`
  - 实现空壳网络连接。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakeNetHandlerPlayServer.java`
  - 实现 fake player 的服务端网络处理器。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`
  - 扩展为完整动作调度器，包含朝向、移动、交互、挖掘、骑乘、丢弃、热键切换。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/Action.java`
  - 保留现有结构，修正 tick 语义以匹配 Carpet 的调度行为。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/ActionType.java`
  - 保留枚举，但需要配套动作执行逻辑。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
  - 扩展为 name -> fake、owner -> bots、多 bot、NBT 持久化、安全注销。
- `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
  - 从占位实现扩展为完整 `ICommand` 命令分发器。
- `src/main/java/com/andgatech/gtstaff/util/PermissionHelper.java`
  - 扩展为完整权限/限制校验，输出明确失败原因。
- `src/main/resources/mixins.gtstaff.json`
  - 注册所有 mixin。
- `src/main/resources/META-INF/accesstransformer.cfg`
  - 暴露 1.7.10 fake player 需要访问的受保护成员。

### New files to create

- `src/main/java/com/andgatech/gtstaff/command/CommandGTstaff.java`
  - 暴露 `/gtstaff` 和 `/gtstaff ui`。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/IFakePlayerHolder.java`
  - Mixin 暴露 `PlayerActionPack` 的访问接口。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/MachineMonitorService.java`
  - 把 GT 机器扫描和状态差分从 `FakePlayer` 中拆出来，避免实体类过大。
- `src/main/java/com/andgatech/gtstaff/fakeplayer/MachineState.java`
  - 独立 GT 机器状态结构，便于测试和序列化。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java`
  - MUI2 主界面。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerSpawnWindow.java`
  - 生成新 bot 的弹窗。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryWindow.java`
  - 只读背包窗口。
- `src/main/java/com/andgatech/gtstaff/ui/FakePlayerLookWindow.java`
  - 视角设置窗口。
- `src/main/java/com/andgatech/gtstaff/mixin/EntityPlayerMPMixin.java`
  - 为 `EntityPlayerMP` 注入 `PlayerActionPack`。
- `src/main/java/com/andgatech/gtstaff/mixin/EntityPlayerMP_RespawnMixin.java`
  - fake player 死亡后 respawn 时替换实例。
- `src/main/java/com/andgatech/gtstaff/mixin/ServerConfigurationManagerMixin.java`
  - 登录路径中替换 net handler，并调用 fake player 初始位置修正。
- `src/main/java/com/andgatech/gtstaff/mixin/Entity_KnockbackMixin.java`
  - 修正 fake player 击退。
- `src/main/java/com/andgatech/gtstaff/mixin/EntityPlayerMP_TickFreezeMixin.java`
  - 为 fake player 绕过 tick freeze。
- `src/test/java/com/andgatech/gtstaff/fakeplayer/ActionTest.java`
  - 覆盖 `Action.once/continuous/interval`。
- `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
  - 覆盖 registry 注册/注销/owner 映射/NBT 持久化。
- `src/test/java/com/andgatech/gtstaff/util/PermissionHelperTest.java`
  - 覆盖权限策略的纯逻辑分支。

## Task 1: Lock Down Core Scaffolding And Tests

**Files:**
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/ActionTest.java`
- Create: `src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java`
- Create: `src/test/java/com/andgatech/gtstaff/util/PermissionHelperTest.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/Action.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Modify: `src/main/java/com/andgatech/gtstaff/util/PermissionHelper.java`

- [ ] **Step 1: Write failing unit tests for `Action` semantics**

```java
@Test
void onceRunsExactlyOnce() {
    Action action = Action.once();
    assertTrue(action.tick());
    assertFalse(action.tick());
    assertTrue(action.done);
}

@Test
void continuousRunsEveryTickWithoutFinishing() {
    Action action = Action.continuous();
    assertTrue(action.tick());
    assertTrue(action.tick());
    assertFalse(action.done);
}

@Test
void intervalSkipsUntilConfiguredTick() {
    Action action = Action.interval(3);
    assertFalse(action.tick());
    assertFalse(action.tick());
    assertTrue(action.tick());
}
```

- [ ] **Step 2: Run tests to confirm current scaffolding fails**

Run: `./gradlew test --tests com.andgatech.gtstaff.fakeplayer.ActionTest`
Expected: FAIL because current `Action.tick()` semantics do not match the intended schedule.

- [ ] **Step 3: Fix `Action` to match Carpet-style scheduling**

```java
public boolean tick() {
    if (done) return false;
    if (offset > 0) {
        offset--;
        return false;
    }
    tickCount++;
    if (tickCount % interval != 0) return false;
    if (limit > 0) {
        limit--;
    }
    if (limit == 0) {
        done = true;
    }
    return true;
}
```

- [ ] **Step 4: Write failing tests for registry multi-owner behavior**

```java
@Test
void registerTracksMultipleBotsPerOwner() {
    UUID owner = UUID.randomUUID();
    FakePlayerRegistry.register("Bot_A", null, owner);
    FakePlayerRegistry.register("Bot_B", null, owner);
    assertEquals(2, FakePlayerRegistry.getCountByOwner(owner));
}
```

- [ ] **Step 5: Refactor registry API before entity work starts**

```java
public static void register(String botName, FakePlayer fakePlayer, UUID ownerUUID) {
    fakePlayers.put(botName.toLowerCase(Locale.ROOT), fakePlayer);
    ownerToBotNames.computeIfAbsent(ownerUUID, ignored -> new HashSet<>()).add(botName);
}
```

- [ ] **Step 6: Run focused tests**

Run: `./gradlew test --tests com.andgatech.gtstaff.fakeplayer.ActionTest --tests com.andgatech.gtstaff.fakeplayer.FakePlayerRegistryTest --tests com.andgatech.gtstaff.util.PermissionHelperTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/Action.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/main/java/com/andgatech/gtstaff/util/PermissionHelper.java src/test/java/com/andgatech/gtstaff/fakeplayer/ActionTest.java src/test/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistryTest.java src/test/java/com/andgatech/gtstaff/util/PermissionHelperTest.java
git commit -m "test: lock down fake player core scaffolding"
```

## Task 2: Implement Fake Network Lifecycle

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakeNetworkManager.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakeNetHandlerPlayServer.java`
- Modify: `src/main/resources/META-INF/accesstransformer.cfg`
- Test: `./gradlew compileJava`

- [ ] **Step 1: Make `FakeNetworkManager` a valid open connection**

```java
public FakeNetworkManager(boolean clientSide) {
    super(clientSide);
    this.channel = new EmbeddedChannel();
}

@Override
public boolean isChannelOpen() {
    return true;
}
```

- [ ] **Step 2: Keep all network output side-effect free**

```java
@Override
public void sendPacket(Packet packet) {}

@Override
public void closeChannel(IChatComponent message) {}

@Override
public void processReceivedPackets() {}
```

- [ ] **Step 3: Rebuild `FakeNetHandlerPlayServer` with real constructor dependencies**

```java
public FakeNetHandlerPlayServer(MinecraftServer server, NetworkManager networkManager, EntityPlayerMP player) {
    super(server, networkManager, player);
}
```

- [ ] **Step 4: Mirror Carpet disconnect behavior**

```java
@Override
public void disconnect(IChatComponent reason) {
    String key = reason instanceof ChatComponentTranslation translation ? translation.getKey() : "";
    if ("multiplayer.disconnect.idling".equals(key) || "multiplayer.disconnect.duplicate_login".equals(key)) {
        ((FakePlayer) this.playerEntity).kill();
    }
}
```

- [ ] **Step 5: Add access transformers only for members actually needed by fake player wiring**

```cfg
net.minecraft.network.NetworkManager field_150746_k
net.minecraft.server.management.ServerConfigurationManager field_72404_b
net.minecraft.entity.player.EntityPlayerMP field_71135_a
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakeNetworkManager.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakeNetHandlerPlayServer.java src/main/resources/META-INF/accesstransformer.cfg
git commit -m "feat: add fake player network lifecycle"
```

## Task 3: Implement `FakePlayer` Entity And Registry Integration

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/MachineState.java`
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/MachineMonitorService.java`
- Test: `./gradlew compileJava`

- [ ] **Step 1: Replace placeholder constructor with a valid profile-based constructor**

```java
private FakePlayer(MinecraftServer server, WorldServer world, GameProfile profile) {
    super(server, world, profile, new ItemInWorldManager(world));
}
```

- [ ] **Step 2: Implement `createFake` using Carpet’s flow adapted to 1.7.10**

```java
public static FakePlayer createFake(String username, MinecraftServer server, BlockPos pos, float yaw, float pitch, int dimension, GameType gamemode, boolean flying) {
    WorldServer world = server.worldServerForDimension(dimension);
    GameProfile profile = resolveGameProfile(server, username);
    FakePlayer fake = new FakePlayer(server, world, profile);
    fake.playerNetServerHandler = new FakeNetHandlerPlayServer(server, new FakeNetworkManager(false), fake);
    fake.setPositionAndRotation(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, pitch);
    fake.theItemInWorldManager.setGameType(gamemode);
    fake.capabilities.isFlying = flying;
    server.getConfigurationManager().playerEntityList.add(fake);
    world.spawnEntityInWorld(fake);
    return fake;
}
```

- [ ] **Step 3: Implement shadow spawn and instant respawn hooks**

```java
public static FakePlayer createShadow(MinecraftServer server, EntityPlayerMP player) {
    FakePlayer shadow = createFake(player.getCommandSenderName(), server, new BlockPos(player), player.rotationYaw, player.rotationPitch, player.dimension, player.theItemInWorldManager.getGameType(), player.capabilities.isFlying);
    shadow.inventory.copyInventory(player.inventory);
    shadow.setHealth(player.getHealth());
    return shadow;
}
```

- [ ] **Step 4: Override lifecycle methods**

```java
@Override
public void onUpdate() {
    if (ticksExisted % 10 == 0 && playerNetServerHandler != null) {
        playerNetServerHandler.setPlayerLocation(posX, posY, posZ, rotationYaw, rotationPitch);
    }
    super.onUpdate();
    ((IFakePlayerHolder) this).gtstaff$getActionPack().onUpdate();
    MachineMonitorService.tick(this);
}
```

- [ ] **Step 5: Move monitoring state into testable helper classes**

```java
public final class MachineState {
    public boolean wasActive;
    public boolean hadPower;
    public boolean neededMaintenance;
    public boolean wasOutputFull;
}
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/main/java/com/andgatech/gtstaff/fakeplayer/MachineState.java src/main/java/com/andgatech/gtstaff/fakeplayer/MachineMonitorService.java
git commit -m "feat: implement fake player entity lifecycle"
```

## Task 4: Inject `PlayerActionPack` With Mixins

**Files:**
- Create: `src/main/java/com/andgatech/gtstaff/fakeplayer/IFakePlayerHolder.java`
- Create: `src/main/java/com/andgatech/gtstaff/mixin/EntityPlayerMPMixin.java`
- Create: `src/main/java/com/andgatech/gtstaff/mixin/EntityPlayerMP_RespawnMixin.java`
- Create: `src/main/java/com/andgatech/gtstaff/mixin/ServerConfigurationManagerMixin.java`
- Create: `src/main/java/com/andgatech/gtstaff/mixin/Entity_KnockbackMixin.java`
- Create: `src/main/java/com/andgatech/gtstaff/mixin/EntityPlayerMP_TickFreezeMixin.java`
- Modify: `src/main/resources/mixins.gtstaff.json`
- Test: `./gradlew compileJava`

- [ ] **Step 1: Create the mixin access interface**

```java
public interface IFakePlayerHolder {
    PlayerActionPack gtstaff$getActionPack();
}
```

- [ ] **Step 2: Inject action pack creation and ticking**

```java
@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMPMixin implements IFakePlayerHolder {
    @Unique private PlayerActionPack gtstaff$actionPack;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtstaff$initActionPack(CallbackInfo ci) {
        this.gtstaff$actionPack = new PlayerActionPack((EntityPlayerMP) (Object) this);
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void gtstaff$tickActionPack(CallbackInfo ci) {
        this.gtstaff$actionPack.onUpdate();
    }
}
```

- [ ] **Step 3: Redirect login and respawn paths for fake players**

```java
@Redirect(method = "initializeConnectionToPlayer", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/NetworkManager;Lnet/minecraft/entity/player/EntityPlayerMP;)Lnet/minecraft/network/NetHandlerPlayServer;"))
private NetHandlerPlayServer gtstaff$replaceHandler(MinecraftServer server, NetworkManager network, EntityPlayerMP player) {
    return player instanceof FakePlayer ? new FakeNetHandlerPlayServer(server, network, player) : new NetHandlerPlayServer(server, network, player);
}
```

- [ ] **Step 4: Register mixins in JSON**

```json
"mixins": [
  "EntityPlayerMPMixin",
  "EntityPlayerMP_RespawnMixin",
  "ServerConfigurationManagerMixin",
  "Entity_KnockbackMixin",
  "EntityPlayerMP_TickFreezeMixin"
]
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/IFakePlayerHolder.java src/main/java/com/andgatech/gtstaff/mixin src/main/resources/mixins.gtstaff.json
git commit -m "feat: wire fake players through mixins"
```

## Task 5: Finish `PlayerActionPack` Behavior

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/ActionType.java`
- Test: `./gradlew test --tests com.andgatech.gtstaff.fakeplayer.ActionTest`

- [ ] **Step 1: Add the missing internal state used by Carpet**

```java
private BlockPos currentBlock;
private int blockHitDelay;
private float curBlockDamageMP;
private int itemUseCooldown;
```

- [ ] **Step 2: Port use/attack mutual exclusion into `onUpdate()`**

```java
Map<ActionType, Boolean> attempts = new EnumMap<>(ActionType.class);
for (Map.Entry<ActionType, Action> entry : actions.entrySet()) {
    if (!(Boolean.TRUE.equals(attempts.get(ActionType.USE)) && entry.getKey() == ActionType.ATTACK)) {
        boolean success = executeAction(entry.getKey());
        attempts.put(entry.getKey(), success);
    }
}
```

- [ ] **Step 3: Implement direct control helpers used by `/player`**

```java
public void turn(float yaw, float pitch) { look(player.rotationYaw + yaw, player.rotationPitch + pitch); }
public void stopMovement() { moveForward = 0.0F; moveStrafing = 0.0F; sneaking = false; sprinting = false; }
public void setSlot(int slot) { player.inventory.currentItem = slot - 1; }
```

- [ ] **Step 4: Implement block-breaking state machine for survival and creative**

```java
if (player.theItemInWorldManager.getGameType().isCreative()) {
    player.theItemInWorldManager.onBlockClicked(pos, side);
    blockHitDelay = 5;
} else {
    curBlockDamageMP += block.getPlayerRelativeBlockHardness(player, player.worldObj, pos);
    if (curBlockDamageMP >= 1.0F) {
        player.theItemInWorldManager.tryHarvestBlock(pos);
        currentBlock = null;
    }
}
```

- [ ] **Step 5: Run focused tests and compile**

Run: `./gradlew test --tests com.andgatech.gtstaff.fakeplayer.ActionTest && ./gradlew compileJava`
Expected: PASS and BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/PlayerActionPack.java src/main/java/com/andgatech/gtstaff/fakeplayer/ActionType.java
git commit -m "feat: implement fake player action pack"
```

## Task 6: Implement `/player` And `/gtstaff` Commands

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java`
- Create: `src/main/java/com/andgatech/gtstaff/command/CommandGTstaff.java`
- Modify: `src/main/java/com/andgatech/gtstaff/CommonProxy.java`
- Modify: `src/main/java/com/andgatech/gtstaff/util/PermissionHelper.java`
- Test: `./gradlew compileJava`

- [ ] **Step 1: Parse top-level subcommands before adding branch logic**

```java
String botName = args[0];
String action = args[1].toLowerCase(Locale.ROOT);
switch (action) {
    case "spawn": handleSpawn(sender, botName, args); break;
    case "kill": handleKill(sender, botName); break;
    case "shadow": handleShadow(sender, botName); break;
    case "monitor": handleMonitor(sender, botName, args); break;
    default: handleManipulation(sender, botName, action, args);
}
```

- [ ] **Step 2: Port Carpet’s spawn validation into `PermissionHelper`**

```java
public static Optional<String> cantSpawn(ICommandSender sender, String botName, MinecraftServer server) {
    if (FakePlayerRegistry.isSpawning(botName)) return Optional.of("Player is currently spawning");
    if (server.getConfigurationManager().func_152612_a(botName) != null) return Optional.of("Player already online");
    if (FakePlayerRegistry.getCount() >= Config.maxBotsTotal) return Optional.of("Server bot limit reached");
    return Optional.empty();
}
```

- [ ] **Step 3: Implement action command mapping**

```java
case "attack": pack.start(ActionType.ATTACK, parseAction(args, 2)); break;
case "use": pack.start(ActionType.USE, parseAction(args, 2)); break;
case "jump": pack.start(ActionType.JUMP, parseAction(args, 2)); break;
case "move": handleMove(pack, args); break;
case "look": handleLook(pack, sender, args); break;
```

- [ ] **Step 4: Register `/gtstaff ui` in server startup**

```java
event.registerServerCommand(new CommandPlayer());
event.registerServerCommand(new CommandGTstaff());
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/command/CommandPlayer.java src/main/java/com/andgatech/gtstaff/command/CommandGTstaff.java src/main/java/com/andgatech/gtstaff/CommonProxy.java src/main/java/com/andgatech/gtstaff/util/PermissionHelper.java
git commit -m "feat: add fake player commands"
```

## Task 7: Add GT Machine Monitoring, Persistence, And MUI2 UI

**Files:**
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java`
- Modify: `src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java`
- Modify: `src/main/java/com/andgatech/gtstaff/CommonProxy.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerManagerUI.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerSpawnWindow.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerInventoryWindow.java`
- Create: `src/main/java/com/andgatech/gtstaff/ui/FakePlayerLookWindow.java`
- Test: `./gradlew compileJava`

- [ ] **Step 1: Implement machine scan service with state-diff reporting**

```java
public static void tick(FakePlayer fakePlayer) {
    if (!fakePlayer.isMonitoring() || fakePlayer.ticksExisted % 60 != 0) return;
    Map<BlockPos, MachineState> latest = scanMachines(fakePlayer);
    emitDiffMessages(fakePlayer, fakePlayer.getOwnerUUID(), fakePlayer.getMonitoredMachines(), latest);
    fakePlayer.replaceMonitoredMachines(latest);
}
```

- [ ] **Step 2: Add registry persistence**

```java
public static void save(File file) throws IOException {
    NBTTagCompound root = new NBTTagCompound();
    // owner -> bot list
    CompressedStreamTools.write(root, file);
}
```

- [ ] **Step 3: Restore registry in proxy lifecycle**

```java
public void serverStarted(FMLServerStartedEvent event) {
    FakePlayerRegistry.load(new File(event.getServer().getFile("."), "data/gtstaff_registry.dat"));
}
```

- [ ] **Step 4: Build the MUI2 main window before popups**

```java
builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> openSpawnWindow(player)));
builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> selectedBot.stopAll()));
builder.widget(new TextWidget().setStringSupplier(this::buildStatusText));
```

- [ ] **Step 5: Add read-only inventory popup**

```java
new SlotWidget(fakePlayer.inventory, slotIndex).setEnabled(false);
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayer.java src/main/java/com/andgatech/gtstaff/fakeplayer/FakePlayerRegistry.java src/main/java/com/andgatech/gtstaff/CommonProxy.java src/main/java/com/andgatech/gtstaff/ui
git commit -m "feat: add bot monitoring persistence and ui"
```

## Task 8: End-To-End Verification And Cleanup

**Files:**
- Modify: `src/main/resources/assets/gtstaff/lang/en_US.lang`
- Test: `./gradlew test`
- Test: `./gradlew compileJava`
- Test: manual dedicated-server smoke test

- [ ] **Step 1: Add user-facing messages and command usage strings**

```properties
gtstaff.command.player.spawned=Spawned fake player %s
gtstaff.command.player.killed=Killed fake player %s
gtstaff.command.monitor.enabled=Enabled GT monitor for %s
```

- [ ] **Step 2: Run full unit test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run final compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run manual smoke test on a dev server**

Run:

```bash
./gradlew runServer
```

Expected:
- `/player Bot_Steve spawn` creates a visible fake player
- `/player Bot_Steve attack continuous` swings and mines
- `/player Bot_Steve monitor on range 16` begins state-diff reporting
- `/gtstaff ui` opens the MUI2 manager for OP

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/assets/gtstaff/lang/en_US.lang
git commit -m "chore: finish fake player feature verification"
```

## Self-Review

- Spec coverage:
  - FakePlayer 核心实体：Task 2-4
  - 动作调度系统：Task 1, Task 5, Task 6
  - GT 机器监控：Task 3, Task 7
  - `/player` 与 `/gtstaff`：Task 6
  - MUI2 管理界面：Task 7
  - Mixin 与 AT：Task 2, Task 4
  - 生命周期、数据流、持久化：Task 3, Task 7, Task 8
- Remaining gaps to watch during implementation:
  - `ServerConfigurationManager` 在 1.7.10 的确切注入点名称需要以编译器报错和 MCP 名称为准微调。
  - GT 机器监控对哪些 `MetaTileEntity` 视为“输出满”需要在实现时查 `GT5-Unofficial` 现有 API。
  - MUI2 同步器类名可能与设计文档中的 `FakeSyncWidget` 有出入，需要以 GTNHLib/ModularUI 的实际类为准。
- Placeholder scan:
  - 没有使用 `TODO`、`TBD`、`similar to task N` 这类占位语。
  - 所有代码步骤都给出了目标方法/结构的具体代码骨架。
- Type consistency:
  - action pack 统一通过 `IFakePlayerHolder.gtstaff$getActionPack()` 访问。
  - registry 统一围绕 `botName + ownerUUID` 存储，不再混用单 bot owner 映射。
  - `MachineState` 单独成类，避免 `FakePlayer.MachineState` 与监控服务类型分裂。
