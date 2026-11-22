package com.game.cosmicbubbles;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;

import java.util.Random;

/**
 * Gently falling snowflakes for seasonal themes. Stateless: each flake's position is a function
 * of the clock, so nothing is stepped per frame and it looks the same after a pause.
 */
final class Snow {
    /** Redraw interval. Snow is slow and soft, so ~30 fps is plenty and saves battery. */
    static final long FRAME_MS = 33;
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

    /** A theme background for plain screens (menus): gradient plus, for snowy themes, falling snow. */
    static Drawable background(Theme t, float dp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.bgTop, t.bgBottom});
        return t.snow ? new Bg(g, new Snow(dp)) : g;
    }

    private static final class Bg extends Drawable implements Runnable {
        private final Drawable base;
        private final Snow snow;

        Bg(Drawable base, Snow snow) {
            this.base = base;
            this.snow = snow;
        }

        @Override
        public void draw(Canvas c) {
            base.setBounds(getBounds());
            base.draw(c);
            // Draw in view coordinates so the snow stays put while a ScrollView scrolls.
            snow.draw(c, getBounds().width(), getBounds().height(), SystemClock.uptimeMillis());
            unscheduleSelf(this);
            if (isVisible()) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }

        @Override
        public void run() {
            invalidateSelf();
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (visible) invalidateSelf();
            else unscheduleSelf(this);
            return changed;
        }

        @Override
        public void setAlpha(int a) {
            base.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            base.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
