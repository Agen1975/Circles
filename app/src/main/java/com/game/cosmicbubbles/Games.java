package com.game.cosmicbubbles;

import android.app.Activity;
import android.widget.Toast;

import com.game.cosmicbubbles.game.GameConfig;
import com.google.android.gms.games.PlayGames;

/** Play Games Services leaderboards. Every call is best-effort: failures never affect the game. */
final class Games {
    private static final int RC_LEADERBOARDS = 9004;

    private Games() {}

    /** Leaderboard for a mode, or 0 if the mode has none. */
    private static int leaderboard(GameConfig.Mode mode) {
        switch (mode) {
            case CLASSIC: return R.string.leaderboard_classic_high_score;
            case DAILY: return R.string.leaderboard_daily_puzzle;
            case BLITZ: return R.string.leaderboard_blitz_60s;
            default: return 0;
        }
    }

    /** Submits a finished game's score. Play keeps only each player's best, so resubmitting is harmless. */
    static void submit(Activity a, GameConfig.Mode mode, int score) {
        int id = leaderboard(mode);
        if (id == 0 || score <= 0) return;
        try {
            PlayGames.getGamesSignInClient(a).isAuthenticated().addOnSuccessListener(r -> {
                if (r.isAuthenticated()) PlayGames.getLeaderboardsClient(a).submitScore(a.getString(id), score);
            });
        } catch (RuntimeException ignored) {
            // Play services missing or outdated.
        }
    }

    /** Opens the list of all leaderboards, asking the player to sign in first if needed. */
    static void showLeaderboards(Activity a) {
        try {
            PlayGames.getGamesSignInClient(a).isAuthenticated().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult().isAuthenticated()) open(a);
                else PlayGames.getGamesSignInClient(a).signIn().addOnCompleteListener(s -> {
                    if (s.isSuccessful() && s.getResult().isAuthenticated()) open(a);
                    else unavailable(a);
                });
            });
        } catch (RuntimeException e) {
            unavailable(a);
        }
    }

    private static void open(Activity a) {
        PlayGames.getLeaderboardsClient(a).getAllLeaderboardsIntent()
                .addOnSuccessListener(i -> a.startActivityForResult(i, RC_LEADERBOARDS))
                .addOnFailureListener(e -> unavailable(a));
    }

    private static void unavailable(Activity a) {
        if (!a.isFinishing()) {
            Toast.makeText(a, "Leaderboards need you to be signed in to Google Play Games.", Toast.LENGTH_LONG).show();
        }
    }
}
