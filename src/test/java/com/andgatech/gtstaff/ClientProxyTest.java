package com.andgatech.gtstaff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ClientProxyTest {

    @Test
    void registerFactoryIfMissingRegistersWhenFactoryIsAbsent() {
        AtomicInteger registrations = new AtomicInteger();

        ClientProxy.registerFactoryIfMissing(() -> false, registrations::incrementAndGet);

        assertEquals(1, registrations.get());
    }

    @Test
    void registerFactoryIfMissingSkipsDuplicateRegistrations() {
        AtomicInteger registrations = new AtomicInteger();

        ClientProxy.registerFactoryIfMissing(() -> true, registrations::incrementAndGet);

        assertEquals(0, registrations.get());
    }
}
