package com.game.cosmicbubbles;

import java.util.*;
public class GameLogic {

    public boolean isGameOver = false;
    public Board board = new Board();
    public List<Piece> preview = new ArrayList<>();
    private Random random = new Random();
    public int score = 0;
    public GameLogic() {
        generatePreview();

    }
    public void checkGameOver() {
        int emptyCells = 0;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                if (board.cells[x][y].isEmpty()) {
                    emptyCells++;
                }
            }
        }

        // If the number of empty cells is less than the upcoming pieces, it's game over.
        if (emptyCells == 0) {
            isGameOver = true;
        }
    }

    public void restart() {
        board = new Board(); // Create a fresh board
        score = 0;
        isGameOver = false;
        preview.clear();
        generatePreview();
    }
    private void generatePreview() {
        preview.clear();
        for (int i = 0; i < 3; i++) {
            preview.add(randomPiece());
        }
    }

    private Piece randomPiece() {
        return new Piece(
                random.nextInt(3),
                Piece.Corner.values()[random.nextInt(4)]
        );
    }

    public Cell selectedCell = null;

    public void placePreview(int x, int y) {

        if (!board.cells[x][y].isEmpty()) {
            selectedCell = board.cells[x][y];
            return;
        }
        if (selectedCell != null) {
            board.cells[x][y].piece = selectedCell.piece;
            selectedCell.clear();
            selectedCell = null;
            List<int[]> foundCircles = checkAndMarkCircles(false);
            if(!foundCircles.isEmpty()) {
                return;
            }
        }

        spawnRandom(3);

        if (preview.isEmpty()) {
            generatePreview();
        }
    }

    public void spawnRandom(int count) {
        if (isGameOver) return;
        List<int[]> empty = new ArrayList<>();

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                if (board.cells[x][y].isEmpty()) {
                    empty.add(new int[]{x, y});
                }
            }
        }

        Collections.shuffle(empty);

        for (int i = 0; i < Math.min(count, empty.size()); i++) {
            int[] p = empty.get(i);
            board.cells[p[0]][p[1]].piece = preview.remove(0);

        }
      // generatePreview();
        //checkAndClear();
    }
    public void clearMarkedCircles(List<int[]> completedCircles) {
        if (completedCircles == null || completedCircles.isEmpty()) return;

        for (int[] topLeft : completedCircles) {
            int x = topLeft[0];
            int y = topLeft[1];

            // Clear the 4 cells that form the circle
            board.cells[x][y + 1].clear();      // Top-Left
            board.cells[x + 1][y + 1].clear();  // Top-Right
            board.cells[x][y].clear();          // Bottom-Left
            board.cells[x + 1][y].clear();      // Bottom-Right
            score +=  5;

        }

    }
    public List<int[]> checkAndMarkCircles(boolean checkComposites) {
        List<int[]> completedCircles = new ArrayList<>();

        // We iterate through all possible top-left corners of a 2x2 square
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 7; y++) {

                Cell tl = board.cells[x][y + 1];
                Cell tr = board.cells[x + 1][y + 1];
                Cell bl = board.cells[x][y];
                Cell br = board.cells[x + 1][y];

                if (tl.isEmpty() || tr.isEmpty() ||
                        bl.isEmpty() || br.isEmpty()) continue;

                Piece a = tl.piece;
                Piece b = tr.piece;
                Piece c = bl.piece;
                Piece d = br.piece;

                // Check if the pieces form a complete circle
                if (a.color == b.color &&
                        a.color == c.color &&
                        a.color == d.color &&
                        (a.corner == Piece.Corner.TOP_LEFT  || a.Composite) &&
                        (b.corner == Piece.Corner.TOP_RIGHT || b.Composite) &&
                        (c.corner == Piece.Corner.BOTTOM_LEFT || c.Composite)&&
                        (d.corner == Piece.Corner.BOTTOM_RIGHT || d.Composite)) {

                    // Instead of clearing, we add the location to our list.
                    a.Composite = true;
                    b.Composite = true;
                    c.Composite = true;
                    d.Composite = true;

                    completedCircles.add(new int[]{x, y});
                }
            }
        }
        if(!checkComposites)
             completedCircles.addAll(checkAndMarkCircles(true));
        return completedCircles;
    }
}
