package com.andgatech.gtstaff.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionTest {

    @Test
    void onceRunsExactlyOnce() {
        Action action = Action.once();

        assertTrue(action.tick());
        assertTrue(action.done);
        assertFalse(action.tick());
    }

    @Test
    void continuousRunsEveryTickWithoutFinishing() {
        Action action = Action.continuous();

        assertTrue(action.tick());
        assertTrue(action.tick());
        assertTrue(action.tick());
        assertFalse(action.done);
    }

    @Test
    void intervalThreeFiresImmediatelyAndRepeatsEveryThreeTicks() {
        Action action = Action.interval(3);

        assertTrue(action.tick());
        assertFalse(action.tick());
        assertFalse(action.tick());
        assertTrue(action.tick());
        assertFalse(action.done);
        assertFalse(action.tick());
        assertFalse(action.tick());
        assertTrue(action.tick());
        assertFalse(action.done);
    }
}
