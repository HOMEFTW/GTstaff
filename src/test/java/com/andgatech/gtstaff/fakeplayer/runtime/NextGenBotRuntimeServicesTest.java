package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;
import com.andgatech.gtstaff.fakeplayer.MachineMonitorService;
import com.andgatech.gtstaff.fakeplayer.MachineState;
import com.andgatech.gtstaff.fakeplayer.FollowService;

class NextGenBotRuntimeServicesTest {

    @Test
    void nextGenRuntimeExposesMutableServiceState() {
        NextGenBotRuntime runtime = runtime("WaveCBot");
        UUID targetUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        runtime.follow()
            .setFollowRange(0);
        runtime.follow()
            .setTeleportRange(1);
        runtime.follow()
            .startFollowing(targetUuid);
        runtime.monitor()
            .setMonitoring(true);
        runtime.monitor()
            .setMonitorRange(0);
        runtime.monitor()
            .setReminderInterval(10);
        runtime.repel()
            .setRepelling(true);
        runtime.repel()
            .setRepelRange(128);

        assertTrue(runtime.follow().following());
        assertEquals(targetUuid, runtime.follow().targetUUID());
        assertEquals(1, runtime.follow().followRange());
        assertEquals(2, runtime.follow().teleportRange());
        assertTrue(runtime.monitor().monitoring());
        assertEquals(1, runtime.monitor().monitorRange());
        assertEquals(60, runtime.monitor().reminderInterval());
        assertTrue(runtime.repel().repelling());
        assertEquals(128, runtime.repel().repelRange());

        runtime.follow()
            .stop();
        assertFalse(runtime.follow().following());
    }

    @Test
    void nextGenInventorySummaryReflectsForgePlayerInventory() {
        GTstaffForgePlayer player = player("InventoryBot");
        player.inventory = new InventoryPlayer(player);
        player.inventory.currentItem = 2;
        player.inventory.mainInventory[0] = stack("Torch", 16);
        player.inventory.mainInventory[9] = stack("Wrench", 1);
        player.inventory.armorInventory[3] = stack("Nano Helmet", 1);

        NextGenBotRuntime runtime = new NextGenBotRuntime(player, new BotSession(player));
        BotInventorySummary summary = runtime.inventory()
            .summary();

        assertEquals(2, summary.selectedHotbarSlot());
        assertTrue(summary.hotbarLines().stream().anyMatch(line -> line.contains("Torch x16")));
        assertTrue(summary.mainInventoryLines().stream().anyMatch(line -> line.contains("Wrench x1")));
        assertTrue(summary.armorLines().stream().anyMatch(line -> line.contains("Nano Helmet x1")));
    }

    @Test
    void nextGenMonitorScanNowUsesMachineMonitorService() {
        GTstaffForgePlayer player = player("MonitorBot");
        TrackingMonitorService service = new TrackingMonitorService();
        NextGenMonitorRuntime runtime = new NextGenMonitorRuntime(player, service);
        runtime.setMonitoring(true);
        runtime.setMonitorRange(32);

        String overview = runtime.scanNow("MonitorBot");

        assertTrue(service.scanCalled);
        assertTrue(overview.contains("[MonitorBot]"));
        assertTrue(overview.contains("(1,2,3)"));
    }

    @Test
    void nextGenFollowTickUsesFollowService() {
        GTstaffForgePlayer player = player("FollowBot");
        TrackingFollowService service = new TrackingFollowService(player);
        NextGenFollowRuntime runtime = new NextGenFollowRuntime(player, service);

        runtime.tick();

        assertEquals(1, service.tickCalls);
    }

    private static NextGenBotRuntime runtime(String name) {
        GTstaffForgePlayer player = player(name);
        return new NextGenBotRuntime(player, new BotSession(player));
    }

    private static GTstaffForgePlayer player(String name) {
        GTstaffForgePlayer player = allocate(GTstaffForgePlayer.class);
        setField(Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            EntityPlayer.class,
            player,
            "field_146106_i",
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        return player;
    }

    private static ItemStack stack(String name, int size) {
        return new ItemStack(new NamedItem(name), size);
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

    private static final class NamedItem extends Item {

        private final String displayName;

        private NamedItem(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getItemStackDisplayName(ItemStack stack) {
            return displayName;
        }
    }

    private static final class TrackingMonitorService extends MachineMonitorService {

        private boolean scanCalled;

        @Override
        protected Map<ChunkCoordinates, MachineState> scanMachines(net.minecraft.entity.player.EntityPlayerMP player) {
            this.scanCalled = true;
            Map<ChunkCoordinates, MachineState> states = new HashMap<ChunkCoordinates, MachineState>();
            states.put(new ChunkCoordinates(1, 2, 3), new MachineState(true, true, false, false, false));
            return states;
        }
    }

    private static final class TrackingFollowService extends FollowService {

        private int tickCalls;

        private TrackingFollowService(GTstaffForgePlayer player) {
            super(player, () -> null, (bot, target, server) -> true);
        }

        @Override
        public void tick() {
            tickCalls++;
        }
    }
}
