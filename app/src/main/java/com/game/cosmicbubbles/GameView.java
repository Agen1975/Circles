package com.game.cosmicbubbles;

import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;

public class GameView extends View {
    private float previewAreaHeight;
    private float adjustLeft;
    private GameLogic game;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cellSize;
   // private long lastTapTime = 0;
    private List<int[]> circlesToPop = new ArrayList<>();
    private Handler popHandler = new Handler(Looper.getMainLooper());
    private int[] colors = {
            Color.CYAN,
            Color.MAGENTA,
            Color.GREEN
    };
    private int selectedX = -1;
    private int selectedY = -1;
    public GameView(Context context) {
        super(context);
        game = new GameLogic();
    }
    private RectF restartButtonRect;
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cellSize = Math.min(w, h) / 9f;// Divide by 9 to make space for preview
        previewAreaHeight = cellSize*2;
        adjustLeft = cellSize /2;
        float buttonWidth = 4 * cellSize;
        float buttonHeight = 1.5f * cellSize;
        float buttonLeft = (w / 2f) - (buttonWidth / 2f);
        float buttonTop = (h / 2f) + (0.5f * cellSize);
        restartButtonRect = new RectF(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight);

    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        drawGrid(canvas);
        drawSelectionHighlight(canvas);
        drawPieces(canvas);
        drawCompletedCircles(canvas);
        drawPreviewPieces(canvas);
        if (game.isGameOver) {
            drawGameOver(canvas);
        }
        invalidate();
    }
    private void drawGameOver(Canvas c) {
        // Draw a semi-transparent overlay
        paint.setColor(Color.argb(200, 0, 0, 0)); // Semi-transparent black
        c.drawRect(0, 0, getWidth(), getHeight(), paint);

        // Draw "Game Over" text
        paint.setColor(Color.RED);
        paint.setTextSize(cellSize * 1.5f);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("Game Over", getWidth() / 2f, getHeight() / 2f - cellSize, paint);

        // Draw the final score
        paint.setColor(Color.WHITE);
        paint.setTextSize(cellSize * 0.8f);
        c.drawText("Final Score: " + game.score, getWidth() / 2f, getHeight() / 2f, paint);

        // Draw "Restart" button background
        paint.setColor(Color.DKGRAY);
        c.drawRoundRect(restartButtonRect, 20f, 20f, paint);

        // Draw "Restart" text
        paint.setColor(Color.WHITE);
        paint.setTextSize(cellSize * 0.7f);
        c.drawText("Restart", restartButtonRect.centerX(), restartButtonRect.centerY() + (paint.getTextSize() / 3), paint);
    }

    private void drawGrid(Canvas c) {
        paint.setStrokeWidth(5f);
        paint.setColor(Color.WHITE);
        for (int i = 0; i <= 8; i++) {
            // Offset all grid drawing by previewAreaHeight
            c.drawLine(i * cellSize + adjustLeft, previewAreaHeight, i * cellSize + adjustLeft, 8 * cellSize + previewAreaHeight, paint);
            c.drawLine(adjustLeft, i * cellSize + previewAreaHeight, 8 * cellSize + adjustLeft, i * cellSize + previewAreaHeight, paint);
        }
    }

    private void drawPieces(Canvas c) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Cell cell = game.board.cells[x][y];
                if (cell.isEmpty()) continue;

                Piece p = cell.piece;
                paint.setColor(colors[p.color]);

                // Offset Y coordinate by previewAreaHeight
                float left = (x * cellSize) + adjustLeft;
                float top = ((7 - y) * cellSize) + previewAreaHeight ;
                RectF r = new RectF(left, top,
                        left + cellSize  ,
                        top + cellSize);

                drawQuarter(c, r, p.corner);
            }
        }
    }

    private void drawPreviewPieces(Canvas c) {
        if (game.preview.isEmpty()) return;

        paint.setColor(Color.WHITE);
        paint.setTextSize(cellSize * 0.5f);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText("Next:", cellSize , cellSize * 1.35f, paint);

        for (int i = 0; i < game.preview.size(); i++) {
            Piece p = game.preview.get(i);
            paint.setColor(colors[p.color]);

            // Position the pieces horizontally in the preview area
            float left = (2.35f + i) * cellSize;
            float top = cellSize;
            RectF r = new RectF(left, top, left + cellSize, top + cellSize);
            drawQuarter(c, r, p.corner);
        }
        paint.setColor(Color.WHITE);
        paint.setTextSize(cellSize * 0.5f);
        paint.setTextAlign(Paint.Align.RIGHT); // Align text to the right
        String scoreText = "Score: " + game.score;
        // Draw it on the right edge of the screen, vertically centered in the preview area
        c.drawText(scoreText, 8f * cellSize   , cellSize * 1.35f, paint);
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
            float left = x * cellSize + adjustLeft;
            float top = (7 - (y + 1)) * cellSize + previewAreaHeight;
            float right = (x + 2) * cellSize + adjustLeft;
            float bottom = (7 - y + 1) * cellSize + previewAreaHeight;

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
    private void drawSelectionHighlight(Canvas c) {
        if (selectedX == -1 && selectedY == -1) {
            return;
        }


        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10f);
        paint.setColor(Color.YELLOW);

        float left = selectedX * cellSize + adjustLeft;
        float top = (7 - selectedY) * cellSize + previewAreaHeight;

        // Draw a border around the selected cell
        c.drawRect(left + 2, top + 2, left + cellSize - 2, top + cellSize - 2, paint);

        // Reset paint style for other drawing methods
        paint.setStyle(Paint.Style.FILL);
    }
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Prevent touches while a pop animation is pending
        if (e.getAction() != MotionEvent.ACTION_DOWN || !circlesToPop.isEmpty()) {
            return true;
        }
        if (game.isGameOver) {
            if (restartButtonRect.contains(e.getX(), e.getY())) {
                game.restart();
            }
            return true; // Don't process other game touches
        }
        // long now = System.currentTimeMillis();
        //if (now - lastTapTime < 200) {
        //     game.spawnRandom(3);
        //}
        //  lastTapTime = now;

        float touchY = e.getY() - previewAreaHeight;
        float touchX = e.getX() - adjustLeft;
        if (touchY < 0) return true; // Touch was in the preview area, ignore
        if (touchX < 0) return true; // Touch was in the preview area, ignore
        int x = (int) (touchX/ cellSize);
        int y = 7 - (int) (touchY / cellSize); // Use the offset touchY

        if (x >= 0 && x < 8 && y >= 0 && y < 8) {
            game.placePreview(x, y);
            if (game.selectedCell != null) {
                selectedX = x;
                selectedY = y;

            }
            else
            {
                selectedX = -1;
                selectedY = -1;
            }
                game.checkGameOver();
                // Check for completed circles (this part of the logic can stay)
                List<int[]> foundCircles = game.checkAndMarkCircles(false);
                if (!foundCircles.isEmpty()) {
                    circlesToPop = foundCircles;
                    popHandler.postDelayed(() -> {
                        game.clearMarkedCircles(circlesToPop);
                        circlesToPop.clear();

                        // Don't spawn here automatically. Let the user's next click trigger it.
                    }, 500);
                }
            }
            return true;
        }

}
