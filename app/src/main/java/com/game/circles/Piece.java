package com.game.circles;

public class Piece {
    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
    }

    public int color;
    public Corner corner;

    public boolean Selected  = false;

    public Piece(int color, Corner corner) {
        this.color = color;
        this.corner = corner;
    }

}
