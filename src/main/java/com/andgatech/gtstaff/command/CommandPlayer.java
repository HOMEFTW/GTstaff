package com.andgatech.gtstaff.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.Action;
import com.andgatech.gtstaff.fakeplayer.ActionType;
import com.andgatech.gtstaff.fakeplayer.FakeNetHandlerPlayServer;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;
import com.andgatech.gtstaff.util.PermissionHelper;

public class CommandPlayer extends CommandBase {

    @Override
    public String getCommandName() {
        return "player";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/player <name> spawn|kill|shadow|attack [once|continuous|interval <ticks>]|stopattack|use [once|continuous|interval <ticks>]|stopuse|jump|drop|dropStack|move|look|turn|sneak|unsneak|sprint|unsprint|mount|dismount|hotbar|stop|monitor ...";
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
            case "shadow":
                handleShadow(sender, botName);
                return;
            case "monitor":
                handleMonitor(sender, botName, trailingArgs);
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

        String botNames = FakePlayerRegistry.getAll()
            .values()
            .stream()
            .map(FakePlayer::getCommandSenderName)
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

        FakePlayer fakePlayer = FakePlayer.createFake(
            botName,
            server,
            options.position,
            options.yaw,
            options.pitch,
            options.dimension,
            options.gameType,
            options.flying);

        if (sender instanceof EntityPlayerMP player) {
            FakePlayerRegistry.register(fakePlayer, player.getUniqueID());
        }

        notifySender(
            sender,
            "Spawned fake player " + fakePlayer.getCommandSenderName() + " at " + formatPosition(fakePlayer));
    }

    protected void handleKill(ICommandSender sender, String botName) {
        FakePlayer target = requireFakePlayer(botName);
        if (PermissionHelper.cantRemove(sender, target)) {
            throw new CommandException("You do not have permission to remove that bot");
        }

        if (target.playerNetServerHandler instanceof FakeNetHandlerPlayServer) {
            target.playerNetServerHandler.kickPlayerFromServer("You logged in from another location");
        } else {
            FakePlayerRegistry.unregister(target.getCommandSenderName());
        }
        notifySender(sender, "Killed fake player " + target.getCommandSenderName());
    }

    protected void handleShadow(ICommandSender sender, String botName) {
        MinecraftServer server = requireServer();
        EntityPlayerMP realPlayer = getPlayer(sender, botName);
        if (realPlayer instanceof FakePlayer) {
            throw new CommandException("Target is already a fake player");
        }
        if (FakePlayerRegistry.getFakePlayer(botName) != null) {
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

        FakePlayer shadow = FakePlayer.createShadow(server, realPlayer);
        if (shadow == null) {
            throw new CommandException("Unable to create shadow player");
        }

        notifySender(sender, "Created shadow fake player " + shadow.getCommandSenderName());
    }

    protected void handleMonitor(ICommandSender sender, String botName, String[] args) {
        FakePlayer target = requireFakePlayer(botName);
        if (PermissionHelper.cantManipulate(sender, target)) {
            throw new CommandException("You do not have permission to control that bot");
        }

        if (args.length == 0) {
            notifySender(
                sender,
                "Monitor for " + target.getCommandSenderName()
                    + ": "
                    + (target.isMonitoring() ? "on" : "off")
                    + ", range="
                    + target.getMonitorRange());
            return;
        }

        Boolean monitoring = null;
        Integer range = null;
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
                default:
                    throw new WrongUsageException("/player <name> monitor [on|off] [range <radius>]");
            }
        }

        if (monitoring != null) {
            target.setMonitoring(monitoring);
        }
        if (range != null) {
            target.setMonitorRange(range);
        }

        notifySender(
            sender,
            "Monitor for " + target.getCommandSenderName()
                + ": "
                + (target.isMonitoring() ? "on" : "off")
                + ", range="
                + target.getMonitorRange());
    }

    protected void handleManipulation(ICommandSender sender, String botName, String action, String[] args) {
        FakePlayer target = requireFakePlayer(botName);
        if (PermissionHelper.cantManipulate(sender, target)) {
            throw new CommandException("You do not have permission to control that bot");
        }

        PlayerActionPack actionPack = requireActionPack(target);
        switch (action.toLowerCase(Locale.ROOT)) {
            case "attack":
                actionPack.start(ActionType.ATTACK, parseAction(sender, args));
                notifySender(sender, "Set attack action for " + target.getCommandSenderName());
                return;
            case "use":
                actionPack.start(ActionType.USE, parseAction(sender, args));
                notifySender(sender, "Set use action for " + target.getCommandSenderName());
                return;
            case "stopattack":
                actionPack.stop(ActionType.ATTACK);
                notifySender(sender, "Stopped attack for " + target.getCommandSenderName());
                return;
            case "stopuse":
                actionPack.stop(ActionType.USE);
                notifySender(sender, "Stopped use for " + target.getCommandSenderName());
                return;
            case "jump":
                actionPack.start(ActionType.JUMP, parseAction(sender, args));
                notifySender(sender, "Set jump action for " + target.getCommandSenderName());
                return;
            case "drop":
                actionPack.start(ActionType.DROP_ITEM, parseAction(sender, args));
                notifySender(sender, "Set drop action for " + target.getCommandSenderName());
                return;
            case "dropstack":
                actionPack.start(ActionType.DROP_STACK, parseAction(sender, args));
                notifySender(sender, "Set dropStack action for " + target.getCommandSenderName());
                return;
            case "move":
                handleMove(sender, target, actionPack, args);
                return;
            case "look":
                handleLook(sender, target, actionPack, args);
                return;
            case "turn":
                handleTurn(sender, target, actionPack, args);
                return;
            case "sneak":
                actionPack.setSneaking(true);
                notifySender(sender, target.getCommandSenderName() + " is now sneaking");
                return;
            case "unsneak":
                actionPack.setSneaking(false);
                notifySender(sender, target.getCommandSenderName() + " stopped sneaking");
                return;
            case "sprint":
                actionPack.setSprinting(true);
                notifySender(sender, target.getCommandSenderName() + " is now sprinting");
                return;
            case "unsprint":
                actionPack.setSprinting(false);
                notifySender(sender, target.getCommandSenderName() + " stopped sprinting");
                return;
            case "mount":
                handleMount(sender, target, args);
                return;
            case "dismount":
                target.mountEntity((Entity) null);
                notifySender(sender, target.getCommandSenderName() + " dismounted");
                return;
            case "hotbar":
                if (args.length != 1) {
                    throw new WrongUsageException("/player <name> hotbar <1-9>");
                }
                actionPack.setSlot(parseIntBounded(sender, args[0], 1, 9));
                notifySender(sender, target.getCommandSenderName() + " switched hotbar slot");
                return;
            case "stop":
                actionPack.stopAll();
                notifySender(sender, target.getCommandSenderName() + " stopped all actions");
                return;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void handleMove(ICommandSender sender, FakePlayer target, PlayerActionPack actionPack, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException("/player <name> move [forward|backward|left|right|stop]");
        }

        actionPack.setForward(0.0F);
        actionPack.setStrafing(0.0F);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "forward":
                actionPack.setForward(1.0F);
                break;
            case "backward":
                actionPack.setForward(-1.0F);
                break;
            case "left":
                actionPack.setStrafing(1.0F);
                break;
            case "right":
                actionPack.setStrafing(-1.0F);
                break;
            case "stop":
                actionPack.stopMovement();
                break;
            default:
                throw new WrongUsageException("/player <name> move [forward|backward|left|right|stop]");
        }

        notifySender(sender, "Updated movement for " + target.getCommandSenderName());
    }

    private void handleLook(ICommandSender sender, FakePlayer target, PlayerActionPack actionPack, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException("/player <name> look <north|south|east|west|up|down|at <x> <y> <z>>");
        }

        String token = args[0].toLowerCase(Locale.ROOT);
        switch (token) {
            case "north":
                actionPack.look(180.0F, 0.0F);
                break;
            case "south":
                actionPack.look(0.0F, 0.0F);
                break;
            case "east":
                actionPack.look(-90.0F, 0.0F);
                break;
            case "west":
                actionPack.look(90.0F, 0.0F);
                break;
            case "up":
                actionPack.look(target.rotationYaw, -90.0F);
                break;
            case "down":
                actionPack.look(target.rotationYaw, 90.0F);
                break;
            case "at":
                if (args.length != 4) {
                    throw new WrongUsageException("/player <name> look at <x> <y> <z>");
                }
                lookAt(sender, target, actionPack, args[1], args[2], args[3]);
                break;
            default:
                throw new WrongUsageException("/player <name> look <north|south|east|west|up|down|at <x> <y> <z>>");
        }

        notifySender(sender, "Updated look direction for " + target.getCommandSenderName());
    }

    private void handleTurn(ICommandSender sender, FakePlayer target, PlayerActionPack actionPack, String[] args) {
        if (args.length == 0 || args.length > 2) {
            throw new WrongUsageException("/player <name> turn <left|right|back|<yaw> [pitch]>");
        }

        String token = args[0].toLowerCase(Locale.ROOT);
        switch (token) {
            case "left":
                actionPack.turn(-90.0F, 0.0F);
                break;
            case "right":
                actionPack.turn(90.0F, 0.0F);
                break;
            case "back":
                actionPack.turn(180.0F, 0.0F);
                break;
            default:
                float yaw = (float) parseDouble(sender, args[0]);
                float pitch = args.length == 2 ? (float) parseDouble(sender, args[1]) : 0.0F;
                actionPack.turn(yaw, pitch);
                break;
        }

        notifySender(sender, "Turned " + target.getCommandSenderName());
    }

    private void handleMount(ICommandSender sender, FakePlayer target, String[] args) {
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

    private void lookAt(ICommandSender sender, FakePlayer target, PlayerActionPack actionPack, String xArg, String yArg,
        String zArg) {
        double x = parseDouble(sender, xArg);
        double y = parseDouble(sender, yArg);
        double z = parseDouble(sender, zArg);
        double dx = x - target.posX;
        double dy = y - (target.posY + target.getEyeHeight());
        double dz = z - target.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0D / Math.PI));
        actionPack.look(yaw, pitch);
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

    private FakePlayer requireFakePlayer(String botName) {
        FakePlayer target = FakePlayerRegistry.getFakePlayer(botName);
        if (target == null) {
            throw new CommandException("Fake player not found: " + botName);
        }
        return target;
    }

    private PlayerActionPack requireActionPack(FakePlayer target) {
        if (!(target instanceof IFakePlayerHolder holder) || holder.getActionPack() == null) {
            throw new CommandException("Action pack is unavailable for " + target.getCommandSenderName());
        }
        return holder.getActionPack();
    }

    private MinecraftServer requireServer() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            throw new CommandException("Server is not available");
        }
        return server;
    }

    private WorldServer resolveWorld(MinecraftServer server, int dimension) {
        WorldServer world = server.worldServerForDimension(dimension);
        if (world == null) {
            world = server.worldServerForDimension(0);
        }
        if (world == null) {
            throw new CommandException("Unable to resolve target world");
        }
        return world;
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

    private void notifySender(ICommandSender sender, String message) {
        if (sender != null) {
            sender.addChatMessage(new ChatComponentText("[GTstaff] " + message));
        }
    }

    private String formatPosition(FakePlayer fakePlayer) {
        return "(" + MathHelper.floor_double(fakePlayer.posX)
            + ", "
            + MathHelper.floor_double(fakePlayer.posY)
            + ", "
            + MathHelper.floor_double(fakePlayer.posZ)
            + ") dim="
            + fakePlayer.dimension;
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
