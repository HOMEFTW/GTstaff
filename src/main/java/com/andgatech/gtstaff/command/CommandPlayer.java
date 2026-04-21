package com.andgatech.gtstaff.command;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.DimensionManager;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotHandle;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventorySummary;
import com.andgatech.gtstaff.fakeplayer.runtime.BotLifecycleManager;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.integration.ServerUtilitiesCompat;
import com.andgatech.gtstaff.util.PermissionHelper;
import com.andgatech.gtstaff.util.PlayerDataCleanup;
import com.mojang.authlib.GameProfile;

public class CommandPlayer extends CommandBase {

    private final BotLifecycleManager lifecycleManager;

    public CommandPlayer() {
        this(new BotLifecycleManager());
    }

    CommandPlayer(BotLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager == null ? new BotLifecycleManager() : lifecycleManager;
    }

    @Override
    public String getCommandName() {
        return "player";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/player <name> spawn|kill|purge|shadow|attack|stopattack|use|stopuse|jump|drop|dropStack|move|look|turn|sneak|unsneak|sprint|unsprint|mount|dismount|hotbar|stop|monitor|repel|inventory|follow [player|stop|range <n>|tprange <n>] ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return Config.fakePlayerPermissionLevel;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP || super.canCommandSenderUseCommand(sender);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && "list".equalsIgnoreCase(args[0])) {
            handleList(sender);
            return;
        }

        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String botName = args[0];
        String action = args[1].toLowerCase(Locale.ROOT);
        String[] trailingArgs = Arrays.copyOfRange(args, 2, args.length);

        switch (action) {
            case "spawn":
                handleSpawn(sender, botName, trailingArgs);
                return;
            case "kill":
                handleKill(sender, botName);
                return;
            case "purge":
                handlePurge(sender, botName);
                return;
            case "shadow":
                handleShadow(sender, botName);
                return;
            case "monitor":
                handleMonitor(sender, botName, trailingArgs);
                return;
            case "repel":
                handleRepel(sender, botName, trailingArgs);
                return;
            case "inventory":
                handleInventory(sender, botName, trailingArgs);
                return;
            case "follow":
                handleFollow(sender, botName, trailingArgs);
                return;
            default:
                handleManipulation(sender, botName, action, trailingArgs);
        }
    }

    protected void handleList(ICommandSender sender) {
        if (FakePlayerRegistry.getCount() == 0) {
            notifySender(sender, "No fake players are registered.");
            return;
        }

        String botNames = FakePlayerRegistry.getAllBotHandles()
            .stream()
            .map(BotHandle::name)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
        notifySender(sender, "Fake players (" + FakePlayerRegistry.getCount() + "): " + botNames);
    }

    protected void handleSpawn(ICommandSender sender, String botName, String[] args) {
        MinecraftServer server = requireServer();
        if (sender instanceof EntityPlayerMP player
            && !player.canCommandSenderUseCommand(Config.fakePlayerPermissionLevel, "gtstaff")) {
            throw new CommandException("You do not have permission to spawn fake players");
        }
        Optional<String> denial = PermissionHelper.cantSpawn(sender, botName, server);
        if (denial.isPresent()) {
            throw new CommandException(denial.get());
        }

        SpawnOptions options = SpawnOptions.defaults(sender, server);
        for (int index = 0; index < args.length; index++) {
            String token = args[index].toLowerCase(Locale.ROOT);
            switch (token) {
                case "at":
                    ensureRemaining(args, index, 3, sender);
                    options.position = new ChunkCoordinates(
                        parseInt(sender, args[++index]),
                        parseInt(sender, args[++index]),
                        parseInt(sender, args[++index]));
                    break;
                case "facing":
                    ensureRemaining(args, index, 2, sender);
                    options.yaw = (float) parseDouble(sender, args[++index]);
                    options.pitch = (float) parseDouble(sender, args[++index]);
                    break;
                case "in":
                    ensureRemaining(args, index, 1, sender);
                    options.dimension = parseInt(sender, args[++index]);
                    break;
                case "as":
                    ensureRemaining(args, index, 1, sender);
                    options.gameType = parseGameType(args[++index]);
                    break;
                default:
                    throw new WrongUsageException(getCommandUsage(sender));
            }
        }

        WorldServer world = resolveWorld(server, options.dimension);
        if (options.position == null) {
            options.position = world.getSpawnPoint();
        }
        if (options.gameType == null) {
            options.gameType = world.getWorldInfo()
                .getGameType();
        }

        UUID ownerUUID = sender instanceof EntityPlayerMP player ? player.getUniqueID() : null;
        BotRuntimeView runtime = lifecycleManager.spawn(
            botName,
            server,
            options.position,
            options.yaw,
            options.pitch,
            options.dimension,
            options.gameType,
            options.flying,
            ownerUUID);

        notifySender(
            sender,
            "Spawned fake player " + runtime.name() + " at " + formatPosition(runtime.entity().asPlayer()));
    }

    protected void handleKill(ICommandSender sender, String botName) {
        BotRuntimeView runtime = requireOnlineRuntime(botName);
        if (PermissionHelper.cantRemove(sender, runtime)) {
            throw new CommandException("You do not have permission to remove that bot");
        }
        if (!lifecycleManager.kill(botName)) {
            throw new CommandException("Fake player not found: " + botName);
        }
        notifySender(sender, "Killed fake player " + runtime.name());
    }

    protected void handlePurge(ICommandSender sender, String botName) {
        BotRuntimeView runtime = FakePlayerRegistry.getRuntimeView(botName);
        if (runtime != null) {
            String targetName = runtime.name();
            EntityPlayerMP player = runtime.entity() == null ? null : runtime.entity()
                .asPlayer();
            UUID profileId = resolveProfileId(targetName, player);
            if (PermissionHelper.cantRemove(sender, runtime)) {
                throw new CommandException("You do not have permission to remove that bot");
            }
            if (!lifecycleManager.kill(targetName)) {
                throw new CommandException("Fake player not found: " + targetName);
            }
            purgeSavedPlayerFiles(requireServer(), targetName, profileId);
            FakePlayerRegistry.saveServerRegistry(requireServer());
            notifySender(sender, "Purged fake player " + targetName);
            return;
        }

        if (!FakePlayerRegistry.contains(botName)) {
            throw new CommandException("Fake player not found: " + botName);
        }
        if (cantRemovePersisted(sender, botName)) {
            throw new CommandException("You do not have permission to remove that bot");
        }

        UUID profileId = resolveProfileId(botName, null);
        FakePlayerRegistry.unregister(botName);
        purgeSavedPlayerFiles(requireServer(), botName, profileId);
        FakePlayerRegistry.saveServerRegistry(requireServer());
        notifySender(sender, "Purged fake player " + botName);
    }

    protected void handleShadow(ICommandSender sender, String botName) {
        MinecraftServer server = requireServer();
        EntityPlayerMP realPlayer = resolvePlayer(sender, botName);
        if (ServerUtilitiesCompat.isFakePlayer(realPlayer)) {
            throw new CommandException("Target is already a fake player");
        }
        if (FakePlayerRegistry.contains(botName)) {
            throw new CommandException("Fake player already exists");
        }
        if (FakePlayerRegistry.getCount() >= Config.maxBotsTotal) {
            throw new CommandException("Server bot limit reached");
        }
        if (!canShadow(sender, realPlayer)) {
            throw new CommandException("You do not have permission to shadow that player");
        }
        if (FakePlayerRegistry.getCountByOwner(realPlayer.getUniqueID()) >= Config.maxBotsPerPlayer) {
            throw new CommandException("Player bot limit reached");
        }

        BotRuntimeView shadow = lifecycleManager.shadow(server, realPlayer);
        if (shadow == null) {
            throw new CommandException("Unable to create shadow player");
        }

        notifySender(sender, "Created shadow fake player " + shadow.name());
    }

    protected void handleMonitor(ICommandSender sender, String botName, String[] args) {
        BotRuntimeView runtime = requireOnlineRuntime(botName);
        if (PermissionHelper.cantManipulate(sender, runtime)) {
            throw new CommandException("You do not have permission to control that bot");
        }
        if (args.length == 0) {
            notifySender(sender, buildMonitorStatus(runtime));
            return;
        }

        Boolean monitoring = null;
        Integer range = null;
        Integer interval = null;
        boolean scan = false;
        for (int index = 0; index < args.length; index++) {
            String token = args[index].toLowerCase(Locale.ROOT);
            switch (token) {
                case "on":
                    monitoring = true;
                    break;
                case "off":
                    monitoring = false;
                    break;
                case "range":
                    ensureRemaining(args, index, 1, sender);
                    range = parseIntWithMin(sender, args[++index], 1);
                    break;
                case "interval":
                    ensureRemaining(args, index, 1, sender);
                    interval = parseIntWithMin(sender, args[++index], 60);
                    break;
                case "scan":
                    scan = true;
                    break;
                default:
                    throw new WrongUsageException(
                        "/player <name> monitor [on|off] [range <radius>] [interval <ticks>] [scan]");
            }
        }

        if (monitoring != null) {
            runtime.monitor()
                .setMonitoring(monitoring);
        }
        if (range != null) {
            runtime.monitor()
                .setMonitorRange(range);
        }
        if (interval != null) {
            runtime.monitor()
                .setReminderInterval(interval);
        }

        notifySender(sender, buildMonitorStatus(runtime));
        if (scan) {
            sendMachineOverview(sender, runtime);
        }
    }

    protected void handleRepel(ICommandSender sender, String botName, String[] args) {
        BotRuntimeView runtime = requireOnlineRuntime(botName);
        if (PermissionHelper.cantManipulate(sender, runtime)) {
            throw new CommandException("You do not have permission to control that bot");
        }

        if (args.length == 0) {
            notifySender(sender, buildRepelStatus(runtime));
            return;
        }

        Boolean repelling = null;
        Integer range = null;
        for (int index = 0; index < args.length; index++) {
            String token = args[index].toLowerCase(Locale.ROOT);
            switch (token) {
                case "on":
                    repelling = true;
                    break;
                case "off":
                    repelling = false;
                    break;
                case "range":
                    ensureRemaining(args, index, 1, sender);
                    range = parseIntWithMin(sender, args[++index], 1);
                    break;
                default:
                    throw new WrongUsageException("/player <name> repel [on|off] [range <radius>]");
            }
        }

        if (repelling != null) {
            runtime.repel()
                .setRepelling(repelling);
        }
        if (range != null) {
            runtime.repel()
                .setRepelRange(range);
        }

        notifySender(sender, buildRepelStatus(runtime));
    }

    protected void handleInventory(ICommandSender sender, String botName, String[] args) {
        BotRuntimeView runtime = requireOnlineRuntime(botName);
        if (PermissionHelper.cantManipulate(sender, runtime)) {
            throw new CommandException("You do not have permission to control that bot");
        }

        if (args.length == 0 || "summary".equalsIgnoreCase(args[0])) {
            notifySenderLines(sender, buildInventorySummary(runtime));
            return;
        }

        if (args.length == 1 && "open".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof EntityPlayerMP player)) {
                throw new CommandException("Inventory manager can only be opened by a player");
            }

            notifySender(sender, runtime.inventory()
                .openInventoryManager(player));
            return;
        }

        throw new WrongUsageException("/player <name> inventory [open|summary]");
    }

    protected void handleFollow(ICommandSender sender, String botName, String[] args) {
        BotRuntimeView runtime = requireOnlineRuntime(botName);
        if (PermissionHelper.cantManipulate(sender, runtime)) {
            throw new CommandException("You do not have permission to control that bot");
        }
        EntityPlayerMP runtimePlayer = runtime.entity()
            .asPlayer();
        BotFollowRuntime followService = runtime.follow();

        if (args.length == 0) {
            if (!(sender instanceof EntityPlayerMP player)) {
                throw new CommandException("Only players can be followed");
            }
            runtime.follow()
                .startFollowing(player.getUniqueID());
            notifySender(sender, FakePlayer.colorizeName(runtime.name()) + " 开始跟随你");
            return;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "stop":
                followService.stop();
                if (runtimePlayer != null) {
                    runtimePlayer.moveForward = 0.0F;
                    runtimePlayer.moveStrafing = 0.0F;
                    runtimePlayer.setJumping(false);
                }
                notifySender(sender, FakePlayer.colorizeName(runtime.name()) + " 停止跟随");
                return;
            case "range":
                if (args.length != 2) {
                    throw new WrongUsageException("/player <name> follow range <blocks>");
                }
                int followRange = parseIntWithMin(sender, args[1], 1);
                followService.setFollowRange(followRange);
                notifySender(sender, runtime.name() + " 跟随距离设置为 " + followRange + " 格");
                return;
            case "tprange":
                if (args.length != 2) {
                    throw new WrongUsageException("/player <name> follow tprange <blocks>");
                }
                int tpRange = parseIntWithMin(sender, args[1], 2);
                followService.setTeleportRange(tpRange);
                notifySender(sender, runtime.name() + " 传送距离设置为 " + tpRange + " 格");
                return;
            default:
                EntityPlayerMP followTarget = getPlayer(sender, args[0]);
                followService.startFollowing(followTarget.getUniqueID());
                notifySender(
                    sender,
                    FakePlayer.colorizeName(runtime.name()) + " 开始跟随 "
                        + followTarget.getCommandSenderName());
                return;
        }
    }

    protected void handleManipulation(ICommandSender sender, String botName, String action, String[] args) {
        BotRuntimeView runtime = requireOnlineRuntime(botName);
        EntityPlayerMP target = runtime.entity() == null ? null : runtime.entity().asPlayer();
        if (PermissionHelper.cantManipulate(sender, runtime)) {
            throw new CommandException("You do not have permission to control that bot");
        }

        BotActionRuntime actionRuntime = runtime.action();
        switch (action.toLowerCase(Locale.ROOT)) {
            case "attack":
                actionRuntime.start(ActionType.ATTACK, parseAction(sender, args));
                notifySender(sender, "Set attack action for " + runtime.name());
                return;
            case "use":
                actionRuntime.start(ActionType.USE, parseAction(sender, args));
                notifySender(sender, "Set use action for " + runtime.name());
                return;
            case "stopattack":
                actionRuntime.stop(ActionType.ATTACK);
                notifySender(sender, "Stopped attack for " + runtime.name());
                return;
            case "stopuse":
                actionRuntime.stop(ActionType.USE);
                notifySender(sender, "Stopped use for " + runtime.name());
                return;
            case "jump":
                actionRuntime.start(ActionType.JUMP, parseAction(sender, args));
                notifySender(sender, "Set jump action for " + runtime.name());
                return;
            case "drop":
                actionRuntime.start(ActionType.DROP_ITEM, parseAction(sender, args));
                notifySender(sender, "Set drop action for " + runtime.name());
                return;
            case "dropstack":
                actionRuntime.start(ActionType.DROP_STACK, parseAction(sender, args));
                notifySender(sender, "Set dropStack action for " + runtime.name());
                return;
            case "move":
                handleMove(sender, runtime, actionRuntime, args);
                return;
            case "look":
                handleLook(sender, runtime, target, actionRuntime, args);
                return;
            case "turn":
                handleTurn(sender, runtime, actionRuntime, args);
                return;
            case "sneak":
                actionRuntime.setSneaking(true);
                notifySender(sender, runtime.name() + " is now sneaking");
                return;
            case "unsneak":
                actionRuntime.setSneaking(false);
                notifySender(sender, runtime.name() + " stopped sneaking");
                return;
            case "sprint":
                actionRuntime.setSprinting(true);
                notifySender(sender, runtime.name() + " is now sprinting");
                return;
            case "unsprint":
                actionRuntime.setSprinting(false);
                notifySender(sender, runtime.name() + " stopped sprinting");
                return;
            case "mount":
                if (target == null) {
                    throw new CommandException("Fake player entity is unavailable");
                }
                handleMount(sender, target, args);
                return;
            case "dismount":
                actionRuntime.dismount();
                notifySender(sender, runtime.name() + " dismounted");
                return;
            case "hotbar":
                if (args.length != 1) {
                    throw new WrongUsageException("/player <name> hotbar <1-9>");
                }
                actionRuntime.setSlot(parseIntBounded(sender, args[0], 1, 9));
                notifySender(sender, runtime.name() + " switched hotbar slot");
                return;
            case "stop":
                actionRuntime.stopAll();
                notifySender(sender, runtime.name() + " stopped all actions");
                return;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void handleMove(ICommandSender sender, BotRuntimeView runtime, BotActionRuntime actionRuntime, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException("/player <name> move [forward|backward|left|right|stop]");
        }

        actionRuntime.setForward(0.0F);
        actionRuntime.setStrafing(0.0F);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "forward":
                actionRuntime.setForward(1.0F);
                break;
            case "backward":
                actionRuntime.setForward(-1.0F);
                break;
            case "left":
                actionRuntime.setStrafing(1.0F);
                break;
            case "right":
                actionRuntime.setStrafing(-1.0F);
                break;
            case "stop":
                actionRuntime.stopMovement();
                break;
            default:
                throw new WrongUsageException("/player <name> move [forward|backward|left|right|stop]");
        }

        notifySender(sender, "Updated movement for " + runtime.name());
    }

    private void handleLook(ICommandSender sender, BotRuntimeView runtime, EntityPlayerMP target,
        BotActionRuntime actionRuntime, String[] args) {
        if (target == null) {
            throw new CommandException("Fake player entity is unavailable");
        }
        if (args.length == 0) {
            throw new WrongUsageException("/player <name> look <north|south|east|west|up|down|at <x> <y> <z>>");
        }

        String token = args[0].toLowerCase(Locale.ROOT);
        switch (token) {
            case "north":
                actionRuntime.look(180.0F, 0.0F);
                break;
            case "south":
                actionRuntime.look(0.0F, 0.0F);
                break;
            case "east":
                actionRuntime.look(-90.0F, 0.0F);
                break;
            case "west":
                actionRuntime.look(90.0F, 0.0F);
                break;
            case "up":
                actionRuntime.look(target.rotationYaw, -90.0F);
                break;
            case "down":
                actionRuntime.look(target.rotationYaw, 90.0F);
                break;
            case "at":
                if (args.length != 4) {
                    throw new WrongUsageException("/player <name> look at <x> <y> <z>");
                }
                lookAt(sender, target, actionRuntime, args[1], args[2], args[3]);
                break;
            default:
                throw new WrongUsageException("/player <name> look <north|south|east|west|up|down|at <x> <y> <z>>");
        }

        notifySender(sender, "Updated look direction for " + runtime.name());
    }

    private void handleTurn(ICommandSender sender, BotRuntimeView runtime, BotActionRuntime actionRuntime, String[] args) {
        if (args.length == 0 || args.length > 2) {
            throw new WrongUsageException("/player <name> turn <left|right|back|<yaw> [pitch]>");
        }

        String token = args[0].toLowerCase(Locale.ROOT);
        switch (token) {
            case "left":
                actionRuntime.turn(-90.0F, 0.0F);
                break;
            case "right":
                actionRuntime.turn(90.0F, 0.0F);
                break;
            case "back":
                actionRuntime.turn(180.0F, 0.0F);
                break;
            default:
                float yaw = (float) parseDouble(sender, args[0]);
                float pitch = args.length == 2 ? (float) parseDouble(sender, args[1]) : 0.0F;
                actionRuntime.turn(yaw, pitch);
                break;
        }

        notifySender(sender, "Turned " + runtime.name());
    }

    private void handleMount(ICommandSender sender, EntityPlayerMP target, String[] args) {
        boolean allowAnything = args.length == 1 && "anything".equalsIgnoreCase(args[0]);
        if (args.length > 1 || (args.length == 1 && !allowAnything)) {
            throw new WrongUsageException("/player <name> mount [anything]");
        }

        @SuppressWarnings("unchecked")
        List<Entity> nearbyEntities = target.worldObj
            .getEntitiesWithinAABBExcludingEntity(target, target.boundingBox.expand(3.0D, 1.0D, 3.0D));
        for (Entity entity : nearbyEntities) {
            if (entity == null || entity == target) {
                continue;
            }
            if (!allowAnything && entity instanceof EntityPlayerMP) {
                continue;
            }
            target.mountEntity(entity);
            notifySender(sender, target.getCommandSenderName() + " mounted " + entity.getCommandSenderName());
            return;
        }

        throw new CommandException("No nearby entity to mount");
    }

    private void lookAt(ICommandSender sender, EntityPlayerMP target, BotActionRuntime actionRuntime, String xArg,
        String yArg, String zArg) {
        double x = parseDouble(sender, xArg);
        double y = parseDouble(sender, yArg);
        double z = parseDouble(sender, zArg);
        double dx = x - target.posX;
        double dy = y - (target.posY + target.getEyeHeight());
        double dz = z - target.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0D / Math.PI));
        actionRuntime.look(yaw, pitch);
    }

    private Action parseAction(ICommandSender sender, String[] args) {
        if (args.length == 0 || "once".equalsIgnoreCase(args[0])) {
            return Action.once();
        }
        if ("continuous".equalsIgnoreCase(args[0])) {
            return Action.continuous();
        }
        if ("interval".equalsIgnoreCase(args[0]) && args.length == 2) {
            return Action.interval(parseIntWithMin(sender, args[1], 1));
        }
        throw new WrongUsageException("[once|continuous|interval <ticks>]");
    }

    private BotRuntimeView requireOnlineRuntime(String botName) {
        BotRuntimeView runtime = FakePlayerRegistry.getRuntimeView(botName);
        if (runtime == null) {
            throw new CommandException("Fake player not found: " + botName);
        }
        return runtime;
    }

    private boolean cantRemovePersisted(ICommandSender sender, String botName) {
        if (!(sender instanceof EntityPlayerMP player)) {
            return false;
        }
        if (player.canCommandSenderUseCommand(Config.fakePlayerPermissionLevel, "gtstaff")) {
            return false;
        }
        return !player.getUniqueID()
            .equals(FakePlayerRegistry.getOwnerUUID(botName));
    }

    private void purgeSavedPlayerFiles(MinecraftServer server, String botName, UUID profileId) {
        for (File saveRoot : getSaveRootsForCleanup(server)) {
            PlayerDataCleanup.purgeBotFiles(saveRoot, botName, profileId);
        }
    }

    private UUID resolveProfileId(String botName, EntityPlayerMP target) {
        if (target != null && target.getGameProfile() != null
            && target.getGameProfile()
                .getId() != null) {
            return target.getGameProfile()
                .getId();
        }
        UUID profileId = FakePlayerRegistry.getProfileId(botName);
        if (profileId != null) {
            return profileId;
        }
        String normalizedBotName = botName == null ? "" : botName.trim();
        return normalizedBotName.isEmpty() ? null
            : EntityPlayer.func_146094_a(new GameProfile(null, normalizedBotName));
    }

    protected java.io.File resolveSaveRoot(MinecraftServer server) {
        java.io.File overworldSaveRoot = getOverworldSaveRoot(server);
        if (overworldSaveRoot != null) {
            return overworldSaveRoot;
        }
        java.io.File currentSaveRoot = getCurrentSaveRootDirectory();
        return currentSaveRoot != null ? currentSaveRoot : getFallbackSaveRoot(server);
    }

    protected List<File> getSaveRootsForCleanup(MinecraftServer server) {
        Set<File> roots = new LinkedHashSet<File>();
        File resolvedRoot = resolveSaveRoot(server);
        if (resolvedRoot != null) {
            roots.add(resolvedRoot);
        }
        File fallbackRoot = getFallbackSaveRoot(server);
        if (fallbackRoot != null) {
            roots.add(fallbackRoot);
        }
        return new ArrayList<File>(roots);
    }

    protected java.io.File getCurrentSaveRootDirectory() {
        return DimensionManager.getCurrentSaveRootDirectory();
    }

    protected java.io.File getOverworldSaveRoot(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        WorldServer overworld = server.worldServerForDimension(0);
        return overworld == null ? null : overworld.getChunkSaveLocation();
    }

    protected java.io.File getFallbackSaveRoot(MinecraftServer server) {
        java.io.File playerdataDirectory = server == null ? null : server.getFile("playerdata");
        java.io.File parent = playerdataDirectory == null ? null : playerdataDirectory.getParentFile();
        return parent == null && server != null ? server.getFile(".") : parent;
    }

    protected MinecraftServer requireServer() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            throw new CommandException("Server is not available");
        }
        return server;
    }

    protected WorldServer resolveWorld(MinecraftServer server, int dimension) {
        WorldServer world = server.worldServerForDimension(dimension);
        if (world == null) {
            world = server.worldServerForDimension(0);
        }
        if (world == null) {
            throw new CommandException("Unable to resolve target world");
        }
        return world;
    }

    protected EntityPlayerMP resolvePlayer(ICommandSender sender, String name) {
        return getPlayer(sender, name);
    }

    private void ensureRemaining(String[] args, int index, int requiredValues, ICommandSender sender) {
        if (args.length - index - 1 < requiredValues) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private WorldSettings.GameType parseGameType(String value) {
        WorldSettings.GameType gameType = WorldSettings.GameType.getByName(value);
        if (gameType != null) {
            return gameType;
        }

        try {
            return WorldSettings.GameType.getByID(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            throw new CommandException("Invalid game type: " + value);
        }
    }

    private boolean canShadow(ICommandSender sender, EntityPlayerMP target) {
        if (!(sender instanceof EntityPlayerMP player)) {
            return true;
        }
        if (player.canCommandSenderUseCommand(Config.fakePlayerPermissionLevel, "gtstaff")) {
            return true;
        }
        return player.getUniqueID()
            .equals(target.getUniqueID());
    }

    protected void notifySender(ICommandSender sender, String message) {
        if (sender != null) {
            sender.addChatMessage(new ChatComponentText("[GTstaff] " + message));
        }
    }

    private void notifySenderLines(ICommandSender sender, String message) {
        if (message == null || message.trim()
            .isEmpty()) {
            return;
        }

        for (String line : message.split("\\r?\\n")) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isEmpty()) {
                notifySender(sender, trimmed);
            }
        }
    }

    private String buildMonitorStatus(BotRuntimeView runtime) {
        return "Monitor for " + runtime.name()
            + ": "
            + (runtime.monitor()
                .monitoring() ? "on" : "off")
            + ", range="
            + runtime.monitor()
                .monitorRange()
            + ", interval="
            + runtime.monitor()
                .reminderInterval()
            + " ticks";
    }

    private String buildRepelStatus(BotRuntimeView runtime) {
        return "Repel for " + runtime.name()
            + ": "
            + (runtime.repel()
                .repelling() ? "on" : "off")
            + ", range="
            + runtime.repel()
                .repelRange();
    }

    private void sendMachineOverview(ICommandSender sender, BotRuntimeView runtime) {
        String overviewMessage = runtime.monitor()
            .scanNow(runtime.name());
        if (overviewMessage == null || overviewMessage.trim()
            .isEmpty()) {
            notifySender(sender, "Monitor service is unavailable for " + runtime.name());
            return;
        }
        notifySenderLines(sender, overviewMessage);
    }

    private String buildInventorySummary(BotRuntimeView runtime) {
        BotInventorySummary inventorySummary = runtime.inventory()
            .summary();
        StringBuilder summary = new StringBuilder();
        summary.append("Inventory for ")
            .append(runtime.name())
            .append('\n');
        if (inventorySummary == null) {
            summary.append("Inventory data is unavailable.");
            return summary.toString();
        }

        summary.append("Selected hotbar slot: ")
            .append(MathHelper.clamp_int(inventorySummary.selectedHotbarSlot(), 0, 8) + 1)
            .append('\n');
        summary.append("Hotbar: ")
            .append(formatSummaryLines(inventorySummary.hotbarLines()))
            .append('\n');
        summary.append("Main: ")
            .append(formatSummaryLines(inventorySummary.mainInventoryLines()))
            .append('\n');
        summary.append("Armor: ")
            .append(formatSummaryLines(inventorySummary.armorLines()));
        return summary.toString();
    }

    private String formatSummaryLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "(empty)";
        }
        return String.join(", ", lines);
    }

    private String formatInventoryRange(net.minecraft.item.ItemStack[] stacks, int start, int end) {
        if (stacks == null || stacks.length == 0 || start >= stacks.length || end < start) {
            return "(empty)";
        }

        List<String> entries = new ArrayList<String>();
        int clampedEnd = Math.min(end, stacks.length - 1);
        for (int index = start; index <= clampedEnd; index++) {
            entries.add((index + 1) + ":" + formatStack(stacks[index]));
        }
        return String.join(", ", entries);
    }

    private String formatStack(net.minecraft.item.ItemStack stack) {
        if (stack == null) {
            return "-";
        }
        return stack.getDisplayName() + " x" + stack.stackSize;
    }

    private String formatPosition(EntityPlayerMP player) {
        if (player == null) {
            return "(unknown)";
        }
        return "(" + MathHelper.floor_double(player.posX)
            + ", "
            + MathHelper.floor_double(player.posY)
            + ", "
            + MathHelper.floor_double(player.posZ)
            + ") dim="
            + player.dimension;
    }

    private static final class SpawnOptions {

        private ChunkCoordinates position;
        private float yaw;
        private float pitch;
        private int dimension;
        private WorldSettings.GameType gameType;
        private boolean flying;

        private static SpawnOptions defaults(ICommandSender sender, MinecraftServer server) {
            SpawnOptions options = new SpawnOptions();
            options.position = sender.getPlayerCoordinates();
            options.dimension = sender instanceof EntityPlayerMP player ? player.dimension : 0;
            options.gameType = sender instanceof EntityPlayerMP player ? player.theItemInWorldManager.getGameType()
                : null;
            options.flying = sender instanceof EntityPlayerMP player && player.capabilities.isFlying;
            if (sender instanceof EntityPlayerMP player) {
                options.yaw = player.rotationYaw;
                options.pitch = player.rotationPitch;
            }

            if (options.position == null) {
                WorldServer world = server.worldServerForDimension(options.dimension);
                if (world == null) {
                    world = server.worldServerForDimension(0);
                    options.dimension = 0;
                }
                if (world != null) {
                    options.position = world.getSpawnPoint();
                    if (options.gameType == null) {
                        options.gameType = world.getWorldInfo()
                            .getGameType();
                    }
                }
            }

            return options;
        }
    }
}
