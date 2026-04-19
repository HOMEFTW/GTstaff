package com.andgatech.gtstaff.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.command.CommandPlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.FollowService;
import com.andgatech.gtstaff.util.PermissionHelper;

public class FakePlayerManagerService {

    @FunctionalInterface
    public interface CommandRunner {

        void run(ICommandSender sender, String[] args);
    }

    public static final class SpawnDraft {

        public String botName = "";
        public int x;
        public int y;
        public int z;
        public int dimension;
        public String gameMode = "survival";

        public SpawnDraft copy() {
            SpawnDraft copy = new SpawnDraft();
            copy.botName = this.botName;
            copy.x = this.x;
            copy.y = this.y;
            copy.z = this.z;
            copy.dimension = this.dimension;
            copy.gameMode = this.gameMode;
            return copy;
        }
    }

    public static final class LookDraft {

        public String botName = "";
        public String mode = "north";
        public int x;
        public int y;
        public int z;

        public LookDraft copy() {
            LookDraft copy = new LookDraft();
            copy.botName = this.botName;
            copy.mode = this.mode;
            copy.x = this.x;
            copy.y = this.y;
            copy.z = this.z;
            return copy;
        }
    }

    public static final class InventoryDraft {

        public String botName = "";

        public InventoryDraft copy() {
            InventoryDraft copy = new InventoryDraft();
            copy.botName = this.botName;
            return copy;
        }
    }

    public static final class InventorySnapshot {

        public final String botName;
        public final int selectedHotbarSlot;
        public final List<String> hotbarLines;
        public final List<String> mainInventoryLines;
        public final List<String> armorLines;

        private InventorySnapshot(String botName, int selectedHotbarSlot, List<String> hotbarLines,
            List<String> mainInventoryLines, List<String> armorLines) {
            this.botName = botName;
            this.selectedHotbarSlot = selectedHotbarSlot;
            this.hotbarLines = hotbarLines;
            this.mainInventoryLines = mainInventoryLines;
            this.armorLines = armorLines;
        }

        public String toDisplayText() {
            StringBuilder builder = new StringBuilder();
            builder.append("Bot: ")
                .append(this.botName)
                .append('\n');
            builder.append("Selected Hotbar Slot: ")
                .append(this.selectedHotbarSlot + 1)
                .append('\n');
            builder.append("Hotbar:\n")
                .append(joinLines(this.hotbarLines))
                .append('\n');
            builder.append("Main Inventory:\n")
                .append(joinLines(this.mainInventoryLines))
                .append('\n');
            builder.append("Armor:\n")
                .append(joinLines(this.armorLines));
            return builder.toString();
        }

        public String toCompactDisplayText() {
            StringBuilder builder = new StringBuilder();
            builder.append("Bot: ")
                .append(this.botName)
                .append('\n');
            builder.append("Selected: ")
                .append(this.selectedHotbarSlot + 1)
                .append('\n');
            builder.append("Hotbar\n")
                .append(compactGrid(this.hotbarLines, 3))
                .append('\n');
            builder.append("Main\n")
                .append(compactGrid(this.mainInventoryLines, 3))
                .append('\n');
            builder.append("Armor\n")
                .append(compactGrid(this.armorLines, 2));
            return builder.toString();
        }

        private static String joinLines(List<String> lines) {
            return lines == null || lines.isEmpty() ? "(empty)" : String.join("\n", lines);
        }

        private static String compactGrid(List<String> lines, int columns) {
            if (lines == null || lines.isEmpty()) {
                return "(empty)";
            }

            List<String> rows = new ArrayList<String>();
            StringBuilder row = new StringBuilder();
            for (int index = 0; index < lines.size(); index++) {
                if (row.length() > 0) {
                    row.append(" | ");
                }
                row.append(compactEntry(lines.get(index)));
                if ((index + 1) % columns == 0 || index + 1 == lines.size()) {
                    rows.add(row.toString());
                    row.setLength(0);
                }
            }
            return String.join("\n", rows);
        }

        private static String compactEntry(String line) {
            if (line == null || line.isEmpty()) {
                return "-";
            }
            return line.replace("[ ] ", "")
                .replace("[*] ", "*")
                .replace(": (empty)", ":-");
        }
    }

    public static final class BotDetails {

        public final String botName;
        public final String ownerLabel;
        public final int blockX;
        public final int blockY;
        public final int blockZ;
        public final int dimension;
        public final int selectedHotbarSlot;
        public final boolean monitoring;
        public final int monitorRange;
        public final int reminderInterval;
        public final boolean online;
        public final boolean monsterRepelling;
        public final int monsterRepelRange;
        public final boolean following;
        public final int followRange;
        public final int teleportRange;

        private BotDetails(String botName, String ownerLabel, int blockX, int blockY, int blockZ, int dimension,
            int selectedHotbarSlot, boolean monitoring, int monitorRange, int reminderInterval, boolean online,
            boolean monsterRepelling, int monsterRepelRange, boolean following, int followRange, int teleportRange) {
            this.botName = botName;
            this.ownerLabel = ownerLabel;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.dimension = dimension;
            this.selectedHotbarSlot = selectedHotbarSlot;
            this.monitoring = monitoring;
            this.monitorRange = monitorRange;
            this.reminderInterval = reminderInterval;
            this.online = online;
            this.monsterRepelling = monsterRepelling;
            this.monsterRepelRange = monsterRepelRange;
            this.following = following;
            this.followRange = followRange;
            this.teleportRange = teleportRange;
        }
    }

    private final CommandRunner commandRunner;

    public FakePlayerManagerService() {
        this(new CommandPlayer()::processCommand);
    }

    public FakePlayerManagerService(CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    public SpawnDraft createSpawnDraft(EntityPlayer player) {
        SpawnDraft draft = new SpawnDraft();
        if (player == null) {
            return draft;
        }

        draft.x = MathHelper.floor_double(player.posX);
        draft.y = MathHelper.floor_double(player.posY);
        draft.z = MathHelper.floor_double(player.posZ);
        draft.dimension = player.dimension;
        if (player instanceof EntityPlayerMP playerMP && playerMP.theItemInWorldManager != null
            && playerMP.theItemInWorldManager.getGameType() != null) {
            draft.gameMode = normalizeGameMode(String.valueOf(playerMP.theItemInWorldManager.getGameType()));
        }
        return draft;
    }

    public LookDraft createLookDraft(EntityPlayer player) {
        LookDraft draft = new LookDraft();
        draft.botName = suggestBotName();
        if (player == null) {
            return draft;
        }

        draft.x = MathHelper.floor_double(player.posX);
        draft.y = MathHelper.floor_double(player.posY + player.getEyeHeight());
        draft.z = MathHelper.floor_double(player.posZ);
        return draft;
    }

    public String submitSpawn(ICommandSender sender, SpawnDraft draft) {
        if (draft == null) {
            throw new CommandException("Spawn draft is missing");
        }

        String botName = requireBotName(draft.botName);

        String[] args = buildSpawnArgs(botName, draft);
        this.commandRunner.run(sender, args);
        return "Spawned fake player " + botName + ".";
    }

    public String submitSpawn(ICommandSender sender, String botName, String xText, String yText, String zText,
        String dimensionText, String gameModeText) {
        SpawnDraft draft = new SpawnDraft();
        draft.botName = botName == null ? "" : botName.trim();
        draft.x = parseRequiredInt(xText, "X coordinate");
        draft.y = parseRequiredInt(yText, "Y coordinate");
        draft.z = parseRequiredInt(zText, "Z coordinate");
        draft.dimension = parseRequiredInt(dimensionText, "Dimension");
        draft.gameMode = normalizeGameMode(gameModeText);
        return submitSpawn(sender, draft);
    }

    public String submitLook(ICommandSender sender, LookDraft draft) {
        if (draft == null) {
            throw new CommandException("Look draft is missing");
        }

        String botName = requireBotName(draft.botName);
        String[] args = buildLookArgs(botName, draft);
        this.commandRunner.run(sender, args);
        return "Updated look direction for " + botName + ".";
    }

    public String executeAction(ICommandSender sender, String botName, String action) {
        String normalizedBotName = requireBotName(botName);
        String normalizedAction = action == null ? ""
            : action.trim()
                .toLowerCase(Locale.ROOT);
        if (normalizedAction.isEmpty()) {
            throw new CommandException("Action cannot be empty");
        }
        String[] actionParts = normalizedAction.split("\\s+");
        String[] args = new String[1 + actionParts.length];
        args[0] = normalizedBotName;
        System.arraycopy(actionParts, 0, args, 1, actionParts.length);
        this.commandRunner.run(sender, args);
        return "Executed " + normalizedAction + " on " + normalizedBotName + ".";
    }

    public String killBot(ICommandSender sender, String botName) {
        String normalizedBotName = requireBotName(botName);
        this.commandRunner.run(sender, new String[] { normalizedBotName, "kill" });
        return "Killed " + normalizedBotName + ".";
    }

    public String purgeBot(ICommandSender sender, String botName) {
        String normalizedBotName = requireBotName(botName);
        this.commandRunner.run(sender, new String[] { normalizedBotName, "purge" });
        return "Purged " + normalizedBotName + ".";
    }

    public String shadowBot(ICommandSender sender, String botName) {
        String normalizedBotName = requireBotName(botName);
        this.commandRunner.run(sender, new String[] { normalizedBotName, "shadow" });
        return "Created shadow of " + normalizedBotName + ".";
    }

    public String toggleMonitor(ICommandSender sender, String botName, boolean enable) {
        String normalizedBotName = requireBotName(botName);
        String[] args = new String[] { normalizedBotName, "monitor", enable ? "on" : "off" };
        this.commandRunner.run(sender, args);
        return (enable ? "Monitor enabled" : "Monitor disabled") + " for " + normalizedBotName + ".";
    }

    public String setMonitorRange(ICommandSender sender, String botName, int range) {
        String normalizedBotName = requireBotName(botName);
        this.commandRunner.run(sender, new String[] { normalizedBotName, "monitor", "range", Integer.toString(range) });
        return "Monitor range set to " + range + " for " + normalizedBotName + ".";
    }

    public String setReminderInterval(ICommandSender sender, String botName, int intervalTicks) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.setReminderInterval(intervalTicks);
        int seconds = intervalTicks / 20;
        return "提醒频率已设置为 " + seconds + " 秒 for " + normalizedBotName + ".";
    }

    public String toggleMonsterRepel(ICommandSender sender, String botName, boolean enable) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.setMonsterRepelling(enable);
        return (enable ? "已开启敌对生物驱逐" : "已关闭敌对生物驱逐") + " for "
            + normalizedBotName
            + " (范围: "
            + fakePlayer.getMonsterRepelRange()
            + "格)";
    }

    public String setMonsterRepelRange(ICommandSender sender, String botName, int range) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.setMonsterRepelRange(range);
        return "敌对生物驱逐范围已设置为 " + range + " 格 for " + normalizedBotName + ".";
    }

    public String startFollow(ICommandSender sender, String botName) {
        if (!(sender instanceof EntityPlayerMP player)) {
            throw new CommandException("Only players can be followed");
        }
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.getFollowService()
            .startFollowing(player.getUniqueID());
        return FakePlayer.colorizeName(normalizedBotName) + " 开始跟随你";
    }

    public String stopFollow(ICommandSender sender, String botName) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.getFollowService()
            .stop();
        fakePlayer.moveForward = 0.0F;
        fakePlayer.moveStrafing = 0.0F;
        fakePlayer.setJumping(false);
        return FakePlayer.colorizeName(normalizedBotName) + " 停止跟随";
    }

    public String setFollowRange(ICommandSender sender, String botName, int range) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.getFollowService()
            .setFollowRange(range);
        return normalizedBotName + " 跟随距离设置为 " + range + " 格";
    }

    public String setTeleportRange(ICommandSender sender, String botName, int range) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = findBot(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        fakePlayer.getFollowService()
            .setTeleportRange(range);
        return normalizedBotName + " 传送距离设置为 " + range + " 格";
    }

    public String scanMachines(String botName) {
        FakePlayer fakePlayer = findBot(botName);
        if (fakePlayer == null) {
            return "假人 " + (botName == null ? "" : botName.trim()) + " 不在线。";
        }
        BotDetails details = describeBot(botName);
        StringBuilder builder = new StringBuilder();
        builder.append("监控: ")
            .append(details.monitoring ? "开" : "关");
        builder.append("  范围: ")
            .append(details.monitorRange)
            .append('\n');
        builder.append(
            fakePlayer.getMachineMonitorService()
                .buildOverviewMessage(botName));
        return builder.toString()
            .trim();
    }

    public String getInventorySummaryText(String botName) {
        FakePlayer fakePlayer = findBot(botName);
        if (fakePlayer == null) {
            return "假人 " + (botName == null ? "" : botName.trim()) + " 不在线。";
        }
        InventoryDraft draft = new InventoryDraft();
        draft.botName = botName;
        return readInventory(draft).toCompactDisplayText();
    }

    public List<String> listBotNames() {
        return FakePlayerRegistry.getAll()
            .values()
            .stream()
            .map(FakePlayer::getCommandSenderName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    public String defaultSelectedBotName() {
        return listBotNames().stream()
            .findFirst()
            .orElse("");
    }

    public BotDetails describeBot(String botName) {
        FakePlayer fakePlayer = findBot(botName);
        if (fakePlayer == null) {
            return new BotDetails(
                botName == null ? "" : botName.trim(),
                "(offline)",
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                600,
                false,
                false,
                64,
                false,
                FollowService.DEFAULT_FOLLOW_RANGE,
                FollowService.DEFAULT_TELEPORT_RANGE);
        }
        boolean following = fakePlayer.isFollowing();
        int followRange = fakePlayer.getFollowService() != null ? fakePlayer.getFollowService()
            .getFollowRange() : FollowService.DEFAULT_FOLLOW_RANGE;
        int teleportRange = fakePlayer.getFollowService() != null ? fakePlayer.getFollowService()
            .getTeleportRange() : FollowService.DEFAULT_TELEPORT_RANGE;
        return new BotDetails(
            fakePlayer.getCommandSenderName(),
            formatOwnerLabel(fakePlayer.getOwnerUUID()),
            MathHelper.floor_double(fakePlayer.posX),
            MathHelper.floor_double(fakePlayer.posY),
            MathHelper.floor_double(fakePlayer.posZ),
            fakePlayer.dimension,
            fakePlayer.inventory == null ? 0 : MathHelper.clamp_int(fakePlayer.inventory.currentItem, 0, 8),
            fakePlayer.isMonitoring(),
            fakePlayer.getMonitorRange(),
            fakePlayer.getReminderInterval(),
            true,
            fakePlayer.isMonsterRepelling(),
            fakePlayer.getMonsterRepelRange(),
            following,
            followRange,
            teleportRange);
    }

    public String openInventoryManager(EntityPlayerMP player, String botName) {
        if (player == null) {
            throw new CommandException("Inventory manager can only be opened by a player");
        }

        FakePlayer fakePlayer = findBotOrThrow(botName);
        if (PermissionHelper.cantManipulate(player, fakePlayer)) {
            throw new CommandException("You do not have permission to manage " + fakePlayer.getCommandSenderName());
        }

        player.openGui(
            GTstaff.instance,
            FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY,
            player.worldObj,
            fakePlayer.getEntityId(),
            0,
            0);
        return "Opening inventory manager for " + fakePlayer.getCommandSenderName() + ".";
    }

    public InventoryDraft createInventoryDraft(EntityPlayer player) {
        InventoryDraft draft = new InventoryDraft();
        draft.botName = suggestBotName();
        return draft;
    }

    public InventorySnapshot readInventory(InventoryDraft draft) {
        if (draft == null) {
            throw new CommandException("Inventory draft is missing");
        }

        String botName = requireBotName(draft.botName);
        FakePlayer fakePlayer = FakePlayerRegistry.getFakePlayer(botName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(botName));
        }

        InventoryPlayer inventory = fakePlayer.inventory;
        List<String> hotbarLines = new ArrayList<String>();
        List<String> mainInventoryLines = new ArrayList<String>();
        List<String> armorLines = new ArrayList<String>();
        int selectedHotbarSlot = inventory == null ? 0 : MathHelper.clamp_int(inventory.currentItem, 0, 8);

        if (inventory != null) {
            for (int index = 0; index < 9; index++) {
                hotbarLines
                    .add(formatInventorySlot(index, inventory.mainInventory[index], index == selectedHotbarSlot));
            }

            for (int index = 9; index < inventory.mainInventory.length; index++) {
                mainInventoryLines.add(formatInventorySlot(index, inventory.mainInventory[index], false));
            }

            armorLines.add(
                formatArmorSlot("Helmet", inventory.armorInventory.length > 3 ? inventory.armorInventory[3] : null));
            armorLines.add(
                formatArmorSlot(
                    "Chestplate",
                    inventory.armorInventory.length > 2 ? inventory.armorInventory[2] : null));
            armorLines.add(
                formatArmorSlot("Leggings", inventory.armorInventory.length > 1 ? inventory.armorInventory[1] : null));
            armorLines.add(
                formatArmorSlot("Boots", inventory.armorInventory.length > 0 ? inventory.armorInventory[0] : null));
        }

        return new InventorySnapshot(botName, selectedHotbarSlot, hotbarLines, mainInventoryLines, armorLines);
    }

    public static String normalizeGameMode(String input) {
        String normalized = input == null ? ""
            : input.trim()
                .toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "0":
            case "survival":
                return "survival";
            case "1":
            case "creative":
                return "creative";
            case "2":
            case "adventure":
                return "adventure";
            default:
                return normalized.isEmpty() ? "survival" : normalized;
        }
    }

    public static String normalizeLookMode(String input) {
        String normalized = input == null ? ""
            : input.trim()
                .toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "north":
            case "south":
            case "east":
            case "west":
            case "up":
            case "down":
            case "at":
                return normalized;
            default:
                return normalized.isEmpty() ? "north" : normalized;
        }
    }

    private String[] buildSpawnArgs(String botName, SpawnDraft draft) {
        List<String> args = new ArrayList<String>();
        args.add(botName);
        args.add("spawn");
        args.add("at");
        args.add(Integer.toString(draft.x));
        args.add(Integer.toString(draft.y));
        args.add(Integer.toString(draft.z));
        args.add("in");
        args.add(Integer.toString(draft.dimension));
        args.add("as");
        args.add(normalizeGameMode(draft.gameMode));
        return args.toArray(new String[0]);
    }

    private String[] buildLookArgs(String botName, LookDraft draft) {
        List<String> args = new ArrayList<String>();
        String mode = normalizeLookMode(draft.mode);
        args.add(botName);
        args.add("look");
        args.add(mode);
        if ("at".equals(mode)) {
            args.add(Integer.toString(draft.x));
            args.add(Integer.toString(draft.y));
            args.add(Integer.toString(draft.z));
        }
        return args.toArray(new String[0]);
    }

    private String formatInventorySlot(int slotIndex, ItemStack stack, boolean selected) {
        String marker = selected ? "[*]" : "[ ]";
        return marker + " " + (slotIndex + 1) + ": " + formatStack(stack);
    }

    private String formatArmorSlot(String label, ItemStack stack) {
        return label + ": " + formatStack(stack);
    }

    private String formatStack(ItemStack stack) {
        if (stack == null) {
            return "(empty)";
        }
        return stack.getDisplayName() + " x" + stack.stackSize;
    }

    private String requireBotName(String botName) {
        String normalizedBotName = botName == null ? "" : botName.trim();
        if (normalizedBotName.isEmpty()) {
            throw new CommandException("Bot name cannot be empty");
        }
        return normalizedBotName;
    }

    private int parseRequiredInt(String text, String label) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            throw new CommandException(label + " cannot be empty");
        }

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + label + ": " + normalized);
        }
    }

    private String suggestBotName() {
        if (FakePlayerRegistry.getCount() == 1) {
            return FakePlayerRegistry.getAll()
                .values()
                .stream()
                .map(FakePlayer::getCommandSenderName)
                .findFirst()
                .orElse("");
        }
        return defaultSelectedBotName();
    }

    private String buildOfflineBotMessage(String botName) {
        List<String> onlineBots = listBotNames();
        if (onlineBots.isEmpty()) {
            return "Fake player " + botName + " is not online";
        }
        return "Fake player " + botName + " is not online. Online bots: " + String.join(", ", onlineBots);
    }

    private FakePlayer findBot(String botName) {
        String normalizedBotName = botName == null ? "" : botName.trim();
        if (normalizedBotName.isEmpty()) {
            return null;
        }
        return FakePlayerRegistry.getFakePlayer(normalizedBotName);
    }

    private FakePlayer findBotOrThrow(String botName) {
        String normalizedBotName = requireBotName(botName);
        FakePlayer fakePlayer = FakePlayerRegistry.getFakePlayer(normalizedBotName);
        if (fakePlayer == null) {
            throw new CommandException(buildOfflineBotMessage(normalizedBotName));
        }
        return fakePlayer;
    }

    private String formatOwnerLabel(UUID ownerUUID) {
        return ownerUUID == null ? "(server)" : ownerUUID.toString();
    }
}
