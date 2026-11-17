package com.game.cosmicbubbles.game;

import java.util.ArrayList;
import java.util.List;

/** What a move did, so the view can animate and play feedback. */
public final class MoveResult {
    public boolean valid;
    public boolean rotation;

    /** Circles made by the player's move (including cluster extensions). */
    public final List<Circle> circles = new ArrayList<>();
    /** Cells cleared by the player's move, with the piece that was there. */
    public final List<Cleared> cleared = new ArrayList<>();
    public int points;
    /** Streak after this move (multiplier = min(streak, 5)). */
    public int streak;
    public int multiplier = 1;
    public boolean bomb;
    public int bombCell = -1;
    public boolean boardCleared;
    public int rotateTokensEarned;

    /** True when the move scored, so no new pieces fell. */
    public boolean skippedSpawn;
    /** {previewIndex, cell} for each piece that fell. */
    public final List<int[]> spawned = new ArrayList<>();
    /** Circles completed by falling pieces. */
    public final List<Circle> luckyCircles = new ArrayList<>();
    public final List<Cleared> luckyCleared = new ArrayList<>();
    public int luckyPoints;

    /** Zen: cells thinned out when the board filled. */
    public final List<Cleared> zenCleared = new ArrayList<>();

    public boolean gameOver;
    public boolean won;

    public static final class Cleared {
        public final int cell;
        public final Piece piece;

        public Cleared(int cell, Piece piece) {
            this.cell = cell;
            this.piece = piece;
        }
    }

    static MoveResult invalid() {
        return new MoveResult();
    }

    public int totalPoints() {
        return points + luckyPoints;
    }
}
