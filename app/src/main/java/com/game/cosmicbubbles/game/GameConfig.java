package com.game.cosmicbubbles.game;

/** Rules for one game. Fixed for the game's lifetime. */
public final class GameConfig {
    public enum Mode { CLASSIC, DAILY, ZEN, BLITZ, JOURNEY, TUTORIAL }

    public static final int SIZE = 8;

    public final Mode mode;
    public final long seed;
    public final int spawnCount;
    public final int colorsStart;
    public final int colorsMax;
    /** Score at which one more colour is added; 0 = never. */
    public final int colorRampScore;
    /** 0 = unlimited. */
    public final int moveLimit;
    /** 0 = untimed. */
    public final int timeLimitSec;
    public final boolean specials;
    /** Zen: a full board is thinned out instead of ending the game. */
    public final boolean zen;
    public final int startRotateTokens;
    /** Starting pieces, or null for an empty board. */
    public final Piece[] preset;
    public final Goal goal;
    /** Journey level number or tutorial step; 0 otherwise. */
    public final int number;

    public GameConfig(Mode mode, long seed, int spawnCount, int colorsStart, int colorsMax, int colorRampScore,
                      int moveLimit, int timeLimitSec, boolean specials, boolean zen, int startRotateTokens,
                      Piece[] preset, Goal goal, int number) {
        this.mode = mode;
        this.seed = seed;
        this.spawnCount = spawnCount;
        this.colorsStart = colorsStart;
        this.colorsMax = colorsMax;
        this.colorRampScore = colorRampScore;
        this.moveLimit = moveLimit;
        this.timeLimitSec = timeLimitSec;
        this.specials = specials;
        this.zen = zen;
        this.startRotateTokens = startRotateTokens;
        this.preset = preset;
        this.goal = goal;
        this.number = number;
    }

    /** Endless game: 2 colours, a third from 100 points. */
    public static GameConfig classic(long seed) {
        return new GameConfig(Mode.CLASSIC, seed, 3, 2, 3, 100, 0, 0, true, false, 3, null, null, 0);
    }

    /** Same board for everyone on a given day; 30 moves. {@code yyyymmdd} e.g. 20261117. */
    public static GameConfig daily(int yyyymmdd) {
        return new GameConfig(Mode.DAILY, yyyymmdd, 3, 3, 3, 0, 30, 0, true, false, 2, null, null, yyyymmdd);
    }

    /** Relaxed: 2 pieces per turn, never ends. */
    public static GameConfig zen(long seed) {
        return new GameConfig(Mode.ZEN, seed, 2, 3, 3, 0, 0, 0, false, true, 3, null, null, 0);
    }

    public static GameConfig blitz(long seed, int seconds) {
        return new GameConfig(Mode.BLITZ, seed, 3, 2, 3, 60, 0, seconds, true, false, 2, null, null, 0);
    }

    public int colorsFor(int score) {
        int n = colorsStart + (colorRampScore > 0 && score >= colorRampScore ? 1 : 0);
        return Math.min(n, colorsMax);
    }

    /** Modes whose unfinished game is kept when the app closes. */
    public boolean resumable() {
        return mode == Mode.CLASSIC || mode == Mode.ZEN || mode == Mode.DAILY;
    }
}
