package com.andgatech.gtstaff.ui;

import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

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
            ModularPanel panel = ModularPanel.defaultPanel("GTstaffManager", 220, 170);
            EntityPlayerMP player = data.getPlayer() instanceof EntityPlayerMP playerMP ? playerMP : null;
            IPanelHandler spawnPanel = syncManager.panel(
                "gtstaffSpawnPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerSpawnWindow(panel, player, panelSyncManager),
                true
            );
            IPanelHandler inventoryPanel = syncManager.panel(
                "gtstaffInventoryPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerInventoryWindow(panel, player, panelSyncManager),
                true
            );
            IPanelHandler lookPanel = syncManager.panel(
                "gtstaffLookPanel",
                (panelSyncManager, panelHandler) -> new FakePlayerLookWindow(panel, player, panelSyncManager),
                true
            );

            return panel.child(ButtonWidget.panelCloseButton())
                .child(new TextWidget("GTstaff Fake Player Manager").top(10).left(10))
                .child(
                    new TextWidget(IKey.dynamic(FakePlayerManagerUI::buildBotSummary))
                        .top(28)
                        .left(10)
                        .size(196, 36))
                .child(
                    createOpenButton("Spawn", 10, 78, spawnPanel)
                )
                .child(
                    createOpenButton("Inventory", 80, 78, inventoryPanel)
                )
                .child(
                    createOpenButton("Look", 150, 78, lookPanel)
                )
                .child(new TextWidget(IKey.dynamic(FakePlayerManagerUI::buildBotList)).top(108).left(10).size(196, 48));
        }

        private ButtonWidget<?> createOpenButton(String label, int x, int y, IPanelHandler panelHandler) {
            return new ButtonWidget<>().size(60, 18)
                .left(x)
                .top(y)
                .overlay(IKey.str(label))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) {
                        return false;
                    }
                    if (panelHandler.isPanelOpen()) {
                        panelHandler.closePanel();
                    } else {
                        panelHandler.openPanel();
                    }
                    return true;
                });
        }
    }

    private static String buildBotSummary() {
        return "Online fake players: " + FakePlayerRegistry.getCount() + "\nStored owners: "
            + FakePlayerRegistry.getAll().values().stream().map(FakePlayer::getCommandSenderName).count();
    }

    private static String buildBotList() {
        if (FakePlayerRegistry.getCount() == 0) {
            return "No fake players are currently registered.";
        }
        return FakePlayerRegistry.getAll()
            .values()
            .stream()
            .map(FakePlayer::getCommandSenderName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining("\n"));
    }
}
