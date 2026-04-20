package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.server.management.ItemInWorldManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

class FakePlayerMovementCompatTest {

    @AfterEach
    void tearDown() {
        FakePlayerMovementCompat.resetHandlersForTesting();
    }

    @Test
    void returnsFalseWhenNoHandlerMatches() {
        StubPlayer player = stubPlayer();

        assertFalse(FakePlayerMovementCompat.tryTrigger(player, PlayerActionPack.MovementTrigger.JUMP));
    }

    @Test
    void usesFirstMatchingHandlerThatSucceeds() {
        StubPlayer player = stubPlayer();

        FakePlayerMovementCompat.setHandlersForTesting(
            Arrays.<FakePlayerMovementCompat.MovementHandler>asList(
                new FakePlayerMovementCompat.MovementHandler() {

                    @Override
                    public boolean tryTrigger(EntityPlayerMP entityPlayer, PlayerActionPack.MovementTrigger trigger) {
                        return false;
                    }
                },
                new FakePlayerMovementCompat.MovementHandler() {

                    @Override
                    public boolean tryTrigger(EntityPlayerMP entityPlayer, PlayerActionPack.MovementTrigger trigger) {
                        return trigger == PlayerActionPack.MovementTrigger.SNEAK;
                    }
                }));

        assertTrue(FakePlayerMovementCompat.tryTrigger(player, PlayerActionPack.MovementTrigger.SNEAK));
    }

    private static StubPlayer stubPlayer() {
        StubPlayer player = allocate(StubPlayer.class);
        player.inventory = new InventoryPlayer(player);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        return player;
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

    private static class StubPlayer extends EntityPlayerMP {

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }
}
