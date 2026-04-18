package com.andgatech.gtstaff.ui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public class FakePlayerInventoryGui extends GuiContainer {

    private static final ResourceLocation CHEST_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 203;
    private static final int TOP_SECTION_HEIGHT = 17 + 5 * 18;

    private final FakePlayerInventoryContainer container;
    private final String playerInventoryLabel;

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
        drawTexturedModalRect(originX, originY, 0, 0, this.xSize, TOP_SECTION_HEIGHT);
        drawTexturedModalRect(originX, originY + TOP_SECTION_HEIGHT, 0, 126, this.xSize, 96);
        drawSelectedHotbarHighlight(originX, originY);
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
}
