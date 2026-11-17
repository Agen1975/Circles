package com.game.cosmicbubbles.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Game rules. Pure Java (no Android), deterministic for a given seed.
 *
 * A turn: the player moves a segment to an empty cell (or to/from the hold slot).
 * If that completes circles they clear and score, and no new segments fall.
 * Otherwise the streak resets and the previewed segments fall onto their marked cells.
 */
public final class GameEngine {
    /** Pseudo cell index for the hold slot. */
    public static final int HOLD = -1;
    public static final int BASE_POINTS = 10;
    public static final int MAX_MULTIPLIER = 5;
    public static final int BOARD_CLEAR_BONUS = 100;
    public static final int BOMB_POINTS_PER_CELL = 3;
    private static final int UNDO_DEPTH = 30;
    private static final int ZEN_THIN_OUT = 16;

    public final GameConfig cfg;
    public final int size;
    private GameState st;
    private Rng rng;
    private final ArrayDeque<GameState> history = new ArrayDeque<>();

    public GameEngine(GameConfig cfg) {
        this.cfg = cfg;
        this.size = GameConfig.SIZE;
        st = new GameState(size);
        rng = new Rng(cfg.seed);
        st.rotateTokens = cfg.startRotateTokens;
        if (cfg.preset != null) st.cells = cfg.preset.clone();
        refillPreview();
        if (cfg.preset == null) {
            // Start with two waves on the board so there is something to move.
            for (int k = 0; k < 2; k++) spawn(new MoveResult());
            st.score = 0;
            st.circlesTotal = 0;
            java.util.Arrays.fill(st.circlesByColor, 0);
            retarget();
        }
        st.rng = rng.state;
    }

    /** Resume a saved game. */
    public GameEngine(GameConfig cfg, GameState saved) {
        this.cfg = cfg;
        this.size = GameConfig.SIZE;
        st = saved;
        rng = new Rng(saved.rng, true);
    }

    public GameState state() {
        return st;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public int colorsNow() {
        return cfg.colorsFor(st.score);
    }

    // ------------------------------------------------------------------ moves

    /**
     * Move a segment. {@code from}/{@code to} are cell indices or {@link #HOLD}.
     * Moving onto the hold slot swaps with whatever is held.
     */
    public MoveResult move(int from, int to) {
        if (st.over || from == to) return MoveResult.invalid();
        Piece[] cells = st.cells;
        if (from == HOLD) {
            if (st.hold == null || !inBoard(to) || cells[to] != null) return MoveResult.invalid();
        } else {
            if (!inBoard(from) || cells[from] == null) return MoveResult.invalid();
            if (to != HOLD && (!inBoard(to) || cells[to] != null)) return MoveResult.invalid();
        }

        pushHistory();
        MoveResult res = new MoveResult();
        res.valid = true;

        if (to == HOLD) {
            Piece prev = st.hold;
            st.hold = cells[from];
            cells[from] = prev;
        } else if (from == HOLD) {
            cells[to] = st.hold;
            st.hold = null;
        } else {
            cells[to] = cells[from];
            cells[from] = null;
        }
        st.moves++;

        boolean scored;
        if (to != HOLD && cells[to].isBomb()) {
            scored = explode(to, res);
        } else {
            scored = clearCircles(res);
        }

        if (scored) {
            res.skippedSpawn = true;
        } else {
            st.streak = 0;
            spawn(res);
        }
        // A cleared board with nothing held would leave no legal move.
        if (st.pieceCount() == 0 && st.hold == null && !st.preview.isEmpty()) spawn(res);
        finishTurn(res);
        return res;
    }

    /** Rotate a segment clockwise, spending a rotation token. Does not count as a move. */
    public MoveResult rotate(int cell) {
        if (st.over || st.rotateTokens <= 0 || !inBoard(cell)) return MoveResult.invalid();
        Piece p = st.cells[cell];
        if (p == null || p.isBomb()) return MoveResult.invalid();
        pushHistory();
        MoveResult res = new MoveResult();
        res.valid = true;
        res.rotation = true;
        st.rotateTokens--;
        st.cells[cell] = p.rotated();
        if (clearCircles(res)) res.skippedSpawn = true;
        if (st.pieceCount() == 0 && st.hold == null && !st.preview.isEmpty()) spawn(res);
        finishTurn(res);
        return res;
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        st = history.pop();
        rng = new Rng(st.rng, true);
        return true;
    }

    /** Blitz timer ran out. */
    public void timeUp() {
        st.over = true;
    }

    public int[] hint() {
        return CircleFinder.findHint(st.cells, st.hold, size);
    }

    public int movesLeft() {
        return cfg.moveLimit == 0 ? -1 : Math.max(0, cfg.moveLimit - st.moves);
    }

    /** Journey stars: 0 if not won, then 1-3 by share of moves left. */
    public int stars() {
        if (!st.won) return 0;
        if (cfg.moveLimit == 0) return 3;
        float left = (cfg.moveLimit - st.moves) / (float) cfg.moveLimit;
        return left >= 0.35f ? 3 : left >= 0.15f ? 2 : 1;
    }

    // ------------------------------------------------------------------ internals

    private boolean inBoard(int i) {
        return i >= 0 && i < size * size;
    }

    private void pushHistory() {
        st.rng = rng.state;
        history.push(st.copy());
        while (history.size() > UNDO_DEPTH) history.removeLast();
    }

    /** Clears circles made by the player; returns true if any. */
    private boolean clearCircles(MoveResult res) {
        List<Circle> circles = CircleFinder.find(st.cells, size);
        if (circles.isEmpty()) return false;
        removeCells(circles, res.cleared);
        res.circles.addAll(circles);
        int n = circles.size();
        st.streak++;
        st.maxStreak = Math.max(st.maxStreak, st.streak);
        res.streak = st.streak;
        res.multiplier = Math.min(st.streak, MAX_MULTIPLIER);
        // 1 circle = 10, 2 in one move = 30, 3 = 60, 4 = 100 ...
        res.points = BASE_POINTS * n * (n + 1) / 2 * res.multiplier;
        countCircles(circles);
        st.bestCluster = Math.max(st.bestCluster, n);
        if (n >= 2) {
            st.rotateTokens++;
            res.rotateTokensEarned = 1;
        }
        if (st.pieceCount() == 0 && cfg.mode != GameConfig.Mode.TUTORIAL) {
            res.boardCleared = true;
            res.points += BOARD_CLEAR_BONUS;
        }
        st.score += res.points;
        return true;
    }

    private boolean explode(int at, MoveResult res) {
        int r0 = at / size, c0 = at % size;
        for (int r = r0 - 1; r <= r0 + 1; r++) {
            for (int c = c0 - 1; c <= c0 + 1; c++) {
                if (r < 0 || c < 0 || r >= size || c >= size) continue;
                int i = r * size + c;
                if (st.cells[i] != null) {
                    res.cleared.add(new MoveResult.Cleared(i, st.cells[i]));
                    st.cells[i] = null;
                }
            }
        }
        res.bomb = true;
        res.bombCell = at;
        st.streak++;
        st.maxStreak = Math.max(st.maxStreak, st.streak);
        res.streak = st.streak;
        res.multiplier = Math.min(st.streak, MAX_MULTIPLIER);
        res.points = BOMB_POINTS_PER_CELL * res.cleared.size() * res.multiplier;
        st.score += res.points;
        return true;
    }

    private void removeCells(List<Circle> circles, List<MoveResult.Cleared> out) {
        for (Circle k : circles) {
            for (int role = 0; role < 4; role++) {
                int i = CircleFinder.cellOf(k.row, k.col, role, size);
                if (st.cells[i] != null) {
                    out.add(new MoveResult.Cleared(i, st.cells[i]));
                    st.cells[i] = null;
                }
            }
        }
    }

    private void countCircles(List<Circle> circles) {
        st.circlesTotal += circles.size();
        for (Circle k : circles) if (k.color >= 0) st.circlesByColor[k.color]++;
    }

    private void spawn(MoveResult res) {
        for (int i = 0; i < st.preview.size(); i++) {
            int target = st.previewTargets.get(i);
            if (target < 0 || st.cells[target] != null) target = randomEmpty(null);
            if (target < 0) break;
            st.cells[target] = st.preview.get(i);
            res.spawned.add(new int[]{i, target});
        }
        refillPreview();

        List<Circle> lucky = CircleFinder.find(st.cells, size);
        if (!lucky.isEmpty()) {
            removeCells(lucky, res.luckyCleared);
            res.luckyCircles.addAll(lucky);
            res.luckyPoints = BASE_POINTS * lucky.size();
            countCircles(lucky);
            st.score += res.luckyPoints;
        }
    }

    private void finishTurn(MoveResult res) {
        retarget();
        if (cfg.goal != null && cfg.goal.met(st)) {
            st.over = true;
            st.won = true;
        } else if (cfg.moveLimit > 0 && st.moves >= cfg.moveLimit) {
            st.over = true;
        } else if (st.emptyCount() == 0) {
            if (cfg.zen) {
                for (int k = 0; k < ZEN_THIN_OUT; k++) {
                    int i = rng.next(st.cells.length);
                    if (st.cells[i] != null) {
                        res.zenCleared.add(new MoveResult.Cleared(i, st.cells[i]));
                        st.cells[i] = null;
                    }
                }
                retarget();
            } else {
                st.over = true;
            }
        }
        res.gameOver = st.over;
        res.won = st.won;
        st.rng = rng.state;
    }

    private void refillPreview() {
        st.preview.clear();
        st.previewTargets.clear();
        for (int i = 0; i < cfg.spawnCount; i++) {
            st.preview.add(nextPiece());
            st.previewTargets.add(-1);
        }
        retarget();
    }

    /** Give every preview piece a distinct empty target cell. */
    private void retarget() {
        List<Integer> taken = new ArrayList<>();
        for (int i = 0; i < st.previewTargets.size(); i++) {
            int t = st.previewTargets.get(i);
            if (t < 0 || st.cells[t] != null || taken.contains(t)) {
                t = randomEmpty(taken);
                st.previewTargets.set(i, t);
            }
            if (t >= 0) taken.add(t);
        }
    }

    private int randomEmpty(List<Integer> exclude) {
        int count = 0;
        for (int i = 0; i < st.cells.length; i++) {
            if (st.cells[i] == null && (exclude == null || !exclude.contains(i))) count++;
        }
        if (count == 0) return -1;
        int k = rng.next(count);
        for (int i = 0; i < st.cells.length; i++) {
            if (st.cells[i] == null && (exclude == null || !exclude.contains(i))) {
                if (k-- == 0) return i;
            }
        }
        return -1;
    }

    private Piece nextPiece() {
        if (cfg.specials) {
            int roll = rng.next(1000);
            if (st.score >= 150 && roll < 15) return Piece.bomb();
            if (st.score >= 60 && roll < 50) return Piece.rainbow(rng.next(4));
        }
        int colors = colorsNow();
        if (st.bagColors != colors || st.bagPos >= st.bag.length) {
            // Bag randomizer: every colour/corner combination once, shuffled.
            st.bag = new int[colors * 4];
            for (int i = 0; i < st.bag.length; i++) st.bag[i] = i;
            for (int i = st.bag.length - 1; i > 0; i--) {
                int j = rng.next(i + 1);
                int t = st.bag[i];
                st.bag[i] = st.bag[j];
                st.bag[j] = t;
            }
            st.bagPos = 0;
            st.bagColors = colors;
        }
        int v = st.bag[st.bagPos++];
        return Piece.normal(v / 4, v % 4);
    }
}
