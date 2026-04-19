package com.andgatech.gtstaff.ui;

import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;

public class FakePlayerManagerUI extends AbstractUIFactory<GuiData> {

    public static final FakePlayerManagerUI INSTANCE = new FakePlayerManagerUI();

    private FakePlayerManagerUI() {
        super("gtstaff:fake_player_manager");
    }

    public void open(EntityPlayerMP player) {
        GuiManager.open(this, new GuiData(player), player);
    }

    @Override
    public @NotNull IGuiHolder<GuiData> getGuiHolder(GuiData data) {
        return Holder.INSTANCE;
    }

    @Override
    public void writeGuiData(GuiData guiData, PacketBuffer buffer) {}

    @Override
    public @NotNull GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new GuiData(player);
    }

    private static final class Holder implements IGuiHolder<GuiData> {

        private static final Holder INSTANCE = new Holder();

        @Override
        public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
            return new ModularScreen(GTstaff.MODID, mainPanel);
        }

        @Override
        public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
            FakePlayerManagerService service = new FakePlayerManagerService();
            ManagerState state = new ManagerState(service.defaultSelectedBotName());
            EntityPlayerMP player = data.getPlayer() instanceof EntityPlayerMP playerMP ? playerMP : null;
            ModularPanel panel = ModularPanel.defaultPanel("GTstaffManager", 420, 200);

            syncManager.syncValue(
                "gtstaffManagerSelectedBot",
                new StringSyncValue(
                    () -> state.selectedBotName,
                    value -> state.selectedBotName = normalizeBotSelection(service, value)));
            syncManager.syncValue(
                "gtstaffManagerActiveTab",
                new StringSyncValue(() -> state.activeTab, value -> state.activeTab = normalizeTab(value)));
            syncManager.syncValue(
                "gtstaffManagerStatus",
                new StringSyncValue(() -> state.statusMessage, value -> state.statusMessage = value));

            IPanelHandler spawnPanel = syncManager.panel(
                "gtstaffSpawnPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerSpawnWindow(panel, player, panelSyncManager),
                true);

            PagedWidget.Controller pageController = new PagedWidget.Controller();

            panel.child(
                ButtonWidget.panelCloseButton()
                    .top(4)
                    .right(4))
                .child(
                    new TextWidget("GTstaff 假人管理器").top(6)
                        .left(8))
                .child(
                    createTabButton("总览", "overview", state, pageController, 0).top(20)
                        .left(4))
                .child(
                    createTabButton("背包", "inventory", state, pageController, 1).top(20)
                        .left(74))
                .child(
                    createTabButton("操作", "actions", state, pageController, 2).top(20)
                        .left(144))
                .child(
                    createTabButton("监控", "monitor", state, pageController, 3).top(20)
                        .left(214))
                .child(
                    createTabButton("其他", "other", state, pageController, 4).top(20)
                        .left(284))
                .child(buildBotList(service, state))
                .child(buildPagedContent(pageController, service, state, player))
                .child(
                    createSpawnButton(spawnPanel).top(180)
                        .left(4));

            return panel;
        }

        @SuppressWarnings("rawtypes")
        private ListWidget buildBotList(FakePlayerManagerService service, ManagerState state) {
            List<String> botNames = service.listBotNames();
            ListWidget<IWidget, ?> list = new ListWidget<>();
            list.scrollDirection(new VerticalScrollData(true))
                .top(38)
                .left(4)
                .size(110, 140);
            if (botNames.isEmpty()) {
                list.child(
                    new TextWidget("暂无假人在线").left(2)
                        .top(2));
            } else {
                for (String botName : botNames) {
                    list.child(createBotButton(botName, state).size(106, 16));
                }
            }
            return list;
        }

        private ButtonWidget<?> createBotButton(String botName, ManagerState state) {
            return new ButtonWidget<>().overlay(IKey.dynamic(() -> {
                String colored = FakePlayer.colorizeName(botName);
                return botName.equalsIgnoreCase(state.selectedBotName) ? "> " + colored : colored;
            }))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0) return;
                    state.selectedBotName = botName;
                }));
        }

        private PagedWidget<?> buildPagedContent(PagedWidget.Controller controller, FakePlayerManagerService service,
            ManagerState state, EntityPlayerMP player) {

            PagedWidget<?> paged = new PagedWidget<>();
            paged.top(38)
                .left(120)
                .size(296, 140);
            paged.controller(controller);

            paged.addPage(buildOverviewPage(service, state, player));
            paged.addPage(buildInventoryPage(service, state, player));
            paged.addPage(buildActionsPage(service, state, player));
            paged.addPage(buildMonitorPage(service, state, player));
            paged.addPage(buildOtherPage(service, state, player));

            return paged;
        }

        // ---- Overview ----
        private Column buildOverviewPage(FakePlayerManagerService service, ManagerState state, EntityPlayerMP player) {
            Column col = new Column();
            col.child(
                new TextWidget(IKey.dynamic(() -> buildOverviewText(service, state))).size(290, 100)
                    .left(2)
                    .top(2))
                .child(
                    new ButtonWidget<>().size(60, 18)
                        .overlay(IKey.str("杀死"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.killBot(player, state.selectedBotName);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .top(106)
                        .left(2))
                .child(
                    new ButtonWidget<>().size(78, 18)
                        .overlay(IKey.str("\u5b8c\u5168\u6e05\u9664"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.purgeBot(player, state.selectedBotName);
                                state.selectedBotName = service.defaultSelectedBotName();
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .top(106)
                        .left(66));
            return col;
        }

        private static String buildOverviewText(FakePlayerManagerService service, ManagerState state) {
            if (!hasSelectedBot(service, state)) {
                return "请从左侧列表选择一个假人。";
            }
            FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
            if (!d.online) {
                return "选中的假人已离线。";
            }
            return "假人: " + FakePlayer.colorizeName(d.botName)
                + "\n所有者: "
                + d.ownerLabel
                + "\n坐标: "
                + d.blockX
                + ", "
                + d.blockY
                + ", "
                + d.blockZ
                + "\n维度: "
                + d.dimension
                + "\n快捷栏: "
                + (d.selectedHotbarSlot + 1)
                + "\n监控: "
                + (d.monitoring ? "开" : "关")
                + "  范围: "
                + d.monitorRange
                + "\n提醒频率: "
                + (d.reminderInterval / 20)
                + "秒";
        }

        // ---- Inventory ----
        private Column buildInventoryPage(FakePlayerManagerService service, ManagerState state, EntityPlayerMP player) {

            Column col = new Column();
            col.child(
                new ButtonWidget<>().size(100, 18)
                    .overlay(IKey.str("管理背包"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        try {
                            state.statusMessage = service.openInventoryManager(player, state.selectedBotName);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(2)
                    .left(2));
            return col;
        }

        // ---- Actions ----
        private Column buildActionsPage(FakePlayerManagerService service, ManagerState state, EntityPlayerMP player) {

            Column col = new Column();

            String[] freqLabels = { "x1", "连", "/5t", "/10t", "/20t" };
            String[] attackFreqActions = { "attack once", "attack continuous", "attack interval 5",
                "attack interval 10", "attack interval 20" };
            String[] useFreqActions = { "use once", "use continuous", "use interval 5", "use interval 10",
                "use interval 20" };

            // Row 1: Attack frequency + stop
            col.child(
                new TextWidget("攻:").top(2)
                    .left(2)
                    .size(18, 14));
            for (int i = 0; i < freqLabels.length; i++) {
                String action = attackFreqActions[i];
                col.child(
                    new ButtonWidget<>().size(30, 14)
                        .overlay(IKey.str(freqLabels[i]))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(player, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(2)
                        .left(22 + i * 32));
            }
            col.child(
                new ButtonWidget<>().size(30, 14)
                    .overlay(IKey.str("停攻"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        try {
                            state.statusMessage = service.executeAction(player, state.selectedBotName, "stopattack");
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(2)
                    .left(22 + 5 * 32));

            // Row 2: Use frequency + stop
            col.child(
                new TextWidget("用:").top(18)
                    .left(2)
                    .size(18, 14));
            for (int i = 0; i < freqLabels.length; i++) {
                String action = useFreqActions[i];
                col.child(
                    new ButtonWidget<>().size(30, 14)
                        .overlay(IKey.str(freqLabels[i]))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(player, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(18)
                        .left(22 + i * 32));
            }
            col.child(
                new ButtonWidget<>().size(30, 14)
                    .overlay(IKey.str("停用"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        try {
                            state.statusMessage = service.executeAction(player, state.selectedBotName, "stopuse");
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(18)
                    .left(22 + 5 * 32));

            // Row 3: Other actions
            String[] otherActions = { "jump", "drop", "stop" };
            String[] otherLabels = { "跳跃", "丢弃", "全部停" };
            for (int i = 0; i < otherActions.length; i++) {
                String action = otherActions[i];
                col.child(
                    new ButtonWidget<>().size(42, 14)
                        .overlay(IKey.str(otherLabels[i]))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(player, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(34)
                        .left(2 + i * 46));
            }

            // Row 4: Hotbar
            for (int slot = 1; slot <= 9; slot++) {
                int slotNum = slot;
                col.child(
                    new ButtonWidget<>().size(16, 14)
                        .overlay(IKey.str(Integer.toString(slot)))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service
                                    .executeAction(player, state.selectedBotName, "hotbar " + slotNum);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(50)
                        .left(2 + (slot - 1) * 20));
            }

            // Row 5-6: WASD keyboard movement
            // [W]
            // [A] [S] [D] [停]
            col.child(createActionBtn(service, state, player, "前", "move forward", 28, 14, 66, 32));
            col.child(createActionBtn(service, state, player, "左", "move left", 28, 14, 82, 2));
            col.child(createActionBtn(service, state, player, "后", "move backward", 28, 14, 82, 32));
            col.child(createActionBtn(service, state, player, "右", "move right", 28, 14, 82, 62));
            col.child(createActionBtn(service, state, player, "停", "move stop", 28, 14, 74, 96));

            // Row 7-9: Compass rose + up/down
            // [北] [上]
            // [西] [东]
            // [南] [下]
            col.child(createActionBtn(service, state, player, "北", "look north", 28, 12, 98, 32));
            col.child(createActionBtn(service, state, player, "西", "look west", 28, 12, 112, 2));
            col.child(createActionBtn(service, state, player, "东", "look east", 28, 12, 112, 62));
            col.child(createActionBtn(service, state, player, "南", "look south", 28, 12, 126, 32));
            col.child(createActionBtn(service, state, player, "上", "look up", 28, 12, 100, 96));
            col.child(createActionBtn(service, state, player, "下", "look down", 28, 12, 114, 96));

            // Stance
            col.child(createActionBtn(service, state, player, "潜行", "sneak", 36, 12, 126, 96));
            col.child(createActionBtn(service, state, player, "疾跑", "sprint", 36, 12, 126, 134));

            return col;
        }

        // ---- Other Features ----
        private Column buildOtherPage(FakePlayerManagerService service, ManagerState state, EntityPlayerMP player) {
            Column col = new Column();

            // ===== Row 1: Monster Repel (full width) =====
            col.child(
                new TextWidget("敌对生物驱逐器").top(2)
                    .left(2)
                    .size(100, 12));

            col.child(
                new ButtonWidget<>().size(70, 14)
                    .overlay(IKey.dynamic(() -> {
                        if (!hasSelectedBot(service, state)) return "驱逐: 关";
                        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                        return d.monsterRepelling ? "驱逐: 开" : "驱逐: 关";
                    }))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        if (!hasSelectedBot(service, state)) {
                            state.statusMessage = "请先选择一个假人。";
                            return;
                        }
                        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                        try {
                            state.statusMessage = service
                                .toggleMonsterRepel(player, state.selectedBotName, !d.monsterRepelling);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(16)
                    .left(2));

            col.child(new TextWidget(IKey.dynamic(() -> {
                if (!hasSelectedBot(service, state)) return "范围: -";
                FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                return "范围: " + d.monsterRepelRange + "格";
            })).top(16)
                .left(76)
                .size(70, 12));

            String[] rangeLabels = { "32", "64", "128", "256", "400" };
            int[] rangeValues = { 32, 64, 128, 256, 400 };
            for (int i = 0; i < rangeLabels.length; i++) {
                final int range = rangeValues[i];
                final String label = rangeLabels[i];
                col.child(
                    new ButtonWidget<>().size(36, 14)
                        .overlay(IKey.dynamic(() -> {
                            if (!hasSelectedBot(service, state)) return label;
                            FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                            return d.monsterRepelRange == range ? "[" + label + "]" : label;
                        }))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            if (!hasSelectedBot(service, state)) {
                                state.statusMessage = "请先选择一个假人。";
                                return;
                            }
                            try {
                                state.statusMessage = service
                                    .setMonsterRepelRange(player, state.selectedBotName, range);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(32)
                        .left(2 + i * 38));
            }

            // ===== Separator =====
            col.child(
                new TextWidget("──────────────────────────────").top(48)
                    .left(2)
                    .size(290, 10));

            // ===== Row 2: Follow (full width) =====
            col.child(
                new TextWidget("假人跟随").top(56)
                    .left(2)
                    .size(60, 12));

            col.child(
                new ButtonWidget<>().size(52, 14)
                    .overlay(IKey.dynamic(() -> {
                        if (!hasSelectedBot(service, state)) return "跟随我";
                        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                        return d.following ? "跟随中" : "跟随我";
                    }))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        if (!hasSelectedBot(service, state)) {
                            state.statusMessage = "请先选择一个假人。";
                            return;
                        }
                        try {
                            state.statusMessage = service.startFollow(player, state.selectedBotName);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(70)
                    .left(2));

            col.child(
                new ButtonWidget<>().size(52, 14)
                    .overlay(IKey.str("停止跟随"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        if (!hasSelectedBot(service, state)) {
                            state.statusMessage = "请先选择一个假人。";
                            return;
                        }
                        try {
                            state.statusMessage = service.stopFollow(player, state.selectedBotName);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(70)
                    .left(58));

            // Follow range
            col.child(
                new TextWidget("跟随:").top(70)
                    .left(116)
                    .size(26, 12));

            String[] followRangeLabels = { "1", "3", "5", "8", "10" };
            int[] followRangeValues = { 1, 3, 5, 8, 10 };
            for (int i = 0; i < followRangeLabels.length; i++) {
                final int range = followRangeValues[i];
                final String label = followRangeLabels[i];
                col.child(
                    new ButtonWidget<>().size(20, 14)
                        .overlay(IKey.dynamic(() -> {
                            if (!hasSelectedBot(service, state)) return label;
                            FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                            return d.followRange == range ? "[" + label + "]" : label;
                        }))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            if (!hasSelectedBot(service, state)) {
                                state.statusMessage = "请先选择一个假人。";
                                return;
                            }
                            try {
                                state.statusMessage = service.setFollowRange(player, state.selectedBotName, range);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(70)
                        .left(144 + i * 22));
            }

            // Teleport range
            col.child(
                new TextWidget("传送:").top(86)
                    .left(2)
                    .size(26, 12));

            String[] tpRangeLabels = { "16", "32", "64", "96", "128" };
            int[] tpRangeValues = { 16, 32, 64, 96, 128 };
            for (int i = 0; i < tpRangeLabels.length; i++) {
                final int range = tpRangeValues[i];
                final String label = tpRangeLabels[i];
                col.child(
                    new ButtonWidget<>().size(24, 14)
                        .overlay(IKey.dynamic(() -> {
                            if (!hasSelectedBot(service, state)) return label;
                            FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                            return d.teleportRange == range ? "[" + label + "]" : label;
                        }))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            if (!hasSelectedBot(service, state)) {
                                state.statusMessage = "请先选择一个假人。";
                                return;
                            }
                            try {
                                state.statusMessage = service.setTeleportRange(player, state.selectedBotName, range);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(86)
                        .left(30 + i * 26));
            }

            // Status text
            col.child(new TextWidget(IKey.dynamic(() -> {
                if (!hasSelectedBot(service, state)) return "选择假人以查看状态。";
                FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                StringBuilder sb = new StringBuilder();
                if (d.monsterRepelling) {
                    sb.append("驱逐中(")
                        .append(d.monsterRepelRange)
                        .append("格) ");
                }
                if (d.following) {
                    sb.append("跟随中 距离:")
                        .append(d.followRange)
                        .append(" 传送:")
                        .append(d.teleportRange);
                } else {
                    sb.append("未跟随");
                }
                return sb.toString();
            })).top(104)
                .left(2)
                .size(290, 30));

            return col;
        }

        // ---- Monitor ----
        private Column buildMonitorPage(FakePlayerManagerService service, ManagerState state, EntityPlayerMP player) {
            Column col = new Column();

            col.child(
                new ButtonWidget<>().size(80, 18)
                    .overlay(IKey.str("切换监控"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        if (!hasSelectedBot(service, state)) {
                            state.statusMessage = "请先选择一个假人。";
                            return;
                        }
                        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                        try {
                            state.statusMessage = service.toggleMonitor(player, state.selectedBotName, !d.monitoring);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(2)
                    .left(2))
                .child(
                    new ButtonWidget<>().size(60, 18)
                        .overlay(IKey.str("扫描"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            if (!hasSelectedBot(service, state)) {
                                state.statusMessage = "请先选择一个假人。";
                                return;
                            }
                            state.machineStatusText = service.scanMachines(state.selectedBotName);
                            state.statusMessage = "扫描完成。";
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(2)
                        .left(86))
                // Reminder interval buttons
                .child(
                    new TextWidget("提醒频率:").top(22)
                        .left(2)
                        .size(50, 14))
                .child(createReminderBtn(service, state, player, "10秒", 200, 22, 54))
                .child(createReminderBtn(service, state, player, "30秒", 600, 22, 106))
                .child(createReminderBtn(service, state, player, "1分", 1200, 22, 158))
                .child(createReminderBtn(service, state, player, "5分", 6000, 22, 210))
                .child(
                    new ListWidget<>().size(290, 100)
                        .left(2)
                        .top(40)
                        .scrollDirection(new VerticalScrollData(true))
                        .child(new TextWidget(IKey.dynamic(() -> {
                            if (state.machineStatusText != null && !state.machineStatusText.isEmpty()) {
                                return state.machineStatusText;
                            }
                            if (!hasSelectedBot(service, state)) return "请先选择一个假人。";
                            return service.scanMachines(state.selectedBotName);
                        })).widthRel(1f)
                            .expanded()));

            return col;
        }

        private ButtonWidget<?> createReminderBtn(FakePlayerManagerService service, ManagerState state,
            EntityPlayerMP player, String label, int ticks, int top, int left) {
            return new ButtonWidget<>().size(48, 14)
                .overlay(IKey.dynamic(() -> {
                    if (!hasSelectedBot(service, state)) return label;
                    FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                    return d.reminderInterval == ticks ? "[" + label + "]" : label;
                }))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                    if (!hasSelectedBot(service, state)) {
                        state.statusMessage = "请先选择一个假人。";
                        return;
                    }
                    try {
                        state.statusMessage = service.setReminderInterval(player, state.selectedBotName, ticks);
                    } catch (CommandException e) {
                        state.statusMessage = e.getMessage();
                    }
                }))
                .setEnabledIf(w -> hasSelectedBot(service, state))
                .top(top)
                .left(left);
        }

        // ---- Tab ----
        private ButtonWidget<?> createTabButton(String label, String tabId, ManagerState state,
            PagedWidget.Controller controller, int pageIndex) {
            return new ButtonWidget<>().size(66, 14)
                .overlay(IKey.dynamic(() -> tabId.equals(state.activeTab) ? "[" + label + "]" : label))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0) return;
                    state.activeTab = tabId;
                    if (controller.isInitialised()) {
                        controller.setPage(pageIndex);
                    }
                }));
        }

        // ---- Spawn ----
        private ButtonWidget<?> createSpawnButton(IPanelHandler spawnPanel) {
            return new ButtonWidget<>().size(80, 18)
                .overlay(IKey.str("生成假人"))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    if (spawnPanel.isPanelOpen()) {
                        spawnPanel.closePanel();
                    } else {
                        spawnPanel.openPanel();
                    }
                    return true;
                });
        }

        // ---- Helpers ----
        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        private ButtonWidget<?> createActionBtn(FakePlayerManagerService service, ManagerState state,
            EntityPlayerMP player, String label, String action, int w, int h, int top, int left) {
            return new ButtonWidget<>().size(w, h)
                .overlay(IKey.str(label))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                    try {
                        state.statusMessage = service.executeAction(player, state.selectedBotName, action);
                    } catch (CommandException e) {
                        state.statusMessage = e.getMessage();
                    }
                }))
                .setEnabledIf(btn -> hasSelectedBot(service, state))
                .top(top)
                .left(left);
        }

        private static String normalizeBotSelection(FakePlayerManagerService service, String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) {
                return service.defaultSelectedBotName();
            }
            return normalized;
        }

        private static String normalizeTab(String value) {
            String normalized = value == null ? ""
                : value.trim()
                    .toLowerCase();
            switch (normalized) {
                case "inventory":
                case "actions":
                case "monitor":
                case "other":
                    return normalized;
                case "overview":
                default:
                    return "overview";
            }
        }

        private static boolean hasSelectedBot(FakePlayerManagerService service, ManagerState state) {
            return state.selectedBotName != null && !state.selectedBotName.trim()
                .isEmpty()
                && service.listBotNames()
                    .stream()
                    .anyMatch(name -> name.equalsIgnoreCase(state.selectedBotName));
        }
    }

    private static final class ManagerState {

        private String selectedBotName;
        private String activeTab = "overview";
        private String statusMessage = "请选择一个假人或点击生成。";
        private String machineStatusText = "";

        private ManagerState(String selectedBotName) {
            this.selectedBotName = selectedBotName == null ? "" : selectedBotName;
        }
    }
}
