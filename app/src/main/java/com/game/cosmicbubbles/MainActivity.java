package com.game.cosmicbubbles;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.game.cosmicbubbles.game.GameConfig;
import com.game.cosmicbubbles.game.GameState;
import com.game.cosmicbubbles.game.Levels;

/** Main menu: modes, progress, achievements, settings. Rebuilt on every resume so numbers stay fresh. */
public class MainActivity extends Activity {
    private Progress progress;
    private boolean firstLaunchHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        progress = Progress.get(this);
        Store.get(this);
        if (!progress.tutorialDone() && savedInstanceState == null) {
            firstLaunchHandled = true;
            startActivity(GameActivity.intent(this, Levels.tutorial(1), false));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.get(this).setListener(msg -> {
            if (msg != null) android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
            build();
        });
        Store.get(this).restore();
        build();
        Games.syncAchievements(this);
        // Not during the first-launch tutorial: the consent form must appear over a visible screen.
        if (progress.tutorialDone()) {
            Ads.start(this);
            Reminders.maybeAskPermission(this);
        }
        Reminders.onAppOpened(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.get(this).setListener(null);
    }

    private void build() {
        Theme t = progress.theme();
        int pad = Ui.dp(this, 24);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, t, getString(R.string.app_name), 36, true, false);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        TextView tag = Ui.text(this, t, "Complete the circles", 15, false, true);
        tag.setGravity(Gravity.CENTER);
        col.addView(tag);

        // Player level and XP progress
        int level = progress.level(), xp = progress.xp();
        int lo = Progress.xpForLevel(level), hi = Progress.xpForLevel(level + 1);
        TextView lv = Ui.text(this, t, "Level " + level + "  ·  " + (xp - lo) + " / " + (hi - lo) + " XP", 14, true, false);
        lv.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 6));
        col.addView(lv);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(hi - lo);
        bar.setProgress(xp - lo);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(t.accent));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(t.panel));
        col.addView(bar);
        TextView stats = Ui.text(this, t, (progress.premium() ? "Premium  ·  " : "") + "Undos " + progress.undoTokens() + "  ·  Hints " + progress.hintTokens()
                + "  ·  Daily streak " + progress.dailyStreak(), 13, false, true);
        stats.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        col.addView(stats);

        View banner = seasonBanner(t);
        if (banner != null) col.addView(banner);

        // Continue
        String saved = progress.savedGame("classic");
        GameState s = saved == null ? null : GameState.decode(saved, GameConfig.SIZE);
        if (s != null && !s.over) {
            col.addView(Ui.button(this, t, "Continue", "Classic · " + s.score + " points", true,
                    v -> play(GameConfig.classic(System.nanoTime()), true)));
        }
        col.addView(Ui.button(this, t, "Classic", "Best " + progress.best("classic"), s == null || s.over,
                v -> {
                    progress.clearGame("classic");
                    play(GameConfig.classic(System.nanoTime()), false);
                }));

        int today = Progress.today();
        int dailyBest = progress.dailyBest(today);
        col.addView(Ui.button(this, t, "Daily puzzle", dailyBest >= 0 ? "Done today · " + dailyBest + " points"
                        : "Same board for everyone · 30 moves", false,
                v -> play(GameConfig.daily(today), true)));
        col.addView(Ui.button(this, t, "Journey", progress.totalStars(Levels.JOURNEY_COUNT) + " / "
                        + (Levels.JOURNEY_COUNT * 3) + " stars · level " + Math.min(progress.journeyUnlocked(), Levels.JOURNEY_COUNT),
                false, v -> {
                    startActivity(new android.content.Intent(this, JourneyActivity.class));
                    Ui.fade(this);
                }));
        col.addView(Ui.button(this, t, "Blitz", GameActivity.BLITZ_SECONDS + " seconds · best " + progress.best("blitz"),
                false, v -> play(GameConfig.blitz(System.nanoTime(), GameActivity.BLITZ_SECONDS), false)));
        col.addView(Ui.button(this, t, "Zen", "No game over · relax", false,
                v -> play(GameConfig.zen(System.nanoTime()), true)));
        col.addView(Ui.button(this, t, "Leaderboards", "Classic · Daily puzzle · Blitz on Google Play Games",
                false, v -> Games.showLeaderboards(this)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        col.addView(Ui.button(this, t, "Shop", progress.premium() ? "Premium owned · token packs"
                : "Remove ads · all themes · tokens", false, v -> showShop()));
        TextView how = Ui.button(this, t, "How to play", null, false, v -> play(Levels.tutorial(1), false));
        TextView ach = Ui.button(this, t, "Awards", null, false, v -> showAchievements());
        TextView set = Ui.button(this, t, "Settings", null, false, v -> showSettings());
        for (TextView b : new TextView[]{how, ach, set}) {
            b.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            row.addView(b, lp);
        }
        col.addView(row);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.background(this, t));
        scroll.addView(col);
        Ui.padForInsets(scroll);
        setContentView(scroll);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        Ui.systemBars(this, t);
    }

    /** While a season runs: how to unlock its theme, or a shortcut to switch to it. */
    private View seasonBanner(Theme current) {
        Season s = Season.current();
        if (s == null) return null;
        for (Theme st : Theme.ALL) {
            if (st.season != s || st == current) continue;
            if (!progress.isThemeUnlocked(st)) {
                return Ui.button(this, current, "❄  " + s.name + "  ❄", "Finish a daily puzzle to unlock the festive theme · by "
                        + s.endLabel, false, v -> play(GameConfig.daily(Progress.today()), true));
            }
            return Ui.button(this, current, "❄  " + s.name + "  ❄", "Your festive theme is ready · tap to switch", false, v -> {
                progress.setTheme(st);
                build();
            });
        }
        return null;
    }

    private void play(GameConfig cfg, boolean resume) {
        startActivity(GameActivity.intent(this, cfg, resume));
        Ui.fade(this);
    }

    private void showAchievements() {
        StringBuilder b = new StringBuilder();
        int got = 0;
        for (Achievements.Def d : Achievements.ALL) {
            boolean has = progress.has(d.id);
            if (has) got++;
            b.append(has ? "✓  " : "○  ").append(d.title).append("\n      ").append(d.desc).append("\n\n");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Awards  " + got + "/" + Achievements.ALL.length)
                .setMessage(b.toString().trim() + "\n\nEach award gives 1 hint.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Play Games", (dlg, w) -> Games.showAchievements(this))
                .show();
    }

    private void showSettings() {
        String[] items = {"Sound", "Haptics", "Colour-blind symbols", "Daily reminder (6 pm)"};
        boolean[] checked = {progress.sound(), progress.haptics(), progress.colorblind(), progress.reminders()};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Settings")
                .setMultiChoiceItems(items, checked, (d, which, on) -> {
                    if (which == 0) progress.setSound(on);
                    else if (which == 1) progress.setHaptics(on);
                    else if (which == 2) progress.setColorblind(on);
                    else Reminders.setEnabled(this, on);
                })
                .setNeutralButton("Theme", (d, w) -> showThemes())
                .setNegativeButton(Ads.privacyOptionsRequired(this) ? "Privacy" : null,
                        (d, w) -> Ads.showPrivacyOptions(this))
                .setPositiveButton("Done", null)
                .show();
    }

    private void showShop() {
        Store store = Store.get(this);
        if (!store.ready()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Shop")
                    .setMessage("The shop is not available right now. Check your connection and that you are "
                            + "signed in to Google Play.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        if (!progress.premium() && store.price(Store.PREMIUM) != null) {
            ids.add(Store.PREMIUM);
            labels.add("Premium: no ads, all themes, +10 undos & hints   " + store.price(Store.PREMIUM));
        }
        if (store.price(Store.TOKENS_SMALL) != null) {
            ids.add(Store.TOKENS_SMALL);
            labels.add("5 undos + 5 hints   " + store.price(Store.TOKENS_SMALL));
        }
        if (store.price(Store.TOKENS_LARGE) != null) {
            ids.add(Store.TOKENS_LARGE);
            labels.add("20 undos + 20 hints   " + store.price(Store.TOKENS_LARGE));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Shop")
                .setItems(labels.toArray(new String[0]), (d, which) -> store.buy(this, ids.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showThemes() {
        // Locked seasonal themes are listed only while their season runs.
        java.util.List<Theme> list = new java.util.ArrayList<>();
        for (Theme th : Theme.ALL) {
            if (th.season == null || th.season.active() || progress.isThemeUnlocked(th)) list.add(th);
        }
        Theme[] all = list.toArray(new Theme[0]);
        String[] names = new String[all.length];
        int current = 0;
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].name + (progress.isThemeUnlocked(all[i]) ? "" : "  (" + lockedHint(all[i]) + ")");
            if (all[i].id.equals(progress.theme().id)) current = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Theme")
                .setSingleChoiceItems(names, current, (d, which) -> {
                    if (progress.isThemeUnlocked(all[which])) {
                        progress.setTheme(all[which]);
                        d.dismiss();
                        build();
                    } else {
                        String how = all[which].season != null ? "Finish a daily puzzle by " + all[which].season.endLabel
                                : "Reach level " + all[which].unlockLevel;
                        android.widget.Toast.makeText(this, how + " to unlock " + all[which].name,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static String lockedHint(Theme t) {
        return t.season != null ? "daily puzzle by " + t.season.endLabel : "unlocks at level " + t.unlockLevel;
    }
}
