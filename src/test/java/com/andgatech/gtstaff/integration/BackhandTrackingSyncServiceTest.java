package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

class BackhandTrackingSyncServiceTest {

    @AfterEach
    void tearDown() {
        BackhandCompat.setOffhandSyncToPlayerActionForTests(null);
    }

    @Test
    void startTrackingNextGenFakePlayerSyncsCurrentOffhandToWatcher() {
        AtomicInteger syncCalls = new AtomicInteger();
        BackhandCompat.setOffhandSyncToPlayerActionForTests((player, watcher) -> syncCalls.incrementAndGet());

        EntityPlayerMP watcher = allocate(EntityPlayerMP.class);
        GTstaffForgePlayer fakePlayer = allocate(GTstaffForgePlayer.class);

        BackhandTrackingSyncService.INSTANCE.syncTrackedFakePlayer(watcher, fakePlayer);

        assertEquals(1, syncCalls.get());
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
}
