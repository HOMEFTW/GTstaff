package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.runtime.BotActionRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotEntityBridge;
import com.andgatech.gtstaff.fakeplayer.runtime.BotFollowRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotInventoryRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotMonitorRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRepelRuntime;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeType;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

import cpw.mods.fml.common.eventhandler.Event;

class MonsterRepellentServiceTest {

    @AfterEach
    void clearRegistry() {
        FakePlayerRegistry.clear();
    }

    @Test
    void denyMobSpawnRecognizesRuntimeOnlyNextGenRepellers() {
        WorldServer world = stubWorld(7);
        StubNextGenPlayer bot = stubNextGenPlayer("RepelBot", world, 7, 10.0D, 64.0D, 10.0D);
        FakePlayerRegistry.registerRuntime(new StubRuntimeView(bot, true, 16));

        TestMonster monster = stubMonster(world, 7, 12.0D, 64.0D, 12.0D);
        CheckSpawn event = new CheckSpawn(monster, world, 12.0F, 64.0F, 12.0F);

        MonsterRepellentService.INSTANCE.denyMobSpawn(event);

        assertEquals(Event.Result.DENY, event.getResult());
    }

    private static WorldServer stubWorld(int dimensionId) {
        WorldServer world = allocate(WorldServer.class);
        WorldProviderSurface provider = allocate(WorldProviderSurface.class);
        provider.dimensionId = dimensionId;
        setField(World.class, world, "provider", provider);
        return world;
    }

    private static StubNextGenPlayer stubNextGenPlayer(String name, WorldServer world, int dimension, double x,
        double y, double z) {
        StubNextGenPlayer player = allocate(StubNextGenPlayer.class);
        player.name = name;
        player.worldObj = world;
        player.dimension = dimension;
        player.posX = x;
        player.posY = y;
        player.posZ = z;
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static TestMonster stubMonster(WorldServer world, int dimension, double x, double y, double z) {
        TestMonster monster = allocate(TestMonster.class);
        monster.worldObj = world;
        monster.dimension = dimension;
        monster.posX = x;
        monster.posY = y;
        monster.posZ = z;
        return monster;
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
        private final BotRepelRuntime repelRuntime;

        private StubRuntimeView(StubNextGenPlayer player, boolean repelling, int repelRange) {
            this.player = player;
            this.repelRuntime = new BotRepelRuntime() {

                private boolean enabled = repelling;
                private int range = repelRange;

                @Override
                public boolean repelling() {
                    return enabled;
                }

                @Override
                public int repelRange() {
                    return range;
                }

                @Override
                public void setRepelling(boolean repelling) {
                    this.enabled = repelling;
                }

                @Override
                public void setRepelRange(int range) {
                    this.range = range;
                }
            };
        }

        @Override
        public String name() {
            return player.getCommandSenderName();
        }

        @Override
        public UUID ownerUUID() {
            return null;
        }

        @Override
        public int dimension() {
            return player.dimension;
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
            return repelRuntime;
        }

        @Override
        public BotInventoryRuntime inventory() {
            return null;
        }
    }

    private static final class StubNextGenPlayer extends GTstaffForgePlayer {

        private String name;

        private StubNextGenPlayer() {
            super(null, null, null);
        }

        @Override
        public String getCommandSenderName() {
            return this.name;
        }
    }

    private static final class TestMonster extends EntityLiving {

        private TestMonster() {
            super(null);
        }

        @Override
        public boolean isCreatureType(EnumCreatureType type, boolean forSpawnCount) {
            return type == EnumCreatureType.monster;
        }
    }
}
