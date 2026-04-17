package com.andgatech.gtstaff.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.andgatech.gtstaff.command.CommandPlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

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
            builder.append("Bot: ").append(this.botName).append('\n');
            builder.append("Selected Hotbar Slot: ").append(this.selectedHotbarSlot + 1).append('\n');
            builder.append("Hotbar:\n").append(joinLines(this.hotbarLines)).append('\n');
            builder.append("Main Inventory:\n").append(joinLines(this.mainInventoryLines)).append('\n');
            builder.append("Armor:\n").append(joinLines(this.armorLines));
            return builder.toString();
        }

        public String toCompactDisplayText() {
            StringBuilder builder = new StringBuilder();
            builder.append("Bot: ").append(this.botName).append('\n');
            builder.append("Selected: ").append(this.selectedHotbarSlot + 1).append('\n');
            builder.append("Hotbar\n").append(compactGrid(this.hotbarLines, 3)).append('\n');
            builder.append("Main\n").append(compactGrid(this.mainInventoryLines, 3)).append('\n');
            builder.append("Armor\n").append(compactGrid(this.armorLines, 2));
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
            return line.replace("[ ] ", "").replace("[*] ", "*").replace(": (empty)", ":-");
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

    public String submitLook(ICommandSender sender, LookDraft draft) {
        if (draft == null) {
            throw new CommandException("Look draft is missing");
        }

        String botName = requireBotName(draft.botName);
        String[] args = buildLookArgs(botName, draft);
        this.commandRunner.run(sender, args);
        return "Updated look direction for " + botName + ".";
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
                hotbarLines.add(formatInventorySlot(index, inventory.mainInventory[index], index == selectedHotbarSlot));
            }

            for (int index = 9; index < inventory.mainInventory.length; index++) {
                mainInventoryLines.add(formatInventorySlot(index, inventory.mainInventory[index], false));
            }

            armorLines.add(formatArmorSlot("Helmet", inventory.armorInventory.length > 3 ? inventory.armorInventory[3] : null));
            armorLines.add(formatArmorSlot("Chestplate", inventory.armorInventory.length > 2 ? inventory.armorInventory[2] : null));
            armorLines.add(formatArmorSlot("Leggings", inventory.armorInventory.length > 1 ? inventory.armorInventory[1] : null));
            armorLines.add(formatArmorSlot("Boots", inventory.armorInventory.length > 0 ? inventory.armorInventory[0] : null));
        }

        return new InventorySnapshot(botName, selectedHotbarSlot, hotbarLines, mainInventoryLines, armorLines);
    }

    public static String normalizeGameMode(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
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
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
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

    private String suggestBotName() {
        if (FakePlayerRegistry.getCount() != 1) {
            return "";
        }
        return FakePlayerRegistry.getAll()
            .values()
            .stream()
            .map(FakePlayer::getCommandSenderName)
            .findFirst()
            .orElse("");
    }

    private String buildOfflineBotMessage(String botName) {
        List<String> onlineBots = FakePlayerRegistry.getAll()
            .values()
            .stream()
            .map(FakePlayer::getCommandSenderName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        if (onlineBots.isEmpty()) {
            return "Fake player " + botName + " is not online";
        }
        return "Fake player " + botName + " is not online. Online bots: " + String.join(", ", onlineBots);
    }
}
