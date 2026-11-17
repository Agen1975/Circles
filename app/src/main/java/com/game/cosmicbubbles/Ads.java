package com.game.cosmicbubbles;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AdMob: consent (UMP), an interstitial between games, and optional rewarded ads for tokens.
 *
 * Rules that protect retention: interstitials never mid-game, never in the first
 * {@link #FREE_GAMES} games, at most once every {@link #GAMES_BETWEEN} finished games,
 * and never for Premium players. Rewarded ads are always the player's choice.
 */
public final class Ads {
    private static final int FREE_GAMES = 3;
    private static final int GAMES_BETWEEN = 3;

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static InterstitialAd interstitial;
    private static RewardedAd rewarded;
    private static boolean loadingInterstitial, loadingRewarded;

    private Ads() {
    }

    private static boolean configured() {
        return !BuildConfig.AD_INTERSTITIAL.isEmpty() || !BuildConfig.AD_REWARDED.isEmpty();
    }

    /** Call from the first activity. Shows the consent form where the law requires it. */
    public static void start(Activity a) {
        if (!configured() || started.get()) return;
        // Wait until the menu has drawn: starting the SDK's WebViews during the first frames can
        // stall the app's rendering (seen as a black screen on the emulator).
        a.getWindow().getDecorView().postDelayed(() -> {
            if (!a.isFinishing() && !a.isDestroyed()) startNow(a);
        }, 2500);
    }

    private static void startNow(Activity a) {
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(a);
        info.requestConsentInfoUpdate(a, new ConsentRequestParameters.Builder().build(),
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, err -> initIfAllowed(a)),
                err -> initIfAllowed(a));
        initIfAllowed(a); // consent from a previous session
    }

    /** Settings needs a "Privacy choices" entry when this is true (GDPR regions). */
    public static boolean privacyOptionsRequired(Context c) {
        return configured() && UserMessagingPlatform.getConsentInformation(c).getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public static void showPrivacyOptions(Activity a) {
        UserMessagingPlatform.showPrivacyOptionsForm(a, err -> { });
    }

    private static void initIfAllowed(Activity a) {
        if (!UserMessagingPlatform.getConsentInformation(a).canRequestAds()) return;
        if (!started.compareAndSet(false, true)) return;
        Context app = a.getApplicationContext();
        new Thread(() -> MobileAds.initialize(app, status -> {
            a.runOnUiThread(() -> preload(app));
        })).start();
    }

    private static void preload(Context c) {
        if (!started.get()) return;
        if (interstitial == null && !loadingInterstitial && !BuildConfig.AD_INTERSTITIAL.isEmpty()) {
            loadingInterstitial = true;
            InterstitialAd.load(c, BuildConfig.AD_INTERSTITIAL, new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull InterstitialAd ad) {
                            interstitial = ad;
                            loadingInterstitial = false;
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError e) {
                            loadingInterstitial = false;
                        }
                    });
        }
        if (rewarded == null && !loadingRewarded && !BuildConfig.AD_REWARDED.isEmpty()) {
            loadingRewarded = true;
            RewardedAd.load(c, BuildConfig.AD_REWARDED, new AdRequest.Builder().build(),
                    new RewardedAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull RewardedAd ad) {
                            rewarded = ad;
                            loadingRewarded = false;
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError e) {
                            loadingRewarded = false;
                        }
                    });
        }
    }

    /** Shows an interstitial if one is due after a finished game, then runs {@code next}. */
    public static void afterGame(Activity a, Runnable next) {
        Progress p = Progress.get(a);
        boolean due = !p.premium() && p.gamesPlayed() >= FREE_GAMES
                && p.gamesPlayed() - p.lastAdGame() >= GAMES_BETWEEN;
        InterstitialAd ad = interstitial;
        if (!due || ad == null) {
            next.run();
            return;
        }
        interstitial = null;
        p.setLastAdGame(p.gamesPlayed());
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                preload(a.getApplicationContext());
                next.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                preload(a.getApplicationContext());
                next.run();
            }
        });
        ad.show(a);
    }

    public static boolean rewardedReady() {
        return rewarded != null;
    }

    /** Shows a rewarded ad; {@code onReward} runs only if the player watched it through. */
    public static void showRewarded(Activity a, Runnable onReward) {
        RewardedAd ad = rewarded;
        if (ad == null) return;
        rewarded = null;
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                preload(a.getApplicationContext());
                if (earned[0]) onReward.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                preload(a.getApplicationContext());
            }
        });
        ad.show(a, item -> earned[0] = true);
    }
}
