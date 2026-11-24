package com.game.cosmicbubbles;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;

import com.game.cosmicbubbles.game.Circle;
import com.game.cosmicbubbles.game.CircleFinder;
import com.game.cosmicbubbles.game.GameConfig;
import com.game.cosmicbubbles.game.GameEngine;
import com.game.cosmicbubbles.game.GameState;
import com.game.cosmicbubbles.game.Goal;
import com.game.cosmicbubbles.game.Levels;
import com.game.cosmicbubbles.game.MoveResult;
import com.game.cosmicbubbles.game.Piece;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The game screen. Drag a segment (or tap it, then tap a cell) to move it.
 * Everything is drawn on one canvas; the frame loop only runs while something moves.
 */
@SuppressLint("ViewConstructor")
public class GameView extends View {

    /** Navigation callbacks, implemented by {@link GameActivity}. */
    public interface Host {
        void openMenu();

        void startGame(GameConfig cfg, boolean resume);

        void share(String text);

        /** Runs {@code next} after an interstitial if one is due (only called after a finished game). */
        void afterGame(Runnable next);

        boolean rewardedReady();

        /** Shows a rewarded ad; {@code onReward} runs only if it was watched through. */
        void watchAd(Runnable onReward);

        /** The player just earned an award: unlock it on Play Games too. */
        void awardUnlocked(Achievements.Def d);

        /** A seasonal theme was just unlocked: report it to analytics. */
        void themeUnlocked(Theme t);

        /** A game just ended: report the score to leaderboards and analytics. */
        void gameEnded(GameConfig cfg, int score, boolean won);

        /** The first-launch tutorial ended at {@code step}, finished or skipped: report it to analytics. */
        void tutorialFinished(int step, boolean skipped);
    }

    private static final int NONE = -2;
    private static final int S = GameConfig.SIZE;

    private final Host host;
    private final Progress progress;
    private final Feedback fb;
    private final Theme theme;
    private final boolean colorblind;
    private final GameConfig cfg;
    private GameEngine engine;
    private final float dp;
    private final int touchSlop;
    private final Random fxRandom = new Random();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final Matrix matrix = new Matrix();
    private Shader bgShader;
    /** Falling snow for seasonal themes, else null. */
    private final Snow snow;
    private SweepGradient rainbowShader;
    /** Unit-radius shaders, positioned per segment with a local matrix (no per-frame allocation). */
    private final RadialGradient shadeShader = new RadialGradient(0, 0, 1,
            new int[]{Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255),
                    Color.argb(0, 0, 0, 0), Color.argb(130, 0, 0, 0)},
            new float[]{0f, 0.38f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    private final RadialGradient glossShader = new RadialGradient(0, 0, 1,
            new int[]{Color.argb(235, 255, 255, 255), Color.argb(170, 255, 255, 255), Color.argb(0, 255, 255, 255)},
            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);

    // ---- layout
    private float insetTop, insetBottom;
    private float cell, boardLeft, boardTop;
    private final RectF boardRect = new RectF();
    private final RectF menuBtn = new RectF();
    private final RectF infoRect = new RectF();
    private final RectF trayRect = new RectF();
    private final RectF holdRect = new RectF();
    private final RectF[] previewSlots = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF undoBtn = new RectF();
    private final RectF hintBtn = new RectF();
    private final RectF rotateBtn = new RectF();
    private float scoreX, scoreY;

    // ---- input
    private int pressSource = NONE;
    private float downX, downY, dragX, dragY;
    private boolean dragging;
    private int selected = NONE;
    private long lastTapTime;
    private int lastTapCell = NONE;
    private int hoverCell = NONE;
    private boolean hoverHold;
    private List<Circle> hoverCircles = new ArrayList<>();
    private boolean hoverBomb;

    // ---- animation and feedback
    private final List<Anim> anims = new ArrayList<>();
    private final Set<Integer> hiddenCells = new HashSet<>();
    private boolean hideHold;
    /** True during the forced first-launch tutorial, which has only {@link Levels#FIRST_RUN_STEPS} steps. */
    private final boolean firstRun;
    private final RectF skipBtn = new RectF();
    /** Show the rotation tip once, in the player's first real game. */
    private boolean rotateTip;
    private float displayedScore;
    private long trayRevealAt;
    private float shakeX, shakeY;
    private int[] hintMove;
    private long hintUntil;
    private long lastFrame;
    private boolean specialsIntroDone;

    // ---- per-game
    private int freeUndos = 1;
    private boolean paused;
    private long blitzRemainingMs;
    private int lastTickSecond = -1;
    private long resultsAt = -1;
    private Results results;
    private int accountedCircles, accountedXp;
    private boolean accountedGame;
    private final List<Button> overlayButtons = new ArrayList<>();

    public GameView(Context context, Host host, GameConfig cfg, boolean resume) {
        super(context);
        this.host = host;
        this.cfg = cfg;
        progress = Progress.get(context);
        firstRun = cfg.mode == GameConfig.Mode.TUTORIAL && !progress.tutorialDone();
        rotateTip = cfg.mode != GameConfig.Mode.TUTORIAL && cfg.startRotateTokens > 0 && !progress.tipShown("rotate");
        theme = progress.theme();
        snow = theme.snow ? new Snow(getResources().getDisplayMetrics().density) : null;
        colorblind = progress.colorblind();
        fb = new Feedback(context, this);
        fb.sound = progress.sound();
        fb.haptics = progress.haptics();
        dp = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setHapticFeedbackEnabled(true);

        GameState saved = null;
        if (resume || cfg.mode == GameConfig.Mode.DAILY || cfg.mode == GameConfig.Mode.ZEN) {
            String data = progress.savedGame(saveKey());
            if (data != null) saved = GameState.decode(data, S);
            if (saved != null && saved.over) saved = null;
        }
        engine = saved != null ? new GameEngine(cfg, saved) : new GameEngine(cfg);
        displayedScore = engine.state().score;
        blitzRemainingMs = cfg.timeLimitSec * 1000L;
        if (cfg.mode == GameConfig.Mode.TUTORIAL && cfg.number <= 2) {
            hintMove = engine.hint();
            hintUntil = Long.MAX_VALUE;
        }
    }

    // ================================================================== lifecycle

    private String saveKey() {
        return cfg.mode == GameConfig.Mode.DAILY ? "daily_" + cfg.number : cfg.mode.name().toLowerCase();
    }

    private String bestKey() {
        switch (cfg.mode) {
            case JOURNEY: return "journey_" + cfg.number;
            case DAILY: return "daily";
            default: return cfg.mode.name().toLowerCase();
        }
    }

    /**
     * Set when the player restarts: this activity's onPause runs before the new game's onCreate,
     * and saving here would hand the old board straight back to Zen and Daily, which always resume.
     */
    private boolean discarded;

    /** Called from onPause: keep the game so it survives rotation, calls and app switches. */
    public void save() {
        if (!cfg.resumable() || discarded) return;
        if (engine.state().over) progress.clearGame(saveKey());
        else progress.saveGame(saveKey(), engine.state().encode());
    }

    public void setPaused(boolean p) {
        if (!p && !overlayButtons.isEmpty() && results == null) return; // pause menu stays open
        paused = p;
        lastFrame = 0;
        invalidate();
    }

    public void release() {
        fb.release();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = i.top;
            insetBottom = i.bottom;
        } else {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getSystemWindowInsetBottom();
        }
        layoutGame(getWidth(), getHeight());
        excludeEdgeGestures();
        invalidate();
        return in;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutGame(w, h);
        excludeEdgeGestures();
        if (rotateTip && w > 0 && h > 0) {
            rotateTip = false;
            progress.setTipShown("rotate");
            add(new FloatText("Tip: double-tap a piece to rotate it", boardRect.centerX(), boardRect.bottom + 2 * dp,
                    theme.accent, 14 * dp, SystemClock.uptimeMillis(), 5000));
        }
        bgShader = new LinearGradient(0, 0, 0, h, theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP);
        // Wildcard pieces: a full red-to-violet spectrum inside every 90-degree quarter, so a rainbow
        // segment never looks like a normal piece with a tint of a neighbouring colour.
        int[] spectrum = {0xFFFF4D4D, 0xFFFFA63D, 0xFFFFE14D, 0xFF5BD75B, 0xFF4DA6FF, 0xFFB57BFF};
        int[] rc = new int[spectrum.length * 4 + 1];
        float[] rp = new float[rc.length];
        for (int i = 0; i < rc.length; i++) {
            rc[i] = spectrum[i % spectrum.length];
            rp[i] = i / (float) (rc.length - 1);
        }
        rc[rc.length - 1] = spectrum[0];
        rainbowShader = new SweepGradient(0, 0, rc, rp);
    }

    private boolean hasTray() {
        return cfg.spawnCount > 0;
    }

    private void layoutGame(int w, int h) {
        if (w == 0 || h == 0) return;
        float m = 16 * dp;
        float top = insetTop + 8 * dp;
        float bottom = h - insetBottom - 12 * dp;
        float topBarH = 56 * dp;
        float infoH = cfg.mode == GameConfig.Mode.TUTORIAL ? 108 * dp : 44 * dp;
        float toolH = 52 * dp;
        float gaps = 4 * 12 * dp;
        float avail = bottom - top - topBarH - infoH - toolH - gaps;
        cell = Math.min((w - 2 * m) / S, avail / (S + 1.3f));

        menuBtn.set(m, top + 8 * dp, m + 40 * dp, top + 48 * dp);
        scoreX = w / 2f;
        scoreY = top + 34 * dp;
        infoRect.set(m, top + topBarH, w - m, top + topBarH + infoH);
        skipBtn.set(infoRect.right - 88 * dp, infoRect.top - 4 * dp, infoRect.right + 8 * dp, infoRect.top + 36 * dp);

        float toolTop = bottom - toolH;
        float bw = (w - 2 * m - 2 * 10 * dp) / 3f;
        undoBtn.set(m, toolTop, m + bw, bottom);
        hintBtn.set(undoBtn.right + 10 * dp, toolTop, undoBtn.right + 10 * dp + bw, bottom);
        rotateBtn.set(hintBtn.right + 10 * dp, toolTop, w - m, bottom);

        float boardSize = cell * S;
        boardLeft = (w - boardSize) / 2f;
        float boardBottom = toolTop - 14 * dp;
        boardTop = boardBottom - boardSize;
        boardRect.set(boardLeft, boardTop, boardLeft + boardSize, boardBottom);

        float trayH = cell * 1.3f;
        float trayBottom = boardTop - 12 * dp;
        trayRect.set(boardLeft, trayBottom - trayH, boardRect.right, trayBottom);
        float slot = cell * 1.1f;
        float sy = trayRect.centerY() - slot / 2;
        float sx = trayRect.left + 56 * dp;
        for (int i = 0; i < previewSlots.length; i++) {
            previewSlots[i].set(sx + i * (slot + 6 * dp), sy, sx + i * (slot + 6 * dp) + slot, sy + slot);
        }
        float hs = cell * 1.2f;
        holdRect.set(trayRect.right - hs, trayRect.centerY() - hs / 2, trayRect.right, trayRect.centerY() + hs / 2);
    }

    // ================================================================== geometry helpers

    private float cellX(int i) {
        return boardLeft + (i % S + 0.5f) * cell;
    }

    private float cellY(int i) {
        return boardTop + (i / S + 0.5f) * cell;
    }

    private int cellAt(float x, float y) {
        if (!boardRect.contains(x, y)) return NONE;
        int c = (int) ((x - boardLeft) / cell);
        int r = (int) ((y - boardTop) / cell);
        if (c < 0 || r < 0 || c >= S || r >= S) return NONE;
        return r * S + c;
    }

    /**
     * Cell under a finger that is starting a press. A touch up to 40% of a cell outside the board (or in the
     * gap at its edge) counts as the nearest edge cell, so pieces in the outer rows and columns are easy to grab.
     */
    private int pressCellAt(float x, float y) {
        float slack = cell * 0.4f;
        if (x < boardRect.left - slack || x > boardRect.right + slack
                || y < boardRect.top - slack || y > boardRect.bottom + slack) return NONE;
        float cx = Math.max(boardRect.left + 1, Math.min(boardRect.right - 1, x));
        float cy = Math.max(boardRect.top + 1, Math.min(boardRect.bottom - 1, y));
        return cellAt(cx, cy);
    }

    private Piece pieceAt(int src) {
        GameState st = engine.state();
        return src == GameEngine.HOLD ? st.hold : (src >= 0 ? st.cells[src] : null);
    }

    /** Pieces are drawn above the finger while dragging so the finger does not hide them. */
    private float liftOffset() {
        return cell * 0.85f;
    }

    // ================================================================== drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        stepTimers(now);

        paint.setShader(bgShader);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        if (snow != null) snow.draw(c, getWidth(), getHeight(), now);

        maybeIntroduceSpecials(now);
        computeShake(now);
        updateHidden(now);
        c.save();
        c.translate(shakeX, shakeY);
        drawBoard(c);
        drawSpawnMarkers(c, now);
        drawHintAndHover(c, now);
        drawPieces(c);
        drawTray(c, now);
        c.restore();

        drawTopBar(c);
        drawInfo(c, now);
        drawTools(c);
        drawAnims(c, now);
        drawDragged(c);

        if (results == null && engine.state().over && resultsAt >= 0 && now >= resultsAt) buildResults();
        if (results != null) drawResults(c);
        else if (paused && !overlayButtons.isEmpty()) drawPauseMenu(c);

        boolean animating = !anims.isEmpty() || dragging || Math.abs(displayedScore - engine.state().score) > 0.5f
                || (hintMove != null && now < hintUntil) || blitzRunning() || resultsPending(now);
        if (animating) postInvalidateOnAnimation();
        else if (snow != null) postInvalidateDelayed(Snow.FRAME_MS);
    }

    private boolean resultsPending(long now) {
        return results == null && resultsAt >= 0;
    }

    private boolean blitzRunning() {
        return cfg.timeLimitSec > 0 && !engine.state().over && !paused;
    }

    private void stepTimers(long now) {
        long dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        float target = engine.state().score;
        displayedScore += (target - displayedScore) * Math.min(1f, dt / 120f);
        if (Math.abs(target - displayedScore) < 1f) displayedScore = target;
        if (blitzRunning()) {
            blitzRemainingMs -= dt;
            int sec = (int) Math.ceil(blitzRemainingMs / 1000f);
            if (sec <= 5 && sec != lastTickSecond && sec > 0) {
                lastTickSecond = sec;
                fb.play(Feedback.Sfx.TICK);
            }
            if (blitzRemainingMs <= 0) {
                blitzRemainingMs = 0;
                cancelDrag();
                engine.timeUp();
                onGameOver(now);
            }
        }
    }

    private void computeShake(long now) {
        shakeX = shakeY = 0;
        for (Anim a : anims) {
            if (a instanceof Shake) {
                float t = a.progress(now);
                if (t >= 0 && t <= 1) {
                    float amp = ((Shake) a).amp * (1 - t);
                    shakeX += (float) Math.sin(t * 40) * amp;
                    shakeY += (float) Math.cos(t * 33) * amp * 0.6f;
                }
            }
        }
    }

    private void drawBoard(Canvas c) {
        paint.setColor(theme.panel);
        tmp.set(boardRect);
        tmp.inset(-6 * dp, -6 * dp);
        c.drawRoundRect(tmp, 14 * dp, 14 * dp, paint);
        float r = 6 * dp;
        for (int i = 0; i < S * S; i++) {
            int row = i / S, col = i % S;
            paint.setColor(((row + col) & 1) == 0 ? theme.cell : theme.cellAlt);
            tmp.set(boardLeft + col * cell + 1.5f * dp, boardTop + row * cell + 1.5f * dp,
                    boardLeft + (col + 1) * cell - 1.5f * dp, boardTop + (row + 1) * cell - 1.5f * dp);
            c.drawRoundRect(tmp, r, r, paint);
        }
        if (selected != NONE && selected != GameEngine.HOLD && !dragging) {
            outlineCell(c, selected, theme.accent, 3 * dp, 255);
        }
    }

    private void outlineCell(Canvas c, int i, int color, float width, int alpha) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        paint.setAlpha(alpha);
        float x = cellX(i), y = cellY(i), h = cell / 2 - 2 * dp;
        tmp.set(x - h, y - h, x + h, y + h);
        c.drawRoundRect(tmp, 7 * dp, 7 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    /** Faint mini pieces where the next segments will fall. */
    private void drawSpawnMarkers(Canvas c, long now) {
        if (engine.state().over) return;
        GameState st = engine.state();
        int alpha = (int) (70 * trayAlpha(now));
        for (int i = 0; i < st.preview.size(); i++) {
            int t = st.previewTargets.get(i);
            if (t < 0 || st.cells[t] != null || hiddenCells.contains(t)) continue;
            drawPiece(c, st.preview.get(i), cellX(t), cellY(t), cell * 0.45f, alpha);
        }
    }

    private void drawHintAndHover(Canvas c, long now) {
        if (hintMove != null && now < hintUntil) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            int a = (int) (120 + 135 * pulse);
            if (hintMove[0] == GameEngine.HOLD) {
                strokeRect(c, holdRect, theme.accent, 3 * dp, a);
            } else {
                outlineCell(c, hintMove[0], theme.accent, 3 * dp, a);
            }
            outlineCell(c, hintMove[1], theme.accent, 3 * dp, a);
        }
        if (cfg.mode == GameConfig.Mode.TUTORIAL && cfg.number == 3 && engine.state().rotateTokens > 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            outlineCell(c, 4 * S + 4, theme.accent, 3 * dp, (int) (120 + 135 * pulse));
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(13 * dp);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(theme.accent);
            c.drawText("double-tap", cellX(4 * S + 4), cellY(4 * S + 4) + cell * 0.95f, text);
            postInvalidateOnAnimation();
        }
        if (dragging && hoverCell >= 0) {
            boolean good = !hoverCircles.isEmpty() || hoverBomb;
            outlineCell(c, hoverCell, good ? theme.accent : theme.textDim, (good ? 3 : 2) * dp, good ? 255 : 160);
            Piece p = pieceAt(pressSource);
            if (p != null) drawPiece(c, p, cellX(hoverCell), cellY(hoverCell), cell, good ? 150 : 70);
            float glow = 0.5f + 0.5f * (float) Math.sin(now / 120.0);
            for (Circle k : hoverCircles) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * dp);
                paint.setColor(k.color >= 0 ? theme.piece(k.color) : theme.accent);
                paint.setAlpha((int) (140 + 115 * glow));
                float cx = boardLeft + (k.col + 1) * cell, cy = boardTop + (k.row + 1) * cell;
                c.drawCircle(cx, cy, cell * 0.98f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(255);
            }
            if (hoverBomb) {
                int r0 = hoverCell / S, c0 = hoverCell % S;
                paint.setColor(theme.accent);
                paint.setAlpha((int) (40 + 40 * glow));
                tmp.set(boardLeft + Math.max(0, c0 - 1) * cell, boardTop + Math.max(0, r0 - 1) * cell,
                        boardLeft + Math.min(S, c0 + 2) * cell, boardTop + Math.min(S, r0 + 2) * cell);
                c.drawRoundRect(tmp, 8 * dp, 8 * dp, paint);
                paint.setAlpha(255);
            }
        }
    }

    private void drawPieces(Canvas c) {
        Piece[] cells = engine.state().cells;
        for (int i = 0; i < cells.length; i++) {
            Piece p = cells[i];
            if (p == null || hiddenCells.contains(i)) continue;
            if (!dragging && pressSource == i) {
                // Pressed but not yet moved: lift it a little and ring it so the grab is visible.
                tmp.set(cellX(i) - cell / 2 + 2 * dp, cellY(i) - cell / 2 + 2 * dp,
                        cellX(i) + cell / 2 - 2 * dp, cellY(i) + cell / 2 - 2 * dp);
                paint.setColor(theme.accent);
                paint.setAlpha(80);
                c.drawRoundRect(tmp, 12 * dp, 12 * dp, paint);
                paint.setAlpha(255);
                strokeRect(c, tmp, theme.accent, 3 * dp, 255);
                drawPiece(c, p, cellX(i), cellY(i), cell * 1.2f, 255);
                continue;
            }
            int alpha = (dragging && pressSource == i) ? 60 : 255;
            drawPiece(c, p, cellX(i), cellY(i), cell, alpha);
        }
    }

    private float trayAlpha(long now) {
        if (now >= trayRevealAt) return 1f;
        return 0f;
    }

    private void drawTray(Canvas c, long now) {
        GameState st = engine.state();
        text.setTypeface(Typeface.DEFAULT_BOLD);
        if (hasTray()) {
            text.setColor(theme.textDim);
            text.setTextSize(12 * dp);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText("NEXT", trayRect.left, trayRect.centerY() + 4 * dp, text);
            float a = trayAlpha(now);
            for (int i = 0; i < st.preview.size() && i < previewSlots.length; i++) {
                RectF s = previewSlots[i];
                paint.setColor(theme.panel);
                c.drawRoundRect(s, 10 * dp, 10 * dp, paint);
                if (a > 0) drawPiece(c, st.preview.get(i), s.centerX(), s.centerY(), s.width() * 0.8f, (int) (255 * a));
            }
        }
        // hold slot
        boolean hot = dragging && hoverHold;
        paint.setColor(theme.panel);
        c.drawRoundRect(holdRect, 12 * dp, 12 * dp, paint);
        strokeRect(c, holdRect, hot ? theme.accent : theme.textDim, (hot ? 3 : 1.2f) * dp,
                hot ? 255 : (st.hold == null ? 120 : 70));
        if (selected == GameEngine.HOLD && !dragging) strokeRect(c, holdRect, theme.accent, 3 * dp, 255);
        if (st.hold != null && !hideHold) {
            boolean pressed = !dragging && pressSource == GameEngine.HOLD;
            if (pressed) strokeRect(c, holdRect, theme.accent, 3 * dp, 255);
            int alpha = (dragging && pressSource == GameEngine.HOLD) ? 60 : 255;
            drawPiece(c, st.hold, holdRect.centerX(), holdRect.centerY(),
                    holdRect.width() * (pressed ? 0.9f : 0.78f), alpha);
        }
        text.setColor(theme.textDim);
        text.setTextSize(11 * dp);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("HOLD", holdRect.left - 8 * dp, holdRect.centerY() + 4 * dp, text);
    }

    private void strokeRect(Canvas c, RectF r, int color, float w, int alpha) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);
        paint.setColor(color);
        paint.setAlpha(alpha);
        c.drawRoundRect(r, 12 * dp, 12 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void drawTopBar(Canvas c) {
        // menu button
        paint.setColor(theme.panel);
        c.drawRoundRect(menuBtn, 12 * dp, 12 * dp, paint);
        paint.setColor(theme.text);
        float mx = menuBtn.centerX(), my = menuBtn.centerY();
        for (int k = -1; k <= 1; k++) {
            tmp.set(mx - 9 * dp, my + k * 6 * dp - 1.2f * dp, mx + 9 * dp, my + k * 6 * dp + 1.2f * dp);
            c.drawRoundRect(tmp, dp, dp, paint);
        }
        // score
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setColor(theme.text);
        text.setTextSize(30 * dp);
        c.drawText(String.valueOf(Math.round(displayedScore)), scoreX, scoreY, text);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(12 * dp);
        text.setColor(theme.textDim);
        int best = cfg.mode == GameConfig.Mode.DAILY ? Math.max(0, progress.dailyBest(cfg.number)) : progress.best(bestKey());
        if (cfg.mode != GameConfig.Mode.TUTORIAL) c.drawText("BEST " + best, scoreX, scoreY + 16 * dp, text);
        // streak pill
        int streak = engine.state().streak;
        float pw = 64 * dp, ph = 32 * dp, right = getWidth() - 16 * dp;
        tmp.set(right - pw, menuBtn.centerY() - ph / 2, right, menuBtn.centerY() + ph / 2);
        boolean hot = streak >= 2;
        paint.setColor(hot ? theme.accent : theme.panel);
        c.drawRoundRect(tmp, ph / 2, ph / 2, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(14 * dp);
        text.setColor(hot ? (theme.light ? Color.WHITE : theme.bgTop) : theme.textDim);
        c.drawText("x" + Math.max(1, Math.min(streak, GameEngine.MAX_MULTIPLIER)), tmp.centerX(), tmp.centerY() + 5 * dp, text);
    }

    private void drawInfo(Canvas c, long now) {
        GameState st = engine.state();
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(14 * dp);
        text.setColor(theme.text);
        text.setTextAlign(Paint.Align.LEFT);
        float y = infoRect.top + 22 * dp;
        String left, right = null;
        switch (cfg.mode) {
            case DAILY:
                left = "Daily · " + dateLabel(cfg.number);
                right = engine.movesLeft() + " moves left";
                break;
            case BLITZ:
                left = "Blitz";
                right = String.format(java.util.Locale.US, "%d:%02d", (blitzRemainingMs + 999) / 60000,
                        ((blitzRemainingMs + 999) / 1000) % 60);
                float frac = cfg.timeLimitSec == 0 ? 0 : blitzRemainingMs / (cfg.timeLimitSec * 1000f);
                paint.setColor(theme.panel);
                tmp.set(infoRect.left, infoRect.bottom - 10 * dp, infoRect.right, infoRect.bottom - 4 * dp);
                c.drawRoundRect(tmp, 3 * dp, 3 * dp, paint);
                paint.setColor(frac < 0.2f ? theme.piece(1) : theme.accent);
                tmp.right = tmp.left + tmp.width() * Math.max(0, frac);
                c.drawRoundRect(tmp, 3 * dp, 3 * dp, paint);
                break;
            case ZEN:
                left = "Zen";
                right = st.circlesTotal + " circles";
                break;
            case JOURNEY: {
                Goal g = cfg.goal;
                left = "Level " + cfg.number + " · " + g.progress(st) + "/" + g.target;
                right = engine.movesLeft() + " moves left";
                text.setTypeface(Typeface.DEFAULT);
                text.setTextSize(12 * dp);
                text.setColor(theme.textDim);
                c.drawText(Levels.goalText(g), infoRect.left + (g.type == Goal.COLOR_CIRCLES ? 18 * dp : 0),
                        y + 18 * dp, text);
                if (g.type == Goal.COLOR_CIRCLES) {
                    paint.setColor(theme.piece(g.color));
                    c.drawCircle(infoRect.left + 6 * dp, y + 14 * dp, 6 * dp, paint);
                }
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(14 * dp);
                text.setColor(theme.text);
                break;
            }
            case TUTORIAL: {
                left = "How to play · " + cfg.number + "/" + tutorialSteps();
                right = "Skip";
                text.setTypeface(Typeface.DEFAULT);
                text.setTextSize(15 * dp);
                text.setColor(theme.text);
                StaticLayout sl = StaticLayout.Builder.obtain(Levels.tutorialText(cfg.number), 0,
                        Levels.tutorialText(cfg.number).length(), text, (int) infoRect.width())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
                c.save();
                c.translate(infoRect.left, y + 8 * dp);
                sl.draw(c);
                c.restore();
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(14 * dp);
                text.setColor(theme.accent);
                break;
            }
            default:
                left = "Classic";
                right = engine.colorsNow() + " colours";
                break;
        }
        c.drawText(left, infoRect.left, y, text);
        if (right != null) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(theme.text);
            c.drawText(right, infoRect.right, y, text);
        }
    }

    private static String dateLabel(int yyyymmdd) {
        String[] months = new DateFormatSymbols().getShortMonths();
        return months[(yyyymmdd / 100) % 100 - 1] + " " + yyyymmdd % 100;
    }

    private void drawTools(Canvas c) {
        boolean tutorial = cfg.mode == GameConfig.Mode.TUTORIAL;
        int undoCount = freeUndos + progress.undoTokens();
        boolean ad = host.rewardedReady();
        drawToolButton(c, undoBtn, "Undo", tutorial ? null : badge(undoCount, ad), engine.canUndo());
        drawToolButton(c, hintBtn, "Hint", tutorial ? null : badge(progress.hintTokens(), ad), true);
        drawToolButton(c, rotateBtn, "Rotate", String.valueOf(engine.state().rotateTokens),
                engine.state().rotateTokens > 0);
    }

    /** Token count, or "AD" when empty but a rewarded ad can refill it. */
    private static String badge(int count, boolean adReady) {
        return count > 0 || !adReady ? String.valueOf(count) : "AD";
    }

    private void drawToolButton(Canvas c, RectF r, String label, String count, boolean enabled) {
        paint.setColor(theme.panel);
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(15 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(enabled ? theme.text : theme.textDim);
        float cx = count == null ? r.centerX() : r.centerX() - 12 * dp;
        c.drawText(label, cx, r.centerY() + 5 * dp, text);
        if (count != null) {
            float bx = r.centerX() + text.measureText(label) / 2 + 2 * dp;
            paint.setColor(theme.accent);
            c.drawCircle(bx, r.centerY(), 11 * dp, paint);
            text.setTextSize(12 * dp);
            text.setColor(theme.light ? Color.WHITE : theme.bgTop);
            c.drawText(count, bx, r.centerY() + 4 * dp, text);
        }
    }

    private void drawDragged(Canvas c) {
        if (!dragging) return;
        Piece p = pieceAt(pressSource);
        if (p == null) return;
        float x = dragX, y = dragY - liftOffset();
        // soft shadow
        drawPiece(c, p, x + 3 * dp, y + 6 * dp, cell * 1.12f, 0, 70);
        drawPiece(c, p, x, y, cell * 1.12f, 255);
    }

    // ---- piece rendering

    private void drawPiece(Canvas c, Piece p, float cx, float cy, float size, int alpha) {
        drawPiece(c, p, cx, cy, size, -1, alpha);
    }

    /**
     * Draws a segment centred on (cx, cy) in a cell of {@code size}. A quarter disc whose centre is
     * the cell corner facing the circle's centre. {@code solid} >= 0 draws a flat shadow of that colour.
     */
    private void drawPiece(Canvas c, Piece p, float cx, float cy, float size, int solid, int alpha) {
        if (alpha <= 0) return;
        float half = size / 2;
        if (p.isBomb()) {
            drawBomb(c, cx, cy, size, solid, alpha);
            return;
        }
        float ox, oy;
        float start;
        switch (p.corner) {
            case Piece.TL: ox = cx + half; oy = cy + half; start = 180; break;
            case Piece.TR: ox = cx - half; oy = cy + half; start = 270; break;
            case Piece.BR: ox = cx - half; oy = cy - half; start = 0; break;
            default:       ox = cx + half; oy = cy - half; start = 90; break;
        }
        float r = size * 0.92f;
        tmp.set(ox - r, oy - r, ox + r, oy + r);
        int base = solid >= 0 ? solid : theme.piece(p.color);
        if (p.isRainbow() && solid < 0) {
            matrix.setTranslate(ox, oy);
            rainbowShader.setLocalMatrix(matrix);
            paint.setShader(rainbowShader);
        }
        paint.setColor(base);
        paint.setAlpha(alpha);
        c.drawArc(tmp, start, 90, true, paint);
        paint.setShader(null);
        if (solid >= 0) {
            paint.setAlpha(255);
            return;
        }
        // Shiny-ball look: shading and highlight are anchored to the whole circle (centre ox, oy),
        // lit from the top-left, so four segments together read as one glossy ball.
        matrix.setScale(r * 1.55f, r * 1.55f);
        matrix.postTranslate(ox - r * 0.35f, oy - r * 0.4f);
        shadeShader.setLocalMatrix(matrix);
        paint.setShader(shadeShader);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
        c.drawArc(tmp, start, 90, true, paint);

        matrix.setScale(r * 0.32f, r * 0.18f);
        matrix.postRotate(-40);
        matrix.postTranslate(ox - r * 0.42f, oy - r * 0.48f);
        glossShader.setLocalMatrix(matrix);
        paint.setShader(glossShader);
        paint.setAlpha(alpha);
        c.drawArc(tmp, start, 90, true, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * 0.03f));
        paint.setColor(p.isRainbow() ? Color.rgb(90, 90, 105) : blend(base, Color.BLACK, 0.35f));
        paint.setAlpha(alpha * 170 / 255);
        c.drawArc(tmp, start, 90, false, paint);           // outer rim of the ball
        paint.setAlpha(alpha * 70 / 255);                  // faint seams between segments
        c.drawArc(tmp, start, 90, true, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);

        if (p.isRainbow()) {
            double ang = Math.toRadians(start + 45);
            float sx = ox + (float) Math.cos(ang) * r * 0.55f;
            float sy = oy + (float) Math.sin(ang) * r * 0.55f;
            drawStar(c, sx + size * 0.01f, sy + size * 0.015f, size * 0.15f, Color.argb(alpha * 90 / 255, 0, 0, 0));
            drawStar(c, sx, sy, size * 0.15f, Color.argb(alpha, 255, 255, 255));
            paint.setAlpha(255);
        }
        if (colorblind && !p.isRainbow()) {
            double ang = Math.toRadians(start + 45);
            float sx = ox + (float) Math.cos(ang) * r * 0.55f;
            float sy = oy + (float) Math.sin(ang) * r * 0.55f;
            drawSymbol(c, p.color, sx, sy, size * 0.1f, alpha);
        }
    }

    private void drawSymbol(Canvas c, int color, float x, float y, float s, int alpha) {
        paint.setColor(theme.light ? Color.WHITE : Color.argb(230, 10, 12, 30));
        paint.setAlpha(alpha);
        path.reset();
        switch (color) {
            case 0:
                c.drawCircle(x, y, s, paint);
                break;
            case 1:
                path.moveTo(x, y - s * 1.15f);
                path.lineTo(x + s * 1.1f, y + s * 0.8f);
                path.lineTo(x - s * 1.1f, y + s * 0.8f);
                path.close();
                c.drawPath(path, paint);
                break;
            case 2:
                c.drawRect(x - s * 0.9f, y - s * 0.9f, x + s * 0.9f, y + s * 0.9f, paint);
                break;
            default:
                path.moveTo(x, y - s * 1.2f);
                path.lineTo(x + s * 1.2f, y);
                path.lineTo(x, y + s * 1.2f);
                path.lineTo(x - s * 1.2f, y);
                path.close();
                c.drawPath(path, paint);
                break;
        }
        paint.setAlpha(255);
    }

    private void drawBomb(Canvas c, float cx, float cy, float size, int solid, int alpha) {
        float r = size * 0.33f;
        float bx = cx, by = cy + size * 0.05f;
        // Steel-grey body: light enough to stand out on dark boards, with a rim that contrasts on light ones.
        paint.setColor(solid >= 0 ? solid : Color.rgb(112, 118, 138));
        paint.setAlpha(alpha);
        c.drawCircle(bx, by, r, paint);
        if (solid >= 0) {
            paint.setAlpha(255);
            return;
        }
        // same lighting as the circle segments
        matrix.setScale(r * 1.55f, r * 1.55f);
        matrix.postTranslate(bx - r * 0.35f, by - r * 0.4f);
        shadeShader.setLocalMatrix(matrix);
        paint.setShader(shadeShader);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
        c.drawCircle(bx, by, r, paint);
        matrix.setScale(r * 0.32f, r * 0.18f);
        matrix.postRotate(-40);
        matrix.postTranslate(bx - r * 0.42f, by - r * 0.48f);
        glossShader.setLocalMatrix(matrix);
        paint.setShader(glossShader);
        paint.setAlpha(alpha);
        c.drawCircle(bx, by, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * 0.03f));
        paint.setColor(theme.light ? Color.rgb(45, 45, 60) : Color.WHITE);
        paint.setAlpha(alpha * (theme.light ? 190 : 130) / 255);
        c.drawCircle(bx, by, r, paint);
        // fuse
        paint.setStrokeWidth(size * 0.05f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(170, 135, 95));
        paint.setAlpha(alpha);
        path.reset();
        path.moveTo(bx + r * 0.5f, by - r * 0.75f);
        path.quadTo(bx + r * 0.9f, by - r * 1.4f, bx + r * 1.3f, by - r * 1.2f);
        c.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        // glowing spark
        float sx = bx + r * 1.3f, sy = by - r * 1.2f;
        paint.setColor(Color.rgb(255, 150, 40));
        paint.setAlpha(alpha * 90 / 255);
        c.drawCircle(sx, sy, size * 0.11f, paint);
        paint.setColor(theme.accent);
        paint.setAlpha(alpha);
        c.drawCircle(sx, sy, size * 0.06f, paint);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
        c.drawCircle(sx, sy, size * 0.025f, paint);
        paint.setAlpha(255);
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    // ================================================================== input

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                pressSource = NONE;
                if (results != null || paused || engine.state().over) return true;
                int cellIdx = pressCellAt(x, y);
                if (cellIdx >= 0 && engine.state().cells[cellIdx] != null) pressSource = cellIdx;
                else if (holdRect.contains(x, y) && engine.state().hold != null) pressSource = GameEngine.HOLD;
                if (pressSource != NONE) {
                    fb.grab();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressSource != NONE && !dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                    selected = NONE;
                    fb.play(Feedback.Sfx.PICK);
                }
                if (dragging) {
                    dragX = x;
                    dragY = y;
                    updateHover();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    // Drop where the finger lifted: when moves are batched (busy frame, fast flick)
                    // the last ACTION_MOVE can trail the release point by a cell or more.
                    dragX = x;
                    dragY = y;
                    updateHover();
                    endDrag();
                } else {
                    onTap(x, y);
                }
                pressSource = NONE;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                return true;
        }
        return true;
    }

    private void cancelDrag() {
        dragging = false;
        pressSource = NONE;
        hoverCell = NONE;
        hoverHold = false;
        hoverCircles.clear();
        invalidate();
    }

    private void updateHover() {
        float px = dragX, py = dragY - liftOffset();
        int target = cellAt(px, py);
        boolean overHold = holdRect.contains(px, py) || holdRect.contains(dragX, dragY);
        if (overHold && pressSource != GameEngine.HOLD) {
            hoverHold = true;
            target = NONE;
        } else {
            hoverHold = false;
        }
        if (target >= 0 && engine.state().cells[target] != null) target = NONE;
        if (target == hoverCell) return;
        hoverCell = target;
        hoverCircles = new ArrayList<>();
        hoverBomb = false;
        if (target >= 0) {
            Piece p = pieceAt(pressSource);
            if (p == null) return;
            if (p.isBomb()) {
                hoverBomb = true;
            } else {
                Piece[] sim = engine.state().cells.clone();
                if (pressSource >= 0) sim[pressSource] = null;
                sim[target] = p;
                hoverCircles = CircleFinder.find(sim, S);
            }
            if (!hoverCircles.isEmpty() || hoverBomb) fb.tick();
        }
    }

    private void endDrag() {
        int src = pressSource;
        int dest = hoverHold ? GameEngine.HOLD : hoverCell;
        float fx = dragX, fy = dragY - liftOffset();
        cancelDrag();
        if (dest == NONE || dest == src) {
            // Released over an occupied cell or off-board: spring back.
            boolean overOccupied = cellAt(fx, fy) >= 0 && cellAt(fx, fy) != src;
            if (overOccupied) {
                fb.reject();
                fb.play(Feedback.Sfx.INVALID, 1f, 0.6f);
            }
            snapBack(src, fx, fy);
            return;
        }
        performMove(src, dest, fx, fy);
    }

    private void onTap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (results != null) {
            for (Button b : overlayButtons) if (b.rect.contains(x, y)) { b.action.run(); return; }
            return;
        }
        if (paused) {
            for (Button b : overlayButtons) if (b.rect.contains(x, y)) { b.action.run(); return; }
            return;
        }
        if (menuBtn.contains(x, y)) { openPause(); return; }
        if (cfg.mode == GameConfig.Mode.TUTORIAL && skipBtn.contains(x, y)) { skipTutorial(); return; }
        if (undoBtn.contains(x, y)) { doUndo(); return; }
        if (hintBtn.contains(x, y)) { doHint(); return; }
        if (rotateBtn.contains(x, y)) {
            showMessage(engine.state().rotateTokens > 0 ? "Double-tap a piece to rotate it"
                    : "Make 2+ circles in one move to earn rotations", theme.textDim);
            return;
        }
        if (engine.state().over) return;

        if (holdRect.contains(x, y)) {
            if (selected >= 0) {
                int src = selected;
                selected = NONE;
                performMove(src, GameEngine.HOLD, cellX(src), cellY(src));
            } else if (engine.state().hold != null) {
                selected = selected == GameEngine.HOLD ? NONE : GameEngine.HOLD;
                fb.tick();
            }
            return;
        }

        int idx = cellAt(x, y);
        if (idx == NONE) {
            selected = NONE;
            return;
        }
        Piece p = engine.state().cells[idx];
        if (p != null) {
            // Double-tap rotates.
            if (lastTapCell == idx && now - lastTapTime < 320) {
                lastTapCell = NONE;
                doRotate(idx);
                return;
            }
            lastTapCell = idx;
            lastTapTime = now;
            selected = selected == idx ? NONE : idx; // tapping again deselects
            fb.tick();
            return;
        }
        // Empty cell: only acts if something is selected; a stray tap never costs a turn.
        if (selected != NONE) {
            int src = selected;
            selected = NONE;
            float fx = src == GameEngine.HOLD ? holdRect.centerX() : cellX(src);
            float fy = src == GameEngine.HOLD ? holdRect.centerY() : cellY(src);
            Piece moving = pieceAt(src);
            if (moving != null) {
                performMove(src, idx, fx, fy);
            }
        }
    }

    // ================================================================== actions

    private void performMove(int src, int dest, float fromX, float fromY) {
        Piece moving = pieceAt(src);
        List<float[]> trayPos = previewPositions();
        MoveResult r = engine.move(src, dest);
        if (!r.valid) {
            fb.reject();
            snapBack(src, fromX, fromY);
            return;
        }
        hintMove = cfg.mode == GameConfig.Mode.TUTORIAL && cfg.number <= 2 ? engine.hint() : null;
        fb.tick();
        fb.play(Feedback.Sfx.DROP);
        long now = SystemClock.uptimeMillis();
        // Glide the piece from where it was released to its new home.
        if (moving != null) {
            float tx = dest == GameEngine.HOLD ? holdRect.centerX() : cellX(dest);
            float ty = dest == GameEngine.HOLD ? holdRect.centerY() : cellY(dest);
            float ts = dest == GameEngine.HOLD ? holdRect.width() * 0.78f : cell;
            boolean cleared = false;
            for (MoveResult.Cleared cl : r.cleared) if (cl.cell == dest) cleared = true;
            Fly f = new Fly(moving, fromX, fromY, cell, tx, ty, ts, now, 0, 120);
            f.hideCell = cleared ? NONE : dest;
            f.hideHoldSlot = dest == GameEngine.HOLD;
            add(f);
        }
        animateResult(r, trayPos, now + 110);
    }

    private void doRotate(int idx) {
        if (engine.state().over) return;
        if (engine.state().rotateTokens <= 0) {
            fb.reject();
            showMessage("No rotations left. Make 2+ circles in one move to earn one", theme.textDim);
            return;
        }
        Piece before = engine.state().cells[idx];
        MoveResult r = engine.rotate(idx);
        if (!r.valid) return;
        selected = NONE;
        fb.tick();
        fb.play(Feedback.Sfx.PICK, 1.3f, 0.8f);
        long now = SystemClock.uptimeMillis();
        boolean cleared = !r.cleared.isEmpty();
        Spin s = new Spin(before, cellX(idx), cellY(idx), now);
        s.hideCell = cleared ? NONE : idx;
        add(s);
        animateResult(r, previewPositions(), now + 160);
    }

    private List<float[]> previewPositions() {
        List<float[]> out = new ArrayList<>();
        for (RectF s : previewSlots) out.add(new float[]{s.centerX(), s.centerY(), s.width() * 0.8f});
        return out;
    }

    private void snapBack(int src, float fx, float fy) {
        Piece p = pieceAt(src);
        if (p == null) return;
        boolean hold = src == GameEngine.HOLD;
        float tx = hold ? holdRect.centerX() : cellX(src);
        float ty = hold ? holdRect.centerY() : cellY(src);
        float ts = hold ? holdRect.width() * 0.78f : cell;
        Fly f = new Fly(p, fx, fy, cell * 1.1f, tx, ty, ts, SystemClock.uptimeMillis(), 0, 180);
        f.hideCell = hold ? NONE : src;
        f.hideHoldSlot = hold;
        add(f);
    }

    /** Turns a move result into animation, sound and haptics. */
    private void animateResult(MoveResult r, List<float[]> trayPos, long t0) {
        long end = t0;
        if (!r.cleared.isEmpty()) {
            for (MoveResult.Cleared cl : r.cleared) add(new Fade(cl.piece, cl.cell, t0, 260));
            int n = r.circles.size();
            if (r.bomb) {
                float bx = cellX(r.bombCell), by = cellY(r.bombCell);
                add(new Ring(bx, by, theme.accent, cell * 2.2f, t0, 420));
                burst(bx, by, theme.accent, 28, t0);
                for (MoveResult.Cleared cl : r.cleared) {
                    if (!cl.piece.isBomb()) burst(cellX(cl.cell), cellY(cl.cell), theme.piece(cl.piece.color), 4, t0);
                }
                add(new Shake(9 * dp, t0, 350));
                fb.play(Feedback.Sfx.BOMB);
                fb.burst(3);
            } else {
                for (Circle k : r.circles) {
                    float cx = boardLeft + (k.col + 1) * cell, cy = boardTop + (k.row + 1) * cell;
                    int col = k.color >= 0 ? theme.piece(k.color) : theme.accent;
                    add(new Disc(cx, cy, col, cell * 0.92f, t0, 380));
                    add(new Ring(cx, cy, col, cell * 1.5f, t0 + 60, 420));
                    burst(cx, cy, col, 14, t0 + 40);
                    if (theme.sparkle) sparkle(cx, cy, t0 + 60);
                }
                fb.play(Feedback.Sfx.POP, 1f + 0.09f * (Math.min(r.streak, 8) - 1) + 0.06f * (n - 1), 1f);
                fb.confirm();
                if (n >= 3 || r.multiplier >= 4) fb.burst(3);
                else if (n >= 2 || r.multiplier >= 2) fb.burst(2);
                if (n >= 2) add(new Shake(5 * dp, t0, 260));
            }
            float[] c = centroid(r.cleared);
            add(new FloatText("+" + r.points, c[0], c[1], theme.text, 26 * dp, t0 + 120, 900));
            int line = 0;
            if (n >= 2) banner("CLUSTER x" + n + "!", theme.accent, t0 + 150, line++);
            if (r.multiplier >= 2) banner("STREAK x" + r.multiplier, theme.piece(1), t0 + 150, line++);
            if (r.boardCleared) banner("BOARD CLEAR +" + GameEngine.BOARD_CLEAR_BONUS, theme.piece(2), t0 + 150, line++);
            if (r.rotateTokensEarned > 0) {
                add(new FloatText("+1 rotation", rotateBtn.centerX(), rotateBtn.top - 8 * dp, theme.accent,
                        14 * dp, t0 + 300, 1100));
            }
            if (r.skippedSpawn && hasTray()) {
                add(new FloatText("No new pieces!", trayRect.centerX(), trayRect.top - 2 * dp, theme.accent,
                        14 * dp, t0 + 200, 1100));
            }
            end = t0 + 450;
        }
        if (!r.spawned.isEmpty()) {
            long s0 = r.cleared.isEmpty() ? t0 : t0 + 300;
            for (int k = 0; k < r.spawned.size(); k++) {
                int[] sp = r.spawned.get(k);
                Piece p = null;
                for (MoveResult.Cleared cl : r.luckyCleared) if (cl.cell == sp[1]) p = cl.piece;
                if (p == null) p = engine.state().cells[sp[1]];
                if (p == null) continue;
                float[] from = trayPos.get(Math.min(sp[0], trayPos.size() - 1));
                Fly f = new Fly(p, from[0], from[1], from[2], cellX(sp[1]), cellY(sp[1]), cell, s0, k * 70, 300);
                f.overshoot = true;
                f.hideCell = sp[1];
                add(f);
            }
            fb.play(Feedback.Sfx.SPAWN, 1f, 0.5f);
            long landed = s0 + (r.spawned.size() - 1) * 70 + 300;
            trayRevealAt = landed;
            end = Math.max(end, landed);
            if (!r.luckyCircles.isEmpty()) {
                for (MoveResult.Cleared cl : r.luckyCleared) add(new Fade(cl.piece, cl.cell, landed, 260));
                for (Circle k : r.luckyCircles) {
                    float cx = boardLeft + (k.col + 1) * cell, cy = boardTop + (k.row + 1) * cell;
                    int col = k.color >= 0 ? theme.piece(k.color) : theme.accent;
                    add(new Disc(cx, cy, col, cell * 0.92f, landed, 380));
                    burst(cx, cy, col, 10, landed);
                }
                float[] c = centroid(r.luckyCleared);
                add(new FloatText("Lucky! +" + r.luckyPoints, c[0], c[1], theme.accent, 20 * dp, landed + 80, 1000));
                add(new Sound(Feedback.Sfx.POP, 1.2f, landed));
                end = landed + 450;
            }
        }
        if (!r.zenCleared.isEmpty()) {
            for (MoveResult.Cleared cl : r.zenCleared) add(new Fade(cl.piece, cl.cell, end, 400));
            banner("Board refreshed", theme.textDim, end, 0);
            end += 400;
        }
        for (Achievements.Def d : Achievements.afterMove(progress, r, engine.state())) {
            showAchievement(d, end);
            host.awardUnlocked(d);
        }
        if (r.gameOver) onGameOver(end);
        invalidate();
    }

    private float[] centroid(List<MoveResult.Cleared> cells) {
        float x = 0, y = 0;
        for (MoveResult.Cleared cl : cells) {
            x += cellX(cl.cell);
            y += cellY(cl.cell);
        }
        int n = Math.max(1, cells.size());
        return new float[]{x / n, y / n};
    }

    /** Big callouts go in the open space between the info row and the tray, away from the action. */
    private float calloutTop() {
        float topEdge = infoRect.bottom;
        float bottomEdge = hasTray() ? trayRect.top : holdRect.top;
        return Math.max(topEdge + 30 * dp, (topEdge + bottomEdge) / 2 - 30 * dp);
    }

    private void banner(String s, int color, long at, int line) {
        add(new FloatText(s, boardRect.centerX(), calloutTop() + line * 40 * dp, color, 28 * dp, at, 1300, true));
    }

    /** The first time ever a rainbow piece or a bomb is queued, explain what it is. */
    private void maybeIntroduceSpecials(long now) {
        if (specialsIntroDone) return;
        boolean rainbowSeen = progress.flag("intro_rainbow"), bombSeen = progress.flag("intro_bomb");
        if (rainbowSeen && bombSeen) {
            specialsIntroDone = true;
            return;
        }
        for (Piece p : engine.state().preview) {
            if (p == null) continue;
            String msg = null;
            if (p.isRainbow() && !rainbowSeen) {
                progress.setFlag("intro_rainbow");
                msg = "\u2605 Rainbow piece: matches any colour";
            } else if (p.isBomb() && !bombSeen) {
                progress.setFlag("intro_bomb");
                msg = "Bomb: drop it to clear the 3\u00d73 around it";
            }
            if (msg != null) {
                add(new FloatText(msg, boardRect.centerX(), boardRect.bottom + 2 * dp, theme.accent, 15 * dp, now, 4000));
                return;
            }
        }
    }

    /**
     * Stops the system back gesture from stealing drags that start on pieces in the outer columns. Android
     * honours at most 200dp per screen edge, so this covers the lower part of the board, where most pieces sit.
     */
    private void excludeEdgeGestures() {
        if (Build.VERSION.SDK_INT < 29 || boardRect.isEmpty()) return;
        int edge = (int) (boardRect.left + cell);
        int bottom = (int) boardRect.bottom;
        int top = (int) Math.max(boardRect.top, bottom - 200 * dp);
        java.util.List<android.graphics.Rect> rects = new ArrayList<>();
        rects.add(new android.graphics.Rect(0, top, edge, bottom));
        rects.add(new android.graphics.Rect(getWidth() - edge, top, getWidth(), bottom));
        setSystemGestureExclusionRects(rects);
    }

    private int tutorialSteps() {
        return firstRun ? Levels.FIRST_RUN_STEPS : Levels.TUTORIAL_STEPS;
    }

    /** First launch: straight into a Classic game. From the menu's "How to play": back to the menu. */
    private void skipTutorial() {
        if (!firstRun) {
            host.openMenu();
            return;
        }
        if (!progress.tutorialDone()) {
            progress.setTutorialDone();
            host.tutorialFinished(cfg.number, true);
        }
        host.startGame(GameConfig.classic(System.nanoTime()), false);
    }

    private void showMessage(String s, int color) {
        add(new FloatText(s, boardRect.centerX(), boardRect.bottom + 2 * dp, color, 13 * dp,
                SystemClock.uptimeMillis(), 1800));
    }

    private void showAchievement(Achievements.Def d, long at) {
        add(new FloatText("Achievement: " + d.title + "  (+1 hint)", boardRect.centerX(), calloutTop() - 34 * dp,
                theme.accent, 15 * dp, at, 2400, true));
    }

    private void burst(float x, float y, int color, int count, long at) {
        Particles p = new Particles(at, 650);
        for (int i = 0; i < count; i++) {
            double a = fxRandom.nextDouble() * Math.PI * 2;
            float speed = cell * (1.2f + fxRandom.nextFloat() * 2.2f);
            p.add(x, y, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed, color,
                    dp * (2 + fxRandom.nextFloat() * 3));
        }
        add(p);
    }

    /** Seasonal extra on a pop: little gold and white four-point stars that twinkle and drift up. */
    private void sparkle(float x, float y, long at) {
        Twinkle tw = new Twinkle(at, 1300);
        // Festive mix: the theme's first three piece colours (red, green, gold), accent and white.
        int[] cols = {theme.pieces[0], theme.pieces[1], theme.pieces[2], theme.accent, Color.WHITE};
        for (int i = 0; i < 13; i++) {
            double a = (i + fxRandom.nextDouble() * 0.6) * Math.PI * 2 / 13;
            float r = cell * (0.5f + fxRandom.nextFloat() * 0.95f);
            tw.add(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r,
                    cols[i % cols.length], dp * (8 + fxRandom.nextFloat() * 6),
                    fxRandom.nextFloat() * 0.4f);
        }
        add(tw);
    }

    private void doUndo() {
        if (cfg.mode == GameConfig.Mode.TUTORIAL) {
            if (engine.undo()) afterUndo();
            return;
        }
        if (!engine.canUndo()) {
            showMessage("Nothing to undo", theme.textDim);
            return;
        }
        if (freeUndos > 0) freeUndos--;
        else if (!progress.useUndo()) {
            if (host.rewardedReady()) {
                host.watchAd(() -> {
                    progress.addUndo(1);
                    doUndo();
                });
                return;
            }
            fb.reject();
            showMessage("No undos left. Earn more from daily puzzles and achievements", theme.textDim);
            return;
        }
        engine.undo();
        afterUndo();
    }

    private void afterUndo() {
        anims.clear();
        hiddenCells.clear();
        hideHold = false;
        results = null;
        resultsAt = -1;
        overlayButtons.clear();
        selected = NONE;
        trayRevealAt = 0;
        fb.play(Feedback.Sfx.SPAWN, 0.7f, 0.6f);
        fb.tick();
        invalidate();
    }

    private void doHint() {
        if (engine.state().over) return;
        int[] h = engine.hint();
        if (h == null) {
            showMessage("No one-move circle right now. Try the hold slot or a rotation", theme.textDim);
            return;
        }
        if (cfg.mode != GameConfig.Mode.TUTORIAL && !progress.useHint()) {
            if (host.rewardedReady()) {
                host.watchAd(() -> {
                    progress.addHint(1);
                    doHint();
                });
                return;
            }
            fb.reject();
            showMessage("No hints left. Earn more from daily puzzles and achievements", theme.textDim);
            return;
        }
        hintMove = h;
        hintUntil = SystemClock.uptimeMillis() + 3500;
        fb.tick();
        invalidate();
    }

    // ================================================================== game over and results

    private static final class Results {
        String title;
        boolean newBest;
        int stars;
        int xpGained;
        int levelsGained;
        String reward;
        final List<String> lines = new ArrayList<>();
    }

    private void onGameOver(long showAt) {
        resultsAt = showAt + 500;
        GameState st = engine.state();
        if (!accountedGame) {
            accountedGame = true;
            progress.addGame();
        }
        progress.addCircles(Math.max(0, st.circlesTotal - accountedCircles));
        accountedCircles = Math.max(accountedCircles, st.circlesTotal);
        add(new Sound(st.won ? Feedback.Sfx.WIN : Feedback.Sfx.OVER, 1f, resultsAt));
        if (cfg.resumable()) progress.clearGame(saveKey());
        host.gameEnded(cfg, st.score, st.won);
    }

    private void buildResults() {
        GameState st = engine.state();
        Results res = new Results();
        boolean tutorial = cfg.mode == GameConfig.Mode.TUTORIAL;
        Theme seasonal = null;
        switch (cfg.mode) {
            case JOURNEY: res.title = st.won ? "Level complete!" : "Out of moves"; break;
            case TUTORIAL: res.title = "Nice!"; break;
            case BLITZ: res.title = "Time's up!"; break;
            case DAILY: res.title = "Daily done"; break;
            default: res.title = "Board full"; break;
        }
        if (!tutorial) {
            if (cfg.mode == GameConfig.Mode.DAILY) {
                int before = progress.dailyBest(cfg.number);
                res.newBest = st.score > before && before >= 0;
                if (progress.recordDaily(cfg.number, st.score)) {
                    res.reward = "Daily reward: +1 undo, +1 hint · streak " + progress.dailyStreak();
                }
                seasonal = progress.unlockSeasonalTheme();
            }
            res.newBest |= progress.submitBest(bestKey(), st.score) && st.score > 0;
            int xp = st.score / 5 + (st.won ? 25 : 0) + (cfg.mode == GameConfig.Mode.DAILY ? 15 : 0);
            res.xpGained = Math.max(0, xp - accountedXp);
            accountedXp = Math.max(accountedXp, xp);
            res.levelsGained = progress.addXp(res.xpGained);
        }
        if (cfg.mode == GameConfig.Mode.JOURNEY && st.won) {
            res.stars = engine.stars();
            progress.setStars(cfg.number, res.stars);
        }
        if (tutorial && firstRun && cfg.number >= Levels.FIRST_RUN_STEPS && !progress.tutorialDone()) {
            progress.setTutorialDone();
            host.tutorialFinished(cfg.number, false);
        }

        res.lines.add(st.circlesTotal + (st.circlesTotal == 1 ? " circle" : " circles") + " · best streak x" + Math.max(1, st.maxStreak)
                + " · biggest cluster " + Math.max(st.bestCluster, 0));
        if (!tutorial) {
            res.lines.add("+" + res.xpGained + " XP · level " + progress.level()
                    + (res.levelsGained > 0 ? "  LEVEL UP!" : ""));
        }
        for (Achievements.Def d : Achievements.afterGame(progress, cfg, st, res.stars)) {
            res.lines.add("Achievement: " + d.title + " (+1 hint)");
            host.awardUnlocked(d);
        }
        if (res.reward != null) res.lines.add(res.reward);
        if (seasonal != null) {
            res.lines.add(seasonal.name + " theme unlocked!");
            res.lines.add("Yours to keep · Settings → Theme");
            host.themeUnlocked(seasonal);
        }
        results = res;
        buildResultButtons();
        if (res.newBest || res.stars == 3) fb.burst(2);
    }

    private void buildResultButtons() {
        overlayButtons.clear();
        GameState st = engine.state();
        switch (cfg.mode) {
            case TUTORIAL:
                if (cfg.number < tutorialSteps()) {
                    overlayButtons.add(new Button("Next", true,
                            () -> host.startGame(Levels.tutorial(cfg.number + 1), false)));
                } else {
                    overlayButtons.add(new Button("Start playing", true,
                            () -> host.startGame(GameConfig.classic(System.nanoTime()), false)));
                }
                break;
            case JOURNEY:
                if (st.won && cfg.number < Levels.JOURNEY_COUNT) {
                    overlayButtons.add(new Button("Next level", true,
                            () -> host.afterGame(() -> host.startGame(Levels.journey(cfg.number + 1), false))));
                }
                overlayButtons.add(new Button(st.won ? "Replay" : "Retry", !st.won,
                        () -> host.afterGame(() -> host.startGame(Levels.journey(cfg.number), false))));
                break;
            case DAILY:
                break;
            default:
                overlayButtons.add(new Button("Play again", true,
                        () -> host.afterGame(() -> host.startGame(newConfigLikeThis(), false))));
                break;
        }
        int undos = freeUndos + progress.undoTokens();
        boolean canContinue = engine.canUndo() && !st.won && cfg.mode != GameConfig.Mode.BLITZ
                && cfg.mode != GameConfig.Mode.TUTORIAL && (undos > 0 || host.rewardedReady());
        if (canContinue) {
            overlayButtons.add(new Button(undos > 0 ? "Undo last move (" + undos + ")"
                    : "Watch an ad to undo last move", false, this::doUndo));
        }
        if (cfg.mode != GameConfig.Mode.TUTORIAL) {
            overlayButtons.add(new Button("Share", false, () -> host.share(shareText())));
        }
        overlayButtons.add(new Button("Menu", false, () -> host.afterGame(host::openMenu)));
    }

    private GameConfig newConfigLikeThis() {
        long seed = System.nanoTime();
        switch (cfg.mode) {
            case ZEN: return GameConfig.zen(seed);
            case BLITZ: return GameConfig.blitz(seed, cfg.timeLimitSec);
            default: return GameConfig.classic(seed);
        }
    }

    private String shareText() {
        GameState st = engine.state();
        StringBuilder dots = new StringBuilder();
        String[] marks = {"🔵", "🟣", "🟢", "🟠"};
        for (int c = 0; c < 4; c++) for (int k = 0; k < Math.min(10, st.circlesByColor[c]); k++) dots.append(marks[c]);
        String head;
        switch (cfg.mode) {
            case DAILY: head = "Daily " + dateLabel(cfg.number); break;
            case JOURNEY: head = "Journey level " + cfg.number + (st.won ? " " + stars(engine.stars()) : ""); break;
            default: head = cfg.mode.name().charAt(0) + cfg.mode.name().substring(1).toLowerCase(); break;
        }
        return getContext().getString(R.string.app_name) + " · " + head + "\n" + st.score + " points · "
                + st.circlesTotal + (st.circlesTotal == 1 ? " circle" : " circles") + " · streak x" + Math.max(1, st.maxStreak) + "\n" + dots;
    }

    private static String stars(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append("\u2B50");
        return b.toString();
    }

    private void drawResults(Canvas c) {
        c.drawColor(Color.argb(170, 0, 0, 0));
        float w = getWidth(), m = 24 * dp;
        float lineH = 20 * dp;
        float btnH = 48 * dp, btnGap = 10 * dp;
        float contentH = 64 * dp + 56 * dp + (results.newBest ? 26 * dp : 0) + (results.stars > 0 ? 44 * dp : 0)
                + results.lines.size() * lineH + 20 * dp + overlayButtons.size() * (btnH + btnGap);
        float top = Math.max(insetTop + 16 * dp, (getHeight() - contentH) / 2);
        tmp.set(m, top, w - m, top + contentH);
        paint.setColor(theme.panel);
        c.drawRoundRect(tmp, 22 * dp, 22 * dp, paint);

        float y = top + 44 * dp;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setColor(theme.text);
        text.setTextSize(24 * dp);
        c.drawText(results.title, w / 2, y, text);
        y += 52 * dp;
        text.setTextSize(44 * dp);
        text.setColor(theme.accent);
        c.drawText(String.valueOf(engine.state().score), w / 2, y, text);
        y += 20 * dp;
        if (results.newBest) {
            text.setTextSize(15 * dp);
            text.setColor(theme.piece(2));
            c.drawText("NEW BEST!", w / 2, y + 6 * dp, text);
            y += 26 * dp;
        }
        if (results.stars > 0) {
            for (int i = 0; i < 3; i++) {
                drawStar(c, w / 2 + (i - 1) * 40 * dp, y + 16 * dp, 15 * dp, i < results.stars ? theme.accent : theme.cellAlt);
            }
            y += 44 * dp;
        }
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(13.5f * dp);
        text.setColor(theme.textDim);
        for (String line : results.lines) {
            y += lineH;
            c.drawText(line, w / 2, y, text);
        }
        y += 24 * dp;
        layoutButtons(c, y, m + 16 * dp, w - m - 16 * dp, btnH, btnGap);
    }

    private void layoutButtons(Canvas c, float y, float left, float right, float h, float gap) {
        for (Button b : overlayButtons) {
            b.rect.set(left, y, right, y + h);
            paint.setColor(b.primary ? theme.accent : theme.cellAlt);
            c.drawRoundRect(b.rect, h / 2, h / 2, paint);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(16 * dp);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(b.primary ? (theme.light ? Color.WHITE : theme.bgTop) : theme.text);
            c.drawText(b.label, b.rect.centerX(), b.rect.centerY() + 6 * dp, text);
            y += h + gap;
        }
    }

    private void drawStar(Canvas c, float cx, float cy, float r, int color) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    // ---- pause menu

    private void openPause() {
        paused = true;
        lastFrame = 0;
        overlayButtons.clear();
        overlayButtons.add(new Button("Resume", true, this::closePause));
        overlayButtons.add(new Button("Restart", false, () -> {
            discarded = true;
            if (cfg.resumable()) progress.clearGame(saveKey());
            GameConfig next = cfg.mode == GameConfig.Mode.CLASSIC || cfg.mode == GameConfig.Mode.ZEN
                    || cfg.mode == GameConfig.Mode.BLITZ ? newConfigLikeThis() : cfg;
            host.startGame(next, false);
        }));
        overlayButtons.add(new Button("Sound: " + (fb.sound ? "on" : "off"), false, () -> {
            fb.sound = !fb.sound;
            progress.setSound(fb.sound);
            openPause();
        }));
        overlayButtons.add(new Button("Haptics: " + (fb.haptics ? "on" : "off"), false, () -> {
            fb.haptics = !fb.haptics;
            progress.setHaptics(fb.haptics);
            openPause();
        }));
        overlayButtons.add(new Button("Quit to menu", false, host::openMenu));
        invalidate();
    }

    private void closePause() {
        paused = false;
        overlayButtons.clear();
        lastFrame = 0;
        invalidate();
    }

    private void drawPauseMenu(Canvas c) {
        c.drawColor(Color.argb(170, 0, 0, 0));
        float w = getWidth(), m = 40 * dp, h = 50 * dp, gap = 12 * dp;
        float total = 60 * dp + overlayButtons.size() * (h + gap);
        float top = (getHeight() - total) / 2;
        tmp.set(m - 16 * dp, top - 16 * dp, w - m + 16 * dp, top + total);
        paint.setColor(theme.panel);
        c.drawRoundRect(tmp, 22 * dp, 22 * dp, paint);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(22 * dp);
        text.setColor(theme.text);
        c.drawText("Paused", w / 2, top + 26 * dp, text);
        layoutButtons(c, top + 52 * dp, m, w - m, h, gap);
    }

    private static final class Button {
        final String label;
        final boolean primary;
        final Runnable action;
        final RectF rect = new RectF();

        Button(String label, boolean primary, Runnable action) {
            this.label = label;
            this.primary = primary;
            this.action = action;
        }
    }

    // ================================================================== animation system

    private void add(Anim a) {
        anims.add(a);
        invalidate();
    }

    private void updateHidden(long now) {
        hiddenCells.clear();
        hideHold = false;
        for (Anim a : anims) {
            if (a.progress(now) > 1f) continue;
            if (a.hideCell >= 0) hiddenCells.add(a.hideCell);
            if (a.hideHoldSlot) hideHold = true;
        }
    }

    private void drawAnims(Canvas c, long now) {
        Iterator<Anim> it = anims.iterator();
        c.save();
        c.translate(shakeX, shakeY);
        while (it.hasNext()) {
            Anim a = it.next();
            float t = a.progress(now);
            if (t > 1f) {
                a.onEnd();
                it.remove();
                continue;
            }
            if (t >= 0) a.draw(c, t);
            else if (a.hideCell >= 0 && a instanceof Fly) {
                // Waiting to start: keep the piece at its origin (e.g. preview slot).
                a.draw(c, 0);
            }
        }
        c.restore();
    }

    private abstract class Anim {
        final long start;
        final long duration;
        int hideCell = NONE;
        boolean hideHoldSlot;

        Anim(long start, long duration) {
            this.start = start;
            this.duration = duration;
        }

        /** <0 before start, 0..1 while running, >1 when done. */
        float progress(long now) {
            if (now < start) return -1;
            return duration <= 0 ? 2 : (now - start) / (float) duration;
        }

        abstract void draw(Canvas c, float t);

        void onEnd() {
        }
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static float overshoot(float t) {
        float s = 1.7f;
        t -= 1;
        return t * t * ((s + 1) * t + s) + 1;
    }

    /** A piece moving between two points and sizes. */
    private final class Fly extends Anim {
        final Piece p;
        final float x0, y0, s0, x1, y1, s1;
        boolean overshoot;

        Fly(Piece p, float x0, float y0, float s0, float x1, float y1, float s1, long t0, long delay, long dur) {
            super(t0 + delay, dur);
            this.p = p;
            this.x0 = x0;
            this.y0 = y0;
            this.s0 = s0;
            this.x1 = x1;
            this.y1 = y1;
            this.s1 = s1;
        }

        @Override
        void draw(Canvas c, float t) {
            float e = overshoot ? overshoot(t) : easeOut(t);
            float arc = overshoot ? -cell * 0.6f * (float) Math.sin(Math.PI * Math.min(1, t)) : 0;
            float x = x0 + (x1 - x0) * e, y = y0 + (y1 - y0) * e + arc;
            float s = s0 + (s1 - s0) * Math.min(1, e);
            drawPiece(c, p, x, y, s, 255);
        }
    }

    /** Rotation: the old orientation turns 90 degrees clockwise into place. */
    private final class Spin extends Anim {
        final Piece p;
        final float x, y;

        Spin(Piece p, float x, float y, long t0) {
            super(t0, 160);
            this.p = p;
            this.x = x;
            this.y = y;
        }

        @Override
        void draw(Canvas c, float t) {
            c.save();
            c.rotate(90 * easeOut(t), x, y);
            drawPiece(c, p, x, y, cell, 255);
            c.restore();
        }
    }

    /** A cleared piece swells and fades. */
    private final class Fade extends Anim {
        final Piece p;
        final int cellIdx;

        Fade(Piece p, int cellIdx, long t0, long dur) {
            super(t0, dur);
            this.p = p;
            this.cellIdx = cellIdx;
        }

        @Override
        void draw(Canvas c, float t) {
            drawPiece(c, p, cellX(cellIdx), cellY(cellIdx), cell * (1 + 0.15f * t), (int) (255 * (1 - t)));
        }
    }

    /** The completed circle flashing as a whole disc. */
    private final class Disc extends Anim {
        final float x, y, r;
        final int color;

        Disc(float x, float y, int color, float r, long t0, long dur) {
            super(t0, dur);
            this.x = x;
            this.y = y;
            this.color = color;
            this.r = r;
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setColor(blend(color, Color.WHITE, 0.25f * (1 - t)));
            paint.setAlpha((int) (255 * (1 - t)));
            c.drawCircle(x, y, r * (1 + 0.3f * easeOut(t)), paint);
            paint.setAlpha(255);
        }
    }

    private final class Ring extends Anim {
        final float x, y, r;
        final int color;

        Ring(float x, float y, int color, float r, long t0, long dur) {
            super(t0, dur);
            this.x = x;
            this.y = y;
            this.color = color;
            this.r = r;
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * dp * (1 - t) + dp);
            paint.setColor(color);
            paint.setAlpha((int) (220 * (1 - t)));
            c.drawCircle(x, y, r * easeOut(t), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
        }
    }

    /** Four-point stars that pop in, twinkle and fade while drifting upwards. */
    private final class Twinkle extends Anim {
        final List<float[]> stars = new ArrayList<>();

        Twinkle(long t0, long dur) {
            super(t0, dur);
        }

        /** {@code delay} is a fraction of the duration before this star appears. */
        void add(float x, float y, int color, float size, float delay) {
            stars.add(new float[]{x, y, color, size, delay});
        }

        @Override
        void draw(Canvas c, float t) {
            for (float[] s : stars) {
                float lt = (t - s[4]) / (1 - s[4]);
                if (lt < 0) continue;
                float grow = lt < 0.25f ? lt / 0.25f : 1;
                float r = s[3] * grow * (1 - 0.6f * lt) * (0.7f + 0.3f * (float) Math.sin(lt * 22 + s[4] * 40));
                float x = s[0], y = s[1] - cell * 0.5f * easeOut(lt);
                int alpha = (int) (255 * (1 - lt * lt));
                paint.setColor((int) s[2]);
                paint.setAlpha(alpha / 4); // soft glow behind the star
                c.drawCircle(x, y, r * 0.75f, paint);
                paint.setAlpha(alpha);
                path.rewind();
                path.moveTo(x, y - r);
                path.quadTo(x, y, x + r, y);
                path.quadTo(x, y, x, y + r);
                path.quadTo(x, y, x - r, y);
                path.quadTo(x, y, x, y - r);
                path.close();
                c.drawPath(path, paint);
            }
            paint.setAlpha(255);
        }
    }

    private final class Particles extends Anim {
        final List<float[]> parts = new ArrayList<>();

        Particles(long t0, long dur) {
            super(t0, dur);
        }

        void add(float x, float y, float vx, float vy, int color, float size) {
            parts.add(new float[]{x, y, vx, vy, color, size});
        }

        @Override
        void draw(Canvas c, float t) {
            float secs = t * duration / 1000f;
            for (float[] p : parts) {
                float x = p[0] + p[2] * secs;
                float y = p[1] + p[3] * secs + 0.5f * cell * 6 * secs * secs;
                paint.setColor((int) p[4]);
                paint.setAlpha((int) (255 * (1 - t)));
                c.drawCircle(x, y, p[5] * (1 - 0.5f * t), paint);
            }
            paint.setAlpha(255);
        }
    }

    private final class FloatText extends Anim {
        final String s;
        final float x, y, size;
        final int color;
        final boolean pop;

        FloatText(String s, float x, float y, int color, float size, long t0, long dur) {
            this(s, x, y, color, size, t0, dur, false);
        }

        FloatText(String s, float x, float y, int color, float size, long t0, long dur, boolean pop) {
            super(t0, dur);
            this.s = s;
            this.x = x;
            this.y = y;
            this.color = color;
            this.size = size;
            this.pop = pop;
        }

        @Override
        void draw(Canvas c, float t) {
            float scale = pop ? (t < 0.15f ? 0.6f + 0.4f * overshoot(t / 0.15f) : 1f) : 1f;
            float alpha = t < 0.7f ? 1f : (1 - t) / 0.3f;
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(size * scale);
            float yy = y - (pop ? 0 : cell * 0.8f * easeOut(t));
            // outline for legibility on any background
            text.setStyle(Paint.Style.STROKE);
            text.setStrokeWidth(4 * dp);
            text.setColor(theme.light ? Color.WHITE : theme.bgTop);
            text.setAlpha((int) (200 * alpha));
            c.drawText(s, x, yy, text);
            text.setStyle(Paint.Style.FILL);
            text.setColor(color);
            text.setAlpha((int) (255 * alpha));
            c.drawText(s, x, yy, text);
            text.setAlpha(255);
        }
    }

    private final class Shake extends Anim {
        final float amp;

        Shake(float amp, long t0, long dur) {
            super(t0, dur);
            this.amp = amp;
        }

        @Override
        void draw(Canvas c, float t) {
        }
    }

    /** Plays a sound when its start time arrives. */
    private final class Sound extends Anim {
        final Feedback.Sfx sfx;
        final float rate;
        boolean played;

        Sound(Feedback.Sfx sfx, float rate, long at) {
            super(at, 1);
            this.sfx = sfx;
            this.rate = rate;
        }

        @Override
        void draw(Canvas c, float t) {
            onEnd();
        }

        @Override
        void onEnd() {
            if (!played) {
                played = true;
                fb.play(sfx, rate, 1f);
            }
        }
    }
}
