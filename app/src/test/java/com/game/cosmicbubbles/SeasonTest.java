package com.game.cosmicbubbles;

import static org.junit.Assert.*;

import org.junit.Test;

public class SeasonTest {
    private final Season w = Season.WINTER_LIGHTS;

    @Test
    public void winterLightsRunsDec1ToJan6Inclusive() {
        assertFalse(w.activeOn(20261130));
        assertTrue(w.activeOn(20261201));
        assertTrue(w.activeOn(20261225));
        assertTrue(w.activeOn(20261231));
        assertTrue(w.activeOn(20270101));
        assertTrue(w.activeOn(20270106));
        assertFalse(w.activeOn(20270107));
        assertFalse(w.activeOn(20270615));
    }

    @Test
    public void repeatsEveryYear() {
        assertTrue(w.activeOn(20301215));
        assertFalse(w.activeOn(20301115));
    }
}
