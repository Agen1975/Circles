package com.game.cosmicbubbles;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building themed screens in code (no XML layouts needed). */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(Theme t) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.bgTop, t.bgBottom});
    }

    static TextView text(Context c, Theme t, String s, float sp, boolean bold, boolean dim) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(dim ? t.textDim : t.text);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Rounded, tactile button with ripple; primary uses the accent colour. */
    static TextView button(Context c, Theme t, String label, String sub, boolean primary, View.OnClickListener l) {
        TextView v = new TextView(c);
        v.setText(sub == null ? label : label + "\n" + sub);
        v.setTextSize(17);
        v.setGravity(Gravity.CENTER);
        int fg = primary ? (t.light ? Color.WHITE : t.bgTop) : t.text;
        v.setTextColor(fg);
        if (sub != null) {
            android.text.SpannableString s = new android.text.SpannableString(v.getText());
            int start = label.length() + 1;
            s.setSpan(new android.text.style.RelativeSizeSpan(0.75f), start, s.length(), 0);
            s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, label.length(), 0);
            v.setText(s);
        }
        if (sub == null) v.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(c, 28));
        shape.setColor(primary ? t.accent : t.panel);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), shape, null));
        int pad = dp(c, 14);
        v.setPadding(pad, pad, pad, pad);
        v.setMinHeight(dp(c, 56));
        v.setOnClickListener(x -> {
            x.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            l.onClick(x);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    /** Apply system-bar insets as padding (the app draws edge to edge). */
    static void padForInsets(View root) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(android.view.WindowInsets.Type.systemBars());
                top = i.top;
                bottom = i.bottom;
            } else {
                top = in.getSystemWindowInsetTop();
                bottom = in.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t + top, r, b + bottom);
            return in;
        });
    }

    /** Dark status- and navigation-bar icons on light themes, light icons on dark ones. Call after setContentView. */
    @SuppressWarnings("deprecation")
    static void systemBars(Activity a, Theme t) {
        View decor = a.getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = decor.getWindowInsetsController();
            if (c == null) return;
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(t.light ? mask : 0, mask);
        } else {
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
            int f = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(t.light ? f | mask : f & ~mask);
        }
    }

    static void fade(Activity a) {
        a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
