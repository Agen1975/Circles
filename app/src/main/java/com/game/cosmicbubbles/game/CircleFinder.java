package com.game.cosmicbubbles.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds completed circles.
 *
 * Pass 1 finds true circles: four segments of one colour, each in its own corner of a 2x2 block.
 * Pass 2 grows clusters: a 2x2 block that reuses segments of an already completed circle of the
 * same colour also counts, as long as every segment it does not borrow faces the right way.
 * This replaces the old "composite" flag, which re-found the same circles a second time.
 */
public final class CircleFinder {
    private static final int NONE = -2; // no colour constraint failed / not composite

    private CircleFinder() {
    }

    /** Index of the cell playing role {@code role} in the block whose top-left is (r, c). */
    static int cellOf(int r, int c, int role, int size) {
        switch (role) {
            case Piece.TL: return r * size + c;
            case Piece.TR: return r * size + c + 1;
            case Piece.BL: return (r + 1) * size + c;
            default:       return (r + 1) * size + c + 1;
        }
    }

    public static List<Circle> find(Piece[] cells, int size) {
        List<Circle> out = new ArrayList<>();
        int[] comp = new int[cells.length];
        Arrays.fill(comp, NONE);
        boolean[] used = new boolean[cells.length]; // block top-left already counted

        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                int color = blockColor(cells, comp, r, c, size, false);
                if (color != NONE) {
                    out.add(new Circle(r, c, color, false));
                    used[r * size + c] = true;
                }
            }
        }
        // Mark after the first pass so overlapping true circles don't influence each other.
        for (Circle k : out) mark(comp, k, size);

        boolean changed = !out.isEmpty();
        while (changed) {
            changed = false;
            for (int r = 0; r < size - 1; r++) {
                for (int c = 0; c < size - 1; c++) {
                    if (used[r * size + c]) continue;
                    int color = blockColor(cells, comp, r, c, size, true);
                    if (color != NONE) {
                        Circle k = new Circle(r, c, color, true);
                        out.add(k);
                        used[r * size + c] = true;
                        mark(comp, k, size);
                        changed = true;
                    }
                }
            }
        }
        return out;
    }

    private static void mark(int[] comp, Circle k, int size) {
        for (int role = 0; role < 4; role++) {
            int i = cellOf(k.row, k.col, role, size);
            if (comp[i] == NONE) comp[i] = k.color;
        }
    }

    /**
     * Colour of the circle at block (r, c), -1 for an all-rainbow circle, or NONE.
     * With {@code allowComposite}, cells already in a completed circle count regardless of
     * orientation; at least one composite and one fresh cell are required.
     */
    private static int blockColor(Piece[] cells, int[] comp, int r, int c, int size, boolean allowComposite) {
        int color = -1;
        int composites = 0;
        for (int role = 0; role < 4; role++) {
            int i = cellOf(r, c, role, size);
            Piece p = cells[i];
            if (p == null || p.isBomb()) return NONE;
            int cellColor;
            if (allowComposite && comp[i] != NONE) {
                composites++;
                cellColor = comp[i];
            } else {
                if (p.corner != role) return NONE;
                cellColor = p.color; // -1 for rainbow
            }
            if (cellColor != -1) {
                if (color == -1) color = cellColor;
                else if (color != cellColor) return NONE;
            }
        }
        if (allowComposite && (composites == 0 || composites == 4)) return NONE;
        return color;
    }

    /** Hint: a single move that completes a circle. {from, to}; from may be {@link GameEngine#HOLD}. */
    public static int[] findHint(Piece[] cells, Piece hold, int size) {
        for (int to = 0; to < cells.length; to++) {
            if (cells[to] != null) continue;
            int r0 = to / size, c0 = to % size;
            for (int role = 0; role < 4; role++) {
                int r = r0 - ((role == Piece.BL || role == Piece.BR) ? 1 : 0);
                int c = c0 - ((role == Piece.TR || role == Piece.BR) ? 1 : 0);
                if (r < 0 || c < 0 || r >= size - 1 || c >= size - 1) continue;
                int need = -1;
                boolean ok = true;
                int[] others = new int[3];
                int k = 0;
                for (int other = 0; other < 4 && ok; other++) {
                    if (other == role) continue;
                    int i = cellOf(r, c, other, size);
                    others[k++] = i;
                    Piece p = cells[i];
                    if (p == null || p.isBomb() || p.corner != other) ok = false;
                    else if (p.color != -1) {
                        if (need == -1) need = p.color;
                        else if (need != p.color) ok = false;
                    }
                }
                if (!ok) continue;
                if (fits(hold, role, need)) return new int[]{GameEngine.HOLD, to};
                for (int from = 0; from < cells.length; from++) {
                    if (from == others[0] || from == others[1] || from == others[2]) continue;
                    if (fits(cells[from], role, need)) return new int[]{from, to};
                }
            }
        }
        return null;
    }

    private static boolean fits(Piece p, int role, int need) {
        return p != null && !p.isBomb() && p.corner == role
                && (need == -1 || p.color == -1 || p.color == need);
    }
}
