package com.game.cosmicbubbles;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.game.cosmicbubbles.game.Levels;

/** Level map: completed levels show stars, the next one is highlighted, later ones are locked. */
public class JourneyActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        Progress progress = Progress.get(this);
        Theme t = progress.theme();
        int pad = Ui.dp(this, 20);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, t, "Journey", 30, true, false);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        TextView sub = Ui.text(this, t, progress.totalStars(Levels.JOURNEY_COUNT) + " of " + Levels.JOURNEY_COUNT * 3
                + " stars", 14, false, true);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, Ui.dp(this, 16));
        col.addView(sub);

        int columns = 5;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        int unlocked = progress.journeyUnlocked();
        int size = (getResources().getDisplayMetrics().widthPixels - 2 * pad) / columns;
        for (int n = 1; n <= Levels.JOURNEY_COUNT; n++) {
            final int level = n;
            int stars = progress.stars(n);
            boolean open = n <= unlocked;
            TextView b = new TextView(this);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            String starText = stars == 0 ? "" : repeat("★", stars) + repeat("☆", 3 - stars);
            b.setText(n + "\n" + starText);
            b.setTextSize(15);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 14));
            boolean next = n == unlocked;
            bg.setColor(next ? t.accent : t.panel);
            b.setBackground(bg);
            b.setTextColor(next ? (t.light ? Color.WHITE : t.bgTop) : (open ? t.text : t.textDim));
            b.setAlpha(open ? 1f : 0.45f);
            if (open) {
                b.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    startActivity(GameActivity.intent(this, Levels.journey(level), false));
                    Ui.fade(this);
                });
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            int m = Ui.dp(this, 4);
            lp.width = size - 2 * m;
            lp.height = size - 2 * m;
            lp.setMargins(m, m, m, m);
            grid.addView(b, lp);
        }
        col.addView(grid);
        col.addView(Ui.button(this, t, "Back", null, false, v -> finish()));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.background(t));
        scroll.addView(col);
        Ui.padForInsets(scroll);
        setContentView(scroll);
        Ui.systemBars(this, t);
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }
}
