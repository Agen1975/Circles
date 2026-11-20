package com.game.cosmicbubbles;

import android.app.Application;

import com.google.android.gms.games.PlayGamesSdk;

/** App entry point: initialises Play Games Services (v2 signs players in automatically). */
public class CirclesApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PlayGamesSdk.initialize(this);
    }
}
