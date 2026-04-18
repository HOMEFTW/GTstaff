package com.andgatech.gtstaff.ui;

import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;

import com.andgatech.gtstaff.GTstaff;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
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
            IPanelHandler lookPanel = syncManager.panel(
                "gtstaffLookPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerLookWindow(panel, player, panelSyncManager),
                true);

            PagedWidget.Controller pageController = new PagedWidget.Controller();

            panel.child(ButtonWidget.panelCloseButton().top(4).right(4))
                .child(new TextWidget("GTstaff 假人管理器").top(6).left(8))
                .child(createTabButton("总览", "overview", state, pageController, 0).top(20).left(4))
                .child(createTabButton("背包", "inventory", state, pageController, 1).top(20).left(74))
                .child(createTabButton("操作", "actions", state, pageController, 2).top(20).left(144))
                .child(createTabButton("监控", "monitor", state, pageController, 3).top(20).left(214))
                .child(buildBotList(service, state))
                .child(buildPagedContent(pageController, service, state, player, lookPanel))
                .child(createSpawnButton(spawnPanel).top(180).left(4))
                .child(new TextWidget(IKey.dynamic(() -> state.statusMessage)).top(182).left(120).size(290, 16));

            return panel;
        }

        @SuppressWarnings("rawtypes")
        private ListWidget buildBotList(FakePlayerManagerService service, ManagerState state) {
            List<String> botNames = service.listBotNames();
            ListWidget list = new ListWidget();
            list.top(38).left(4).size(110, 140);
            if (botNames.isEmpty()) {
                list.child(new TextWidget("暂无假人在线").left(2).top(2));
            } else {
                for (String botName : botNames) {
                    list.child(createBotButton(botName, state).size(106, 16));
                }
            }
            return list;
        }

        private ButtonWidget<?> createBotButton(String botName, ManagerState state) {
            return new ButtonWidget<>()
                .overlay(IKey.dynamic(() -> botName.equalsIgnoreCase(state.selectedBotName) ? "> " + botName : botName))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0) return;
                    state.selectedBotName = botName;
                }));
        }

        private PagedWidget<?> buildPagedContent(PagedWidget.Controller controller,
            FakePlayerManagerService service, ManagerState state, EntityPlayerMP player,
            IPanelHandler lookPanel) {

            PagedWidget<?> paged = new PagedWidget<>();
            paged.top(38).left(120).size(296, 140);
            paged.controller(controller);

            paged.addPage(buildOverviewPage(service, state));
            paged.addPage(buildInventoryPage(service, state, player));
            paged.addPage(buildActionsPage(service, state, lookPanel));
            paged.addPage(buildMonitorPage(service, state));

            return paged;
        }

        // ---- Overview ----
        private Column buildOverviewPage(FakePlayerManagerService service, ManagerState state) {
            Column col = new Column();
            col.child(new TextWidget(IKey.dynamic(() -> buildOverviewText(service, state))).size(290, 100).left(2).top(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("杀死"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.killBot(null, state.selectedBotName);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .top(106).left(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("影分身"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.shadowBot(null, state.selectedBotName);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .top(106).left(66));
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
            return "假人: " + d.botName
                + "\n所有者: " + d.ownerLabel
                + "\n坐标: " + d.blockX + ", " + d.blockY + ", " + d.blockZ
                + "\n维度: " + d.dimension
                + "\n快捷栏: " + (d.selectedHotbarSlot + 1)
                + "\n监控: " + (d.monitoring ? "开" : "关") + "  范围: " + d.monitorRange;
        }

        // ---- Inventory ----
        private Column buildInventoryPage(FakePlayerManagerService service, ManagerState state,
            EntityPlayerMP player) {

            Column col = new Column();
            col.child(
                new ButtonWidget<>().size(100, 18).overlay(IKey.str("管理背包"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        try {
                            state.statusMessage = service.openInventoryManager(player, state.selectedBotName);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(2).left(2));
            return col;
        }

        // ---- Actions ----
        private Column buildActionsPage(FakePlayerManagerService service, ManagerState state,
            IPanelHandler lookPanel) {

            Column col = new Column();

            String[] quickActions = { "attack", "use", "jump", "drop", "stop" };
            String[] quickLabels = { "攻击", "使用", "跳跃", "丢弃", "停止" };
            for (int i = 0; i < quickActions.length; i++) {
                String action = quickActions[i];
                col.child(
                    new ButtonWidget<>().size(50, 18).overlay(IKey.str(quickLabels[i]))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(null, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(2).left(2 + i * 54));
            }

            col.child(new TextWidget("快捷栏:").top(24).left(2));
            for (int slot = 1; slot <= 9; slot++) {
                int slotNum = slot;
                col.child(
                    new ButtonWidget<>().size(16, 16).overlay(IKey.str(Integer.toString(slot)))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(null, state.selectedBotName, "hotbar " + slotNum);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(36).left(2 + (slot - 1) * 20));
            }

            String[] moveActions = { "sneak", "sprint" };
            String[] moveLabels = { "潜行", "疾跑" };
            for (int i = 0; i < moveActions.length; i++) {
                String action = moveActions[i];
                col.child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str(moveLabels[i]))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                            if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                            try {
                                state.statusMessage = service.executeAction(null, state.selectedBotName, action);
                            } catch (CommandException e) {
                                state.statusMessage = e.getMessage();
                            }
                        }))
                        .setEnabledIf(w -> hasSelectedBot(service, state))
                        .top(58).left(2 + i * 64));
            }

            col.child(
                new ButtonWidget<>().size(60, 18).overlay(IKey.str("视角..."))
                    .onMousePressed(mouseButton -> {
                        if (mouseButton != 0) return false;
                        if (lookPanel.isPanelOpen()) {
                            lookPanel.closePanel();
                        } else {
                            lookPanel.openPanel();
                        }
                        return true;
                    })
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(80).left(2));

            col.child(
                new TextWidget(IKey.dynamic(() -> state.statusMessage)).top(102).left(2).size(290, 14));

            return col;
        }

        // ---- Monitor ----
        private Column buildMonitorPage(FakePlayerManagerService service, ManagerState state) {
            Column col = new Column();

            col.child(
                new ButtonWidget<>().size(80, 18).overlay(IKey.str("切换监控"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || mouseData.isClient()) return;
                        if (!hasSelectedBot(service, state)) {
                            state.statusMessage = "请先选择一个假人。";
                            return;
                        }
                        FakePlayerManagerService.BotDetails d = service.describeBot(state.selectedBotName);
                        try {
                            state.statusMessage = service.toggleMonitor(null, state.selectedBotName, !d.monitoring);
                        } catch (CommandException e) {
                            state.statusMessage = e.getMessage();
                        }
                    }))
                    .setEnabledIf(w -> hasSelectedBot(service, state))
                    .top(2).left(2))
                .child(
                    new ButtonWidget<>().size(60, 18).overlay(IKey.str("扫描"))
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
                        .top(2).left(86))
                .child(
                    new TextWidget(IKey.dynamic(() -> {
                        if (state.machineStatusText != null && !state.machineStatusText.isEmpty()) {
                            return state.machineStatusText;
                        }
                        if (!hasSelectedBot(service, state)) return "请先选择一个假人。";
                        return service.scanMachines(state.selectedBotName);
                    })).size(290, 110).left(2).top(24));

            return col;
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

        private static String normalizeBotSelection(FakePlayerManagerService service, String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) {
                return service.defaultSelectedBotName();
            }
            return normalized;
        }

        private static String normalizeTab(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase();
            switch (normalized) {
                case "inventory":
                case "actions":
                case "monitor":
                    return normalized;
                case "overview":
                default:
                    return "overview";
            }
        }

        private static boolean hasSelectedBot(FakePlayerManagerService service, ManagerState state) {
            return state.selectedBotName != null && !state.selectedBotName.trim().isEmpty()
                && service.listBotNames().stream().anyMatch(name -> name.equalsIgnoreCase(state.selectedBotName));
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
