package com.game.cosmicbubbles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.game.cosmicbubbles.game.GameConfig;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.tasks.Task;

import java.util.Locale;

/** Play Games Services leaderboards and achievements. Every call is best-effort: failures never affect the game. */
final class Games {
    private static final int RC_PLAY_GAMES = 9004;
    private static boolean synced;

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

    /**
     * Play Games achievement for an award, or 0 if games-ids.xml has none. Play Console names the resource after the
     * achievement's name ("500 club" becomes achievement_500_club), so each award needs a Play achievement with the
     * same name as its title. res/raw/keep.xml stops resource shrinking from removing these strings.
     */
    private static int achievement(Context c, Achievements.Def d) {
        String slug = d.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return c.getResources().getIdentifier("achievement_" + slug, "string", c.getPackageName());
    }

    /** Submits a finished game's score. Play keeps only each player's best, so resubmitting is harmless. */
    static void submit(Activity a, GameConfig.Mode mode, int score) {
        int id = leaderboard(mode);
        if (id == 0 || score <= 0) return;
        ifSignedIn(a, () -> PlayGames.getLeaderboardsClient(a).submitScore(a.getString(id), score));
    }

    /** Unlocks the Play Games achievement for an award the player just earned. */
    static void unlock(Activity a, Achievements.Def d) {
        int id = achievement(a, d);
        if (id == 0) return;
        ifSignedIn(a, () -> PlayGames.getAchievementsClient(a).unlock(a.getString(id)));
    }

    /**
     * Once per app launch, unlocks every award already earned on this device: ones from before this feature
     * existed and ones earned while signed out. Unlocking an achievement twice is harmless.
     */
    static void syncAchievements(Activity a) {
        if (synced) return;
        ifSignedIn(a, () -> {
            synced = true;
            Progress p = Progress.get(a);
            for (Achievements.Def d : Achievements.ALL) {
                int id = achievement(a, d);
                if (id != 0 && p.has(d.id)) PlayGames.getAchievementsClient(a).unlock(a.getString(id));
            }
        });
    }

    /** Opens the list of all leaderboards, asking the player to sign in first if needed. */
    static void showLeaderboards(Activity a) {
        withSignIn(a, () -> open(a, PlayGames.getLeaderboardsClient(a).getAllLeaderboardsIntent()));
    }

    /** Opens the Play Games achievements screen, asking the player to sign in first if needed. */
    static void showAchievements(Activity a) {
        withSignIn(a, () -> {
            syncAchievements(a);
            open(a, PlayGames.getAchievementsClient(a).getAchievementsIntent());
        });
    }

    private static void ifSignedIn(Activity a, Runnable action) {
        try {
            PlayGames.getGamesSignInClient(a).isAuthenticated().addOnSuccessListener(r -> {
                if (r.isAuthenticated()) action.run();
            });
        } catch (RuntimeException ignored) {
            // Play services missing or outdated.
        }
    }

    private static void withSignIn(Activity a, Runnable action) {
        try {
            PlayGames.getGamesSignInClient(a).isAuthenticated().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult().isAuthenticated()) action.run();
                else PlayGames.getGamesSignInClient(a).signIn().addOnCompleteListener(s -> {
                    if (s.isSuccessful() && s.getResult().isAuthenticated()) action.run();
                    else unavailable(a);
                });
            });
        } catch (RuntimeException e) {
            unavailable(a);
        }
    }

    private static void open(Activity a, Task<Intent> intent) {
        intent.addOnSuccessListener(i -> a.startActivityForResult(i, RC_PLAY_GAMES))
                .addOnFailureListener(e -> unavailable(a));
    }

    private static void unavailable(Activity a) {
        if (!a.isFinishing()) {
            Toast.makeText(a, "This needs you to be signed in to Google Play Games.", Toast.LENGTH_LONG).show();
        }
    }
}
