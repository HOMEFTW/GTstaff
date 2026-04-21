package com.andgatech.gtstaff.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.runtime.GTstaffForgePlayer;

class ServerUtilitiesCompatTest {

    @Test
    void detectsGtstaffFakePlayersAsServerUtilitiesFakePlayers() {
        assertTrue(invokeCompatCheck(allocate(TestFakePlayer.class)));
        assertTrue(invokeCompatCheck(allocate(TestNextGenFakePlayer.class)));
        assertFalse(invokeCompatCheck(allocate(TestRealPlayer.class)));
        assertFalse(invokeCompatCheck(null));
    }

    @Test
    void registersServerUtilitiesMixinInMixinConfig() throws IOException {
        try (InputStream input = ServerUtilitiesCompatTest.class.getClassLoader()
            .getResourceAsStream("mixins.gtstaff.json")) {
            if (input == null) {
                throw new AssertionError("Unable to locate mixins.gtstaff.json");
            }

            String json = new String(readFully(input), StandardCharsets.UTF_8);
            assertTrue(json.contains("ServerUtils_ServerUtilitiesMixin"));
        }
    }

    private static boolean invokeCompatCheck(EntityPlayerMP player) {
        try {
            Class<?> compatClass = Class.forName("com.andgatech.gtstaff.integration.ServerUtilitiesCompat");
            Method method = compatClass.getMethod("isFakePlayer", EntityPlayerMP.class);
            return ((Boolean) method.invoke(null, player)).booleanValue();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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

    private static final class TestFakePlayer extends FakePlayer {

        private TestFakePlayer() {
            super(null, null, "test-fake");
        }
    }

    private static final class TestRealPlayer extends EntityPlayerMP {

        private TestRealPlayer() {
            super(null, null, null, null);
        }
    }

    private static final class TestNextGenFakePlayer extends GTstaffForgePlayer {

        private TestNextGenFakePlayer() {
            super(null, null, null);
        }
    }
}
