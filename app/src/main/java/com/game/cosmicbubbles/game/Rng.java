package com.game.cosmicbubbles.game;

/** Small deterministic RNG whose whole state is one long, so it can be saved and undone. */
public final class Rng {
    long state;

    public Rng(long seed) {
        long s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        state = s == 0 ? 1 : s;
        next(2); // warm up
    }

    Rng(long rawState, boolean raw) {
        state = rawState == 0 ? 1 : rawState;
    }

    /** Uniform-ish integer in [0, n). */
    public int next(int n) {
        long x = state;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state = x;
        return (int) ((x >>> 1) % n);
    }
}
