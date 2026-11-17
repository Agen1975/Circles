package com.game.cosmicbubbles.game;

/** Journey levels (generated from the level number) and the tutorial boards. */
public final class Levels {
    public static final int JOURNEY_COUNT = 60;
    public static final int TUTORIAL_STEPS = 4;

    private Levels() {
    }

    /**
     * Level n (1-based). Difficulty rises with n: more colours, prefilled clutter, tighter goals.
     * Targets are first guesses and should be tuned from playtests.
     */
    public static GameConfig journey(int n) {
        int colors = n <= 10 ? 2 : n <= 35 ? 3 : 4;
        int moves = 24 + n / 3;
        Goal goal;
        switch (n % 3) {
            case 1:
                goal = new Goal(Goal.CIRCLES, 3 + n / 4, 0);
                break;
            case 2:
                goal = new Goal(Goal.SCORE, 60 + 12 * n, 0);
                break;
            default:
                goal = new Goal(Goal.COLOR_CIRCLES, 2 + n / 6, (n / 3) % colors);
                break;
        }
        long seed = 7919L * n + 17;
        Piece[] preset = null;
        int clutter = n <= 5 ? 0 : Math.min(24, (n - 3) / 2);
        if (clutter > 0) {
            preset = new Piece[GameConfig.SIZE * GameConfig.SIZE];
            Rng r = new Rng(seed ^ 0x5DEECE66DL);
            for (int k = 0; k < clutter; k++) {
                int i = r.next(preset.length);
                preset[i] = Piece.normal(r.next(colors), r.next(4));
            }
            // Never start with a circle already made.
            for (java.util.List<Circle> found = CircleFinder.find(preset, GameConfig.SIZE); !found.isEmpty();
                 found = CircleFinder.find(preset, GameConfig.SIZE)) {
                for (Circle c : found) preset[c.row * GameConfig.SIZE + c.col] = null;
            }
        }
        return new GameConfig(GameConfig.Mode.JOURNEY, seed, 3, colors, colors, 0, moves, 0,
                n >= 8, false, n >= 4 ? 2 : 0, preset, goal, n);
    }

    public static String goalText(Goal g) {
        switch (g.type) {
            case Goal.SCORE: return "Score " + g.target + " points";
            case Goal.CIRCLES: return "Make " + g.target + " circles";
            case Goal.COLOR_CIRCLES: return "Make " + g.target + " circles of this colour";
            default: return "Make " + g.target + " circles in one move";
        }
    }

    /** Tutorial step text, shown above the board. */
    public static String tutorialText(int step) {
        switch (step) {
            case 1: return "Drag the glowing piece into the gap to complete the circle.";
            case 2: return "Circles next to a matching half clear together. Make 2 circles in one move!";
            case 3: return "Double-tap a piece to rotate it. Turn the odd one to finish the circle.";
            default: return "Scoring stops new pieces from falling. Watch the markers, then score!";
        }
    }

    public static GameConfig tutorial(int step) {
        int s = GameConfig.SIZE;
        Piece[] p = new Piece[s * s];
        Goal goal;
        int spawn = 0;
        int rotations = 0;
        switch (step) {
            case 1:
                p[2 * s + 3] = Piece.normal(0, Piece.TL);
                p[2 * s + 4] = Piece.normal(0, Piece.TR);
                p[3 * s + 3] = Piece.normal(0, Piece.BL);
                p[6 * s + 1] = Piece.normal(0, Piece.BR);
                goal = new Goal(Goal.CIRCLES, 1, 0);
                break;
            case 2:
                p[2 * s + 2] = Piece.normal(1, Piece.TL);
                p[2 * s + 3] = Piece.normal(1, Piece.TR);
                p[3 * s + 2] = Piece.normal(1, Piece.BL);
                p[2 * s + 4] = Piece.normal(1, Piece.TR);
                p[3 * s + 4] = Piece.normal(1, Piece.BR);
                p[6 * s + 6] = Piece.normal(1, Piece.BR);
                goal = new Goal(Goal.CLUSTER, 2, 0);
                break;
            case 3:
                p[3 * s + 3] = Piece.normal(2, Piece.TL);
                p[3 * s + 4] = Piece.normal(2, Piece.TR);
                p[4 * s + 3] = Piece.normal(2, Piece.BL);
                p[4 * s + 4] = Piece.normal(2, Piece.TL); // wrong way: rotate twice
                rotations = 2;
                goal = new Goal(Goal.CIRCLES, 1, 0);
                break;
            default:
                p[1 * s + 1] = Piece.normal(0, Piece.TL);
                p[1 * s + 2] = Piece.normal(0, Piece.TR);
                p[2 * s + 1] = Piece.normal(0, Piece.BL);
                p[5 * s + 5] = Piece.normal(0, Piece.BR);
                p[4 * s + 6] = Piece.normal(1, Piece.TL);
                p[6 * s + 2] = Piece.normal(1, Piece.BR);
                spawn = 3;
                goal = new Goal(Goal.CIRCLES, 1, 0);
                break;
        }
        return new GameConfig(GameConfig.Mode.TUTORIAL, 42 + step, spawn, 2, 2, 0, 0, 0, false, false,
                rotations, p, goal, step);
    }
}
