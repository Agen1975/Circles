package com.game.cosmicbubbles;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Everything kept between sessions: settings, bests, saves, tokens, XP, daily, journey, achievements. */
public final class Progress {
    private static final int START_TOKENS = 3;
    private final SharedPreferences p;

    private Progress(Context c) {
        p = c.getApplicationContext().getSharedPreferences("progress", Context.MODE_PRIVATE);
        if (!p.getBoolean("init", false)) {
            p.edit().putBoolean("init", true).putInt("undo", START_TOKENS).putInt("hint", START_TOKENS).apply();
        }
    }

    public static Progress get(Context c) {
        return new Progress(c);
    }

    // ---- settings
    public boolean sound() { return p.getBoolean("sound", true); }
    public boolean haptics() { return p.getBoolean("haptics", true); }
    public boolean colorblind() { return p.getBoolean("colorblind", false); }
    public void setSound(boolean v) { p.edit().putBoolean("sound", v).apply(); }
    public void setHaptics(boolean v) { p.edit().putBoolean("haptics", v).apply(); }
    public void setColorblind(boolean v) { p.edit().putBoolean("colorblind", v).apply(); }

    public Theme theme() {
        Theme t = Theme.byId(p.getString("theme", "space"));
        return isThemeUnlocked(t) ? t : Theme.ALL[0];
    }

    public void setTheme(Theme t) { p.edit().putString("theme", t.id).apply(); }

    public boolean isThemeUnlocked(Theme t) {
        return level() >= t.unlockLevel || p.getBoolean("theme_owned_" + t.id, false);
    }

    // ---- bests and saved games
    public int best(String key) { return p.getInt("best_" + key, 0); }

    /** Returns true if this is a new best. */
    public boolean submitBest(String key, int score) {
        if (score <= best(key)) return false;
        p.edit().putInt("best_" + key, score).apply();
        return true;
    }

    public String savedGame(String key) { return p.getString("save_" + key, null); }
    public void saveGame(String key, String data) { p.edit().putString("save_" + key, data).apply(); }
    public void clearGame(String key) { p.edit().remove("save_" + key).apply(); }

    // ---- tokens
    public int undoTokens() { return p.getInt("undo", 0); }
    public int hintTokens() { return p.getInt("hint", 0); }
    public void addUndo(int n) { p.edit().putInt("undo", undoTokens() + n).apply(); }
    public void addHint(int n) { p.edit().putInt("hint", hintTokens() + n).apply(); }

    public boolean useUndo() {
        if (undoTokens() <= 0) return false;
        addUndo(-1);
        return true;
    }

    public boolean useHint() {
        if (hintTokens() <= 0) return false;
        addHint(-1);
        return true;
    }

    // ---- XP and level: level L needs 100 * L * (L - 1) / 2 total XP (0, 100, 300, 600, ...)
    public int xp() { return p.getInt("xp", 0); }
    public int level() { return levelFor(xp()); }

    public static int levelFor(int xp) {
        int l = 1;
        while (xpForLevel(l + 1) <= xp) l++;
        return l;
    }

    public static int xpForLevel(int l) {
        return 100 * l * (l - 1) / 2;
    }

    /** Adds XP; returns the number of levels gained. */
    public int addXp(int n) {
        int before = level();
        p.edit().putInt("xp", xp() + Math.max(0, n)).apply();
        return level() - before;
    }

    // ---- stats
    public int totalCircles() { return p.getInt("circles", 0); }
    public void addCircles(int n) { p.edit().putInt("circles", totalCircles() + n).apply(); }
    public int gamesPlayed() { return p.getInt("games", 0); }
    public void addGame() { p.edit().putInt("games", gamesPlayed() + 1).apply(); }

    // ---- tutorial
    public boolean tutorialDone() { return p.getBoolean("tutorial", false); }
    public void setTutorialDone() { p.edit().putBoolean("tutorial", true).apply(); }

    // ---- daily
    public static int today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static int yesterday() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    public int dailyBest(int date) { return p.getInt("daily_" + date, -1); }

    public int dailyStreak() {
        int last = p.getInt("daily_last", 0);
        if (last != today() && last != yesterday()) return 0;
        return p.getInt("daily_streak", 0);
    }

    /**
     * Records a finished daily game. The first finish of a day extends the streak and grants
     * 1 undo + 1 hint. Returns true if that reward was granted.
     */
    public boolean recordDaily(int date, int score) {
        SharedPreferences.Editor e = p.edit();
        if (score > dailyBest(date)) e.putInt("daily_" + date, score);
        boolean first = p.getInt("daily_last", 0) != date && date == today();
        if (first) {
            int streak = p.getInt("daily_last", 0) == yesterday() ? p.getInt("daily_streak", 0) + 1 : 1;
            e.putInt("daily_last", date).putInt("daily_streak", streak);
            e.putInt("undo", undoTokens() + 1).putInt("hint", hintTokens() + 1);
        }
        e.apply();
        return first;
    }

    // ---- journey
    public int stars(int level) { return p.getInt("stars_" + level, 0); }

    public void setStars(int level, int stars) {
        if (stars > stars(level)) p.edit().putInt("stars_" + level, stars).apply();
    }

    public int journeyUnlocked() {
        int n = 1;
        while (stars(n) > 0) n++;
        return n;
    }

    public int totalStars(int levels) {
        int s = 0;
        for (int i = 1; i <= levels; i++) s += stars(i);
        return s;
    }

    // ---- achievements
    public boolean has(String id) { return p.getBoolean("ach_" + id, false); }

    /** Returns true if newly unlocked (and grants its hint token). */
    public boolean unlock(String id) {
        if (has(id)) return false;
        p.edit().putBoolean("ach_" + id, true).putInt("hint", hintTokens() + 1).apply();
        return true;
    }
}
