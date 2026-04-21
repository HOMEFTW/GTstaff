package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.mojang.authlib.GameProfile;

class LegacyBotHandleTest {

    @Test
    void exposesLegacyFakePlayerThroughRuntimeNeutralInterface() {
        FakePlayer fakePlayer = allocate(FakePlayer.class);
        setField(Entity.class, fakePlayer, "worldObj", allocate(WorldServer.class));
        setField(EntityPlayerMP.class, fakePlayer, "theItemInWorldManager", allocate(ItemInWorldManager.class));
        setField(
            net.minecraft.entity.player.EntityPlayer.class,
            fakePlayer,
            "field_146106_i",
            new GameProfile(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "PlanBot"));
        fakePlayer.setOwnerUUID(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        fakePlayer.dimension = 7;

        LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

        assertEquals("PlanBot", handle.name());
        assertEquals(BotRuntimeType.LEGACY, handle.runtimeType());
        assertEquals(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), handle.ownerUUID());
        assertEquals(7, handle.dimension());
        assertSame(fakePlayer, handle.entity().asPlayer());
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
