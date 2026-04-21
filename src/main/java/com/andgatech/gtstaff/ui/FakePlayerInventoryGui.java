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
    private static final int CHEST_WIDTH = 176;
    private static final int BAUBLES_PANEL_X = 180;
    private static final int BAUBLES_PANEL_Y = 12;
    private static final int BAUBLES_PANEL_WIDTH = 72;
    private static final int BAUBLES_PANEL_HEIGHT = 154;
    private static final int BAUBLES_SCROLLBAR_X = 241;
    private static final int BAUBLES_SCROLLBAR_Y = 18;
    private static final int BAUBLES_SCROLLBAR_WIDTH = 10;
    private static final int BAUBLES_SCROLLBAR_HEIGHT = 144;
    private static final int BAUBLES_SCROLLBAR_HANDLE_HEIGHT = 15;

    private final FakePlayerInventoryContainer container;
    private final String playerInventoryLabel;
    private boolean draggingBaublesScrollbar;

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
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        this.mc.getTextureManager()
            .bindTexture(CHEST_TEXTURE);
        int originX = (this.width - this.xSize) / 2;
        int originY = (this.height - this.ySize) / 2;
        drawTexturedModalRect(originX, originY, 0, 0, CHEST_WIDTH, this.container.getTopSectionHeight());
        drawTexturedModalRect(originX, originY + this.container.getTopSectionHeight(), 0, 126, CHEST_WIDTH, 96);
        drawBaublesPanel(originX, originY);
        drawSelectedHotbarHighlight(originX, originY);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || !this.container.canScrollBaubles()) {
            return;
        }
        float step = 1F / this.container.getBaublesHiddenRowCount();
        this.container.scrollBaublesTo(this.container.getBaublesScrollOffset() + (wheel < 0 ? step : -step));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && this.container.canScrollBaubles() && isMouseOverBaublesScrollbar(mouseX, mouseY)) {
            this.draggingBaublesScrollbar = true;
            updateBaublesScrollFromMouse(mouseY);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (this.draggingBaublesScrollbar) {
            updateBaublesScrollFromMouse(mouseY);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            this.draggingBaublesScrollbar = false;
        }
    }

    private void drawBaublesPanel(int originX, int originY) {
        if (this.container.getBaublesVisibleSlotCount() <= 0) {
            return;
        }
        int left = originX + BAUBLES_PANEL_X;
        int top = originY + BAUBLES_PANEL_Y;
        drawRect(left, top, left + BAUBLES_PANEL_WIDTH, top + BAUBLES_PANEL_HEIGHT, 0xFFC6C6C6);
        drawRect(left + 1, top + 1, left + BAUBLES_PANEL_WIDTH - 1, top + BAUBLES_PANEL_HEIGHT - 1, 0xFF8B8B8B);
        drawRect(left + 3, top + 3, left + BAUBLES_PANEL_WIDTH - 3, top + BAUBLES_PANEL_HEIGHT - 3, 0xFFCFCFCF);
        if (this.container.canScrollBaubles()) {
            drawBaublesScrollbar(originX, originY);
        }
    }

    private void drawBaublesScrollbar(int originX, int originY) {
        int scrollbarLeft = originX + BAUBLES_SCROLLBAR_X;
        int scrollbarTop = originY + BAUBLES_SCROLLBAR_Y;
        drawRect(
            scrollbarLeft,
            scrollbarTop,
            scrollbarLeft + BAUBLES_SCROLLBAR_WIDTH,
            scrollbarTop + BAUBLES_SCROLLBAR_HEIGHT,
            0xFF555555);
        int maxTravel = BAUBLES_SCROLLBAR_HEIGHT - BAUBLES_SCROLLBAR_HANDLE_HEIGHT;
        int handleTop = scrollbarTop + (int) (this.container.getBaublesScrollOffset() * maxTravel + 0.5F);
        drawRect(
            scrollbarLeft + 1,
            handleTop,
            scrollbarLeft + BAUBLES_SCROLLBAR_WIDTH - 1,
            handleTop + BAUBLES_SCROLLBAR_HANDLE_HEIGHT,
            0xFFE0E0E0);
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

    private boolean isMouseOverBaublesScrollbar(int mouseX, int mouseY) {
        int left = this.guiLeft + BAUBLES_SCROLLBAR_X;
        int top = this.guiTop + BAUBLES_SCROLLBAR_Y;
        return mouseX >= left && mouseX < left + BAUBLES_SCROLLBAR_WIDTH
            && mouseY >= top
            && mouseY < top + BAUBLES_SCROLLBAR_HEIGHT;
    }

    private void updateBaublesScrollFromMouse(int mouseY) {
        int top = this.guiTop + BAUBLES_SCROLLBAR_Y;
        int maxTravel = BAUBLES_SCROLLBAR_HEIGHT - BAUBLES_SCROLLBAR_HANDLE_HEIGHT;
        float scrollOffset = (mouseY - top - BAUBLES_SCROLLBAR_HANDLE_HEIGHT / 2F) / maxTravel;
        this.container.scrollBaublesTo(scrollOffset);
    }
}
