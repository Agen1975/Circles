package com.game.cosmicbubbles;

import java.util.Locale;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import com.game.cosmicbubbles.game.GameConfig;
import com.game.cosmicbubbles.game.GameState;
import com.game.cosmicbubbles.game.Levels;
import com.google.firebase.analytics.FirebaseAnalytics;

/** Hosts {@link GameView}. The game is described by mode + number + seed in the intent. */
public class GameActivity extends Activity implements GameView.Host {
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_NUMBER = "number";
    private static final String EXTRA_SEED = "seed";
    private static final String EXTRA_RESUME = "resume";
    public static final int BLITZ_SECONDS = 60;

    private GameView view;
    private GameConfig cfg;
    /** Time actually spent on this screen, for game_leave. */
    private long playedMs, resumedAt;
    /** Moves at the last game_leave, so leaving again without playing doesn't log twice. */
    private int leaveLoggedAtMoves = -1;

    public static Intent intent(Context c, GameConfig cfg, boolean resume) {
        return new Intent(c, GameActivity.class)
                .putExtra(EXTRA_MODE, cfg.mode.name())
                .putExtra(EXTRA_NUMBER, cfg.number)
                .putExtra(EXTRA_SEED, cfg.seed)
                .putExtra(EXTRA_RESUME, resume);
    }

    /** The game the intent describes, or null if it names no valid mode (e.g. launched by a test crawler). */
    static GameConfig configFrom(Intent i) {
        GameConfig.Mode mode;
        try {
            mode = GameConfig.Mode.valueOf(i.getStringExtra(EXTRA_MODE));
        } catch (RuntimeException e) {
            return null;
        }
        int number = i.getIntExtra(EXTRA_NUMBER, 0);
        long seed = i.getLongExtra(EXTRA_SEED, System.nanoTime());
        switch (mode) {
            case DAILY: return GameConfig.daily(number);
            case ZEN: return GameConfig.zen(seed);
            case BLITZ: return GameConfig.blitz(seed, BLITZ_SECONDS);
            case JOURNEY: return Levels.journey(number);
            case TUTORIAL: return Levels.tutorial(number);
            default: return GameConfig.classic(seed);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        // After a process restart, resume what was saved in onPause instead of starting over.
        boolean resume = i.getBooleanExtra(EXTRA_RESUME, false) || savedInstanceState != null;
        GameConfig cfg = configFrom(i);
        if (cfg == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        this.cfg = cfg;
        view = new GameView(this, this, cfg, resume);
        setContentView(view);
        if (savedInstanceState == null && !resume) logGameStart(cfg);
        Ui.systemBars(this, Progress.get(this).theme());
    }

    /** game_start for every new game; tutorial_begin when the first tutorial step opens. */
    private void logGameStart(GameConfig cfg) {
        FirebaseAnalytics fa = FirebaseAnalytics.getInstance(this);
        fa.logEvent("game_start", gameParams(cfg));
        if (cfg.mode == GameConfig.Mode.TUTORIAL && cfg.number == 1) {
            fa.logEvent(FirebaseAnalytics.Event.TUTORIAL_BEGIN, null);
        }
    }

    /** mode, plus level for Journey levels and tutorial steps. */
    private static Bundle gameParams(GameConfig cfg) {
        Bundle b = new Bundle();
        b.putString("mode", cfg.mode.name().toLowerCase(Locale.ROOT));
        if (cfg.mode == GameConfig.Mode.JOURNEY || cfg.mode == GameConfig.Mode.TUTORIAL) b.putLong("level", cfg.number);
        return b;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view == null) return;
        view.save();
        view.setPaused(true);
        if (resumedAt > 0) playedMs += SystemClock.elapsedRealtime() - resumedAt;
        resumedAt = 0;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (view != null) logGameLeave();
    }

    /**
     * game_leave: the player exited an unfinished game or switched away from the app mid-game.
     * Shows how far players get before giving up (game_end only covers games that reach game over).
     */
    private void logGameLeave() {
        if (!view.inProgress()) return;
        GameState st = view.state();
        if (st.moves == leaveLoggedAtMoves) return;
        leaveLoggedAtMoves = st.moves;
        Bundle b = gameParams(cfg);
        b.putLong("score", st.score);
        b.putLong("moves", st.moves);
        b.putLong("circles", st.circlesTotal);
        b.putLong("seconds", playedMs / 1000);
        b.putString("how", isFinishing() ? "exit" : "background");
        FirebaseAnalytics.getInstance(this).logEvent("game_leave", b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view == null) return;
        view.setPaused(false);
        resumedAt = SystemClock.elapsedRealtime();
        Reminders.onAppOpened(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (view != null) view.release();
    }

    @Override
    public void openMenu() {
        finish();
    }

    @Override
    public void startGame(GameConfig cfg, boolean resume) {
        startActivity(intent(this, cfg, resume));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void afterGame(Runnable next) {
        Ads.afterGame(this, next);
    }

    @Override
    public boolean rewardedReady() {
        return Ads.rewardedReady();
    }

    @Override
    public void watchAd(Runnable onReward) {
        Ads.showRewarded(this, () -> runOnUiThread(() -> {
            onReward.run();
            view.invalidate();
        }));
    }

    @Override
    public void share(String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.share_title)));
    }

    @Override
    public void awardUnlocked(Achievements.Def d) {
        Games.unlock(this, d);
    }

    @Override
    public void themeUnlocked(Theme t) {
        Bundle b = new Bundle();
        b.putString("theme", t.id);
        FirebaseAnalytics.getInstance(this).logEvent("unlock_theme", b);
    }

    @Override
    public void gameEnded(GameConfig cfg, int score, boolean won) {
        Games.submit(this, cfg.mode, score);
        Bundle b = gameParams(cfg);
        b.putLong("score", score);
        b.putLong("success", won ? 1 : 0);
        FirebaseAnalytics fa = FirebaseAnalytics.getInstance(this);
        fa.logEvent("game_end", b);
        // New players rarely go back to the menu, so ask about reminders here too, once the results card is up.
        if (cfg.mode != GameConfig.Mode.TUTORIAL) {
            view.postDelayed(() -> Reminders.maybeAskPermission(this), 2500);
        }
    }

    /** tutorial_complete when the first-launch tutorial is finished; tutorial_skip (with the step) when skipped. */
    @Override
    public void tutorialFinished(int step, boolean skipped) {
        Bundle b = new Bundle();
        b.putLong("level", step);
        FirebaseAnalytics.getInstance(this)
                .logEvent(skipped ? "tutorial_skip" : FirebaseAnalytics.Event.TUTORIAL_COMPLETE, b);
    }
}
