package com.game.cosmicbubbles.game;

/**
 * One quarter-circle segment. Immutable.
 * A circle is made of four segments in a 2x2 block: TL, TR, BL, BR, each facing the block centre.
 */
public final class Piece {
    public static final int NORMAL = 0;
    public static final int RAINBOW = 1; // matches any colour
    public static final int BOMB = 2;    // clears a 3x3 area where it is dropped

    /** Corners in clockwise order, so rotating clockwise is (corner + 1) % 4. */
    public static final int TL = 0, TR = 1, BR = 2, BL = 3;

    /** 0..3 for normal pieces, -1 for rainbow and bomb. */
    public final int color;
    public final int corner;
    public final int kind;

    public Piece(int color, int corner, int kind) {
        this.color = kind == NORMAL ? color : -1;
        this.corner = corner;
        this.kind = kind;
    }

    public static Piece normal(int color, int corner) {
        return new Piece(color, corner, NORMAL);
    }

    public static Piece rainbow(int corner) {
        return new Piece(-1, corner, RAINBOW);
    }

    public static Piece bomb() {
        return new Piece(-1, TL, BOMB);
    }

    public Piece rotated() {
        return new Piece(color, (corner + 1) % 4, kind);
    }

    public boolean isBomb() {
        return kind == BOMB;
    }

    public boolean isRainbow() {
        return kind == RAINBOW;
    }

    int encode() {
        return kind * 100 + (color + 1) * 10 + corner;
    }

    static Piece decode(int v) {
        return new Piece((v / 10) % 10 - 1, v % 10, v / 100);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Piece)) return false;
        Piece p = (Piece) o;
        return p.color == color && p.corner == corner && p.kind == kind;
    }

    @Override
    public int hashCode() {
        return encode();
    }

    @Override
    public String toString() {
        return kind == BOMB ? "Bomb" : (kind == RAINBOW ? "Rainbow" : "C" + color) + "/" + corner;
    }
}
