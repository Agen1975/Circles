package com.game.cosmicbubbles.game;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class GameEngineTest {
    private static final int S = GameConfig.SIZE;

    private static int at(int r, int c) {
        return r * S + c;
    }

    private static Piece n(int color, int corner) {
        return Piece.normal(color, corner);
    }

    /** Custom game: no spawns unless asked, preset board, no specials. */
    private static GameEngine engine(Piece[] preset, int spawn, Goal goal, int moveLimit, boolean zen) {
        GameConfig cfg = new GameConfig(GameConfig.Mode.CLASSIC, 1, spawn, 3, 3, 0, moveLimit, 0,
                false, zen, 2, preset, goal, 0);
        return new GameEngine(cfg);
    }

    /** Circle at block (r,c) missing its BR piece. */
    private static Piece[] almostCircle(int r, int c, int color) {
        Piece[] p = new Piece[S * S];
        p[at(r, c)] = n(color, Piece.TL);
        p[at(r, c + 1)] = n(color, Piece.TR);
        p[at(r + 1, c)] = n(color, Piece.BL);
        return p;
    }

    // ---------------------------------------------------------------- detection

    @Test
    public void findsSimpleCircleOnce() {
        Piece[] p = almostCircle(2, 2, 0);
        p[at(3, 3)] = n(0, Piece.BR);
        List<Circle> found = CircleFinder.find(p, S);
        assertEquals(1, found.size());
        assertFalse(found.get(0).extension);
        assertEquals(0, found.get(0).color);
    }

    @Test
    public void wrongOrientationOrColourIsNotACircle() {
        Piece[] p = almostCircle(2, 2, 0);
        p[at(3, 3)] = n(0, Piece.TL);
        assertTrue(CircleFinder.find(p, S).isEmpty());
        p[at(3, 3)] = n(1, Piece.BR);
        assertTrue(CircleFinder.find(p, S).isEmpty());
    }

    @Test
    public void rainbowIsWildcardColour() {
        Piece[] p = almostCircle(0, 0, 2);
        p[at(1, 1)] = Piece.rainbow(Piece.BR);
        List<Circle> found = CircleFinder.find(p, S);
        assertEquals(1, found.size());
        assertEquals(2, found.get(0).color);
    }

    @Test
    public void clusterExtensionCountsOnce() {
        Piece[] p = almostCircle(2, 2, 1);
        p[at(3, 3)] = n(1, Piece.BR);
        p[at(2, 4)] = n(1, Piece.TR);
        p[at(3, 4)] = n(1, Piece.BR);
        List<Circle> found = CircleFinder.find(p, S);
        assertEquals(2, found.size());
        assertTrue(found.get(1).extension);
    }

    @Test
    public void twoSeparateCirclesSideBySideAreTwoNotThree() {
        Piece[] p = almostCircle(0, 0, 0);
        p[at(1, 1)] = n(0, Piece.BR);
        p[at(0, 2)] = n(0, Piece.TL);
        p[at(0, 3)] = n(0, Piece.TR);
        p[at(1, 2)] = n(0, Piece.BL);
        p[at(1, 3)] = n(0, Piece.BR);
        assertEquals(2, CircleFinder.find(p, S).size());
    }

    @Test
    public void extensionNeedsSameColour() {
        Piece[] p = almostCircle(2, 2, 1);
        p[at(3, 3)] = n(1, Piece.BR);
        p[at(2, 4)] = n(0, Piece.TR);
        p[at(3, 4)] = n(0, Piece.BR);
        assertEquals(1, CircleFinder.find(p, S).size());
    }

    // ---------------------------------------------------------------- moves and scoring

    @Test
    public void scoringMoveClearsAndSkipsSpawn() {
        Piece[] p = almostCircle(2, 2, 0);
        p[at(7, 7)] = n(0, Piece.BR);
        p[at(6, 0)] = n(1, Piece.TL);
        GameEngine e = engine(p, 3, null, 0, false);
        MoveResult r = e.move(at(7, 7), at(3, 3));
        assertTrue(r.valid);
        assertEquals(1, r.circles.size());
        assertEquals(4, r.cleared.size());
        assertTrue(r.skippedSpawn);
        assertTrue(r.spawned.isEmpty());
        assertEquals(10, r.points);
        assertEquals(10, e.state().score);
        assertEquals(1, e.state().pieceCount());
    }

    @Test
    public void nonScoringMoveSpawnsAndResetsStreak() {
        Piece[] p = new Piece[S * S];
        p[0] = n(0, Piece.TL);
        GameEngine e = engine(p, 3, null, 0, false);
        e.state().streak = 3;
        MoveResult r = e.move(0, 10);
        assertTrue(r.valid);
        assertEquals(3, r.spawned.size());
        assertEquals(0, e.state().streak);
        assertEquals(4, e.state().pieceCount() + r.luckyCleared.size());
    }

    @Test
    public void spawnLandsOnMarkedCells() {
        Piece[] p = new Piece[S * S];
        p[0] = n(0, Piece.TL);
        GameEngine e = engine(p, 3, null, 0, false);
        List<Integer> targets = e.state().previewTargets;
        int t0 = targets.get(0), t1 = targets.get(1), t2 = targets.get(2);
        int dest = 63;
        while (dest == t0 || dest == t1 || dest == t2) dest--;
        MoveResult r = e.move(0, dest);
        assertEquals(t0, r.spawned.get(0)[1]);
        assertEquals(t1, r.spawned.get(1)[1]);
        assertEquals(t2, r.spawned.get(2)[1]);
    }

    @Test
    public void streakMultipliesPoints() {
        Piece[] p = almostCircle(0, 0, 0);
        p[at(7, 7)] = n(0, Piece.BR);
        p[at(4, 4)] = n(1, Piece.TL);
        p[at(4, 5)] = n(1, Piece.TR);
        p[at(5, 4)] = n(1, Piece.BL);
        p[at(7, 0)] = n(1, Piece.BR);
        p[at(7, 3)] = n(2, Piece.TL); // keeps the board from being fully cleared
        GameEngine e = engine(p, 3, null, 0, false);
        assertEquals(10, e.move(at(7, 7), at(1, 1)).points);
        MoveResult second = e.move(at(7, 0), at(5, 5));
        assertEquals(2, second.multiplier);
        assertEquals(20, second.points);
    }

    @Test
    public void clusterMoveScoresTriangularAndEarnsRotation() {
        GameEngine e = new GameEngine(Levels.tutorial(2));
        int before = e.state().rotateTokens;
        MoveResult r = e.move(at(6, 6), at(3, 3));
        assertEquals(2, r.circles.size());
        assertTrue(r.boardCleared);
        assertEquals(30 + GameEngine.BOARD_CLEAR_BONUS, r.points);
        assertEquals(before + 1, e.state().rotateTokens);
        assertTrue(r.won);
    }

    @Test
    public void invalidMovesAreRejectedWithoutSideEffects() {
        Piece[] p = new Piece[S * S];
        p[0] = n(0, Piece.TL);
        p[1] = n(0, Piece.TR);
        GameEngine e = engine(p, 3, null, 0, false);
        assertFalse(e.move(0, 1).valid);   // occupied
        assertFalse(e.move(5, 6).valid);   // empty source
        assertFalse(e.move(0, 0).valid);   // same cell
        assertFalse(e.move(GameEngine.HOLD, 9).valid); // nothing held
        assertEquals(0, e.state().moves);
        assertFalse(e.canUndo());
    }

    @Test
    public void scoringMoveOnNearlyFullBoardIsNotGameOver() {
        // Fill with TL pieces (never form circles), leave a gap at BR of a circle.
        Piece[] p = new Piece[S * S];
        for (int i = 0; i < p.length; i++) p[i] = n(i % 2, Piece.TL);
        p[at(0, 1)] = n(0, Piece.TR);
        p[at(1, 0)] = n(0, Piece.BL);
        p[at(0, 0)] = n(0, Piece.TL);
        p[at(1, 1)] = null;
        p[at(7, 7)] = n(0, Piece.BR);
        GameEngine e = engine(p, 3, null, 0, false);
        MoveResult r = e.move(at(7, 7), at(1, 1));
        assertEquals(1, r.circles.size());
        assertFalse(r.gameOver);
    }

    @Test
    public void fullBoardAfterSpawnEndsGame() {
        Piece[] p = new Piece[S * S];
        for (int i = 0; i < p.length; i++) p[i] = n(i % 2, Piece.TL);
        p[0] = null;
        p[1] = null;
        GameEngine e = engine(p, 3, null, 0, false);
        MoveResult r = e.move(2, 0);
        assertTrue(r.gameOver);
        assertTrue(e.state().over);
    }

    @Test
    public void zenThinsOutInsteadOfEnding() {
        Piece[] p = new Piece[S * S];
        for (int i = 0; i < p.length; i++) p[i] = n(i % 2, Piece.TL);
        p[0] = null;
        p[1] = null;
        GameEngine e = engine(p, 3, null, 0, true);
        MoveResult r = e.move(2, 0);
        assertFalse(r.gameOver);
        assertFalse(r.zenCleared.isEmpty());
        assertTrue(e.state().emptyCount() > 0);
    }

    @Test
    public void holdSlotSwapsAndReturns() {
        Piece[] p = new Piece[S * S];
        Piece a = n(0, Piece.TL), b = n(1, Piece.BR);
        p[0] = a;
        p[10] = b;
        GameEngine e = engine(p, 0, null, 0, false);
        assertTrue(e.move(0, GameEngine.HOLD).valid);
        assertEquals(a, e.state().hold);
        assertNull(e.state().cells[0]);
        assertTrue(e.move(10, GameEngine.HOLD).valid); // swap: a goes back to cell 10
        assertEquals(b, e.state().hold);
        assertEquals(a, e.state().cells[10]);
        assertTrue(e.move(GameEngine.HOLD, 20).valid);
        assertNull(e.state().hold);
        assertEquals(b, e.state().cells[20]);
    }

    @Test
    public void undoRestoresEverythingIncludingRng() {
        Piece[] p = new Piece[S * S];
        p[0] = n(0, Piece.TL);
        GameEngine e = engine(p, 3, null, 0, false);
        String before = e.state().encode();
        e.move(0, 30);
        String afterFirst = e.state().encode();
        assertTrue(e.undo());
        assertEquals(before, e.state().encode());
        e.move(0, 30);
        assertEquals(afterFirst, e.state().encode()); // same random spawns again
    }

    @Test
    public void rotationSpendsTokenAndCanScore() {
        GameEngine e = new GameEngine(Levels.tutorial(3));
        assertEquals(2, e.state().rotateTokens);
        MoveResult r1 = e.rotate(at(4, 4));
        assertTrue(r1.valid);
        assertTrue(r1.circles.isEmpty());
        MoveResult r2 = e.rotate(at(4, 4));
        assertEquals(1, r2.circles.size());
        assertEquals(0, e.state().rotateTokens);
        assertEquals(0, e.state().moves);
        assertFalse(e.rotate(at(4, 4)).valid);
    }

    @Test
    public void bombClearsThreeByThree() {
        Piece[] p = new Piece[S * S];
        for (int r = 2; r <= 4; r++) for (int c = 2; c <= 4; c++) p[at(r, c)] = n(0, Piece.TL);
        p[at(3, 3)] = null;
        p[at(7, 7)] = Piece.bomb();
        GameEngine e = engine(p, 3, null, 0, false);
        MoveResult r = e.move(at(7, 7), at(3, 3));
        assertTrue(r.bomb);
        assertEquals(9, r.cleared.size());
        assertEquals(27, r.points);
        assertTrue(r.skippedSpawn);
        assertEquals(0, e.state().pieceCount() - r.spawned.size());
    }

    @Test
    public void boardClearGivesBonusAndRefills() {
        Piece[] p = almostCircle(0, 0, 0);
        p[at(5, 5)] = n(0, Piece.BR);
        GameEngine e = engine(p, 3, null, 0, false);
        MoveResult r = e.move(at(5, 5), at(1, 1));
        assertTrue(r.boardCleared);
        assertEquals(10 + GameEngine.BOARD_CLEAR_BONUS, r.points);
        assertEquals(3, r.spawned.size()); // otherwise no legal move would remain
    }

    @Test
    public void hintFindsCompletingMove() {
        Piece[] p = almostCircle(2, 2, 0);
        p[at(7, 0)] = n(1, Piece.BR); // wrong colour
        p[at(6, 6)] = n(0, Piece.BR);
        GameEngine e = engine(p, 3, null, 0, false);
        int[] h = e.hint();
        assertNotNull(h);
        assertEquals(at(6, 6), h[0]);
        assertEquals(at(3, 3), h[1]);
    }

    @Test
    public void moveLimitEndsGameAndGoalWins() {
        Piece[] p = almostCircle(0, 0, 0);
        p[at(7, 7)] = n(0, Piece.BR);
        p[at(7, 0)] = n(1, Piece.TL);
        GameEngine won = engine(p.clone(), 3, new Goal(Goal.CIRCLES, 1, 0), 10, false);
        assertTrue(won.move(at(7, 7), at(1, 1)).won);
        assertEquals(3, won.stars());

        GameEngine lost = engine(p.clone(), 0, null, 1, false);
        MoveResult r = lost.move(at(7, 0), at(6, 0));
        assertTrue(r.gameOver);
        assertFalse(r.won);
    }

    @Test
    public void sameSeedSameGame() {
        GameEngine a = new GameEngine(GameConfig.daily(20261117));
        GameEngine b = new GameEngine(GameConfig.daily(20261117));
        assertEquals(a.state().encode(), b.state().encode());
        assertTrue(a.state().pieceCount() > 0);
        for (int k = 0; k < 5; k++) {
            int from = -1, to = -1;
            Piece[] cells = a.state().cells;
            for (int i = 0; i < cells.length && from < 0; i++) if (cells[i] != null) from = i;
            for (int i = cells.length - 1; i >= 0 && to < 0; i--) if (cells[i] == null) to = i;
            assertTrue(a.move(from, to).valid);
            assertTrue(b.move(from, to).valid);
        }
        assertEquals(a.state().encode(), b.state().encode());
    }

    @Test
    public void encodeDecodeRoundTrip() {
        GameEngine e = new GameEngine(GameConfig.classic(99));
        Piece[] cells = e.state().cells;
        e.state().cells[5] = Piece.rainbow(Piece.BL);
        e.state().cells[6] = Piece.bomb();
        e.state().hold = n(1, Piece.TR);
        e.move(5, 40);
        String s = e.state().encode();
        GameState d = GameState.decode(s, S);
        assertNotNull(d);
        assertEquals(s, d.encode());
        GameEngine resumed = new GameEngine(GameConfig.classic(99), d);
        GameEngine copy = new GameEngine(GameConfig.classic(99), GameState.decode(s, S));
        resumed.move(6, 41);
        copy.move(6, 41);
        assertEquals(resumed.state().encode(), copy.state().encode());
        assertNull(GameState.decode("garbage", S));
        assertNotNull(cells);
    }

    @Test
    public void colourRampAddsThirdColour() {
        GameConfig c = GameConfig.classic(1);
        assertEquals(2, c.colorsFor(0));
        assertEquals(3, c.colorsFor(100));
    }

    @Test
    public void bagGivesEveryTypeBeforeRepeating() {
        GameConfig cfg = new GameConfig(GameConfig.Mode.CLASSIC, 5, 8, 2, 2, 0, 0, 0, false, false, 0,
                null, null, 0);
        GameEngine e = new GameEngine(cfg);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Piece p : e.state().preview) seen.add(p.color * 4 + p.corner);
        assertEquals(8, seen.size());
    }

    @Test
    public void journeyLevelsAreValid() {
        for (int n = 1; n <= Levels.JOURNEY_COUNT; n++) {
            GameConfig cfg = Levels.journey(n);
            assertNotNull(cfg.goal);
            assertTrue(cfg.moveLimit > 0);
            GameEngine e = new GameEngine(cfg);
            assertTrue(CircleFinder.find(e.state().cells, S).isEmpty());
            assertTrue(e.state().emptyCount() > 30);
        }
    }

    @Test
    public void tutorialStepsAreSolvable() {
        GameEngine t1 = new GameEngine(Levels.tutorial(1));
        assertNotNull(t1.hint());
        GameEngine t4 = new GameEngine(Levels.tutorial(4));
        int[] h = t4.hint();
        assertNotNull(h);
        assertTrue(t4.move(h[0], h[1]).won);
    }
}
