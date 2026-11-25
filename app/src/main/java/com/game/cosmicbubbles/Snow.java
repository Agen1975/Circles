package com.game.cosmicbubbles;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * Gently falling snowflakes for seasonal themes. Stateless: each flake's position is a function
 * of the clock, so nothing is stepped per frame and it looks the same after a pause.
 */
final class Snow extends Backdrop {
    private static final int FLAKES = 60;

    private final float[] x = new float[FLAKES], speed = new float[FLAKES], size = new float[FLAKES],
            sway = new float[FLAKES], phase = new float[FLAKES], alpha = new float[FLAKES];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    Snow(float dp) {
        this.dp = dp;
        Random r = new Random(1225);
        for (int i = 0; i < FLAKES; i++) {
            float depth = r.nextFloat(); // 0 = far (small, slow, faint), 1 = near
            x[i] = r.nextFloat();
            size[i] = 1.2f + depth * 2.6f;
            speed[i] = 14 + depth * 34; // dp per second
            sway[i] = 6 + r.nextFloat() * 14;
            phase[i] = r.nextFloat() * 1000;
            alpha[i] = 70 + depth * 130;
        }
        paint.setColor(0xFFFFFFFF);
    }

    @Override
    long frameMs() {
        return 33; // ~30 fps: snow is slow and soft
    }

    @Override
    void draw(Canvas c, int w, int h, long now) {
        if (w <= 0 || h <= 0) return;
        float secs = now / 1000f;
        float span = h + 20 * dp;
        for (int i = 0; i < FLAKES; i++) {
            float y = ((phase[i] + secs * speed[i]) * dp) % span - 10 * dp;
            float fx = x[i] * w + (float) Math.sin(secs * 0.7f + phase[i]) * sway[i] * dp;
            paint.setAlpha((int) alpha[i]);
            c.drawCircle(fx, y, size[i] * dp, paint);
        }
    }
}
