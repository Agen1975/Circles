package com.game.circles;
import java.util.*;
public class GameLogic {


    public Board board = new Board();
    private List<Piece> preview = new ArrayList<>();
    private Random random = new Random();

    public GameLogic() {
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

    Cell selectedCell = null;

    public void placePreview(int x, int y) {
        if (!board.cells[x][y].isEmpty()) {
            selectedCell = board.cells[x][y];
            return;
        }
        if (selectedCell != null) {
            board.cells[x][y].piece = selectedCell.piece;
            selectedCell.clear();
            selectedCell = null;
        }
        //   else
        //       board.cells[x][y].piece = preview.remove(0);


       spawnRandom(3);


        if (preview.isEmpty()) {
            generatePreview();
        }
    }

    public void spawnRandom(int count) {
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
            board.cells[p[0]][p[1]].piece = randomPiece();
        }

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
        }
    }
    public List<int[]> checkAndMarkCircles() {
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
                        a.corner == Piece.Corner.TOP_LEFT &&
                        b.corner == Piece.Corner.TOP_RIGHT &&
                        c.corner == Piece.Corner.BOTTOM_LEFT &&
                        d.corner == Piece.Corner.BOTTOM_RIGHT) {

                    // Instead of clearing, we add the location to our list.
                    completedCircles.add(new int[]{x, y});
                }
            }
        }
        return completedCircles;
    }
}
