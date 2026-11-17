package com.game.cosmicbubbles.game;

import java.util.ArrayList;
import java.util.List;

/** Everything that changes during a game. Copyable for undo, encodable for saving. */
public final class GameState {
    public final int size;
    public Piece[] cells;
    public Piece hold;
    public List<Piece> preview = new ArrayList<>();
    /** Board cell each preview piece is aimed at, or -1. Parallel to {@link #preview}. */
    public List<Integer> previewTargets = new ArrayList<>();
    long rng;
    int[] bag = new int[0];
    int bagPos;
    int bagColors;

    public int score;
    public int streak;
    public int maxStreak;
    public int moves;
    public int circlesTotal;
    public int[] circlesByColor = new int[4];
    public int bestCluster;
    public int rotateTokens;
    public boolean over;
    public boolean won;

    public GameState(int size) {
        this.size = size;
        cells = new Piece[size * size];
    }

    public GameState copy() {
        GameState s = new GameState(size);
        s.cells = cells.clone();
        s.hold = hold;
        s.preview = new ArrayList<>(preview);
        s.previewTargets = new ArrayList<>(previewTargets);
        s.rng = rng;
        s.bag = bag.clone();
        s.bagPos = bagPos;
        s.bagColors = bagColors;
        s.score = score;
        s.streak = streak;
        s.maxStreak = maxStreak;
        s.moves = moves;
        s.circlesTotal = circlesTotal;
        s.circlesByColor = circlesByColor.clone();
        s.bestCluster = bestCluster;
        s.rotateTokens = rotateTokens;
        s.over = over;
        s.won = won;
        return s;
    }

    public int emptyCount() {
        int n = 0;
        for (Piece p : cells) if (p == null) n++;
        return n;
    }

    public int pieceCount() {
        return cells.length - emptyCount();
    }

    // ---- persistence: "v1|cells|hold|preview|targets|rng|bag|bagPos|bagColors|numbers|byColor"

    public String encode() {
        StringBuilder b = new StringBuilder("v1|");
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            b.append(cells[i] == null ? -1 : cells[i].encode());
        }
        b.append('|').append(hold == null ? -1 : hold.encode()).append('|');
        for (int i = 0; i < preview.size(); i++) {
            if (i > 0) b.append(',');
            b.append(preview.get(i).encode());
        }
        b.append('|');
        for (int i = 0; i < previewTargets.size(); i++) {
            if (i > 0) b.append(',');
            b.append(previewTargets.get(i));
        }
        b.append('|').append(rng).append('|');
        for (int i = 0; i < bag.length; i++) {
            if (i > 0) b.append(',');
            b.append(bag[i]);
        }
        b.append('|').append(bagPos).append('|').append(bagColors).append('|')
                .append(score).append(',').append(streak).append(',').append(maxStreak).append(',')
                .append(moves).append(',').append(circlesTotal).append(',').append(bestCluster).append(',')
                .append(rotateTokens).append(',').append(over ? 1 : 0).append(',').append(won ? 1 : 0)
                .append('|');
        for (int i = 0; i < 4; i++) {
            if (i > 0) b.append(',');
            b.append(circlesByColor[i]);
        }
        return b.toString();
    }

    /** Returns null if the text is not a valid save. */
    public static GameState decode(String text, int size) {
        try {
            String[] f = text.split("\\|", -1);
            if (f.length != 11 || !f[0].equals("v1")) return null;
            GameState s = new GameState(size);
            int[] cells = ints(f[1]);
            if (cells.length != size * size) return null;
            for (int i = 0; i < cells.length; i++) s.cells[i] = cells[i] < 0 ? null : Piece.decode(cells[i]);
            int h = Integer.parseInt(f[2]);
            s.hold = h < 0 ? null : Piece.decode(h);
            for (int v : ints(f[3])) s.preview.add(Piece.decode(v));
            for (int v : ints(f[4])) s.previewTargets.add(v);
            if (s.preview.size() != s.previewTargets.size()) return null;
            s.rng = Long.parseLong(f[5]);
            s.bag = ints(f[6]);
            s.bagPos = Integer.parseInt(f[7]);
            s.bagColors = Integer.parseInt(f[8]);
            int[] n = ints(f[9]);
            s.score = n[0];
            s.streak = n[1];
            s.maxStreak = n[2];
            s.moves = n[3];
            s.circlesTotal = n[4];
            s.bestCluster = n[5];
            s.rotateTokens = n[6];
            s.over = n[7] == 1;
            s.won = n[8] == 1;
            s.circlesByColor = ints(f[10]);
            if (s.circlesByColor.length != 4) return null;
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int[] ints(String s) {
        if (s.isEmpty()) return new int[0];
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }
}
