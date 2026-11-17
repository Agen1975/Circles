package com.game.cosmicbubbles.game;

/** Target for journey levels and tutorial steps. */
public final class Goal {
    public static final int SCORE = 0;
    public static final int CIRCLES = 1;
    public static final int COLOR_CIRCLES = 2;
    /** Make {@code target} circles in a single move (tutorial). */
    public static final int CLUSTER = 3;

    public final int type;
    public final int target;
    public final int color;

    public Goal(int type, int target, int color) {
        this.type = type;
        this.target = target;
        this.color = color;
    }

    public int progress(GameState st) {
        switch (type) {
            case SCORE: return st.score;
            case CIRCLES: return st.circlesTotal;
            case COLOR_CIRCLES: return st.circlesByColor[color];
            default: return st.bestCluster;
        }
    }

    public boolean met(GameState st) {
        return progress(st) >= target;
    }
}
