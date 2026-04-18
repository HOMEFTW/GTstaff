package com.andgatech.gtstaff.ui;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

public class FakePlayerSpawnWindow extends ModularPanel {

    private final FakePlayerManagerService service;
    private final EntityPlayerMP player;
    private final FakePlayerManagerService.SpawnDraft draft;
    private String statusMessage = "Fill form, then Spawn.";

    public FakePlayerSpawnWindow(ModularPanel parent, EntityPlayerMP player, PanelSyncManager syncManager) {
        this(parent, player, syncManager, new FakePlayerManagerService());
    }

    FakePlayerSpawnWindow(ModularPanel parent, EntityPlayerMP player, PanelSyncManager syncManager,
        FakePlayerManagerService service) {
        super("GTstaffSpawnWindow");
        this.player = player;
        this.service = service;
        this.draft = service.createSpawnDraft(player);

        syncManager.syncValue(
            "gtstaffSpawnStatus",
            new StringSyncValue(() -> this.statusMessage, val -> this.statusMessage = val));

        PopupPanelLayout.centerInParent(this, parent, 190, 140)
            .background(new Rectangle().setColor(0xDD404040))
            .child(ButtonWidget.panelCloseButton())
            .child(
                new TextWidget("Spawn Bot").top(10)
                    .left(10))
            .child(
                new TextWidget("Name").top(30)
                    .left(10))
            .child(
                createNameField().top(28)
                    .left(58))
            .child(
                new TextWidget("X").top(54)
                    .left(10))
            .child(
                createCoordinateField(() -> this.draft.x, val -> this.draft.x = val).top(52)
                    .left(24))
            .child(
                new TextWidget("Y").top(54)
                    .left(72))
            .child(
                createCoordinateField(() -> this.draft.y, val -> this.draft.y = val).top(52)
                    .left(86))
            .child(
                new TextWidget("Z").top(54)
                    .left(134))
            .child(
                createCoordinateField(() -> this.draft.z, val -> this.draft.z = val).top(52)
                    .left(148))
            .child(
                new TextWidget("Dim").top(78)
                    .left(10))
            .child(
                createDimensionField().top(76)
                    .left(36))
            .child(
                new TextWidget("Mode").top(78)
                    .left(94))
            .child(
                createGameModeField().top(76)
                    .left(126))
            .child(
                createSpawnButton().top(104)
                    .left(10))
            .child(
                new TextWidget(IKey.dynamic(() -> this.statusMessage)).top(106)
                    .left(80)
                    .size(100, 28));
    }

    private TextFieldWidget createNameField() {
        return new TextFieldWidget()
            .value(new StringSyncValue(() -> this.draft.botName, val -> this.draft.botName = val))
            .setMaxLength(16)
            .size(120, 16);
    }

    private TextFieldWidget createCoordinateField(IntGetter getter, IntSetter setter) {
        return new TextFieldWidget().value(new IntSyncValue(getter::get, setter::set))
            .setFormatAsInteger(true)
            .setNumbers()
            .size(42, 16);
    }

    private TextFieldWidget createDimensionField() {
        return new TextFieldWidget()
            .value(new IntSyncValue(() -> this.draft.dimension, val -> this.draft.dimension = val))
            .setFormatAsInteger(true)
            .setNumbers()
            .size(42, 16);
    }

    private TextFieldWidget createGameModeField() {
        return new TextFieldWidget()
            .value(
                new StringSyncValue(
                    () -> this.draft.gameMode,
                    val -> this.draft.gameMode = FakePlayerManagerService.normalizeGameMode(val)))
            .setValidator(FakePlayerManagerService::normalizeGameMode)
            .setMaxLength(16)
            .size(54, 16);
    }

    private ButtonWidget<?> createSpawnButton() {
        return new ButtonWidget<>().size(60, 18)
            .overlay(IKey.str("Spawn"))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || mouseData.isClient()) {
                    return;
                }

                FakePlayerManagerService.SpawnDraft snapshot = this.draft.copy();
                try {
                    ServerThreadUtil.addScheduledTask(() -> {
                        try {
                            this.statusMessage = this.service.submitSpawn(this.player, snapshot);
                        } catch (CommandException exception) {
                            this.statusMessage = exception.getMessage();
                        }
                    });
                } catch (IllegalStateException ignored) {
                    try {
                        this.statusMessage = this.service.submitSpawn(this.player, snapshot);
                    } catch (CommandException exception) {
                        this.statusMessage = exception.getMessage();
                    }
                }
            }));
    }

    @Override
    public boolean isDraggable() {
        return false;
    }

    @FunctionalInterface
    private interface IntGetter {

        int get();
    }

    @FunctionalInterface
    private interface IntSetter {

        void set(int value);
    }
}
