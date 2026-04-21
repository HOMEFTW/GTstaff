package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.util.DamageSource;

class GTstaffForgePlayerTest {

    @Test
    void onUpdateRunsBaseActionAndLivingPhasesInOrder() {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);
        player.events = new ArrayList<String>();
        player.pack = new TrackingActionPack(player.events, player);

        player.onUpdate();

        assertEquals(Arrays.asList("base", "action", "runtime", "living"), player.events);
        assertEquals(1, player.pack.onUpdateCalls);
    }

    @Test
    void onUpdateRespawnsDeadNextGenBotBeforeTickPhases() {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);
        player.events = new ArrayList<String>();
        player.pack = new TrackingActionPack(player.events, player);
        player.isDead = true;
        player.deathTime = 5;

        player.onUpdate();

        assertEquals(1, player.respawnCalls);
        assertEquals(Arrays.asList("respawn", "base", "action", "runtime", "living"), player.events);
    }

    @Test
    void onUpdateDoesNothingAfterDisconnect() {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);
        player.events = new ArrayList<String>();
        player.pack = new TrackingActionPack(player.events, player);
        player.markDisconnected();

        player.onUpdate();

        assertTrue(player.events.isEmpty());
        assertEquals(0, player.pack.onUpdateCalls);
    }

    @Test
    void onlineNextGenBotIsDamageable() {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);

        assertFalse(player.isEntityInvulnerable());
    }

    @Test
    void nextGenBotCanAttackPlayerTargets() {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);
        TrackingForgePlayer target = allocate(TrackingForgePlayer.class);

        assertTrue(player.canAttackPlayer(target));
    }

    @Test
    void attackTemporarilyClearsCreativeDamageFlag() {
        TrackingForgePlayer player = allocate(TrackingForgePlayer.class);
        setField(net.minecraft.entity.player.EntityPlayer.class, player, "capabilities", new PlayerCapabilities());
        player.capabilities.disableDamage = true;
        player.vanillaAttackResult = true;

        assertTrue(player.attackEntityFrom(DamageSource.generic, 2.0F));
        assertEquals(1, player.vanillaAttackCalls);
        assertFalse(player.disableDamageSeenByVanilla);
        assertTrue(player.capabilities.disableDamage);
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

    private static final class TrackingForgePlayer extends GTstaffForgePlayer {

        private List<String> events;
        private TrackingActionPack pack;
        private int respawnCalls;
        private boolean vanillaAttackResult;
        private int vanillaAttackCalls;
        private boolean disableDamageSeenByVanilla;

        private TrackingForgePlayer() {
            super(null, null, null);
        }

        @Override
        public PlayerActionPack getActionPack() {
            return pack;
        }

        @Override
        protected void runBaseUpdate() {
            events.add("base");
        }

        @Override
        protected void runRuntimeServicesPhase() {
            events.add("runtime");
        }

        @Override
        protected void runLivingUpdatePhase() {
            events.add("living");
        }

        @Override
        public void respawnFake() {
            respawnCalls++;
            isDead = false;
            deathTime = 0;
            events.add("respawn");
        }

        @Override
        protected boolean attackEntityFromWithVanillaRules(DamageSource source, float amount) {
            vanillaAttackCalls++;
            disableDamageSeenByVanilla = capabilities != null && capabilities.disableDamage;
            return vanillaAttackResult;
        }
    }

    private static final class TrackingActionPack extends PlayerActionPack {

        private final List<String> events;
        private int onUpdateCalls;

        private TrackingActionPack(List<String> events, TrackingForgePlayer player) {
            super(player);
            this.events = events;
        }

        @Override
        public void onUpdate() {
            onUpdateCalls++;
            events.add("action");
        }
    }
}
