package com.game.cosmicbubbles.game;

/** A completed circle: the 2x2 block whose top-left cell is (row, col). */
public final class Circle {
    public final int row, col;
    /** Colour of the circle, or -1 if it is made only of rainbow pieces. */
    public final int color;
    /** True if the circle borrowed segments from a neighbouring completed circle (cluster bonus). */
    public final boolean extension;

    public Circle(int row, int col, int color, boolean extension) {
        this.row = row;
        this.col = col;
        this.color = color;
        this.extension = extension;
    }
}
