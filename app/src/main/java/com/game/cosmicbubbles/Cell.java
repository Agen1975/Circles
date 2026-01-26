package com.game.cosmicbubbles;

public class Cell {
    public Piece piece;

    public boolean isEmpty() {
        return piece == null;
    }

    public void clear() {
        piece = null;
    }
}
