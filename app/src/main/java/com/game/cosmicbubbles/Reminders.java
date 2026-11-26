package com.game.cosmicbubbles;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * "Come back" reminders. Every time the app is opened, a reminder is (re)scheduled for 6 pm on the
 * next day, so only players who skip a day see one. After {@link #MAX_UNANSWERED} reminders without
 * the app being opened, they stop until the next visit.
 */
public final class Reminders {
    private static final String CHANNEL = "reminders";
    private static final String WORK = "come_back_reminder";
    private static final int HOUR = 18;
    private static final int MAX_UNANSWERED = 2;
    private static final int NOTIFICATION_ID = 1;
    private static final int PERMISSION_REQUEST = 7001;

    private Reminders() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }

    /** Call whenever the app comes to the foreground. */
    public static void onAppOpened(Context c) {
        prefs(c).edit().putInt("unanswered", 0).apply();
        NotificationManagerCompat.from(c).cancel(NOTIFICATION_ID);
        schedule(c);
    }

    /** Schedules the next reminder for 6 pm tomorrow, or cancels it if reminders are off. */
    static void schedule(Context c) {
        WorkManager wm = WorkManager.getInstance(c.getApplicationContext());
        if (!Progress.get(c).reminders()) {
            wm.cancelUniqueWork(WORK);
            return;
        }
        Calendar at = Calendar.getInstance();
        at.add(Calendar.DAY_OF_MONTH, 1);
        at.set(Calendar.HOUR_OF_DAY, HOUR);
        at.set(Calendar.MINUTE, 0);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        long delay = Math.max(0, at.getTimeInMillis() - System.currentTimeMillis());
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req);
    }

    public static void setEnabled(Activity a, boolean on) {
        Progress.get(a).setReminders(on);
        if (on) requestPermission(a);
        schedule(a);
    }

    // ---- permission (Android 13+)

    private static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static void requestPermission(Activity a) {
        if (!hasPermission(a)) a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST);
    }

    /**
     * Asks whether the player wants reminders with our own dialog first; only "Yes" opens the system permission
     * dialog, which Android lets an app show just a couple of times. Asked after the first finished real game (on its
     * results screen, or on a menu visit), and, after "Not now", once more after the player finishes a daily puzzle.
     */
    public static void maybeAskPermission(Activity a) {
        if (a.isFinishing() || a.isDestroyed() || hasPermission(a)) return;
        Progress progress = Progress.get(a);
        SharedPreferences p = prefs(a);
        int asks = p.getInt("soft_asks", 0);
        // "asked": the system dialog was already shown (also set by versions before the soft prompt).
        if (!progress.reminders() || p.getBoolean("asked", false) || asks >= 2) return;
        if (progress.gamesPlayed() < 1) return;
        if (asks == 1 && progress.dailyBest(Progress.today()) < 0) return;
        p.edit().putInt("soft_asks", asks + 1).apply();
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(asks == 0 ? "Daily reminders?" : "Keep your streak?")
                .setMessage(asks == 0
                        ? "Get a reminder at 6 pm when a new daily puzzle is ready. Only on days you haven't played."
                        : "A reminder at 6 pm on days you haven't played helps you keep your daily streak going.")
                .setPositiveButton("Yes, remind me", (d, w) -> {
                    p.edit().putBoolean("asked", true).apply();
                    requestPermission(a);
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    // ---- the notification

    private static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("A nudge at 6 pm if you have not played that day");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private static String message(Context c, int unanswered) {
        Progress p = Progress.get(c);
        int streak = p.dailyStreak();
        boolean dailyDone = p.dailyBest(Progress.today()) >= 0;
        if (streak > 0 && !dailyDone) return "Keep your " + streak + "-day streak going! Today's puzzle is waiting.";
        Season season = Season.current();
        if (season != null && unanswered == 0 && !seasonalUnlocked(p, season)) {
            return season.name + " is here! Finish a daily puzzle by " + season.endLabel + " to unlock the festive theme.";
        }
        if (unanswered == 0) return "Today's daily puzzle is ready. Can you beat it?";
        return "Your circles miss you. Fancy a quick game?";
    }

    private static boolean seasonalUnlocked(Progress p, Season s) {
        for (Theme t : Theme.ALL) if (t.season == s && !p.isThemeUnlocked(t)) return false;
        return true;
    }

    static void show(Context c) {
        SharedPreferences p = prefs(c);
        int unanswered = p.getInt("unanswered", 0);
        if (!Progress.get(c).reminders() || unanswered >= MAX_UNANSWERED) return;
        if (hasPermission(c) && NotificationManagerCompat.from(c).areNotificationsEnabled()) {
            createChannel(c);
            Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String text = message(c, unanswered);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_circles)
                    .setColor(0xFF3DDCFF)
                    .setContentTitle(c.getString(R.string.app_name))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            try {
                NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, b.build());
            } catch (SecurityException ignored) {
                // permission revoked between the check and the post
            }
        }
        p.edit().putInt("unanswered", unanswered + 1).apply();
        if (unanswered + 1 < MAX_UNANSWERED) schedule(c);
    }

    public static class ReminderWorker extends Worker {
        public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            show(getApplicationContext());
            return Result.success();
        }
    }
}
