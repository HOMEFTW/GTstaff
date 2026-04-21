package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.FollowService;
import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;
import com.mojang.authlib.GameProfile;

class LegacyBotHandleServicesTest {

    @Test
    void legacyHandleExposesServiceFacades() {
        FakePlayer fakePlayer = allocate(FakePlayer.class);
        setField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj", allocate(WorldServer.class));
        setField(net.minecraft.entity.player.EntityPlayerMP.class, fakePlayer, "theItemInWorldManager",
            allocate(ItemInWorldManager.class));
        setField(EntityPlayer.class, fakePlayer, "field_146106_i",
            new GameProfile(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "WaveCBot"));
        setField(FakePlayer.class, fakePlayer, "machineMonitorService", new MachineMonitorService());
        setField(FakePlayer.class, fakePlayer, "followService", new FollowService(fakePlayer));
        fakePlayer.inventory = new InventoryPlayer(fakePlayer);

        LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

        assertNotNull(handle.follow());
        assertNotNull(handle.monitor());
        assertNotNull(handle.repel());
        assertNotNull(handle.inventory());
        assertFalse(handle.follow().following());
        assertEquals(0, handle.inventory().selectedHotbarSlot());
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return type.cast(unsafe.allocateInstance(type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
