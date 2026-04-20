package com.andgatech.gtstaff.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

import baubles.common.container.InventoryBaubles;
import baubles.common.lib.PlayerHandler;
import cpw.mods.fml.common.network.IGuiHandler;

public final class FakePlayerInventoryGuiHandler implements IGuiHandler {

    public static final FakePlayerInventoryGuiHandler INSTANCE = new FakePlayerInventoryGuiHandler();

    private FakePlayerInventoryGuiHandler() {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY || !(player instanceof EntityPlayerMP playerMP)) {
            return null;
        }

        FakePlayer fakePlayer = findServerFakePlayer(x);
        if (fakePlayer == null) {
            return null;
        }
        return FakePlayerInventoryContainer.server(playerMP, fakePlayer);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY) {
            return null;
        }

        FakePlayer fakePlayer = findClientFakePlayer(world, x);
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView
            .client(fakePlayer == null ? "Fake Player" : fakePlayer.getCommandSenderName());
        InventoryBaubles baublesInventory = fakePlayer == null ? null : PlayerHandler.getPlayerBaubles(fakePlayer);
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory, baublesInventory);
        return new FakePlayerInventoryGui(container, player.inventory.getInventoryName());
    }

    private FakePlayer findServerFakePlayer(int entityId) {
        for (FakePlayer fakePlayer : FakePlayerRegistry.getAll()
            .values()) {
            if (fakePlayer != null && fakePlayer.getEntityId() == entityId) {
                return fakePlayer;
            }
        }
        return null;
    }

    private FakePlayer findClientFakePlayer(World world, int entityId) {
        if (world != null) {
            Entity entity = world.getEntityByID(entityId);
            if (entity instanceof FakePlayer fakePlayer) {
                return fakePlayer;
            }
        }

        World clientWorld = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().theWorld;
        if (clientWorld != null) {
            Entity entity = clientWorld.getEntityByID(entityId);
            if (entity instanceof FakePlayer fakePlayer) {
                return fakePlayer;
            }
        }
        return null;
    }
}
