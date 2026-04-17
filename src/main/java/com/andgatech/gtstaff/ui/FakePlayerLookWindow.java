package com.andgatech.gtstaff.ui;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

public class FakePlayerLookWindow extends ModularPanel {

    private final FakePlayerManagerService service;
    private final EntityPlayerMP player;
    private final FakePlayerManagerService.LookDraft draft;
    private String statusMessage = "Choose a direction or look at a target position.";

    public FakePlayerLookWindow(ModularPanel parent, EntityPlayerMP player, PanelSyncManager syncManager) {
        this(parent, player, syncManager, new FakePlayerManagerService());
    }

    FakePlayerLookWindow(ModularPanel parent, EntityPlayerMP player, PanelSyncManager syncManager,
        FakePlayerManagerService service) {
        super("GTstaffLookWindow");
        this.player = player;
        this.service = service;
        this.draft = service.createLookDraft(player);

        syncManager.syncValue("gtstaffLookStatus", new StringSyncValue(() -> this.statusMessage, val -> this.statusMessage = val));

        relative(parent).leftRel(1).topRel(0).size(214, 148).widgetTheme("backgroundPopup")
            .child(ButtonWidget.panelCloseButton())
            .child(new TextWidget("Look Control").top(10).left(10))
            .child(new TextWidget("Bot").top(30).left(10))
            .child(createBotNameField().top(28).left(42))
            .child(createPresetButton("North", "north").top(54).left(10))
            .child(createPresetButton("South", "south").top(54).left(78))
            .child(createPresetButton("East", "east").top(54).left(146))
            .child(createPresetButton("West", "west").top(76).left(10))
            .child(createPresetButton("Up", "up").top(76).left(78))
            .child(createPresetButton("Down", "down").top(76).left(146))
            .child(new TextWidget("At").top(104).left(10))
            .child(createCoordinateField(() -> this.draft.x, val -> this.draft.x = val).top(102).left(28))
            .child(createCoordinateField(() -> this.draft.y, val -> this.draft.y = val).top(102).left(82))
            .child(createCoordinateField(() -> this.draft.z, val -> this.draft.z = val).top(102).left(136))
            .child(createLookAtButton().top(124).left(10))
            .child(new TextWidget(IKey.dynamic(() -> this.statusMessage)).top(124).left(78).size(124, 18));
    }

    private TextFieldWidget createBotNameField() {
        return new TextFieldWidget().value(new StringSyncValue(() -> this.draft.botName, val -> this.draft.botName = val))
            .setMaxLength(16)
            .size(150, 16);
    }

    private TextFieldWidget createCoordinateField(IntGetter getter, IntSetter setter) {
        return new TextFieldWidget().value(new IntSyncValue(getter::get, setter::set))
            .setFormatAsInteger(true)
            .setNumbers()
            .size(48, 16);
    }

    private ButtonWidget<?> createPresetButton(String label, String mode) {
        return new ButtonWidget<>().size(58, 18)
            .overlay(IKey.str(label))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || mouseData.isClient()) {
                    return;
                }

                FakePlayerManagerService.LookDraft snapshot = this.draft.copy();
                snapshot.mode = mode;
                submitLook(snapshot);
            }));
    }

    private ButtonWidget<?> createLookAtButton() {
        return new ButtonWidget<>().size(60, 18)
            .overlay(IKey.str("Look At"))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || mouseData.isClient()) {
                    return;
                }

                FakePlayerManagerService.LookDraft snapshot = this.draft.copy();
                snapshot.mode = "at";
                submitLook(snapshot);
            }));
    }

    private void submitLook(FakePlayerManagerService.LookDraft snapshot) {
        try {
            ServerThreadUtil.addScheduledTask(() -> applyLook(snapshot));
        } catch (IllegalStateException ignored) {
            applyLook(snapshot);
        }
    }

    private void applyLook(FakePlayerManagerService.LookDraft snapshot) {
        try {
            this.statusMessage = this.service.submitLook(this.player, snapshot);
        } catch (CommandException exception) {
            this.statusMessage = exception.getMessage();
        }
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
