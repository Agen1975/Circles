package com.game.cosmicbubbles;

import android.app.Activity;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Google Play's in-app rating card, asked for after a good result (new best, 3 stars) once the player has
 * finished {@link #MIN_GAMES} real games on {@link #MIN_DAYS} different days, and at most every
 * {@link #GAP_DAYS} days. Play decides whether the card actually appears (it has its own quota) and never
 * tells us whether the player rated, so nothing may depend on the outcome. Play policy also forbids asking
 * "do you like the game?" first, so the card is shown directly.
 */
public final class Review {
    private static final int MIN_GAMES = 5;
    private static final int MIN_DAYS = 2;
    private static final long GAP_DAYS = 30;

    private Review() {}

    public static void maybeAsk(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Progress progress = Progress.get(a);
        if (progress.gamesPlayed() < MIN_GAMES || progress.playDays() < MIN_DAYS) return;
        long now = System.currentTimeMillis();
        if (now - progress.reviewAskedAt() < GAP_DAYS * 24 * 60 * 60 * 1000L) return;
        progress.setReviewAskedAt(now);
        ReviewManager manager = ReviewManagerFactory.create(a);
        manager.requestReviewFlow().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || a.isFinishing() || a.isDestroyed()) return;
            ReviewInfo info = task.getResult();
            manager.launchReviewFlow(a, info);
            FirebaseAnalytics.getInstance(a).logEvent("review_prompt", null);
        });
    }
}
