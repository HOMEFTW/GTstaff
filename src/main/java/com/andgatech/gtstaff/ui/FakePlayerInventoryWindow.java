package com.andgatech.gtstaff.ui;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

public class FakePlayerInventoryWindow extends ModularPanel {

    private final FakePlayerManagerService service;
    private final FakePlayerManagerService.InventoryDraft draft;
    private String statusMessage = "Enter bot name, then Read.";
    private String inventoryText = "No inventory snapshot loaded.";

    public FakePlayerInventoryWindow(ModularPanel parent, EntityPlayerMP player, PanelSyncManager syncManager) {
        this(parent, player, syncManager, new FakePlayerManagerService());
    }

    FakePlayerInventoryWindow(ModularPanel parent, EntityPlayerMP player, PanelSyncManager syncManager,
        FakePlayerManagerService service) {
        super("GTstaffInventoryWindow");
        this.service = service;
        this.draft = service.createInventoryDraft(player);

        syncManager.syncValue(
            "gtstaffInventoryStatus",
            new StringSyncValue(() -> this.statusMessage, val -> this.statusMessage = val));
        syncManager.syncValue(
            "gtstaffInventorySnapshot",
            new StringSyncValue(() -> this.inventoryText, val -> this.inventoryText = val));

        PopupPanelLayout.centerInParent(this, parent, 220, 196)
            .widgetTheme("backgroundPopup")
            .child(ButtonWidget.panelCloseButton())
            .child(
                new TextWidget("Inventory Snapshot").top(10)
                    .left(10))
            .child(
                new TextWidget("Bot").top(30)
                    .left(10))
            .child(
                createBotNameField().top(28)
                    .left(40))
            .child(
                createRefreshButton().top(28)
                    .left(168))
            .child(
                new TextWidget(IKey.dynamic(() -> this.statusMessage)).top(52)
                    .left(10)
                    .size(198, 18))
            .child(
                new TextWidget(IKey.dynamic(() -> this.inventoryText)).top(74)
                    .left(10)
                    .size(198, 112));
    }

    private TextFieldWidget createBotNameField() {
        return new TextFieldWidget()
            .value(new StringSyncValue(() -> this.draft.botName, val -> this.draft.botName = val))
            .setMaxLength(16)
            .size(120, 16);
    }

    private ButtonWidget<?> createRefreshButton() {
        return new ButtonWidget<>().size(42, 18)
            .overlay(IKey.str("Read"))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || mouseData.isClient()) {
                    return;
                }

                FakePlayerManagerService.InventoryDraft snapshot = this.draft.copy();
                refreshInventory(snapshot);
            }));
    }

    private void refreshInventory(FakePlayerManagerService.InventoryDraft snapshot) {
        try {
            ServerThreadUtil.addScheduledTask(() -> applyInventorySnapshot(snapshot));
        } catch (IllegalStateException ignored) {
            applyInventorySnapshot(snapshot);
        }
    }

    private void applyInventorySnapshot(FakePlayerManagerService.InventoryDraft snapshot) {
        try {
            FakePlayerManagerService.InventorySnapshot inventorySnapshot = this.service.readInventory(snapshot);
            this.inventoryText = inventorySnapshot.toCompactDisplayText();
            this.statusMessage = "Loaded inventory for " + inventorySnapshot.botName + ".";
        } catch (CommandException exception) {
            this.inventoryText = "No inventory snapshot loaded.";
            this.statusMessage = exception.getMessage();
        }
    }

    @Override
    public boolean isDraggable() {
        return false;
    }
}
