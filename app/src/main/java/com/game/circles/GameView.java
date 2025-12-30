package com.game.circles;

import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;

public class GameView extends View {

    private GameLogic game;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cellSize;
    private long lastTapTime = 0;
    private List<int[]> circlesToPop = new ArrayList<>();
    private Handler popHandler = new Handler(Looper.getMainLooper());
    private int[] colors = {
            Color.CYAN,
            Color.MAGENTA,
            Color.GREEN
    };

    public GameView(Context context) {
        super(context);
        game = new GameLogic();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cellSize = Math.min(w, h) / 8f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        drawGrid(canvas);
        drawPieces(canvas);
        drawCompletedCircles(canvas);
        invalidate();
    }

    private void drawGrid(Canvas c) {
        paint.setColor(Color.DKGRAY);
        for (int i = 0; i <= 8; i++) {
            c.drawLine(i * cellSize, 0, i * cellSize, 8 * cellSize, paint);
            c.drawLine(0, i * cellSize, 8 * cellSize, i * cellSize, paint);
        }
    }

    private void drawPieces(Canvas c) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Cell cell = game.board.cells[x][y];
                if (cell.isEmpty()) continue;

                Piece p = cell.piece;
                paint.setColor(colors[p.color]);

                float left = (x  * cellSize) ;
                float top = ((7 - y) * cellSize) ;
                RectF r = new RectF(left, top,
                        left + cellSize  ,
                        top + cellSize);

                drawQuarter(c, r, p.corner);
            }
        }
    }

    private void drawCompletedCircles(Canvas c) {
        if (circlesToPop.isEmpty()) return;

        for (int[] topLeft : circlesToPop) {
            int x = topLeft[0];
            int y = topLeft[1];

            // Get color from one of the pieces (e.g., bottom-left)
            Piece piece = game.board.cells[x][y].piece;
            if (piece == null) continue; // Should not happen
            paint.setColor(colors[piece.color]);

            // Calculate the bounds for the full circle
            float left = x * cellSize;
            float top = (7 - (y + 1)) * cellSize; // y+1 is the top row of the 2x2 square
            float right = (x + 2) * cellSize;
            float bottom = (7 - y + 1) * cellSize;

            c.drawOval(left, top, right, bottom, paint);
        }
    }
    private void drawQuarter(Canvas c, RectF r, Piece.Corner corner) {
        float startAngle = 0;
        switch (corner) {
            case TOP_LEFT:
                r.right += cellSize;
                r.bottom += cellSize;
                startAngle = 180;
                break;
            case TOP_RIGHT:
              r.left -= cellSize;

                r.bottom += cellSize;
                startAngle = 270;
                break;
            case BOTTOM_RIGHT:
                r.top -= cellSize;
                r.left -= cellSize;

                startAngle = 0;
                break;
            case BOTTOM_LEFT:
                r.top-= cellSize;
               r.right += cellSize;
                startAngle = 90;
                break;


        }

        c.drawArc(r, startAngle, 90, true, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Prevent touches while a pop animation is pending
        if (e.getAction() != MotionEvent.ACTION_DOWN || !circlesToPop.isEmpty()) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastTapTime < 200) {
            game.spawnRandom(3);
        }
        lastTapTime = now;

        int x = (int)(e.getX() / cellSize);
        int y = 7 - (int)(e.getY() / cellSize);

        if (x >= 0 && x < 8 && y >= 0 && y < 8) {
            // --- MODIFIED GAME LOGIC ---
            game.placePreview(x, y);

            // Now, check for circles using the new method
            List<int[]> foundCircles = game.checkAndMarkCircles(); // Assumes this is in GameLogic

            if (!foundCircles.isEmpty()) {
                // A circle was completed!
                // 1. Update our list to trigger the drawing of full circles
                circlesToPop = foundCircles;

                // 2. Post a delayed task to clear them
                popHandler.postDelayed(() -> {
                    // This code will run after 500ms
                    game.clearMarkedCircles(circlesToPop); // Assumes this is in GameLogic
                    circlesToPop.clear(); // Clear the list so they are no longer drawn
                    // Optionally, spawn new pieces here if that's the game rule
                  //  game.spawnRandom(3);
                }, 500); // 500ms delay

            }
        }
        return true;
    }

}
