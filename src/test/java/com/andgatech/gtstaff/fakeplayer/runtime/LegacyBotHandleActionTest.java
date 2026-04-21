package com.andgatech.gtstaff.fakeplayer.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.andgatech.gtstaff.fakeplayer.FakePlayer;
import com.andgatech.gtstaff.fakeplayer.IFakePlayerHolder;
import com.andgatech.gtstaff.fakeplayer.PlayerActionPack;

class LegacyBotHandleActionTest {

    @Test
    void legacyHandleExposesActionFacade() {
        StubFakePlayer fakePlayer = allocate(StubFakePlayer.class);
        fakePlayer.actionPack = new PlayerActionPack(fakePlayer);

        LegacyBotHandle handle = new LegacyBotHandle(fakePlayer);

        assertNotNull(handle.action());
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

    private static final class StubFakePlayer extends FakePlayer implements IFakePlayerHolder {

        private PlayerActionPack actionPack;

        private StubFakePlayer() {
            super(null, null, "stub");
        }

        @Override
        public PlayerActionPack getActionPack() {
            return actionPack;
        }
    }
}
