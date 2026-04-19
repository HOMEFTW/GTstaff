package com.andgatech.gtstaff.ui;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
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
    private String statusMessage = "Fill out the form, then click Spawn.";
    private String pendingSpawnRequest = "";

    private TextFieldWidget nameField;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private TextFieldWidget dimensionField;
    private TextFieldWidget gameModeField;
    private StringSyncValue spawnRequestSyncValue;

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
        this.spawnRequestSyncValue = new StringSyncValue(
            () -> this.pendingSpawnRequest,
            val -> this.pendingSpawnRequest = val,
            () -> this.pendingSpawnRequest,
            this::handleSpawnRequest);
        syncManager.syncValue("gtstaffSpawnRequest", this.spawnRequestSyncValue);

        PopupPanelLayout.centerInParent(this, parent, 190, 140)
            .background(new Rectangle().setColor(0xDD404040))
            .child(ButtonWidget.panelCloseButton())
            .child(
                new TextWidget("Generate Fake Player").top(10)
                    .left(10))
            .child(
                new TextWidget("Name").top(30)
                    .left(10))
            .child(
                createNameField().top(28)
                    .left(50))
            .child(
                new TextWidget("X").top(54)
                    .left(10))
            .child(
                createXField().top(52)
                    .left(24))
            .child(
                new TextWidget("Y").top(54)
                    .left(72))
            .child(
                createYField().top(52)
                    .left(86))
            .child(
                new TextWidget("Z").top(54)
                    .left(134))
            .child(
                createZField().top(52)
                    .left(148))
            .child(
                new TextWidget("Dim").top(78)
                    .left(10))
            .child(
                createDimensionField().top(76)
                    .left(40))
            .child(
                new TextWidget("Mode").top(78)
                    .left(100))
            .child(
                createGameModeField().top(76)
                    .left(132))
            .child(
                createSpawnButton().top(104)
                    .left(10))
            .child(
                new TextWidget(IKey.dynamic(() -> this.statusMessage)).top(106)
                    .left(80)
                    .size(100, 28));
    }

    private TextFieldWidget createNameField() {
        this.nameField = new TextFieldWidget()
            .value(new StringSyncValue(() -> this.draft.botName, val -> this.draft.botName = val))
            .setMaxLength(16)
            .size(120, 16);
        return this.nameField;
    }

    private TextFieldWidget createXField() {
        this.xField = createCoordinateField(() -> this.draft.x, val -> this.draft.x = val);
        return this.xField;
    }

    private TextFieldWidget createYField() {
        this.yField = createCoordinateField(() -> this.draft.y, val -> this.draft.y = val);
        return this.yField;
    }

    private TextFieldWidget createZField() {
        this.zField = createCoordinateField(() -> this.draft.z, val -> this.draft.z = val);
        return this.zField;
    }

    private TextFieldWidget createCoordinateField(IntGetter getter, IntSetter setter) {
        return new TextFieldWidget().value(new IntSyncValue(getter::get, setter::set))
            .setFormatAsInteger(true)
            .setNumbers()
            .size(42, 16);
    }

    private TextFieldWidget createDimensionField() {
        this.dimensionField = new TextFieldWidget()
            .value(new IntSyncValue(() -> this.draft.dimension, val -> this.draft.dimension = val))
            .setFormatAsInteger(true)
            .setNumbers()
            .size(42, 16);
        return this.dimensionField;
    }

    private TextFieldWidget createGameModeField() {
        this.gameModeField = new TextFieldWidget()
            .value(
                new StringSyncValue(
                    () -> this.draft.gameMode,
                    val -> this.draft.gameMode = FakePlayerManagerService.normalizeGameMode(val)))
            .setValidator(FakePlayerManagerService::normalizeGameMode)
            .setMaxLength(16)
            .size(54, 16);
        return this.gameModeField;
    }

    private ButtonWidget<?> createSpawnButton() {
        return new ButtonWidget<>().size(60, 18)
            .overlay(IKey.str("Spawn"))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0 && this.spawnRequestSyncValue != null) {
                    this.pendingSpawnRequest = buildSpawnRequest();
                    this.statusMessage = "Spawning...";
                    this.spawnRequestSyncValue.setStringValue(this.pendingSpawnRequest);
                }
                return true;
            });
    }

    private String buildSpawnRequest() {
        String botName = this.nameField == null ? this.draft.botName : this.nameField.getText();
        String x = this.xField == null ? Integer.toString(this.draft.x) : this.xField.getText();
        String y = this.yField == null ? Integer.toString(this.draft.y) : this.yField.getText();
        String z = this.zField == null ? Integer.toString(this.draft.z) : this.zField.getText();
        String dimension = this.dimensionField == null ? Integer.toString(this.draft.dimension)
            : this.dimensionField.getText();
        String gameMode = this.gameModeField == null ? this.draft.gameMode : this.gameModeField.getText();
        return String.join("\n", botName, x, y, z, dimension, gameMode);
    }

    private void handleSpawnRequest(String request) {
        this.pendingSpawnRequest = request;
        if (request == null || request.isEmpty()) {
            return;
        }

        try {
            ServerThreadUtil.addScheduledTask(() -> applySpawnRequest(request));
        } catch (IllegalStateException ignored) {
            applySpawnRequest(request);
        }
    }

    private void applySpawnRequest(String request) {
        String[] parts = request.split("\n", -1);
        if (parts.length != 6) {
            this.statusMessage = "Spawn request payload is invalid";
            return;
        }

        try {
            this.statusMessage = this.service
                .submitSpawn(this.player, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
        } catch (CommandException exception) {
            this.statusMessage = exception.getMessage();
        } finally {
            this.pendingSpawnRequest = "";
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
