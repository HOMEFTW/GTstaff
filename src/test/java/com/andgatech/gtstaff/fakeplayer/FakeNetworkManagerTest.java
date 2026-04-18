package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class FakeNetworkManagerTest {

    @Test
    void constructorInitializesEmbeddedChannel() {
        assertDoesNotThrow(FakeNetworkManager::new);
    }
}
