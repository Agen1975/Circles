package com.game.cosmicbubbles;

public class Board {
    public static final int SIZE = 8;
    public Cell[][] cells = new Cell[SIZE][SIZE];

    public Board() {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                cells[x][y] = new Cell();
            }
        }
    }
}
