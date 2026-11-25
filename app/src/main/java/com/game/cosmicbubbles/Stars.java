package com.game.cosmicbubbles;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * A colourful starfield for the Space theme, matching the app icon: a faint field of fixed stars,
 * some that twinkle, a few four-point sparkles that flare now and then, and a soft violet nebula.
 * Every star has its own period and phase, so they never pulse together.
 */
final class Stars extends Backdrop {
    private static final int DOTS = 170, TWINKLERS = 48, SPARKLES = 13;
    private static final float TAU = (float) (Math.PI * 2);

    private static final int WHITE = 0xFFFFFFFF, ICE = 0xFFBFD6FF, LILAC = 0xFFCDA8FF,
            GOLD = 0xFFFFD36E, PINK = 0xFFFF6FC4, CYAN = 0xFF7FE3FF, VIOLET = 0xFFB27BFF;
    /** Dim background stars: mostly cool whites, as in the icon. */
    private static final int[] FIELD = {WHITE, ICE, LILAC, WHITE, ICE, PINK, GOLD};
    /** The bright ones carry the icon's colours. */
    private static final int[] BRIGHT = {GOLD, PINK, CYAN, VIOLET, WHITE};

    private final float[] dx = new float[DOTS], dy = new float[DOTS], dr = new float[DOTS];
    private final int[] dc = new int[DOTS];

    private final float[] tx = new float[TWINKLERS], ty = new float[TWINKLERS], tr = new float[TWINKLERS],
            tPeriod = new float[TWINKLERS], tPhase = new float[TWINKLERS];
    private final int[] tc = new int[TWINKLERS];

    private final float[] sx = new float[SPARKLES], sy = new float[SPARKLES], sSize = new float[SPARKLES],
            sPeriod = new float[SPARKLES], sPhase = new float[SPARKLES];
    private final int[] sc = new int[SPARKLES];

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Unit-radius glow per bright colour, placed per sparkle with a local matrix (no per-frame allocation). */
    private final RadialGradient[] glowShaders = new RadialGradient[BRIGHT.length];
    private final Matrix matrix = new Matrix();
    /** Four-point star of unit radius. */
    private final Path ray = new Path();
    private final float dp;

    private int w, h;
    private Shader nebulaA, nebulaB;

    Stars(float dp) {
        this.dp = dp;
        Random r = new Random(1969);
        for (int i = 0; i < DOTS; i++) {
            dx[i] = r.nextFloat();
            dy[i] = r.nextFloat();
            float depth = r.nextFloat();
            dr[i] = 0.6f + depth * depth * 1.4f;
            dc[i] = withAlpha(FIELD[r.nextInt(FIELD.length)], 70 + (int) (depth * 150));
        }
        for (int i = 0; i < TWINKLERS; i++) {
            tx[i] = r.nextFloat();
            ty[i] = r.nextFloat();
            tr[i] = 1.2f + r.nextFloat() * 1.4f;
            tc[i] = (i % 2 == 0) ? BRIGHT[r.nextInt(BRIGHT.length)] : FIELD[r.nextInt(FIELD.length)];
            tPeriod[i] = 2.5f + r.nextFloat() * 4.5f;
            tPhase[i] = r.nextFloat() * TAU;
        }
        for (int i = 0; i < SPARKLES; i++) {
            // Spread sparkles across horizontal bands so they don't clump.
            sx[i] = 0.05f + r.nextFloat() * 0.9f;
            sy[i] = (i + 0.15f + r.nextFloat() * 0.7f) / SPARKLES;
            sSize[i] = 7f + r.nextFloat() * 8f;
            sc[i] = i % BRIGHT.length;
            sPeriod[i] = 4f + r.nextFloat() * 5f;
            sPhase[i] = r.nextFloat() * TAU;
        }
        for (int i = 0; i < BRIGHT.length; i++) {
            glowShaders[i] = new RadialGradient(0, 0, 1,
                    new int[]{withAlpha(BRIGHT[i], 190), withAlpha(BRIGHT[i], 60), withAlpha(BRIGHT[i], 0)},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        }
        float t = 0.13f;
        ray.moveTo(0, -1);
        ray.lineTo(t, -t);
        ray.lineTo(1, 0);
        ray.lineTo(t, t);
        ray.lineTo(0, 1);
        ray.lineTo(-t, t);
        ray.lineTo(-1, 0);
        ray.lineTo(-t, -t);
        ray.close();
    }

    @Override
    long frameMs() {
        // Twinkles are slow; ~20 fps is smooth enough and cheaper than snow.
        return 50;
    }

    @Override
    void draw(Canvas c, int w, int h, long now) {
        if (w <= 0 || h <= 0) return;
        if (w != this.w || h != this.h) resize(w, h);
        float secs = now / 1000f;

        // Soft nebula haze, like the icon's purple glow. Reset alpha first: the paint's alpha scales the
        // shader, and the last sparkle drawn left it varying, which made the whole sky pulse.
        paint.setAlpha(255);
        paint.setShader(nebulaA);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(nebulaB);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        for (int i = 0; i < DOTS; i++) {
            paint.setColor(dc[i]);
            c.drawCircle(dx[i] * w, dy[i] * h, dr[i] * dp, paint);
        }

        for (int i = 0; i < TWINKLERS; i++) {
            float s = 0.5f + 0.5f * (float) Math.sin(secs * TAU / tPeriod[i] + tPhase[i]);
            float b = 0.3f + 0.7f * s * s * s;
            paint.setColor(withAlpha(tc[i], (int) (255 * b)));
            c.drawCircle(tx[i] * w, ty[i] * h, tr[i] * dp * (0.8f + 0.4f * b), paint);
        }

        for (int i = 0; i < SPARKLES; i++) {
            float s = 0.5f + 0.5f * (float) Math.sin(secs * TAU / sPeriod[i] + sPhase[i]);
            // Mostly a small glint; a sharp flare near the peak.
            float f = s * s * s * s * s * s;
            float b = 0.5f + 0.5f * f;
            drawSparkle(c, sx[i] * w, sy[i] * h, sSize[i] * dp * (0.6f + 0.4f * f), sc[i], b);
        }
    }

    private void drawSparkle(Canvas c, float x, float y, float size, int colorIndex, float b) {
        int color = BRIGHT[colorIndex];
        float glowR = size * 1.8f;
        matrix.setScale(glowR, glowR);
        matrix.postTranslate(x, y);
        glowShaders[colorIndex].setLocalMatrix(matrix);
        glow.setShader(glowShaders[colorIndex]);
        glow.setAlpha((int) (255 * b));
        c.drawCircle(x, y, glowR, glow);

        c.save();
        c.translate(x, y);
        c.scale(size, size);
        paint.setColor(withAlpha(color, (int) (255 * b)));
        c.drawPath(ray, paint);
        // A smaller, diagonal cross for the star's secondary glint.
        c.rotate(45);
        c.scale(0.45f, 0.45f);
        c.drawPath(ray, paint);
        c.restore();

        paint.setColor(withAlpha(WHITE, (int) (255 * b)));
        c.drawCircle(x, y, Math.max(1f, size * 0.12f), paint);
    }

    private void resize(int w, int h) {
        this.w = w;
        this.h = h;
        nebulaA = new RadialGradient(w * 0.12f, h * 0.22f, Math.max(w, h) * 0.55f,
                new int[]{0x488A4DFF, 0x1A8A4DFF, 0x008A4DFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        nebulaB = new RadialGradient(w * 0.9f, h * 0.78f, Math.max(w, h) * 0.6f,
                new int[]{0x3AD2368C, 0x14D2368C, 0x00D2368C}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    private static int withAlpha(int color, int a) {
        return Color.argb(Math.max(0, Math.min(255, a)), Color.red(color), Color.green(color), Color.blue(color));
    }
}
