package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;

class BotSessionTest {

    @Test
    void nextGenRuntimeExposesForgeBackedPlayerAndSession() {
        GTstaffForgePlayer player = allocate(GTstaffForgePlayer.class);
        setField(net.minecraft.entity.Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            net.minecraft.entity.player.EntityPlayer.class,
            player,
            "field_146106_i",
            new GameProfile(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), "NextGenBot"));

        BotSession session = new BotSession(player);
        NextGenBotRuntime runtime = new NextGenBotRuntime(player, session);

        assertEquals(BotRuntimeType.NEXTGEN, runtime.runtimeType());
        assertSame(session, runtime.session());
        assertSame(player, runtime.entity().asPlayer());
        assertSame(FakePlayer.class, GTstaffForgePlayer.class.getSuperclass());
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
