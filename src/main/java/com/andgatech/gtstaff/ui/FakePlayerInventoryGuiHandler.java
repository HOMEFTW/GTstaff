package com.andgatech.gtstaff.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotHandle;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.integration.ServerUtilitiesCompat;

import cpw.mods.fml.common.network.IGuiHandler;

public final class FakePlayerInventoryGuiHandler implements IGuiHandler {

    public static final FakePlayerInventoryGuiHandler INSTANCE = new FakePlayerInventoryGuiHandler();

    private FakePlayerInventoryGuiHandler() {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY || !(player instanceof EntityPlayerMP playerMP)) {
            return null;
        }

        EntityPlayerMP fakePlayer = findServerFakePlayer(x);
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

        EntityPlayer fakePlayer = resolveClientFakePlayer(world, x);
        FakePlayerInventoryView fakeInventory = FakePlayerInventoryView.client(resolveInventoryName(world, x), fakePlayer);
        FakePlayerInventoryContainer container = FakePlayerInventoryContainer.client(player, fakeInventory);
        return new FakePlayerInventoryGui(container, player.inventory.getInventoryName());
    }

    private EntityPlayerMP findServerFakePlayer(int entityId) {
        for (BotHandle handle : FakePlayerRegistry.getAllBotHandles()) {
            if (!(handle instanceof BotRuntimeView runtime)) {
                continue;
            }
            EntityPlayerMP fakePlayer = runtime.entity()
                .asPlayer();
            if (fakePlayer != null && fakePlayer.getEntityId() == entityId
                && ServerUtilitiesCompat.isFakePlayer(fakePlayer)) {
                return fakePlayer;
            }
        }
        return null;
    }

    private EntityPlayer resolveClientFakePlayer(World world, int entityId) {
        if (world != null) {
            Entity entity = world.getEntityByID(entityId);
            if (entity instanceof EntityPlayer fakePlayer) {
                return fakePlayer;
            }
        }

        World clientWorld = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().theWorld;
        if (clientWorld != null) {
            Entity entity = clientWorld.getEntityByID(entityId);
            if (entity instanceof EntityPlayer fakePlayer) {
                return fakePlayer;
            }
        }
        return null;
    }

    private String resolveInventoryName(World world, int entityId) {
        for (BotHandle handle : FakePlayerRegistry.getAllBotHandles()) {
            if (!(handle instanceof BotRuntimeView runtime)) {
                continue;
            }
            EntityPlayerMP fakePlayer = runtime.entity()
                .asPlayer();
            if (fakePlayer != null && fakePlayer.getEntityId() == entityId) {
                return runtime.name();
            }
        }

        if (world != null) {
            Entity entity = world.getEntityByID(entityId);
            if (entity instanceof EntityPlayer fakePlayer) {
                return fakePlayer.getCommandSenderName();
            }
        }

        World clientWorld = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().theWorld;
        if (clientWorld != null) {
            Entity entity = clientWorld.getEntityByID(entityId);
            if (entity instanceof EntityPlayer fakePlayer) {
                return fakePlayer.getCommandSenderName();
            }
        }
        return "Fake Player";
    }
}
