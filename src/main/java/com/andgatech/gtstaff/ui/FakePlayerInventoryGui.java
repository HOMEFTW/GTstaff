package com.andgatech.gtstaff.ui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class FakePlayerInventoryGui extends GuiContainer {

    private static final ResourceLocation CHEST_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 203;
    private static final int TOP_SECTION_HEIGHT = 17 + 5 * 18;
    private static final int BAUBLES_PANEL_X = 180;
    private static final int BAUBLES_PANEL_Y = 12;
    private static final int BAUBLES_PANEL_WIDTH = 72;
    private static final int BAUBLES_PANEL_HEIGHT = 154;
    private static final int BAUBLES_SCROLLBAR_X = 241;
    private static final int BAUBLES_SCROLLBAR_Y = 18;
    private static final int BAUBLES_SCROLLBAR_HEIGHT = 144;
    private static final int BAUBLES_SCROLLBAR_WIDTH = 10;
    private static final int BAUBLES_SCROLLBAR_HANDLE_HEIGHT = 15;
    private static final int BAUBLES_PANEL_BORDER_COLOR = 0xFFB8AA88;
    private static final int BAUBLES_PANEL_FILL_COLOR = 0xFFD7CCAE;
    private static final int BAUBLES_PANEL_INNER_COLOR = 0xFFCBBE9E;
    private static final int BAUBLES_SCROLLBAR_BORDER_COLOR = 0xFF8E826A;
    private static final int BAUBLES_SCROLLBAR_TRACK_COLOR = 0xFFD0C4A5;
    private static final int BAUBLES_SCROLLBAR_HANDLE_BORDER_COLOR = 0xFF7E735C;
    private static final int BAUBLES_SCROLLBAR_HANDLE_COLOR = 0xFFE6D9BC;
    private static final int BAUBLES_SCROLLBAR_DISABLED_HANDLE_COLOR = 0xFFAEA38A;

    private final FakePlayerInventoryContainer container;
    private final String playerInventoryLabel;
    private boolean wasClickingScrollbar;
    private boolean scrollingBaubles;
    private float baublesScrollOffset;

    public FakePlayerInventoryGui(FakePlayerInventoryContainer container, String playerInventoryLabel) {
        super(container);
        this.container = container;
        this.playerInventoryLabel = playerInventoryLabel == null ? "Inventory" : playerInventoryLabel;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        InventoryPlayer playerInventory = this.mc == null || this.mc.thePlayer == null ? null
            : this.mc.thePlayer.inventory;
        String fakeInventoryName = this.container.getFakeInventory()
            .getInventoryName();
        this.fontRendererObj.drawString(fakeInventoryName, 8, 6, 4210752);
        if (this.container.getBaublesVisibleSlotCount() > 0) {
            this.fontRendererObj.drawString("Baubles", BAUBLES_PANEL_X + 2, 6, 4210752);
        }
        this.fontRendererObj.drawString(
            playerInventory == null ? this.playerInventoryLabel : playerInventory.getInventoryName(),
            8,
            this.ySize - 96 + 2,
            4210752);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        handleBaublesScrollbar(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        this.mc.getTextureManager()
            .bindTexture(CHEST_TEXTURE);
        int originX = (this.width - this.xSize) / 2;
        int originY = (this.height - this.ySize) / 2;
        drawTexturedModalRect(originX, originY, 0, 0, 176, TOP_SECTION_HEIGHT);
        drawTexturedModalRect(originX, originY + TOP_SECTION_HEIGHT, 0, 126, 176, 96);
        drawBaublesPanel(originX, originY);
        drawSelectedHotbarHighlight(originX, originY);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheelDelta = Mouse.getEventDWheel();
        if (wheelDelta == 0 || !this.container.canScrollBaubles()) {
            return;
        }

        float step = 1F / Math.max(1, this.container.getBaublesHiddenRowCount());
        this.baublesScrollOffset = clampScrollOffset(this.baublesScrollOffset - Integer.signum(wheelDelta) * step);
        this.container.scrollBaublesTo(this.baublesScrollOffset);
    }

    private void drawBaublesPanel(int originX, int originY) {
        if (this.container.getBaublesVisibleSlotCount() <= 0) {
            return;
        }

        int left = originX + BAUBLES_PANEL_X;
        int top = originY + BAUBLES_PANEL_Y;
        int right = left + BAUBLES_PANEL_WIDTH;
        int bottom = top + BAUBLES_PANEL_HEIGHT;

        drawRect(left, top, right, bottom, BAUBLES_PANEL_BORDER_COLOR);
        drawRect(left + 1, top + 1, right - 1, bottom - 1, BAUBLES_PANEL_FILL_COLOR);
        drawRect(left + 3, top + 3, right - 3, bottom - 3, BAUBLES_PANEL_INNER_COLOR);
        drawBaublesScrollbar(originX, originY);
    }

    private void drawBaublesScrollbar(int originX, int originY) {
        int trackLeft = originX + BAUBLES_SCROLLBAR_X;
        int trackTop = originY + BAUBLES_SCROLLBAR_Y;
        int trackRight = trackLeft + BAUBLES_SCROLLBAR_WIDTH;
        int trackBottom = trackTop + BAUBLES_SCROLLBAR_HEIGHT;

        drawRect(trackLeft, trackTop, trackRight, trackBottom, BAUBLES_SCROLLBAR_BORDER_COLOR);
        drawRect(trackLeft + 1, trackTop + 1, trackRight - 1, trackBottom - 1, BAUBLES_SCROLLBAR_TRACK_COLOR);

        int handleTop = trackTop;
        if (this.container.canScrollBaubles()) {
            handleTop += (int) ((BAUBLES_SCROLLBAR_HEIGHT - BAUBLES_SCROLLBAR_HANDLE_HEIGHT) * this.baublesScrollOffset + 0.5F);
        }
        int handleColor = this.container.canScrollBaubles() ? BAUBLES_SCROLLBAR_HANDLE_COLOR
            : BAUBLES_SCROLLBAR_DISABLED_HANDLE_COLOR;
        drawRect(
            trackLeft + 1,
            handleTop,
            trackRight - 1,
            handleTop + BAUBLES_SCROLLBAR_HANDLE_HEIGHT,
            BAUBLES_SCROLLBAR_HANDLE_BORDER_COLOR);
        drawRect(trackLeft + 2, handleTop + 1, trackRight - 2, handleTop + BAUBLES_SCROLLBAR_HANDLE_HEIGHT - 1, handleColor);
    }

    private void handleBaublesScrollbar(int mouseX, int mouseY) {
        boolean mouseDown = Mouse.isButtonDown(0);
        if (!this.wasClickingScrollbar && mouseDown && isMouseOverBaublesScrollbar(mouseX, mouseY)) {
            this.scrollingBaubles = this.container.canScrollBaubles();
        }
        if (!mouseDown) {
            this.scrollingBaubles = false;
        }
        this.wasClickingScrollbar = mouseDown;
        if (!this.scrollingBaubles) {
            return;
        }

        int originY = (this.height - this.ySize) / 2;
        int trackTop = originY + BAUBLES_SCROLLBAR_Y;
        float scrollRange = BAUBLES_SCROLLBAR_HEIGHT - BAUBLES_SCROLLBAR_HANDLE_HEIGHT;
        this.baublesScrollOffset = clampScrollOffset((mouseY - trackTop - BAUBLES_SCROLLBAR_HANDLE_HEIGHT / 2F) / scrollRange);
        this.container.scrollBaublesTo(this.baublesScrollOffset);
    }

    private boolean isMouseOverBaublesScrollbar(int mouseX, int mouseY) {
        int originX = (this.width - this.xSize) / 2;
        int originY = (this.height - this.ySize) / 2;
        int left = originX + BAUBLES_SCROLLBAR_X;
        int top = originY + BAUBLES_SCROLLBAR_Y;
        return mouseX >= left && mouseX < left + BAUBLES_SCROLLBAR_WIDTH && mouseY >= top
            && mouseY < top + BAUBLES_SCROLLBAR_HEIGHT;
    }

    private void drawSelectedHotbarHighlight(int originX, int originY) {
        int slot = this.container.getSelectedHotbarSlot();
        int x = originX + 7 + slot * 18;
        int y = originY + 35;
        drawRect(x, y, x + 18, y + 1, 0xFFF0D060);
        drawRect(x, y + 17, x + 18, y + 18, 0xFFF0D060);
        drawRect(x, y, x + 1, y + 18, 0xFFF0D060);
        drawRect(x + 17, y, x + 18, y + 18, 0xFFF0D060);
    }

    private static float clampScrollOffset(float scrollOffset) {
        if (scrollOffset < 0F) {
            return 0F;
        }
        return Math.min(1F, scrollOffset);
    }
}
