package com.andgatech.gtstaff.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;
import com.andgatech.gtstaff.fakeplayer.runtime.BotSession;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;
import com.andgatech.gtstaff.fakeplayer.runtime.NextGenBotRuntime;
import com.mojang.authlib.GameProfile;

class RespawnMixinsTest {

    @AfterEach
    void tearDown() {
        FakePlayerRegistry.clear();
        RespawnMixinHooks.setNextGenRespawnFactoryForTesting(null);
    }

    @Test
    void respawnConstructorKeepsNextGenFakePlayerClassAndRuntimeBinding() {
        UUID ownerUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        GTstaffForgePlayer source = nextGenPlayer("RespawnBot");
        source.setOwnerUUID(ownerUuid);
        RespawnMixinHooks.setNextGenRespawnFactoryForTesting(
            (minecraftServer, worldServer, gameProfile, itemInWorldManager, sourcePlayer) -> {
                GTstaffForgePlayer respawned = nextGenPlayer(gameProfile.getName());
                GTstaffForgePlayer nextGenSource = (GTstaffForgePlayer) sourcePlayer;
                respawned.setOwnerUUID(nextGenSource.getOwnerUUID());
                new NextGenBotRuntime(respawned, new BotSession(respawned), nextGenSource.getOwnerUUID());
                return respawned;
            });

        EntityPlayerMP respawned = RespawnMixinHooks.createRespawnPlayer(
            null,
            allocate(WorldServer.class),
            profile("RespawnBot"),
            allocate(ItemInWorldManager.class),
            source);

        assertTrue(respawned instanceof GTstaffForgePlayer);
        GTstaffForgePlayer nextGenRespawned = (GTstaffForgePlayer) respawned;
        assertEquals(ownerUuid, nextGenRespawned.getOwnerUUID());
        assertNotNull(nextGenRespawned.runtime());
        assertEquals(ownerUuid, nextGenRespawned.runtime().ownerUUID());
    }

    @Test
    void cloneHelperRebindsNextGenRuntimeAndCopiesServiceState() {
        UUID ownerUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID followTarget = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        GTstaffForgePlayer source = nextGenPlayer("RespawnBot");
        source.setOwnerUUID(ownerUuid);
        NextGenBotRuntime sourceRuntime = new NextGenBotRuntime(source, new BotSession(source), ownerUuid);
        sourceRuntime.monitor().setMonitoring(true);
        sourceRuntime.monitor().setMonitorRange(24);
        sourceRuntime.monitor().setReminderInterval(200);
        sourceRuntime.repel().setRepelling(true);
        sourceRuntime.repel().setRepelRange(96);
        sourceRuntime.follow().setFollowRange(7);
        sourceRuntime.follow().setTeleportRange(40);
        sourceRuntime.follow().startFollowing(followTarget);

        GTstaffForgePlayer target = nextGenPlayer("RespawnBot");
        RespawnMixinHooks.copyFakePlayerState(source, target);

        assertEquals(ownerUuid, target.getOwnerUUID());
        assertNotNull(target.runtime());
        assertTrue(target.runtime().monitor().monitoring());
        assertEquals(24, target.runtime().monitor().monitorRange());
        assertEquals(200, target.runtime().monitor().reminderInterval());
        assertTrue(target.runtime().repel().repelling());
        assertEquals(96, target.runtime().repel().repelRange());
        assertTrue(target.runtime().follow().following());
        assertEquals(followTarget, target.runtime().follow().targetUUID());
        assertEquals(7, target.runtime().follow().followRange());
        assertEquals(40, target.runtime().follow().teleportRange());

        BotRuntimeView registered = FakePlayerRegistry.getRuntimeView("RespawnBot");
        assertSame(target.runtime(), registered);
    }

    private static GTstaffForgePlayer nextGenPlayer(String name) {
        GTstaffForgePlayer player = allocate(GTstaffForgePlayer.class);
        setField(Entity.class, player, "worldObj", allocate(WorldServer.class));
        setField(
            Entity.class,
            player,
            "boundingBox",
            AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
        setField(EntityPlayer.class, player, "field_146106_i", profile(name));
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
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
