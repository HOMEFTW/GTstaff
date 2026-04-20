package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakePlayerClientUseCompatTest {

    @AfterEach
    void tearDown() {
        FakePlayerClientUseCompat.resetHandlersForTesting();
    }

    @Test
    void returnsFalseWhenNoHandlerMatches() {
        StubPlayer player = stubPlayer();
        ItemStack stack = new ItemStack(new Item());

        assertFalse(FakePlayerClientUseCompat.tryUse(player, stack, null, false, false));
    }

    @Test
    void usesFirstMatchingHandlerThatSucceeds() {
        StubPlayer player = stubPlayer();
        ItemStack stack = new ItemStack(new Item());

        FakePlayerClientUseCompat.setHandlersForTesting(
            Arrays.<FakePlayerClientUseCompat.ClientUseHandler>asList(
                new FakePlayerClientUseCompat.ClientUseHandler() {

                    @Override
                    public boolean matches(ItemStack held) {
                        return true;
                    }

                    @Override
                    public boolean tryUse(EntityPlayerMP entityPlayer, ItemStack held, net.minecraft.util.MovingObjectPosition target,
                        boolean blockUsed, boolean itemUsed) {
                        return false;
                    }
                },
                new FakePlayerClientUseCompat.ClientUseHandler() {

                    @Override
                    public boolean matches(ItemStack held) {
                        return true;
                    }

                    @Override
                    public boolean tryUse(EntityPlayerMP entityPlayer, ItemStack held, net.minecraft.util.MovingObjectPosition target,
                        boolean blockUsed, boolean itemUsed) {
                        return itemUsed && !blockUsed;
                    }
                }));

        assertTrue(FakePlayerClientUseCompat.tryUse(player, stack, null, false, true));
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
