package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class FakePlayerMovementUpdateTest {

    @Test
    void runLivingUpdateExecutesProvidedMovementHookOnce() {
        AtomicInteger executions = new AtomicInteger();

        FakePlayer.runLivingUpdate(executions::incrementAndGet);

        assertEquals(1, executions.get());
    }

    @Test
    void runLivingUpdateDoesNotSwallowFailures() {
        IllegalStateException exception = new IllegalStateException("unexpected");

        assertThrows(IllegalStateException.class, () -> FakePlayer.runLivingUpdate(() -> {
            throw exception;
        }));
    }
}
