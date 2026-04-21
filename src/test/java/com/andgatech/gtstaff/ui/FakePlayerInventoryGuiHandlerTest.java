package com.andgatech.gtstaff.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.server.management.ItemInWorldManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventoryRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventorySummary;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

class FakePlayerInventoryGuiHandlerTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void serverGuiElementResolvesRuntimeOnlyNextGenBot() {
        TestPlayer viewer = stubViewer();
        StubNextGenPlayer runtimeBot = stubNextGenPlayer("RuntimeBot", 2451);
        FakePlayerRegistry.registerRuntime(new StubRuntimeView(runtimeBot));

        Object gui = FakePlayerInventoryGuiHandler.INSTANCE.getServerGuiElement(
            FakePlayerInventoryGuiIds.FAKE_PLAYER_INVENTORY,
            viewer,
            null,
            2451,
            0,
            0);

        assertNotNull(gui);
    }

    private static TestPlayer stubViewer() {
        TestPlayer player = allocate(TestPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = allocate(StubContainer.class);
        setField(EntityPlayerMP.class, player, "theItemInWorldManager", allocate(StubItemInWorldManager.class));
        return player;
    }

    private static StubNextGenPlayer stubNextGenPlayer(String name, int entityId) {
        StubNextGenPlayer player = allocate(StubNextGenPlayer.class);
        player.name = name;
        player.entityId = entityId;
        player.inventory = new InventoryPlayer(player);
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

    private static final class StubRuntimeView implements BotRuntimeView {

        private final StubNextGenPlayer player;

        private StubRuntimeView(StubNextGenPlayer player) {
            this.player = player;
        }

        @Override
        public String name() {
            return player.getCommandSenderName();
        }

        @Override
        public java.util.UUID ownerUUID() {
            return null;
        }

        @Override
        public int dimension() {
            return 0;
        }

        @Override
        public BotRuntimeType runtimeType() {
            return BotRuntimeType.NEXTGEN;
        }

        @Override
        public BotEntityBridge entity() {
            return () -> player;
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public BotActionRuntime action() {
            return null;
        }

        @Override
        public BotFollowRuntime follow() {
            return null;
        }

        @Override
        public BotMonitorRuntime monitor() {
            return null;
        }

        @Override
        public BotRepelRuntime repel() {
            return null;
        }

        @Override
        public BotInventoryRuntime inventory() {
            return new BotInventoryRuntime() {

                @Override
                public int selectedHotbarSlot() {
                    return 0;
                }

                @Override
                public BotInventorySummary summary() {
                    return new BotInventorySummary(
                        player.getCommandSenderName(),
                        0,
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList());
                }

                @Override
                public String openInventoryManager(EntityPlayerMP viewer) {
                    return "Opening inventory manager for " + player.getCommandSenderName() + ".";
                }
            };
        }
    }

    private static final class StubNextGenPlayer extends GTstaffForgePlayer {

        private String name;
        private int entityId;

        private StubNextGenPlayer() {
            super(null, null, null);
        }

        @Override
        public String getCommandSenderName() {
            return this.name;
        }

        @Override
        public int getEntityId() {
            return this.entityId;
        }
    }

    private static final class TestPlayer extends EntityPlayerMP {

        private TestPlayer() {
            super(null, null, null, (ItemInWorldManager) null);
        }

        @Override
        public void sendContainerToPlayer(net.minecraft.inventory.Container container) {}
    }

    private static final class StubContainer extends net.minecraft.inventory.Container {

        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
            return true;
        }
    }

    private static final class StubItemInWorldManager extends ItemInWorldManager {

        private StubItemInWorldManager() {
            super(null);
        }
    }
}
