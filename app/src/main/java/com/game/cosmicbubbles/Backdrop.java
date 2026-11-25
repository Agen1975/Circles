package com.game.cosmicbubbles;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;

/**
 * Animated decoration drawn over a theme's background gradient (falling snow, twinkling stars).
 * Implementations are stateless in time: what they draw is a function of the clock, so nothing is
 * stepped per frame and it looks the same after a pause.
 */
abstract class Backdrop {
    /** Redraw interval in ms. Backdrops are slow and soft, so a low frame rate is plenty and saves battery. */
    abstract long frameMs();

    abstract void draw(Canvas c, int w, int h, long now);

    /** The theme's decoration, or null for a plain gradient. */
    static Backdrop forTheme(Theme t, float dp) {
        if (t.snow) return new Snow(dp);
        if (t.stars) return new Stars(dp);
        return null;
    }

    /** A theme background for plain screens (menus): gradient plus the theme's decoration, if any. */
    static Drawable background(Theme t, float dp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.bgTop, t.bgBottom});
        Backdrop b = forTheme(t, dp);
        return b != null ? new Bg(g, b) : g;
    }

    private static final class Bg extends Drawable implements Runnable {
        private final Drawable base;
        private final Backdrop decor;

        Bg(Drawable base, Backdrop decor) {
            this.base = base;
            this.decor = decor;
        }

        @Override
        public void draw(Canvas c) {
            base.setBounds(getBounds());
            base.draw(c);
            // Draw in view coordinates so the decoration stays put while a ScrollView scrolls.
            decor.draw(c, getBounds().width(), getBounds().height(), SystemClock.uptimeMillis());
            unscheduleSelf(this);
            if (isVisible()) scheduleSelf(this, SystemClock.uptimeMillis() + decor.frameMs());
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
