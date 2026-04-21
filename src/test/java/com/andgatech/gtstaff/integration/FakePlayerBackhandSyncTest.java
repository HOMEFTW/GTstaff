package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;

class FakePlayerBackhandSyncTest {

    @AfterEach
    void tearDown() {
        BackhandCompat.setOffhandSyncActionForTests(null);
    }

    @Test
    void fakePlayerEquipmentSyncAlsoTriggersBackhandOffhandSync() {
        AtomicInteger offhandSyncCalls = new AtomicInteger();
        BackhandCompat.setOffhandSyncActionForTests(player -> offhandSyncCalls.incrementAndGet());

        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.inventory = new InventoryPlayer(fakePlayer);
        WorldServer world = allocate(WorldServer.class);
        world.playerEntities = new ArrayList<>();
        setField(World.class, world, "isRemote", false);
        setField(net.minecraft.entity.Entity.class, fakePlayer, "worldObj", world);

        fakePlayer.syncEquipmentToWatchers();

        assertEquals(1, offhandSyncCalls.get());
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

    private static final class StubFakePlayer extends FakePlayer {

        private StubFakePlayer() {
            super(null, null, "stub");
        }
    }
}
