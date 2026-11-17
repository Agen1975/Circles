package com.game.cosmicbubbles;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Sound effects and haptics, both switchable in settings. */
public final class Feedback {
    public enum Sfx { PICK, DROP, POP, INVALID, BOMB, OVER, WIN, TICK, SPAWN }

    private final SoundPool pool;
    private final int[] ids = new int[Sfx.values().length];
    private final Vibrator vibrator;
    private final View view;
    public boolean sound = true;
    public boolean haptics = true;

    public Feedback(Context c, View view) {
        this.view = view;
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        int[] res = {R.raw.sfx_pick, R.raw.sfx_drop, R.raw.sfx_pop, R.raw.sfx_invalid, R.raw.sfx_bomb,
                R.raw.sfx_over, R.raw.sfx_win, R.raw.sfx_tick, R.raw.sfx_spawn};
        for (int i = 0; i < res.length; i++) ids[i] = pool.load(c, res[i], 1);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void play(Sfx s) {
        play(s, 1f, 1f);
    }

    /** rate 0.5..2.0 shifts pitch (used to make streak pops climb). */
    public void play(Sfx s, float rate, float volume) {
        if (!sound) return;
        pool.play(ids[s.ordinal()], volume, volume, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    /** Light tick: pick up, drop. */
    public void tick() {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    /** A circle completed. */
    public void confirm() {
        if (!haptics) return;
        view.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Invalid drop. */
    public void reject() {
        if (!haptics) return;
        view.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }

    /** Bigger moments: clusters, streaks, bombs. Strength 1..3. */
    public void burst(int strength) {
        if (!haptics || vibrator == null || !vibrator.hasVibrator()) return;
        long[] timings;
        int[] amps;
        if (strength >= 3) {
            timings = new long[]{0, 40, 50, 40, 50, 70};
            amps = new int[]{0, 200, 0, 220, 0, 255};
        } else if (strength == 2) {
            timings = new long[]{0, 35, 60, 55};
            amps = new int[]{0, 180, 0, 230};
        } else {
            timings = new long[]{0, 30, 50, 30};
            amps = new int[]{0, 140, 0, 160};
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
        }
    }

    public void release() {
        pool.release();
    }
}
