package com.andgatech.gtstaff.fakeplayer.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import org.junit.jupiter.api.Test;

class UseExecutorTest {

    @Test
    void reportsBridgeStateAfterDirectUseSucceeds() {
        StubPlayer player = allocate(StubPlayer.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.inventory.mainInventory[0] = new ItemStack(new Item());
        player.inventory.currentItem = 0;
        setField(net.minecraft.entity.Entity.class, player, "worldObj", allocate(WorldServer.class));
        StubItemInWorldManager manager = allocate(StubItemInWorldManager.class);
        setField(ItemInWorldManager.class, manager, "gameType", WorldSettings.GameType.SURVIVAL);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", manager);

        TrackingFeedbackSync feedback = new TrackingFeedbackSync();
        UseExecutor executor = new UseExecutor(player, feedback) {
            @Override
            protected boolean performDirectItemUse(ItemStack held) {
                return true;
            }

            @Override
            protected boolean performClientUseBridge(net.minecraft.util.MovingObjectPosition target, ItemStack held,
                boolean blockUsed, boolean itemUsed) {
                return !blockUsed && itemUsed;
            }
        };

        UseResult result = executor.execute(null, 0);

        assertTrue(result.itemUsed());
        assertTrue(result.bridgeUsed());
        assertFalse(result.blockUsed());
        assertFalse(result.swingTriggered());
        assertFalse(feedback.swung);
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

    private static final class StubPlayer extends EntityPlayerMP {

        private StubPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void sendContainerToPlayer(net.minecraft.inventory.Container container) {}
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }

    private static final class TrackingFeedbackSync extends FeedbackSync {

        private boolean swung;

        private TrackingFeedbackSync() {
            super(null);
        }

        @Override
        public void swing() {
            swung = true;
        }
    }
}
